package dev.jujutsunpc.compat;

import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Opt-in, per-NPC damage compatibility. Vanilla damage, armor, hit immunity,
 * death callbacks and Forge events still handle the hit after native gate
 * bypass. Other NPCs and players are never made globally attackable.
 */
public final class NpcDamageCompatibility {
    public static boolean enabledForHit(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || !NpcCompatibility.isCompatible(victim)
                || !NpcStorage.configured(victim)
                || !NpcStorage.profile(victim).allowCombatDamage) return false;
        Entity attacker = source.getEntity();
        // Avoid making projectile/environmental/unknown sources globally lethal.
        if (!(attacker instanceof LivingEntity living) || attacker == victim) return false;
        if (attacker instanceof Player player && player.isSpectator()) return false;
        return attacker instanceof Player || NpcCompatibility.isCompatible(living);
    }

    /** Easy NPC defaults to invulnerable and player-attack-disabled. */
    public static boolean bypassEasyNpcDamageGate(LivingEntity victim, DamageSource source) {
        return enabledForHit(victim, source);
    }

    /**
     * CustomNPCs GBPort denies friendly-player damage and same-faction NPC
     * damage directly in EntityNPCInterface.hurt, before LivingDamageEvent.
     * Only bypass those two specific native restrictions when opted in.
     */
    public static boolean bypassCustomNpcFactionGate(LivingEntity victim, DamageSource source) {
        if (!enabledForHit(victim, source)) return false;
        Entity attacker = source.getEntity();
        try {
            if (Boolean.TRUE.equals(staticField("noppes.npcs.CustomNpcs", "FreezeNPCs")))
                return false;
            Method owner = findPublicMethod(victim.getClass(), "getOwner");
            if (owner != null && owner.invoke(victim) == attacker) return false;
            Object victimFaction = field(victim, "faction");
            if (victimFaction == null) return false;
            if (attacker instanceof Player player) {
                Method friendly = victimFaction.getClass().getMethod("isFriendlyToPlayer", Player.class);
                return Boolean.TRUE.equals(friendly.invoke(victimFaction, player));
            }
            if (attacker instanceof LivingEntity other && isCustomNpc(other)) {
                Object attackerFaction = field(other, "faction");
                if (attackerFaction == null) return false;
                return factionId(attackerFaction) == factionId(victimFaction);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail closed: do not circumvent protection if original API differs.
        }
        return false;
    }

    private static int factionId(Object faction) throws ReflectiveOperationException {
        return faction.getClass().getField("id").getInt(faction);
    }
    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        Field f = instance.getClass().getField(name);
        return f.get(instance);
    }
    private static Object staticField(String className, String name) throws ReflectiveOperationException {
        return Class.forName(className).getField(name).get(null);
    }
    private static Method findPublicMethod(Class<?> type, String name) {
        try { return type.getMethod(name); }
        catch (NoSuchMethodException ignored) { return null; }
    }
    private static boolean isCustomNpc(LivingEntity living) {
        for (Class<?> type = living.getClass(); type != null; type = type.getSuperclass()) {
            if ("noppes.npcs.entity.EntityNPCInterface".equals(type.getName())) return true;
        }
        return false;
    }
    private NpcDamageCompatibility() {}
}
