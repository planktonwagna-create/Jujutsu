package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.NpcCompatibility;
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
    private static final String NEXT_FALLBACK = "nextMeleeFallback";
    private static final String MELEE_STARTED = "meleeEngagementStarted";

    public static void setDuel(LivingEntity npc, LivingEntity target) {
        if (!NpcCompatibility.isCompatible(npc) || !NpcCompatibility.isCompatible(target)) return;
        CompoundTag state = NpcStorage.runtime(npc, NpcStorage.profile(npc));
        state.putUUID(DUEL, target.getUUID());
        state.remove(NEXT_FALLBACK);
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
        state.remove(NEXT_FALLBACK);
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

    public static boolean dueling(LivingEntity npc) {
        return NpcCompatibility.isCompatible(npc)
                && NpcStorage.runtime(npc, NpcStorage.profile(npc)).hasUUID(DUEL);
    }

    public static void onDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity living) || !NpcCompatibility.isCompatible(living))return;
        // Only use NPC victims for the v0.6 combat hit registration; we may
        // still animate a successful NPC melee hit against a PLAYER.
        if(NpcCompatibility.isCompatible(victim)){
            CompoundTag data=victim.getPersistentData();
            data.putLong(LAST_NPC_ATTACK,level.getGameTime());
            data.putUUID(LAST_NPC_ATTACKER,living.getUUID());
        }
        // Play a visible humanoid attack pose for successful ordinary NPC
        // melee hits, without overwriting an active JJK technique animation.
        if(NpcStorage.configured(living)){
            CompoundTag state=NpcStorage.runtime(living,NpcStorage.profile(living));
            long now=level.getGameTime();
            if(!state.contains("activeJjkMove",10) && now>=state.getLong("nextMeleeAnimation")){
                // Alternate two verified JCraft full-body attacks for ordinary
                // melee hits; the resource loader falls back to JJK if absent.
                int combo=state.getInt("ordinaryMeleeCombo")+1;
                state.putInt("ordinaryMeleeCombo",combo);
                String pose;
                if(holdsSword(living)){
                    pose=combo%3==0?"jc_an_2hit":combo%2==0?"jc_an_swp":"jc_an_slsh";
                }else{
                    pose=combo%3==0?"jc_br_upct":"jc_br_gut";
                }
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(living,pose);
                state.putLong("nextMeleeAnimation",now+15);
            }
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

    public static void tick(ServerLevel level, LivingEntity source, CompoundTag state) {
        if (!(source instanceof Mob attacker) || !source.isAlive()) return;
        long now = level.getGameTime();
        LivingEntity target = attacker.getTarget();

        if (state.hasUUID(DUEL)) {
            Entity opponent = level.getEntity(state.getUUID(DUEL));
            if (!(opponent instanceof LivingEntity living) || !living.isAlive()
                    || living == source || source.distanceToSqr(living) > 64d * 64d) {
                stopDuel(source);
                return;
            }
            target = living;
            if (attacker.getTarget() != living) attacker.setTarget(living);
        }

        if (target == null || !target.isAlive() || !NpcCompatibility.isCompatible(target)
                || !NpcStorage.configured(target)
                || !NpcStorage.profile(target).allowCombatDamage) {
            state.remove(MELEE_STARTED);
            return;
        }
        // Never invent hit registrations for spells or distant targets.
        double reach = Math.max(2.6, (source.getBbWidth() + target.getBbWidth()) * 0.5 + 1.8);
        if (state.hasUUID(DUEL) && source.distanceToSqr(target) > reach * reach
                && now % 12 == 0) {
            attacker.getNavigation().moveTo(target,1.15);
            // Only a brief forward chase in an active duel uses the dash pose,
            // with its own rate limit. This never changes physics or AI speed.
            double distance=source.distanceTo(target);
            if(distance>reach+0.5 && distance<9.0
                    && now>=state.getLong("nextChaseAnimation")){
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(source,"jc_dash");
                state.putLong("nextChaseAnimation",now+20);
            }
        }
        if (source.distanceToSqr(target) > reach * reach || !attacker.hasLineOfSight(target)) {
            state.remove(MELEE_STARTED);
            return;
        }
        if (!state.contains(MELEE_STARTED)) state.putLong(MELEE_STARTED, now);
        // Give original NPC AI time to do its own damage and preserve scripting.
        if (now - state.getLong(MELEE_STARTED) < 30) return;
        if (now < state.getLong(NEXT_FALLBACK)) return;
        if (recentlyHitBy(target, source, now, 27)) return;

        state.putLong(NEXT_FALLBACK, now + 24);
        // Respect an existing native attack before using a fallback. Some NPC mods
        // return true for animation even when no actual damage was applied.
        float healthBefore = target.getHealth();
        attacker.doHurtTarget(target);
        if (target.getHealth() < healthBefore || recentlyHitBy(target, source, now, 0)) return;

        // Native AI did not land a damaging hit; retry through vanilla's damage
        // pipeline. Native CustomNPCs faction immunity is bypassed only when
        // that victim's Allow combat damage toggle is explicitly on.
        float damage = meleeStrength(attacker);
        DamageSource sourceDamage = attacker.damageSources().mobAttack(attacker);
        target.hurt(sourceDamage, damage);
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
