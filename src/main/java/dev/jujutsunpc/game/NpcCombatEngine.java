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
import java.util.UUID;

/**
 * Server-authoritative casts for native NPC targets. JujutsuCraft v51's own procedures are
 * state machines: many must run EVERY TICK, not once per activation. Exactly one procedure
 * runs per NPC per tick. No custom AOE/damage/friendly-fire rules are substituted.
 */
public final class NpcCombatEngine {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ACTIVE = "activeJjkMove";

    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity npc = event.getEntity();
        if (!(npc.level() instanceof ServerLevel level) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc) || !npc.isAlive()) return;
        NpcProfile profile = NpcStorage.profile(npc);
        CompoundTag state = NpcStorage.runtime(npc, profile);
        long now = level.getGameTime();

        if (now % 20 == 0) {
            state.putDouble("energy", Math.min(profile.maxEnergy,
                    state.getDouble("energy") + profile.regenPerSecond));
            if (profile.rct && npc.getHealth() < npc.getMaxHealth()
                    && state.getDouble("energy") >= 8) {
                npc.heal(1.0f);
                state.putDouble("energy", state.getDouble("energy") - 8);
            }
        }

        // JJK v51 abilities advance counters on subsequent ticks. Never start a second
        // power while the original one is progressing (shared cnt1/cnt2/cnt3 tags).
        if (state.contains(ACTIVE, 10)) {
            advance(level, npc, profile, state, now);
            return;
        }

        if (!(npc instanceof Mob mob)) return;
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            if (!state.getBoolean("hasEnteredCombat")) {
                state.putLong("combatStarted", now);
            }
            state.putBoolean("hasEnteredCombat", true);
        } else if (profile.holdPositionWhenIdle && state.getBoolean("hasEnteredCombat")) {
            mob.getNavigation().stop();
            state.remove("combatStarted");
        }
        if (!profile.autoCombat || now % 10 != Math.floorMod(npc.getId(), 10)) return;
        if (target == null || !target.isAlive()) return;

        double distance = npc.distanceTo(target);
        boolean threatenedByDomain = DomainDefense.targetHasDomain(target);

        // The original domain procedure now gets a full continuous sequence, rather
        // than the old single invocation. Clashes/ownership are still unverified.
        AbilityCatalog.Move domain = AbilityCatalog.technique(profile.technique).moves()
                .stream().filter(AbilityCatalog::isDomain).findFirst().orElse(null);
        if (domain != null
                && distance <= domain.optimalMaxRange()
                && CastPolicy.canCast(profile.mode(domain.key()), false, state.getDouble("energy"),
                                      domain.cost(), now, state.getLong("ready_" + domain.key()))
                && (threatenedByDomain
                    || npc.getHealth() <= npc.getMaxHealth() * 0.65f
                    || (state.contains("combatStarted") && now - state.getLong("combatStarted") >= 160))) {
            if (cast(level, npc, profile, domain, target.getBoundingBox().getCenter(), target, false)) {
                return;
            }
        }

        if (threatenedByDomain) {
            if (now >= state.getLong("nextDomainDefense") && state.getDouble("energy") >= 30
                    && DomainDefense.tryAntiDomain(npc, profile)) {
                state.putDouble("energy", state.getDouble("energy") - 30);
                state.putLong("nextDomainDefense", now + 100);
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc, "simple_domain1");
                return;
            }
        }

        AbilityCatalog.technique(profile.technique).moves().stream()
            .filter(m -> !AbilityCatalog.isDomain(m))
            .filter(m -> distance >= m.optimalMinRange() && distance <= m.optimalMaxRange())
            .filter(m -> CastPolicy.canCast(profile.mode(m.key()), false, state.getDouble("energy"),
                    m.cost(), now, state.getLong("ready_" + m.key())))
            .min(Comparator.comparingDouble(m -> Math.abs(distance -
                    (m.optimalMinRange() + m.optimalMaxRange()) / 2)
                    + (npc.getHealth() < npc.getMaxHealth() * .25f ? m.cost() / 10d : 0)))
            .ifPresent(m -> cast(level, npc, profile, m, target.getBoundingBox().getCenter(), target, false));
    }

    /**
     * Durations are experimental, based on v51 bytecode counters and initial procedures.
     * Summon procedures remain single-call so one cast cannot spawn many creatures.
     */
    private static int totalTicks(AbilityCatalog.Move move) {
        if (AbilityCatalog.isDomain(move)) return 95;
        return switch (move.key()) {
            case "blue", "red", "dismantle" -> 32;
            case "purple" -> 65;
            case "cleave" -> 36;
            case "piercing_blood" -> 42;
            case "supernova" -> 24;
            case "convergence" -> 24;
            default -> 1;
        };
    }

    /** Finish exactly one existing cast step and never select a new target here. */
    private static void advance(ServerLevel level, LivingEntity npc, NpcProfile profile,
                                CompoundTag state, long now) {
        CompoundTag active = state.getCompound(ACTIVE);
        String key = active.getString("move");
        AbilityCatalog.Move move = AbilityCatalog.move(profile.technique, key).orElse(null);
        int left = active.getInt("remaining");
        if (move == null || left <= 0) {
            finish(state);
            return;
        }
        LivingEntity explicitTarget = null;
        if (active.hasUUID("target")) {
            Entity entity = level.getEntity(active.getUUID("target"));
            if (entity instanceof LivingEntity living && living.isAlive()) {
                explicitTarget = living;
            } else if (!AbilityCatalog.isDomain(move)) {
                finish(state);
                return;
            }
        }
        Vec3 position = explicitTarget != null
                ? explicitTarget.getBoundingBox().getCenter()
                : new Vec3(active.getDouble("x"), active.getDouble("y"), active.getDouble("z"));
        if (!JujutsuProcedureBridge.tryCast(level, npc, move, position, explicitTarget)) {
            LOG.warn("JJK NPC {} sequence {} stopped early after {} steps",
                    npc.getUUID(), key, totalTicks(move) - left);
            finish(state);
            return;
        }
        active.putInt("remaining", left - 1);
        active.putLong("lastAdvanced", now);
        if (left <= 1) finish(state);
    }

    private static void finish(CompoundTag state) { state.remove(ACTIVE); }

    public static boolean cast(ServerLevel level, LivingEntity npc, NpcProfile profile,
                               AbilityCatalog.Move move, Vec3 target, boolean manual) {
        return cast(level, npc, profile, move, target, null, manual);
    }

    public static boolean cast(ServerLevel level, LivingEntity npc, NpcProfile profile,
                               AbilityCatalog.Move move, Vec3 target, LivingEntity explicitTarget,
                               boolean manual) {
        if (!NpcCompatibility.isCompatible(npc) || !NpcStorage.configured(npc) || !npc.isAlive()
                || AbilityCatalog.move(profile.technique, move.key()).isEmpty()) return false;
        CompoundTag state = NpcStorage.runtime(npc, profile);
        if (state.contains(ACTIVE, 10)) return false;
        long now = level.getGameTime();
        if (!CastPolicy.canCast(profile.mode(move.key()), manual,
                state.getDouble("energy"), move.cost(), now,
                state.getLong("ready_" + move.key()))) return false;
        if (!manual && !AutomaticCastLimiter.canBegin(level, now)) return false;

        // All original JJK procedures share cnt1/cnt2/cnt3 state on their caster.
        // Clear prior cast counters only when a NEW cast starts; never every tick.
        CompoundTag jjk = npc.getPersistentData();
        jjk.putDouble("cnt1", 0);
        jjk.putDouble("cnt2", 0);
        jjk.putDouble("cnt3", 0);
        if (!JujutsuProcedureBridge.tryCast(level, npc, move, target, explicitTarget)) return false;

        if (!manual) {
            AutomaticCastLimiter.didBegin(level, now);
            state.putDouble("energy", Math.max(0, state.getDouble("energy") - move.cost()));
            state.putLong("ready_" + move.key(), now + move.cooldownTicks());
        }

        int remaining = totalTicks(move) - 1;
        if (remaining > 0) {
            CompoundTag active = new CompoundTag();
            active.putString("move", move.key());
            active.putInt("remaining", remaining);
            active.putDouble("x", target.x);
            active.putDouble("y", target.y);
            active.putDouble("z", target.z);
            active.putBoolean("manual", manual);
            active.putLong("lastAdvanced", now);
            if (explicitTarget != null) active.putUUID("target", explicitTarget.getUUID());
            state.put(ACTIVE, active);
        }
        String animation = AbilityCatalog.originalAnimation(move.key());
        if (!animation.isEmpty())
            dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc, animation);
        LOG.info("JJK NPC cast started: npc={} move={} steps={} manual={}",
                npc.getUUID(), move.key(), totalTicks(move), manual);
        return true;
    }

    public static void onDamage(LivingDamageEvent event) {
        LivingEntity npc = event.getEntity();
        if (!(npc.level() instanceof ServerLevel) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc)) return;
        NpcProfile profile = NpcStorage.profile(npc);
        if (!profile.fatalRecovery || event.getAmount() < npc.getHealth()) return;
        CompoundTag state = NpcStorage.runtime(npc, profile);
        if (state.getInt("fatalChargesLeft") <= 0 || state.getDouble("energy") < 100) return;
        state.putInt("fatalChargesLeft", state.getInt("fatalChargesLeft") - 1);
        state.putDouble("energy", state.getDouble("energy") - 100);
        event.setAmount(0);
        npc.setHealth(Math.max(1, npc.getMaxHealth() * profile.fatalRecoveryHealthFraction));
    }

    private NpcCombatEngine() {}
}
