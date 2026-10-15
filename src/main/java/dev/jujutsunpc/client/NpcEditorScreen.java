package dev.jujutsunpc.client;

import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.network.CompatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** In-game editor for both NPC families; server validates every save. */
public final class NpcEditorScreen extends Screen {
    private static final String[] TABS = {"Techniques", "Energy & Recovery", "Domain Defense"};
    private final int target;
    private final NpcProfile profile;
    private int page;
    public NpcEditorScreen(int target, NpcProfile profile) {
        super(Component.literal("Jujutsu NPC Configuration"));
        this.target = target;
        this.profile = profile;
    }
    private void add(int x, int y, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
            action.run(); rebuildWidgets();
        }).bounds(x, y, 278, 20).build());
    }
    private void pair(int x,int y, String left, Runnable onLeft, String right, Runnable onRight) {
        addRenderableWidget(Button.builder(Component.literal(left),b->{onLeft.run();rebuildWidgets();})
                .bounds(x,y,136,20).build());
        addRenderableWidget(Button.builder(Component.literal(right),b->{onRight.run();rebuildWidgets();})
                .bounds(x+142,y,136,20).build());
    }
    @Override protected void init() {
        int x = width / 2 - 139, y = 43;
        if (page == 0) {
            add(x,y,"Technique: " + AbilityCatalog.technique(profile.technique).title(), () -> {
                List<AbilityCatalog.Technique> list = AbilityCatalog.ALL;
                int i=0;
                for (int j=0;j<list.size();j++) if (list.get(j).key().equals(profile.technique)) i=j;
                profile.setTechnique(list.get((i+1)%list.size()).key());
            });
            y += 26;
            for (AbilityCatalog.Move m : AbilityCatalog.technique(profile.technique).moves()) {
                String id=m.key();
                add(x,y,m.title()+": "+profile.mode(id),() ->
                        profile.setMoveMode(id,profile.mode(id).next()));
                y += 25;
            }
            add(x,y,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
        } else if (page == 1) {
            pair(x,y,"Max Energy -100",()->profile.maxEnergy=Math.max(100,profile.maxEnergy-100),
                    "Max Energy +100",()->profile.maxEnergy=Math.min(100000,profile.maxEnergy+100)); y+=26;
            pair(x,y,"Regen -1/s",()->profile.regenPerSecond=Math.max(0,profile.regenPerSecond-1),
                    "Regen +1/s",()->profile.regenPerSecond=Math.min(1000,profile.regenPerSecond+1)); y+=26;
            add(x,y,"RCT Healing: "+profile.rct,()->profile.rct=!profile.rct); y+=26;
            add(x,y,"Fatal-hit Recovery: "+profile.fatalRecovery,()->profile.fatalRecovery=!profile.fatalRecovery); y+=26;
            add(x,y,"Recovery Charges: "+profile.fatalRecoveryCharges,()->
                    profile.fatalRecoveryCharges=(profile.fatalRecoveryCharges+1)%6); y+=26;
            add(x,y,"Recovery Health: "+Math.round(profile.fatalRecoveryHealthFraction*100)+"%",()->
                    profile.fatalRecoveryHealthFraction=(profile.fatalRecoveryHealthFraction>=1f
                            ?0.25f:profile.fatalRecoveryHealthFraction+0.25f));
        } else {
            add(x,y,"Simple Domain: "+on(NpcProfile.Defence.SIMPLE_DOMAIN),()->
                    toggle(NpcProfile.Defence.SIMPLE_DOMAIN)); y+=26;
            add(x,y,"Falling Blossom Emotion: "+on(NpcProfile.Defence.FALLING_BLOSSOM_EMOTION),()->
                    toggle(NpcProfile.Defence.FALLING_BLOSSOM_EMOTION)); y+=26;
            add(x,y,"Domain Amplification: "+on(NpcProfile.Defence.DOMAIN_AMPLIFICATION),()->
                    toggle(NpcProfile.Defence.DOMAIN_AMPLIFICATION));
            // Hollow Wicker Basket deliberately omitted: not yet verified in JJK v51.
        }
        int bottom = height-26;
        pair(x,bottom-26,"< Previous",()->page=Math.floorMod(page-1,TABS.length),
                "Next >",()->page=(page+1)%TABS.length);
        addRenderableWidget(Button.builder(Component.literal("Save & Close"), b -> {
            CompatNetwork.saveFromClient(target,profile);
            onClose();
        }).bounds(x,bottom,278,20).build());
    }
    private String on(NpcProfile.Defence d) { return profile.defences.getOrDefault(d,false)?"Enabled":"Disabled"; }
    private void toggle(NpcProfile.Defence d) { profile.defences.put(d,!profile.defences.getOrDefault(d,false)); }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,11,0xFFFFFF);
        g.drawCenteredString(font,TABS[page],width/2,27,0xAACCFF);
        if (page==1) {
            g.drawCenteredString(font,"CE: "+(int)profile.maxEnergy+"   Regen: "+(int)profile.regenPerSecond+"/s",
                    width/2, height-78, 0xBBBBBB);
        }
        if (page==2) {
            g.drawCenteredString(font,"Uses original JJK status effects; domain clash support pending",
                    width/2,height-78,0xAAAAAA);
        }
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
