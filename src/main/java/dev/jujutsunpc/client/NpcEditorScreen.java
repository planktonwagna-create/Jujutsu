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
    private boolean editingMoveDamage=false,editingDomainDamage=false,advancedCombat=false,itemEditingDamage=false;
    private EditBox moveCooldown,moveCharge,moveDamage,rctActive,rctCooldown,defenceCooldown,meleeCooldown,itemCooldown,itemDamage,domainHealth,domainDamage,dashCooldown;
    private String equipmentState="";

    public NpcEditorScreen(int target,NpcProfile profile){
        super(Component.literal("Jujutsu NPC Editor v0.13"));
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
        if(moveDamage!=null && selected!=null)
            profile.setDamage(selected.key(),parse(moveDamage,(int)Math.max(0,profile.damage(selected.key())),0,10000));
        if(domainDamage!=null)profile.domainSureHitDamage=
            parse(domainDamage,(int)Math.max(0,profile.domainSureHitDamage),0,10000);
        if(dashCooldown!=null)profile.backDashCooldownSeconds=
            parse(dashCooldown,profile.backDashCooldownSeconds,0,3600);
        if(rctActive!=null)profile.rctActiveSeconds=
                parse(rctActive,profile.rctActiveSeconds,1,600);
        if(rctCooldown!=null)profile.rctCooldownSeconds=
                parse(rctCooldown,profile.rctCooldownSeconds,0,3600);
        if(defenceCooldown!=null)profile.setDefenceCooldown(selectedDefence(),
                parse(defenceCooldown,profile.defenceCooldown(selectedDefence()),0,3600));
        if(domainHealth!=null)profile.domainHealthThreshold=
                parse(domainHealth,profile.domainHealthThreshold,1,100);
        AbilityCatalog.Move basic=selectedBasic();
        if(basic!=null&&meleeCooldown!=null)profile.setCooldown(basic.key(),
                parse(meleeCooldown,profile.cooldown(basic.key()),0,3600));
        AbilityCatalog.Move item=selectedItem();
        if(item!=null&&itemCooldown!=null){
            String id=itemKey(item);
            profile.setItemCooldown(id,parse(itemCooldown,profile.itemCooldown(id),0,3600));
        }
        if(item!=null&&itemDamage!=null){
            String key=itemKey(item);
            profile.setItemDamage(key,parse(itemDamage,(int)Math.max(0,profile.itemDamage(key)),0,10000));
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
        defenceCooldown=null;meleeCooldown=null;itemCooldown=null;domainHealth=null;
        moveDamage=null;domainDamage=null;dashCooldown=null;itemDamage=null;
        equipmentState=equipmentSignature();
        page=Math.min(page,tabCount()-1);
        int x=width/2-139;
        for(int i=0;i<tabCount();i++){
            final int tab=i;
            action(width/2-166+(i%4)*83,21+(i/4)*24,80,
                page==i?"["+TABS[i]+"]":TABS[i],()->page=tab);
        }

        if(page==0){
            pair(x,79,"< Technique",()->changeTechnique(-1),"Technique >",()->changeTechnique(1));
            pair(x,125,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
            AbilityCatalog.Move move=selectedMove();
            if(move!=null)wide(x,157,"Move mode: "+profile.mode(move.key()),
                    ()->profile.setMoveMode(move.key(),profile.mode(move.key()).next()));
            wide(x,197,"Automatic combat: "+profile.autoCombat,
                    ()->profile.autoCombat=!profile.autoCombat);

        }else if(page==1){
            AbilityCatalog.Move move=selectedMove();
            if(move!=null){
                pair(x,79,"< Previous Move",()->moveIndex--,"Next Move >",()->moveIndex++);
                wide(x,130,editingMoveDamage?"Back to Cooldown / Charge":"Edit Move Damage",
                        ()->editingMoveDamage=!editingMoveDamage);
                if(editingMoveDamage){
                    wide(x,166,profile.damage(move.key())<0?"Damage: Original JJK":"Damage: Custom",
                            ()->{if(profile.damage(move.key())<0)profile.setDamage(move.key(),10);
                                 else profile.moveDamage.remove(move.key());});
                    if(profile.damage(move.key())>=0)
                        moveDamage=numeric(x,218,(int)profile.damage(move.key()),"Move damage");
                }else{
                    moveCooldown=numeric(x,176,profile.cooldown(move.key()),"Cooldown (seconds)");
                    if(AbilityCatalog.supportsCharge(move))
                        moveCharge=numeric(x,225,profile.charge(move.key()),"Held key (seconds)");
                    if(AbilityCatalog.supportsCrouch(move))
                        wide(x,267,"Crouching variation: "+profile.crouch(move.key()),
                            ()->profile.setCrouch(move.key(),profile.crouch(move.key()).next()));
                }
            }

        }else if(page==2){
            wide(x,88,"RCT healing: "+profile.rct,()->profile.rct=!profile.rct);
            rctActive=numeric(x,151,profile.rctActiveSeconds,"RCT active seconds");
            rctCooldown=numeric(x,207,profile.rctCooldownSeconds,"RCT cooldown seconds");

        }else if(page==3){
            pair(x,83,"< Previous Defense",()->defenceIndex--,"Next Defense >",()->defenceIndex++);
            NpcProfile.Defence defense=selectedDefence();
            wide(x,143,"Enabled: "+profile.defences.getOrDefault(defense,false),
                    ()->profile.defences.put(defense,!profile.defences.getOrDefault(defense,false)));
            defenceCooldown=numeric(x,204,profile.defenceCooldown(defense),"Defense cooldown seconds");

        }else if(page==4){
            wide(x,86,editingDomainDamage?"Back to Domain Settings":"Edit Sure-Hit Damage",
                    ()->editingDomainDamage=!editingDomainDamage);
            if(editingDomainDamage){
                wide(x,131,profile.domainSureHitDamage<0?"Sure-hit: Original JJK":"Sure-hit: Custom",
                    ()->profile.domainSureHitDamage=profile.domainSureHitDamage<0?15:-1);
                if(profile.domainSureHitDamage>=0)
                    domainDamage=numeric(x,193,(int)profile.domainSureHitDamage,"Domain sure-hit damage");
            }else{
                domainHealth=numeric(x,153,profile.domainHealthThreshold,"Domain health threshold %");
                if(NpcProfile.allowsOpenDomain(profile.technique))
                    wide(x,194,"Barrier: "+(profile.domainOpen?"OPEN":"CLOSED"),
                        ()->profile.domainOpen=!profile.domainOpen);
                if("shrine".equals(profile.technique))
                    wide(x,231,"Shrine appearance: "+profile.shrineModel,
                        ()->profile.shrineModel=profile.shrineModel.next());
            }

        }else if(page==5){
            wide(x,83,advancedCombat?"Back to General Combat":"Backdash Settings",
                    ()->advancedCombat=!advancedCombat);
            if(advancedCombat){
                wide(x,136,"Backdash: "+profile.backDash,()->profile.backDash=!profile.backDash);
                dashCooldown=numeric(x,202,profile.backDashCooldownSeconds,"Backdash cooldown seconds");
            }else{
                wide(x,128,"Automatic combat: "+profile.autoCombat,
                    ()->profile.autoCombat=!profile.autoCombat);
                wide(x,160,"Hold after fighting: "+profile.holdPositionWhenIdle,
                    ()->profile.holdPositionWhenIdle=!profile.holdPositionWhenIdle);
                wide(x,192,"Allow combat damage: "+profile.allowCombatDamage,
                    ()->profile.allowCombatDamage=!profile.allowCombatDamage);
                wide(x,224,"Fatal-hit recovery: "+profile.fatalRecovery,
                    ()->profile.fatalRecovery=!profile.fatalRecovery);
            }

        }else if(page==6){
            pair(x,84,"< Previous Melee",()->meleeIndex--,"Next Melee >",()->meleeIndex++);
            AbilityCatalog.Move move=selectedBasic();
            if(move!=null){
                wide(x,144,"Attack mode: "+profile.mode(move.key()),
                    ()->profile.setMoveMode(move.key(),profile.mode(move.key()).next()));
                meleeCooldown=numeric(x,204,profile.cooldown(move.key()),"Melee cooldown seconds");
            }

        }else if(page==7){
            AbilityCatalog.Move move=selectedItem();
            if(move!=null){
                pair(x,83,"< Previous Item Move",()->itemIndex--,"Next Item Move >",()->itemIndex++);
                String id=itemKey(move);
                wide(x,137,"Item ability mode: "+profile.itemMode(id),
                    ()->profile.setItemMode(id,profile.itemMode(id).next()));
                wide(x,176,itemEditingDamage?"Back to Item Cooldown":"Edit Item Damage",
                    ()->itemEditingDamage=!itemEditingDamage);
                if(itemEditingDamage){
                    wide(x,211,profile.itemDamage(id)<0?"Damage: Original JJK":"Damage: Custom",
                        ()->{if(profile.itemDamage(id)<0)profile.setItemDamage(id,10);
                             else profile.itemMoveDamage.remove(id);});
                    if(profile.itemDamage(id)>=0)
                        itemDamage=numeric(x,267,(int)profile.itemDamage(id),"Item damage");
                }else{
                    itemCooldown=numeric(x,230,profile.itemCooldown(id),"Item cooldown seconds");
                }
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
    private void heading(GuiGraphics graphics,String content,int y){
        graphics.drawCenteredString(font,font.plainSubstrByWidth(content,278),width/2,y,0xEEEEEE);
    }
    private void fieldLabel(GuiGraphics graphics,String label,int y){
        graphics.drawString(font,label,width/2-139,y,0xB6BEC9);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        renderBackground(graphics);
        graphics.drawCenteredString(font,title,width/2,7,0xFFFFFF);
        if(page==0){
            heading(graphics,AbilityCatalog.technique(profile.technique).title(),107);
            AbilityCatalog.Move move=selectedMove();
            if(move!=null)heading(graphics,"Move "+(Math.floorMod(moveIndex,moves().size())+1)
                    +"/"+moves().size()+": "+move.title(),149);
        }else if(page==1 && selectedMove()!=null){
            AbilityCatalog.Move move=selectedMove();
            heading(graphics,move.title(),108);
            if(editingMoveDamage){
                if(profile.damage(move.key())>=0)fieldLabel(graphics,"Damage (health points)",204);
            }else{
                fieldLabel(graphics,"Cooldown in seconds",163);
                if(AbilityCatalog.supportsCharge(move))
                    fieldLabel(graphics,"Hold activation key in seconds (0 = tap)",211);
                else heading(graphics,"No held-key charge for this move",217);
                if(!AbilityCatalog.supportsCrouch(move))
                    heading(graphics,"No crouching variant detected",271);
            }
        }else if(page==2){
            fieldLabel(graphics,"How many seconds RCT stays active",137);
            fieldLabel(graphics,"Seconds before the next RCT use",193);
        }else if(page==3){
            heading(graphics,selectedDefence().name().replace('_',' '),112);
            fieldLabel(graphics,"Defense cooldown in seconds",190);
            if(selectedDefence()==NpcProfile.Defence.HOLLOW_WICKER_BASKET)
                heading(graphics,"Hollow Wicker Basket activation unverified",244);
        }else if(page==4){
            if(editingDomainDamage){
                if(profile.domainSureHitDamage>=0)fieldLabel(graphics,"Sure-hit damage (health points)",179);
                else heading(graphics,"Original JJK sure-hit damage retained",187);
            }else{
                fieldLabel(graphics,"Maximum HP % allowed to cast Domain",139);
                heading(graphics,"100% allows casting at any health",122);
                if(!NpcProfile.allowsOpenDomain(profile.technique))
                    heading(graphics,"No open/closed variant for this domain",200);
            }
        }else if(page==5 && advancedCombat){
            fieldLabel(graphics,"Seconds between backdashes",188);
        }else if(page==6){
            AbilityCatalog.Move move=selectedBasic();
            if(move!=null){
                heading(graphics,move.title(),113);
                fieldLabel(graphics,"Melee cooldown in seconds",190);
            }
        }else if(page==7 && selectedItem()!=null){
            heading(graphics,selectedItem().title(),111);
            if(itemEditingDamage){
                if(profile.itemDamage(itemKey(selectedItem()))>=0)
                    fieldLabel(graphics,"Right-click ability damage (HP)",253);
            }else{
                fieldLabel(graphics,"Right-click ability cooldown (seconds)",216);
            }
        }
        super.render(graphics,mouseX,mouseY,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
