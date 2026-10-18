package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.JujutsuProcedureBridge;
import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Comparator;

/** Per-move seconds-based cooldowns and pre-cast charging, without CE requirements. */
public final class NpcCombatEngine {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String ACTIVE="activeJjkMove";

    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity npc=event.getEntity();
        if(!(npc.level() instanceof ServerLevel level) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc) || !npc.isAlive())return;
        NpcProfile profile=NpcStorage.profile(npc);
        CompoundTag state=NpcStorage.runtime(npc,profile);
        long now=level.getGameTime();
        tickRct(npc,profile,state,now);
        // The v0.6 melee fix must remain available even while a JJK move is charging.
        NpcMeleeBridge.tick(level,npc,state);
        if(state.contains(ACTIVE,10)){
            advance(level,npc,profile,state,now);
            return;
        }
        if(!(npc instanceof Mob mob))return;
        LivingEntity target=mob.getTarget();
        if(target!=null&&target.isAlive()){
            if(!state.getBoolean("hasEnteredCombat"))state.putLong("combatStarted",now);
            state.putBoolean("hasEnteredCombat",true);
        }else if(profile.holdPositionWhenIdle && state.getBoolean("hasEnteredCombat")){
            mob.getNavigation().stop();
            state.remove("combatStarted");
        }
        if(!profile.autoCombat || now%10!=Math.floorMod(npc.getId(),10)
                || target==null || !target.isAlive())return;
        double distance=npc.distanceTo(target);
        boolean threatened=DomainDefense.targetHasDomain(target);
        AbilityCatalog.Move domain=AbilityCatalog.technique(profile.technique).moves()
                .stream().filter(AbilityCatalog::isDomain).findFirst().orElse(null);
        if(domain!=null && distance<=domain.optimalMaxRange()
                && CastPolicy.canCast(profile.mode(domain.key()),false,now,state.getLong("ready_"+domain.key()))
                && (threatened || npc.getHealth()<=npc.getMaxHealth()*0.65f
                    || (state.contains("combatStarted") && now-state.getLong("combatStarted")>=160))) {
            if(cast(level,npc,profile,domain,target.getBoundingBox().getCenter(),target,false))return;
        }
        if(threatened){
            NpcProfile.Defence used=DomainDefense.tryAntiDomain(npc,profile,state,now);
            if(used!=null){
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc,"simple_domain1");
                return;
            }
        }
        for(AbilityCatalog.Move itemMove:ItemMoveCatalog.equipped(npc)){
            String itemKey=ItemMoveCatalog.key(npc,itemMove);
            if(CastPolicy.canCast(profile.itemMode(itemKey),false,now,state.getLong("ready_"+itemKey))
                    && distance<=itemMove.optimalMaxRange()
                    && cast(level,npc,profile,itemMove,target.getBoundingBox().getCenter(),target,false))
                return;
        }
        AbilityCatalog.technique(profile.technique).moves().stream()
            .filter(m -> !AbilityCatalog.isDomain(m))
            .filter(m -> distance>=m.optimalMinRange() && distance<=m.optimalMaxRange())
            .filter(m -> CastPolicy.canCast(profile.mode(m.key()),false,now,state.getLong("ready_"+m.key())))
            .min(Comparator.comparingDouble(m -> Math.abs(distance -
                     (m.optimalMinRange()+m.optimalMaxRange())/2d)))
            .ifPresent(m -> cast(level,npc,profile,m,target.getBoundingBox().getCenter(),target,false));
    }

    /**
     * One point of health per second during an editable active window, then an
     * editable cooldown. No cursed energy is read or deducted.
     */
    private static void tickRct(LivingEntity npc,NpcProfile p,CompoundTag state,long now){
        if(!p.rct || now%20!=0 || npc.getHealth()>=npc.getMaxHealth())return;
        long activeUntil=state.getLong("rctUseEnd");
        if(now<activeUntil){
            npc.heal(1f);
        }else if(now>=state.getLong("rctReady")){
            long activeTicks=CastPolicy.secondsToTicks(Math.max(1,p.rctActiveSeconds));
            state.putLong("rctUseEnd",now+activeTicks);
            state.putLong("rctReady",now+activeTicks+CastPolicy.secondsToTicks(p.rctCooldownSeconds));
            npc.heal(1f);
        }
    }

    private static int totalTicks(AbilityCatalog.Move move) {
        if(AbilityCatalog.isDomain(move))return 95;
        if(AbilityCatalog.supportsCharge(move))return 40;
        return switch(move.key()){
            case "attack" -> 15;
            case "knockback" -> 20;
            case "barrage" -> 40;
            case "blue","red","dismantle" -> 32;
            case "purple" -> 65;
            case "cleave" -> 36;
            case "piercing_blood" -> 42;
            case "supernova","convergence" -> 24;
            default -> 1;
        };
    }
    private static String cooldownKey(LivingEntity npc,AbilityCatalog.Move move) {
        return ItemMoveCatalog.available(npc,move)?ItemMoveCatalog.key(npc,move):move.key();
    }
    private static void finish(NpcProfile p,CompoundTag state,CompoundTag active,long now) {
        state.remove(ACTIVE);
        String key=active.getString("cooldownKey");
        if(!key.isEmpty()){
            int seconds=active.getBoolean("item")
                    ?p.itemCooldown(key):p.cooldown(active.getString("move"));
            state.putLong("ready_"+key,now+CastPolicy.secondsToTicks(seconds));
        }
    }
    private static Vec3 aim(CompoundTag active,LivingEntity target) {
        return target!=null?target.getBoundingBox().getCenter()
                :new Vec3(active.getDouble("x"),active.getDouble("y"),active.getDouble("z"));
    }
    private static LivingEntity resolve(ServerLevel level,CompoundTag active) {
        if(!active.hasUUID("target"))return null;
        Entity e=level.getEntity(active.getUUID("target"));
        return e instanceof LivingEntity living && living.isAlive()?living:null;
    }
    private static boolean original(ServerLevel level,LivingEntity npc,NpcProfile p,
                                    AbilityCatalog.Move move,Vec3 target,LivingEntity enemy,
                                    boolean pressed,boolean crouching) {
        boolean shrine=AbilityCatalog.isDomain(move)
                && "MalevolentShrineProcedure".equals(move.procedure())
                && NpcProfile.allowsOpenDomain(p.technique);
        return JujutsuProcedureBridge.tryCast(level,npc,move,target,enemy,
                shrine?p.domainOpen:null,shrine?p.shrineModel:null,pressed,crouching);
    }
    private static void resetJjkCounters(LivingEntity npc){
        CompoundTag jjk=npc.getPersistentData();
        jjk.putDouble("cnt1",0);jjk.putDouble("cnt2",0);jjk.putDouble("cnt3",0);
    }
    private static boolean chooseCrouch(NpcProfile p,AbilityCatalog.Move move,CompoundTag state) {
        if(!AbilityCatalog.supportsCrouch(move))return false;
        return switch(p.crouch(move.key())){
            case REGULAR -> false;
            case CROUCHING -> true;
            case ALTERNATE -> {
                String id="alternate_"+move.key();
                boolean next=!state.getBoolean(id);
                state.putBoolean(id,next);
                yield next;
            }
        };
    }
    private static void advance(ServerLevel level,LivingEntity npc,NpcProfile p,
                                CompoundTag state,long now) {
        CompoundTag active=state.getCompound(ACTIVE);
        AbilityCatalog.Move move;
        if(active.getBoolean("item")){
            move=ItemMoveCatalog.equipped(npc).stream()
                    .filter(m->m.key().equals(active.getString("move"))).findFirst().orElse(null);
        }else{
            move=AbilityCatalog.move(p.technique,active.getString("move")).orElse(null);
        }
        if(move==null){finish(p,state,active,now);return;}
        LivingEntity target=resolve(level,active);
        if(active.hasUUID("target") && target==null && !AbilityCatalog.isDomain(move)){
            finish(p,state,active,now);
            return;
        }
        long until=active.getLong("releaseTick");
        boolean pressing=now<until;
        if(active.getBoolean("holding") && !pressing){
            active.putBoolean("holding",false);
            active.putInt("remaining",totalTicks(move));
        }
        if(!original(level,npc,p,move,aim(active,target),target,
                pressing,active.getBoolean("crouching"))){
            LOG.warn("JJK NPC {} could not continue {}",npc.getUUID(),move.key());
            finish(p,state,active,now);
            return;
        }
        if(!pressing){
            int left=active.getInt("remaining")-1;
            active.putInt("remaining",left);
            if(left<=0)finish(p,state,active,now);
        }
        active.putLong("lastAdvanced",now);
    }
    private static void play(LivingEntity npc,AbilityCatalog.Move move) {
        String anim=AbilityCatalog.originalAnimation(move.key());
        if(!anim.isEmpty())dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc,anim);
    }
    public static boolean cast(ServerLevel level,LivingEntity npc,NpcProfile p,
                               AbilityCatalog.Move move,Vec3 pos,boolean manual) {
        return cast(level,npc,p,move,pos,null,manual);
    }
    public static boolean cast(ServerLevel level,LivingEntity npc,NpcProfile p,
                               AbilityCatalog.Move move,Vec3 pos,LivingEntity target,boolean manual) {
        if(!NpcCompatibility.isCompatible(npc)||!NpcStorage.configured(npc)||!npc.isAlive())return false;
        boolean item=ItemMoveCatalog.available(npc,move);
        if(!item&&AbilityCatalog.move(p.technique,move.key()).isEmpty())return false;
        CompoundTag state=NpcStorage.runtime(npc,p);
        if(state.contains(ACTIVE,10))return false;
        long now=level.getGameTime();
        String readyKey=item?ItemMoveCatalog.key(npc,move):move.key();
        if(!CastPolicy.canCast(item?p.itemMode(readyKey):p.mode(move.key()),
                manual,now,state.getLong("ready_"+readyKey)))return false;
        if(!manual&&!AutomaticCastLimiter.canBegin(level,now))return false;
        if(pos==null||!Double.isFinite(pos.x)||!Double.isFinite(pos.y)||!Double.isFinite(pos.z)
                || npc.position().distanceToSqr(pos)>128*128)return false;
        if(target!=null&&(!target.isAlive()||target.level()!=level))return false;
        int charge=!item && AbilityCatalog.supportsCharge(move)?p.charge(move.key()):0;
        boolean crouching=!item&&chooseCrouch(p,move,state);
        resetJjkCounters(npc);
        if(!original(level,npc,p,move,pos,target,true,crouching))return false;
        if(!manual)AutomaticCastLimiter.didBegin(level,now);
        // JJK item-use procedures are invoked on right-click, not every tick.
        // Re-running the original item procedure caused duplicate lightning/
        // projectile activations. A single click starts its independent timer.
        if(item){
            state.putLong("ready_"+readyKey,now+CastPolicy.secondsToTicks(p.itemCooldown(readyKey)));
            play(npc,move,false);
            LOG.info("JJK NPC {} right-clicked {} | cooldown={}s",npc.getUUID(),
                    readyKey,p.itemCooldown(readyKey));
            return true;
        }
        CompoundTag active=new CompoundTag();
        active.putString("move",move.key());
        active.putString("cooldownKey",readyKey);
        active.putBoolean("item",item);
        active.putBoolean("crouching",crouching);
        active.putBoolean("holding",true);
        active.putLong("releaseTick",now+Math.max(1,charge*20L));
        active.putInt("remaining",totalTicks(move));
        active.putDouble("x",pos.x);active.putDouble("y",pos.y);active.putDouble("z",pos.z);
        if(target!=null)active.putUUID("target",target.getUUID());
        state.put(ACTIVE,active);
        play(npc,move);
        LOG.info("JJK NPC {} began {} pressed for {} ticks, crouching={} item={}",
                npc.getUUID(),move.key(),Math.max(1,charge*20),crouching,item);
        return true;
    }

    public static void onDamage(LivingDamageEvent event){
        LivingEntity npc=event.getEntity();
        if(!(npc.level() instanceof ServerLevel)||!NpcCompatibility.isCompatible(npc)
                ||!NpcStorage.configured(npc))return;
        NpcProfile p=NpcStorage.profile(npc);
        if(!p.fatalRecovery || event.getAmount()<npc.getHealth())return;
        CompoundTag state=NpcStorage.runtime(npc,p);
        if(state.getInt("fatalChargesLeft")<=0)return;
        state.putInt("fatalChargesLeft",state.getInt("fatalChargesLeft")-1);
        event.setAmount(0);
        npc.setHealth(Math.max(1,npc.getMaxHealth()*p.fatalRecoveryHealthFraction));
    }
    private NpcCombatEngine(){}
}
