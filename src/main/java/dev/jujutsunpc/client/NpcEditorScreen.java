package dev.jujutsunpc.client;

import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.network.CompatNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * In-game per-NPC seconds editors. Numeric inputs are committed on tab/move
 * navigation and on Save. Server independently validates all NBT values.
 */
public final class NpcEditorScreen extends Screen {
    private static final String[] TABS={
        "Techniques","Move Timing","RCT & Recovery","Anti-Domain Timing","Domain Variant","Combat"
    };
    private final int target;
    private final NpcProfile profile;
    private int page,moveIndex,defenceIndex;
    private EditBox moveCooldownField,moveChargeField,rctActiveField,rctCooldownField,defenceCooldownField;

    public NpcEditorScreen(int target,NpcProfile profile){
        super(Component.literal("Jujutsu NPC Configuration"));
        this.target=target;this.profile=profile;
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
    private static int parse(EditBox box,int fallback,int min,int max){
        if(box==null)return fallback;
        try{return clamp(Integer.parseInt(box.getValue()),min,max);}
        catch(NumberFormatException ex){return fallback;}
    }
    private List<AbilityCatalog.Move> moves(){return AbilityCatalog.technique(profile.technique).moves();}
    private AbilityCatalog.Move selectedMove(){
        List<AbilityCatalog.Move> list=moves();
        if(list.isEmpty())return null;
        moveIndex=Math.floorMod(moveIndex,list.size());
        return list.get(moveIndex);
    }
    private NpcProfile.Defence selectedDefence(){
        NpcProfile.Defence[] values=NpcProfile.Defence.values();
        defenceIndex=Math.floorMod(defenceIndex,values.length);
        return values[defenceIndex];
    }
    private EditBox seconds(int x,int y,int value,String label){
        EditBox b=new EditBox(font,x,y,278,20,Component.literal(label));
        b.setMaxLength(4);
        b.setFilter(valueText -> valueText.matches("[0-9]{0,4}"));
        b.setValue(Integer.toString(value));
        addRenderableWidget(b);
        return b;
    }
    private void commitFields(){
        AbilityCatalog.Move move=selectedMove();
        if(move!=null){
            if(moveCooldownField!=null)profile.setCooldown(move.key(),
                    parse(moveCooldownField,profile.cooldown(move.key()),0,3600));
            if(moveChargeField!=null)profile.setCharge(move.key(),
                    parse(moveChargeField,profile.charge(move.key()),0,120));
        }
        if(rctActiveField!=null)profile.rctActiveSeconds=
                parse(rctActiveField,profile.rctActiveSeconds,1,600);
        if(rctCooldownField!=null)profile.rctCooldownSeconds=
                parse(rctCooldownField,profile.rctCooldownSeconds,0,3600);
        if(defenceCooldownField!=null){
            NpcProfile.Defence defence=selectedDefence();
            profile.setDefenceCooldown(defence,parse(defenceCooldownField,
                    profile.defenceCooldown(defence),0,3600));
        }
    }
    private void add(int x,int y,String title,Runnable action){
        addRenderableWidget(Button.builder(Component.literal(title),b -> {
            commitFields();
            action.run();
            rebuildWidgets();
        }).bounds(x,y,278,20).build());
    }
    private void pair(int x,int y,String a,Runnable left,String b,Runnable right){
        addRenderableWidget(Button.builder(Component.literal(a),button -> {
            commitFields();left.run();rebuildWidgets();
        }).bounds(x,y,136,20).build());
        addRenderableWidget(Button.builder(Component.literal(b),button -> {
            commitFields();right.run();rebuildWidgets();
        }).bounds(x+142,y,136,20).build());
    }
    @Override protected void init(){
        moveCooldownField=null;moveChargeField=null;rctActiveField=null;
        rctCooldownField=null;defenceCooldownField=null;
        int x=width/2-139,y=43;
        if(page==0){
            add(x,y,"Technique: "+AbilityCatalog.technique(profile.technique).title(),()->{
                List<AbilityCatalog.Technique> list=AbilityCatalog.ALL;
                int i=0;
                for(int j=0;j<list.size();j++)if(list.get(j).key().equals(profile.technique))i=j;
                profile.setTechnique(list.get((i+1)%list.size()).key());
                moveIndex=0;
            });
            y+=26;
            for(AbilityCatalog.Move move:moves()){
                String id=move.key();
                add(x,y,move.title()+": "+profile.mode(id),()->
                        profile.setMoveMode(id,profile.mode(id).next()));
                y+=25;
            }
            add(x,y,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
        }else if(page==1){
            if(selectedMove()!=null){
                pair(x,55,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
                AbilityCatalog.Move move=selectedMove();
                moveCooldownField=seconds(x,111,profile.cooldown(move.key()),"Cooldown seconds");
                moveChargeField=seconds(x,165,profile.charge(move.key()),"Charge seconds");
            }else{
                add(x,90,"Choose a cursed technique first",()->page=0);
            }
        }else if(page==2){
            add(x,47,"RCT Healing: "+profile.rct,()->profile.rct=!profile.rct);
            rctActiveField=seconds(x,109,profile.rctActiveSeconds,"RCT active seconds");
            rctCooldownField=seconds(x,166,profile.rctCooldownSeconds,"RCT cooldown seconds");
            add(x,210,"Fatal-hit recovery: "+profile.fatalRecovery,
                    ()->profile.fatalRecovery=!profile.fatalRecovery);
            add(x,236,"Recovery Charges: "+profile.fatalRecoveryCharges,
                    ()->profile.fatalRecoveryCharges=(profile.fatalRecoveryCharges+1)%6);
            add(x,262,"Recovery Health: "+Math.round(profile.fatalRecoveryHealthFraction*100)+"%",
                    ()->profile.fatalRecoveryHealthFraction=profile.fatalRecoveryHealthFraction>=1f
                            ?0.25f:profile.fatalRecoveryHealthFraction+0.25f);
        }else if(page==3){
            pair(x,55,"< Previous Defense",()->defenceIndex--,"Next Defense >",()->defenceIndex++);
            NpcProfile.Defence defence=selectedDefence();
            add(x,109,defence.name().replace('_',' ')+": "+
                    (profile.defences.getOrDefault(defence,false)?"Enabled":"Disabled"),
                    ()->profile.defences.put(defence,!profile.defences.getOrDefault(defence,false)));
            defenceCooldownField=seconds(x,169,profile.defenceCooldown(defence),"Cooldown seconds");
        }else if(page==4){
            if(NpcProfile.allowsOpenDomain(profile.technique)){
                add(x,70,"Malevolent Shrine: "+(profile.domainOpen?"Open":"Closed"),
                        ()->profile.domainOpen=!profile.domainOpen);
            }else{
                add(x,70,"No selectable domain variant for this technique",()->{});
            }
        }else if(page==5){
            add(x,48,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
            add(x,75,"Hold position after fighting: "+profile.holdPositionWhenIdle,
                    ()->profile.holdPositionWhenIdle=!profile.holdPositionWhenIdle);
            add(x,102,"Allow combat damage: "+profile.allowCombatDamage,
                    ()->profile.allowCombatDamage=!profile.allowCombatDamage);
        }
        int bottom=height-26;
        pair(x,bottom-26,"< Previous Tab",()->page=Math.floorMod(page-1,TABS.length),
                "Next Tab >",()->page=(page+1)%TABS.length);
        addRenderableWidget(Button.builder(Component.literal("Save & Close"),b -> {
            commitFields();
            CompatNetwork.saveFromClient(target,profile);
            onClose();
        }).bounds(x,bottom,278,20).build());
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float dt){
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,9,0xFFFFFF);
        g.drawCenteredString(font,TABS[page],width/2,26,0xAACCFF);
        int x=width/2-139;
        if(page==1&&selectedMove()!=null){
            g.drawCenteredString(font,selectedMove().title(),width/2,84,0xEEEEEE);
            g.drawString(font,"Move cooldown (0–3600 seconds)",x,99,0xAAAAAA);
            g.drawString(font,"Charge before attack (0–120 seconds)",x,153,0xAAAAAA);
        }else if(page==2){
            g.drawString(font,"RCT can heal for this many seconds (1–600)",x,97,0xAAAAAA);
            g.drawString(font,"Then wait (0–3600 seconds)",x,154,0xAAAAAA);
        }else if(page==3){
            g.drawCenteredString(font,selectedDefence().name().replace('_',' '),width/2,86,0xEEEEEE);
            g.drawString(font,"Defense cooldown (0–3600 seconds)",x,155,0xAAAAAA);
            if(selectedDefence()==NpcProfile.Defence.HOLLOW_WICKER_BASKET)
                g.drawString(font,"Hollow Wicker Basket is not supported in v51",x,207,0xFF8888);
        }else if(page==4){
            g.drawCenteredString(font,"Only Shrine has a verified open barrier path in v51",
                    width/2,108,0xAAAAAA);
            g.drawCenteredString(font,"Open/closed behavior still requires an in-game test",
                    width/2,123,0xAAAAAA);
        }
        super.render(g,mouseX,mouseY,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
}
