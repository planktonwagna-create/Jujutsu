package dev.jujutsunpc.client;

import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.game.ItemMoveCatalog;
import dev.jujutsunpc.network.CompatNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Objects;

/**
 * Eight short, visible top tabs. Item Moves exists only when this entity
 * actually holds a v51 cursed tool with a native special-use procedure.
 */
public final class NpcEditorScreen extends Screen {
    private static final String[] TABS={
        "Moves","Timers","RCT","Anti-Domain","Domain","Combat","Melee","Item Moves"
    };
    private final int target;
    private final NpcProfile profile;
    private int page,moveIndex,defenceIndex,meleeIndex,itemIndex;
    private EditBox moveCooldown,moveCharge,rctActive,rctCooldown,defenceCooldown,meleeCooldown,itemCooldown;
    private String equipmentState="";

    public NpcEditorScreen(int target,NpcProfile profile){
        super(Component.literal("Jujutsu NPC Editor v0.9"));
        this.target=target;this.profile=profile;
    }
    private static int bounded(int x,int min,int max){return Math.max(min,Math.min(max,x));}
    private static int parse(EditBox f,int fallback,int low,int high) {
        if(f==null)return fallback;
        try{return bounded(Integer.parseInt(f.getValue()),low,high);}
        catch(NumberFormatException ignored){return fallback;}
    }
    private LivingEntity entity() {
        if(Minecraft.getInstance().level==null)return null;
        Entity value=Minecraft.getInstance().level.getEntity(target);
        return value instanceof LivingEntity living?living:null;
    }
    private List<AbilityCatalog.Move> items() {
        LivingEntity npc=entity();
        return npc==null?List.of():ItemMoveCatalog.equipped(npc);
    }
    private int tabCount(){return items().isEmpty()?7:8;}
    private String itemKey(AbilityCatalog.Move move) {
        LivingEntity npc=entity();
        return npc==null?move.key():ItemMoveCatalog.key(npc,move);
    }
    private List<AbilityCatalog.Move> allMoves(){
        return AbilityCatalog.technique(profile.technique).moves();
    }
    private List<AbilityCatalog.Move> moves(){
        return allMoves().stream().filter(m->!AbilityCatalog.isBasic(m)).toList();
    }
    private List<AbilityCatalog.Move> basics(){
        return allMoves().stream().filter(AbilityCatalog::isBasic).toList();
    }
    private AbilityCatalog.Move selectedMove(){
        List<AbilityCatalog.Move> list=moves();
        return list.isEmpty()?null:list.get(Math.floorMod(moveIndex,list.size()));
    }
    private AbilityCatalog.Move selectedBasic(){
        List<AbilityCatalog.Move> list=basics();
        return list.isEmpty()?null:list.get(Math.floorMod(meleeIndex,list.size()));
    }
    private AbilityCatalog.Move selectedItem(){
        List<AbilityCatalog.Move> list=items();
        return list.isEmpty()?null:list.get(Math.floorMod(itemIndex,list.size()));
    }
    private NpcProfile.Defence selectedDefence(){
        NpcProfile.Defence[] values=NpcProfile.Defence.values();
        return values[Math.floorMod(defenceIndex,values.length)];
    }
    private EditBox numeric(int x,int y,int value,String hint){
        EditBox e=new EditBox(font,x,y,278,20,Component.literal(hint));
        e.setMaxLength(4);
        e.setFilter(s->s.matches("[0-9]{0,4}"));
        e.setValue(Integer.toString(value));
        addRenderableWidget(e);
        return e;
    }
    private void commit(){
        AbilityCatalog.Move selected=selectedMove();
        if(selected!=null) {
            if(moveCooldown!=null)profile.setCooldown(selected.key(),
                    parse(moveCooldown,profile.cooldown(selected.key()),0,3600));
            if(moveCharge!=null)profile.setCharge(selected.key(),
                    parse(moveCharge,profile.charge(selected.key()),0,120));
        }
        if(rctActive!=null)profile.rctActiveSeconds=
                parse(rctActive,profile.rctActiveSeconds,1,600);
        if(rctCooldown!=null)profile.rctCooldownSeconds=
                parse(rctCooldown,profile.rctCooldownSeconds,0,3600);
        if(defenceCooldown!=null)profile.setDefenceCooldown(selectedDefence(),
                parse(defenceCooldown,profile.defenceCooldown(selectedDefence()),0,3600));
        AbilityCatalog.Move basic=selectedBasic();
        if(basic!=null&&meleeCooldown!=null)profile.setCooldown(basic.key(),
                parse(meleeCooldown,profile.cooldown(basic.key()),0,3600));
        AbilityCatalog.Move item=selectedItem();
        if(item!=null&&itemCooldown!=null){
            String id=itemKey(item);
            profile.setItemCooldown(id,parse(itemCooldown,profile.itemCooldown(id),0,3600));
        }
    }
    private void action(int x,int y,int width,String name,Runnable callback){
        addRenderableWidget(Button.builder(Component.literal(name),b->{
            commit();callback.run();rebuildWidgets();
        }).bounds(x,y,width,20).build());
    }
    private void wide(int x,int y,String label,Runnable callback){action(x,y,278,label,callback);}
    private void pair(int x,int y,String left,Runnable prev,String right,Runnable next){
        action(x,y,136,left,prev);action(x+142,y,136,right,next);
    }
    @Override protected void init(){
        moveCooldown=null;moveCharge=null;rctActive=null;rctCooldown=null;
        defenceCooldown=null;meleeCooldown=null;itemCooldown=null;
        equipmentState=equipmentSignature();
        page=Math.min(page,tabCount()-1);
        int x=width/2-139;
        for(int i=0;i<tabCount();i++){
            final int dest=i;
            action(width/2-166+(i%4)*83,21+(i/4)*24,80,
                    page==i?"["+TABS[i]+"]":TABS[i],()->page=dest);
        }
        if(page==0){
            List<AbilityCatalog.Technique> all=AbilityCatalog.ALL;
            pair(x,79,"< Technique",()->changeTechnique(-1),"Technique >",()->changeTechnique(1));
            pair(x,118,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
            AbilityCatalog.Move move=selectedMove();
            if(move!=null)wide(x,146,move.title()+": "+profile.mode(move.key()),
                    ()->profile.setMoveMode(move.key(),profile.mode(move.key()).next()));
            wide(x,181,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
        }else if(page==1){
            AbilityCatalog.Move move=selectedMove();
            if(move!=null){
                pair(x,77,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
                moveCooldown=numeric(x,134,profile.cooldown(move.key()),"Cooldown seconds");
                if(AbilityCatalog.supportsCharge(move))
                    moveCharge=numeric(x,169,profile.charge(move.key()),"Held-key seconds");
                if(AbilityCatalog.supportsCrouch(move))
                    wide(x,193,"Crouch: "+profile.crouch(move.key()),
                            ()->profile.setCrouch(move.key(),profile.crouch(move.key()).next()));
            }else wide(x,100,"Choose a cursed technique first",()->page=0);
        }else if(page==2){
            wide(x,81,"RCT Healing: "+profile.rct,()->profile.rct=!profile.rct);
            rctActive=numeric(x,135,profile.rctActiveSeconds,"RCT active seconds");
            rctCooldown=numeric(x,185,profile.rctCooldownSeconds,"RCT cooldown seconds");
        }else if(page==3){
            pair(x,79,"< Previous Defense",()->defenceIndex--,"Next Defense >",()->defenceIndex++);
            NpcProfile.Defence choice=selectedDefence();
            wide(x,113,choice.name().replace('_',' ')+": "+
                    (profile.defences.getOrDefault(choice,false)?"Enabled":"Disabled"),
                    ()->profile.defences.put(choice,!profile.defences.getOrDefault(choice,false)));
            defenceCooldown=numeric(x,169,profile.defenceCooldown(choice),"Defense cooldown seconds");
        }else if(page==4){
            if(NpcProfile.allowsOpenDomain(profile.technique)){
                wide(x,86,"Barrier: "+(profile.domainOpen?"OPEN":"CLOSED"),
                        ()->profile.domainOpen=!profile.domainOpen);
                wide(x,119,"Shrine Model: "+profile.shrineModel,
                        ()->profile.shrineModel=profile.shrineModel.next());
            }else wide(x,90,"Shrine-specific options only",()->page=0);
        }else if(page==5){
            wide(x,78,"Automatic combat: "+profile.autoCombat,()->profile.autoCombat=!profile.autoCombat);
            wide(x,104,"Hold after fighting: "+profile.holdPositionWhenIdle,
                    ()->profile.holdPositionWhenIdle=!profile.holdPositionWhenIdle);
            wide(x,130,"Allow combat damage: "+profile.allowCombatDamage,
                    ()->profile.allowCombatDamage=!profile.allowCombatDamage);
            wide(x,156,"Fatal-hit recovery: "+profile.fatalRecovery,
                    ()->profile.fatalRecovery=!profile.fatalRecovery);
        }else if(page==6){
            pair(x,79,"< Previous Melee",()->meleeIndex--,"Next Melee >",()->meleeIndex++);
            AbilityCatalog.Move move=selectedBasic();
            if(move!=null){
                wide(x,117,move.title()+": "+profile.mode(move.key()),
                        ()->profile.setMoveMode(move.key(),profile.mode(move.key()).next()));
                meleeCooldown=numeric(x,165,profile.cooldown(move.key()),"Melee cooldown seconds");
            }
        }else if(page==7){
            AbilityCatalog.Move move=selectedItem();
            if(move!=null){
                pair(x,79,"< Previous Item Move",()->itemIndex--,"Next Item Move >",()->itemIndex++);
                String id=itemKey(move);
                wide(x,118,move.title()+": "+profile.itemMode(id),
                        ()->profile.setItemMode(id,profile.itemMode(id).next()));
                itemCooldown=numeric(x,166,profile.itemCooldown(id),"Item cooldown seconds");
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Save NPC Settings"),b->{
            commit();
            CompatNetwork.saveFromClient(target,profile);
            onClose();
        }).bounds(x,height-25,278,20).build());
    }
    private void changeTechnique(int delta){
        List<AbilityCatalog.Technique> all=AbilityCatalog.ALL;
        int i=0;
        for(int j=0;j<all.size();j++)if(all.get(j).key().equals(profile.technique)){i=j;break;}
        profile.setTechnique(all.get(Math.floorMod(i+delta,all.size())).key());
        moveIndex=0;
    }
    private String equipmentSignature(){
        LivingEntity npc=entity();
        return npc==null?"":String.valueOf(npc.getMainHandItem().getItem())+"|"
                +String.valueOf(npc.getOffhandItem().getItem());
    }
    @Override public void tick(){
        super.tick();
        String current=equipmentSignature();
        if(!Objects.equals(equipmentState,current)){
            commit();
            if(page==7 && items().isEmpty())page=6;
            rebuildWidgets();
        }
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        renderBackground(graphics);
        graphics.drawCenteredString(font,title,width/2,7,0xFFFFFF);
        int x=width/2-139;
        if(page==0){
            graphics.drawCenteredString(font,AbilityCatalog.technique(profile.technique).title(),
                    width/2,105,0xFFFFFF);
            graphics.drawCenteredString(font,"Move "+(moves().isEmpty()?0:Math.floorMod(moveIndex,moves().size())+1)
                    +"/"+moves().size(),width/2,172,0xAAAAAA);
        }else if(page==1 && selectedMove()!=null){
            AbilityCatalog.Move m=selectedMove();
            graphics.drawCenteredString(font,m.title(),width/2,107,0xFFFFFF);
            graphics.drawString(font,"Cooldown (seconds)",x,122,0xAAAAAA);
            graphics.drawString(font,AbilityCatalog.supportsCharge(m)
                    ?"Hold key (seconds; 0 = tap)":"This move has no recognized held-key charge",x,157,0xAAAAAA);
            if(!AbilityCatalog.supportsCrouch(m))
                graphics.drawCenteredString(font,"No crouching branch found for this move",
                        width/2,195,0xAAAAAA);
        }else if(page==2){
            graphics.drawString(font,"RCT active duration in seconds",x,121,0xAAAAAA);
            graphics.drawString(font,"RCT cooldown duration in seconds",x,172,0xAAAAAA);
        }else if(page==3){
            graphics.drawString(font,"Defense cooldown seconds",x,154,0xAAAAAA);
            if(selectedDefence()==NpcProfile.Defence.HOLLOW_WICKER_BASKET)
                graphics.drawCenteredString(font,"Hollow Wicker Basket is not implemented",width/2,198,0xFF8888);
        }else if(page==4){
            graphics.drawCenteredString(font,"Barrier and Shrine appearance are separate.",
                    width/2,160,0xAAAAAA);
        }else if(page==6 && selectedBasic()!=null){
            graphics.drawString(font,"Cooldown seconds for "+selectedBasic().title(),x,152,0xAAAAAA);
        }else if(page==7 && selectedItem()!=null){
            graphics.drawString(font,"Right-click item ability cooldown (seconds)",x,153,0xAAAAAA);
            graphics.drawCenteredString(font,"Native JujutsuCraft use effect - not repeated every tick",
                    width/2,205,0xBBBBBB);
        }
        super.render(graphics,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
