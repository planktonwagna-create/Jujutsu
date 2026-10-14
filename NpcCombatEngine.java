package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.JujutsuProcedureBridge;
import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import dev.jujutsunpc.game.CastPolicy.Mode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.Comparator;

/** Server-side only. Never replaces the original NPC target selector or damage rules. */
public final class NpcCombatEngine {
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity npc = event.getEntity();
        if (!(npc.level() instanceof ServerLevel level) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc) || !npc.isAlive()) return;
        NpcProfile profile = NpcStorage.profile(npc);
        CompoundTag state = NpcStorage.runtime(npc,profile);
        long tick = level.getGameTime();
        if (tick % 20 == 0) {
            state.putDouble("energy", Math.min(profile.maxEnergy,
                    state.getDouble("energy") + profile.regenPerSecond));
            if (profile.rct && npc.getHealth() < npc.getMaxHealth() && state.getDouble("energy") >= 8) {
                npc.heal(1.0f); // Interim healing implementation; animation integration pending.
                state.putDouble("energy", state.getDouble("energy") - 8);
            }
        }
        if (!profile.autoCombat || tick % 10 != Math.floorMod(npc.getId(), 10)) return;
        if (!(npc instanceof Mob mob)) return;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return; // Preserve the native NPC target.
        // Domain defense preempts ordinary tactical attacks. Domain clash path remains
        // disabled until v51's domain owner/creation logic is verified for both NPC types.
        if (DomainDefense.targetHasDomain(target)) {
            if (!DomainDefense.tryOriginalDomain(level, npc, profile)
                    && tick >= state.getLong("nextDomainDefense") && state.getDouble("energy") >= 30
                    && DomainDefense.tryAntiDomain(npc, profile)) {
                state.putDouble("energy", state.getDouble("energy") - 30);
                state.putLong("nextDomainDefense", tick + 100);
                dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc, "simple_domain1");
                return;
            }
        }
        double distance = npc.distanceTo(target);
        AbilityCatalog.technique(profile.technique).moves().stream()
            .filter(m -> distance >= m.optimalMinRange() && distance <= m.optimalMaxRange())
            .filter(m -> CastPolicy.canCast(profile.mode(m.key()), false, state.getDouble("energy"), m.cost(),
                   tick, state.getLong("ready_" + m.key())))
            // Prioritize a nonlethal-range option; conserve expensive abilities when wounded.
            .min(Comparator.comparingDouble(m -> Math.abs(distance - (m.optimalMinRange()+m.optimalMaxRange())/2)
                         + (npc.getHealth() < npc.getMaxHealth() * 0.25 ? m.cost()/10.0 : 0)))
             .ifPresent(m -> cast(level,npc,profile,m,target.getBoundingBox().getCenter(),target,false));
    }

    public static boolean cast(ServerLevel level, LivingEntity npc, NpcProfile profile,
                               AbilityCatalog.Move move, Vec3 target, boolean manual) {
        return cast(level,npc,profile,move,target,null,manual);
    }

    public static boolean cast(ServerLevel level, LivingEntity npc, NpcProfile profile,
                               AbilityCatalog.Move move, Vec3 target, LivingEntity explicitTarget,
                               boolean manual) {
        if (!NpcCompatibility.isCompatible(npc) || !NpcStorage.configured(npc)) return false;
        if (AbilityCatalog.move(profile.technique,move.key()).isEmpty()) return false;
        CompoundTag state = NpcStorage.runtime(npc,profile);
        long tick = level.getGameTime();
        if (!CastPolicy.canCast(profile.mode(move.key()),manual,state.getDouble("energy"),move.cost(),
                                tick,state.getLong("ready_"+move.key()))) return false;
        if (!manual && !AutomaticCastLimiter.canBegin(level, tick)) return false;
        if (!JujutsuProcedureBridge.tryCast(level,npc,move,target,explicitTarget)) return false;
        if (!manual) {
            AutomaticCastLimiter.didBegin(level, tick);
            state.putDouble("energy", Math.max(0,state.getDouble("energy")-move.cost()));
            state.putLong("ready_"+move.key(),tick+move.cooldownTicks());
        }
        String anim = AbilityCatalog.originalAnimation(move.key());
        if (!anim.isEmpty()) dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc, anim);
        return true;
    }

    public static void onDamage(LivingDamageEvent event) {
        LivingEntity npc = event.getEntity();
        if (!(npc.level() instanceof ServerLevel) || !NpcCompatibility.isCompatible(npc)
                  || !NpcStorage.configured(npc)) return;
        NpcProfile profile = NpcStorage.profile(npc);
        if (!profile.fatalRecovery || event.getAmount() < npc.getHealth()) return;
        CompoundTag state = NpcStorage.runtime(npc,profile);
        if (state.getInt("fatalChargesLeft") <= 0 || state.getDouble("energy") < 100) return;
        state.putInt("fatalChargesLeft",state.getInt("fatalChargesLeft")-1);
        state.putDouble("energy",state.getDouble("energy")-100);
        event.setAmount(0);
        npc.setHealth(Math.max(1, npc.getMaxHealth()*profile.fatalRecoveryHealthFraction));
    }
    private NpcCombatEngine() {}
}
