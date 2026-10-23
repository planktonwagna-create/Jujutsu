package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.JujutsuNpcCompatMod;
import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Native NPC-vs-NPC targeting and melee damage bridge.
 *
 * Respects native successful strikes. Only schedules a missing-strike fallback
 * if a configured NPC has stayed in melee range without a registered hit.
 * All fallback damage still goes through LivingEntity.hurt(), armor, immunity,
 * Forge events and the v0.5 per-victim opt-in protection.
 */
public final class NpcMeleeBridge {
    private static final String DUEL = "duelOpponent";
    private static final String LAST_NPC_ATTACK = "lastNpcDamageTick";
    private static final String LAST_NPC_ATTACKER = "lastNpcDamageAttacker";
    // A pre-hit animation window replaces the old post-damage animation.
    private static final String NEXT_ATTACK = "nextMeleeReady";
    private static final String PENDING = "pendingMeleeStrike";
    private static final int WINDUP_TICKS = 6;
    private static final ThreadLocal<Boolean> EXECUTING = ThreadLocal.withInitial(() -> false);
    private static int gapTicks() {
        return Math.max(20,Math.min(40,JujutsuNpcCompatMod.MELEE_BUFFER_TICKS.get()));
    }
    private static final String MELEE_STARTED = "meleeEngagementStarted";

    public static void setDuel(LivingEntity npc, LivingEntity target) {
        if (!NpcCompatibility.isCompatible(npc) || !NpcCompatibility.isCompatible(target)) return;
        CompoundTag state = NpcStorage.runtime(npc, NpcStorage.profile(npc));
        state.putUUID(DUEL, target.getUUID());
        state.remove(PENDING);
        state.remove(MELEE_STARTED);
        if (npc instanceof Mob mob) mob.setTarget(target);
    }

    public static void stopDuel(LivingEntity npc) {
        if (!NpcCompatibility.isCompatible(npc)) return;
        CompoundTag state = NpcStorage.runtime(npc, NpcStorage.profile(npc));
        if (npc instanceof Mob mob && state.hasUUID(DUEL) && mob.getTarget() != null
                && mob.getTarget().getUUID().equals(state.getUUID(DUEL))) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        state.remove(DUEL);
        state.remove(PENDING);
        state.remove(MELEE_STARTED);
    }

    /** Stop both sides of a previously configured pair, if still loaded. */
    public static void stopDuelPair(LivingEntity npc) {
        if (!NpcCompatibility.isCompatible(npc)) return;
        CompoundTag state = NpcStorage.runtime(npc, NpcStorage.profile(npc));
        Entity other = npc.level() instanceof ServerLevel level && state.hasUUID(DUEL)
                ? level.getEntity(state.getUUID(DUEL)) : null;
        stopDuel(npc);
        if (other instanceof LivingEntity living && NpcCompatibility.isCompatible(living)) {
            CompoundTag reciprocal = NpcStorage.runtime(living, NpcStorage.profile(living));
            if (reciprocal.hasUUID(DUEL) && reciprocal.getUUID(DUEL).equals(npc.getUUID())) {
                stopDuel(living);
            }
        }
    }

    public static boolean windingUp(CompoundTag runtime){
        return runtime.contains(PENDING,10);
    }

    public static boolean dueling(LivingEntity npc) {
        return NpcCompatibility.isCompatible(npc)
                && NpcStorage.runtime(npc, NpcStorage.profile(npc)).hasUUID(DUEL);
    }

    /** Registers successful NPC-vs-NPC hits; NEVER starts animations. */
    public static void onDamage(LivingDamageEvent event){
        if(event.getAmount()<=0 || !(event.getEntity().level() instanceof ServerLevel level))return;
        LivingEntity victim=event.getEntity();
        Entity attacker=event.getSource().getEntity();
        if(!(attacker instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)
                || !NpcCompatibility.isCompatible(victim))return;
        CompoundTag data=victim.getPersistentData();
        data.putLong(LAST_NPC_ATTACK,level.getGameTime());
        data.putUUID(LAST_NPC_ATTACKER,npc.getUUID());
    }

    /**
     * NPC melee may strike BEFORE our living tick callback. Convert the first
     * attempted direct melee hit into a six-tick visual windup instead; cancel
     * its initial damage. The delayed native doHurtTarget() executes under
     * EXECUTING to preserve each NPC mod's normal hit and faction behavior.
     */
    public static void onAttack(LivingAttackEvent event){
        LivingEntity victim=event.getEntity();
        if(!(victim.level() instanceof ServerLevel level))return;
        Entity origin=event.getSource().getEntity();
        if(!(origin instanceof Mob attacker) || !NpcCompatibility.isCompatible(attacker)
                || !NpcStorage.configured(attacker)
                || event.getSource().getDirectEntity()!=attacker
                || origin==victim || EXECUTING.get())return;
        CompoundTag state=NpcStorage.runtime(attacker,NpcStorage.profile(attacker));
        // Never intercept a cursed-technique or special-item hit.
        if(state.contains("activeJjkMove",10)||KashimoLightningTrail.active(state)
                || (!state.getString("currentMove").isEmpty()
                    && level.getGameTime()<=state.getLong("currentMoveUntil")))return;
        schedule(level,attacker,victim,state);
        event.setCanceled(true);
    }

    private static void playAttack(Mob attacker,CompoundTag state){
        int combo=state.getInt("ordinaryMeleeCombo")+1;
        state.putInt("ordinaryMeleeCombo",combo);
        String pose=holdsSword(attacker)
                ?(combo%3==0?"jc_an_2hit":combo%2==0?"jc_an_swp":"jc_an_slsh")
                :(combo%3==0?"jc_br_upct":"jc_br_gut");
        dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(attacker,pose);
    }

    /** Only one attacker at a time for an NPC pair; 1–2s per-caster gap. */
    private static boolean schedule(ServerLevel level,Mob attacker,LivingEntity victim,CompoundTag state){
        long now=level.getGameTime();
        if(!victim.isAlive()||!attacker.isAlive()||victim==attacker
                || state.contains(PENDING,10)||now<state.getLong(NEXT_ATTACK))return false;
        double reach=Math.max(2.6,(attacker.getBbWidth()+victim.getBbWidth())*.5+1.8);
        if(attacker.distanceToSqr(victim)>reach*reach || !attacker.hasLineOfSight(victim))return false;
        CompoundTag opponent=null;
        if(NpcCompatibility.isCompatible(victim)&&NpcStorage.configured(victim)){
            opponent=NpcStorage.runtime(victim,NpcStorage.profile(victim));
            if(now<opponent.getLong(NEXT_ATTACK)||opponent.contains(PENDING,10))return false;
        }
        long next=now+gapTicks();
        state.putLong(NEXT_ATTACK,next);
        if(opponent!=null)opponent.putLong(NEXT_ATTACK,next);
        CompoundTag pending=new CompoundTag();
        pending.putUUID("victim",victim.getUUID());
        pending.putLong("impactAt",now+WINDUP_TICKS);
        state.put(PENDING,pending);
        playAttack(attacker,state);
        return true;
    }

    private static void finishAttack(ServerLevel level,Mob attacker,CompoundTag state){
        CompoundTag pending=state.getCompound(PENDING);
        state.remove(PENDING);
        if(!pending.hasUUID("victim"))return;
        Entity raw=level.getEntity(pending.getUUID("victim"));
        if(!(raw instanceof LivingEntity target) || !target.isAlive())return;
        double reach=Math.max(2.6,(attacker.getBbWidth()+target.getBbWidth())*.5+1.8);
        if(attacker.distanceToSqr(target)>reach*reach || !attacker.hasLineOfSight(target))return;
        // Animation always played earlier, including if this hit fails.
        EXECUTING.set(true);
        try{
            float before=target.getHealth();
            attacker.doHurtTarget(target);
            if(target.getHealth()<before || recentlyHitBy(target,attacker,level.getGameTime(),0))return;
            // Preserve original v0.6 fallback only for opt-in NPC victims.
            if(!NpcCompatibility.isCompatible(target)||!NpcStorage.configured(target)
                    ||!NpcStorage.profile(target).allowCombatDamage)return;
            target.hurt(attacker.damageSources().mobAttack(attacker),meleeStrength(attacker));
        }finally{
            EXECUTING.set(false);
        }
    }

    private static boolean holdsSword(LivingEntity entity){
        var item=entity.getMainHandItem().getItem();
        if(item instanceof SwordItem)return true;
        var id=ForgeRegistries.ITEMS.getKey(item);
        if(id==null)return false;
        String path=id.getPath();
        return path.contains("sword")||path.contains("katana")
                ||path.contains("blade")||path.equals("weapon_nanami");
    }

    public static void tick(ServerLevel level,LivingEntity source,CompoundTag state){
        if(!(source instanceof Mob attacker)||!source.isAlive())return;
        long now=level.getGameTime();
        if(state.contains(PENDING,10)){
            if(now>=state.getCompound(PENDING).getLong("impactAt"))
                finishAttack(level,attacker,state);
            return;
        }
        if(state.contains("activeJjkMove",10)||KashimoLightningTrail.active(state))return;
        LivingEntity target=attacker.getTarget();
        if(state.hasUUID(DUEL)){
            Entity other=level.getEntity(state.getUUID(DUEL));
            if(!(other instanceof LivingEntity living)||!living.isAlive()
                    ||living==source||source.distanceToSqr(living)>64d*64d){
                stopDuel(source);
                return;
            }
            target=living;
            if(attacker.getTarget()!=living)attacker.setTarget(living);
        }
        if(target==null||!target.isAlive()){
            state.remove(MELEE_STARTED);
            return;
        }
        double reach=Math.max(2.6,(source.getBbWidth()+target.getBbWidth())*.5+1.8);
        if(state.hasUUID(DUEL)&&source.distanceToSqr(target)>reach*reach&&now%12==0){
            attacker.getNavigation().moveTo(target,1.15);
            double distance=source.distanceTo(target);
            if(distance>reach+0.5 && distance<9 && now>=state.getLong("nextChaseAnimation")){
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(source,"jc_dash");
                state.putLong("nextChaseAnimation",now+20);
            }
        }
        if(source.distanceToSqr(target)>reach*reach || !attacker.hasLineOfSight(target)){
            state.remove(MELEE_STARTED);
            return;
        }
        if(!state.contains(MELEE_STARTED))state.putLong(MELEE_STARTED,now);
        // Native AI gets priority for attack attempts. onAttack() starts
        // animations whether or not the attempted strike would have landed.
        if(now-state.getLong(MELEE_STARTED)<12)return;
        // When NPC mods cannot damage each other, our opt-in bridge winds
        // up an animation before triggering the v0.6 damage fallback.
        if(NpcCompatibility.isCompatible(target)&&NpcStorage.configured(target)
                &&NpcStorage.profile(target).allowCombatDamage)
            schedule(level,attacker,target,state);
    }

    private static boolean recentlyHitBy(LivingEntity victim, LivingEntity attacker, long tick, int window) {
        CompoundTag tag = victim.getPersistentData();
        return tag.hasUUID(LAST_NPC_ATTACKER)
                && tag.getUUID(LAST_NPC_ATTACKER).equals(attacker.getUUID())
                && tag.contains(LAST_NPC_ATTACK)
                && tick >= tag.getLong(LAST_NPC_ATTACK)
                && tick - tag.getLong(LAST_NPC_ATTACK) <= window;
    }

    private static float meleeStrength(Mob attacker) {
        double vanilla = attacker.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 0 : attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // CustomNPCs stores its true melee strength in DataStats, not necessarily
        // in the vanilla attack-damage attribute.
        try {
            Field statsField = attacker.getClass().getField("stats");
            Object stats = statsField.get(attacker);
            Object melee = stats.getClass().getField("melee").get(stats);
            Method getStrength = melee.getClass().getMethod("getStrength");
            Object value = getStrength.invoke(melee);
            if (value instanceof Number number && number.floatValue() > 0)
                return Math.max(1, Math.min(number.floatValue(), 100f));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Other NPC types have standard attributes.
        }
        return (float) Math.max(4, Math.min(vanilla, 100));
    }
    private NpcMeleeBridge() {}
}
