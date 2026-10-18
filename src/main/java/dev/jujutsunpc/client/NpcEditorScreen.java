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
 * v0.8: visible tabs across the TOP. Previous v0.7 buttons sat at screen
 * bottom and technique lists could overlap navigation on small GUI scales.
 * All move/defense settings use paginated controls with no scrolling needed.
 */
public final class NpcEditorScreen extends Screen {
    private static final String[] TABS={
        "1 Moves","2 Move Timers","3 RCT","4 Anti-Domain","5 Domain","6 Combat"
    };
    private final int target;
    private final NpcProfile profile;
    private int page,moveIndex,defenceIndex;
    private EditBox moveCooldown,moveCharge,rctActive,rctCooldown,defenceCooldown;

    public NpcEditorScreen(int target,NpcProfile profile){
        super(Component.literal("Jujutsu NPC Editor v0.8"));
        this.target=target;this.profile=profile;
    }
    private static int bounded(int x,int min,int max){return Math.max(min,Math.min(max,x));}
    private static int parse(EditBox field,int fallback,int low,int high){
        if(field==null)return fallback;
        try{return bounded(Integer.parseInt(field.getValue()),low,high);}
        catch(NumberFormatException e){return fallback;}
    }
    private List<AbilityCatalog.Move> moves(){return AbilityCatalog.technique(profile.technique).moves();}
    private AbilityCatalog.Move selectedMove(){
        List<AbilityCatalog.Move> list=moves();
        if(list.isEmpty())return null;
        moveIndex=Math.floorMod(moveIndex,list.size());
        return list.get(moveIndex);
    }
    private NpcProfile.Defence selectedDefence(){
        NpcProfile.Defence[] defs=NpcProfile.Defence.values();
        defenceIndex=Math.floorMod(defenceIndex,defs.length);
        return defs[defenceIndex];
    }
    private EditBox numeric(int x,int y,int initial,String hint){
        EditBox box=new EditBox(font,x,y,278,20,Component.literal(hint));
        box.setMaxLength(4);
        box.setFilter(s->s.matches("[0-9]{0,4}"));
        box.setValue(Integer.toString(initial));
        addRenderableWidget(box);
        return box;
    }
    private void commit(){
        AbilityCatalog.Move selected=selectedMove();
        if(selected!=null){
            if(moveCooldown!=null)profile.setCooldown(selected.key(),
                    parse(moveCooldown,profile.cooldown(selected.key()),0,3600));
            if(moveCharge!=null)profile.setCharge(selected.key(),
                    parse(moveCharge,profile.charge(selected.key()),0,120));
        }
        if(rctActive!=null)profile.rctActiveSeconds=parse(rctActive,profile.rctActiveSeconds,1,600);
        if(rctCooldown!=null)profile.rctCooldownSeconds=parse(rctCooldown,profile.rctCooldownSeconds,0,3600);
        if(defenceCooldown!=null)profile.setDefenceCooldown(selectedDefence(),
                parse(defenceCooldown,profile.defenceCooldown(selectedDefence()),0,3600));
    }
    private void action(int x,int y,int w,String label,Runnable task){
        addRenderableWidget(Button.builder(Component.literal(label),btn->{
            commit();
            task.run();
            rebuildWidgets();
        }).bounds(x,y,w,20).build());
    }
    private void wide(int x,int y,String label,Runnable task){action(x,y,278,label,task);}
    private void arrows(int x,int y,String back,Runnable previous,String next,Runnable following){
        action(x,y,136,back,previous);
        action(x+142,y,136,next,following);
    }
    @Override protected void init(){
        moveCooldown=null;moveCharge=null;rctActive=null;rctCooldown=null;defenceCooldown=null;
        int x=width/2-139;
        for(int i=0;i<TABS.length;i++){
            final int tab=i;
            int tabX=width/2-166+(i%3)*112;
            int tabY=21+(i/3)*25;
            action(tabX,tabY,108,(page==i?"[":"")+TABS[i]+(page==i?"]":""),
                    ()->page=tab);
        }
        int y=79;
        if(page==0){
            wide(x,y,"Technique: "+AbilityCatalog.technique(profile.technique).title(),()->{
                List<AbilityCatalog.Technique> all=AbilityCatalog.ALL;
                int index=0;
                for(int i=0;i<all.size();i++)
                    if(all.get(i).key().equals(profile.technique)){index=i;break;}
                profile.setTechnique(all.get((index+1)%all.size()).key());
                moveIndex=0;
            });
            if(selectedMove()!=null){
                arrows(x,113,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
                AbilityCatalog.Move move=selectedMove();
                wide(x,144,move.title()+": "+profile.mode(move.key()),
                        ()->profile.setMoveMode(move.key(),profile.mode(move.key()).next()));
            }
            wide(x,179,"Automatic combat: "+profile.autoCombat,
                    ()->profile.autoCombat=!profile.autoCombat);
        }else if(page==1){
            if(selectedMove()!=null){
                arrows(x,85,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
                AbilityCatalog.Move move=selectedMove();
                moveCooldown=numeric(x,135,profile.cooldown(move.key()),"Cooldown seconds");
                moveCharge=numeric(x,185,profile.charge(move.key()),"Charge seconds");
            }else wide(x,99,"Select a technique on the Moves tab",()->page=0);
        }else if(page==2){
            wide(x,83,"RCT Healing: "+profile.rct,()->profile.rct=!profile.rct);
            rctActive=numeric(x,135,profile.rctActiveSeconds,"RCT active seconds");
            rctCooldown=numeric(x,185,profile.rctCooldownSeconds,"RCT cooldown seconds");
        }else if(page==3){
            arrows(x,83,"< Previous Defense",()->defenceIndex--,"Next Defense >",()->defenceIndex++);
            NpcProfile.Defence choice=selectedDefence();
            wide(x,113,choice.name().replace('_',' ')+": "+
                    (profile.defences.getOrDefault(choice,false)?"Enabled":"Disabled"),
                    ()->profile.defences.put(choice,!profile.defences.getOrDefault(choice,false)));
            defenceCooldown=numeric(x,169,profile.defenceCooldown(choice),"Defense cooldown seconds");
        }else if(page==4){
            if(NpcProfile.allowsOpenDomain(profile.technique)){
                wide(x,86,"Barrier: "+(profile.domainOpen?"OPEN (no shell)":"CLOSED (shell)"),
                        ()->profile.domainOpen=!profile.domainOpen);
                wide(x,118,"Shrine Appearance: "+
                        (profile.shrineModel==NpcProfile.ShrineModel.NORMAL
                                ?"NORMAL SHRINE":"MUTATED SHRINE"),
                        ()->profile.shrineModel=profile.shrineModel.next());
            }else{
                wide(x,97,"Select SHRINE on Moves tab to edit its domain",()->page=0);
            }
        }else{
            wide(x,80,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
            wide(x,106,"Hold after fighting: "+profile.holdPositionWhenIdle,
                    ()->profile.holdPositionWhenIdle=!profile.holdPositionWhenIdle);
            wide(x,132,"Allow combat damage: "+profile.allowCombatDamage,
                    ()->profile.allowCombatDamage=!profile.allowCombatDamage);
            wide(x,158,"Fatal-hit recovery: "+profile.fatalRecovery,
                    ()->profile.fatalRecovery=!profile.fatalRecovery);
            wide(x,184,"Fatal recovery charges: "+profile.fatalRecoveryCharges,
                    ()->profile.fatalRecoveryCharges=(profile.fatalRecoveryCharges+1)%6);
        }
        addRenderableWidget(Button.builder(Component.literal("Save NPC Settings"),button->{
            commit();
            CompatNetwork.saveFromClient(target,profile);
            onClose();
        }).bounds(x,height-25,278,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        renderBackground(graphics);
        graphics.drawCenteredString(font,title,width/2,7,0xFFFFFF);
        int x=width/2-139;
        if(page==0&&selectedMove()!=null){
            graphics.drawCenteredString(font,"Move "+(moveIndex+1)+"/"+moves().size()
                +" - click to change its mode",width/2,169,0xBBBBBB);
        }else if(page==1&&selectedMove()!=null){
            graphics.drawCenteredString(font,selectedMove().title(),width/2,114,0xFFFFFF);
            graphics.drawString(font,"Cooldown (seconds, 0-3600)",x,121,0xAAAAAA);
            graphics.drawString(font,"Charge before casting (seconds, 0-120)",x,171,0xAAAAAA);
        }else if(page==2){
            graphics.drawString(font,"RCT stays active for (seconds, 1-600)",x,121,0xAAAAAA);
            graphics.drawString(font,"Wait before next RCT (seconds, 0-3600)",x,171,0xAAAAAA);
        }else if(page==3){
            graphics.drawString(font,"Defense cooldown (seconds, 0-3600)",x,154,0xAAAAAA);
            if(selectedDefence()==NpcProfile.Defence.HOLLOW_WICKER_BASKET)
                graphics.drawCenteredString(font,"Hollow Wicker Basket not implemented in v51",
                    width/2,197,0xFF8888);
        }else if(page==4){
            graphics.drawCenteredString(font,"Barrier and shrine model are independent.",
                    width/2,154,0xBBBBBB);
            graphics.drawCenteredString(font,"Appearance changes which original v51 shrine spawns.",
                    width/2,168,0xBBBBBB);
            graphics.drawCenteredString(font,"Test on a world backup.",width/2,183,0xBBBBBB);
        }
        super.render(graphics,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
