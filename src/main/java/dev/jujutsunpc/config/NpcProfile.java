package dev.jujutsunpc.config;

import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.game.CastPolicy.Mode;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Saved to the NPC's Forge persistent NBT, not a global name-based database. */
public final class NpcProfile {
    public static final String ROOT = "jujutsunpccompat_profile";
    public String technique = "none";
    public EnumMap<Defence, Boolean> defences = new EnumMap<>(Defence.class);
    public Map<String, Mode> moveModes = new LinkedHashMap<>();
    public double maxEnergy = 500.0;
    public double regenPerSecond = 5.0;
    public boolean autoCombat = true;
    public boolean holdPositionWhenIdle = true;
    /** Opt-in per NPC: bypass specific third-party faction/invulnerability gates for player/NPC combat. */
    public boolean allowCombatDamage = false;
    public boolean rct = false;
    public boolean fatalRecovery = false;
    public int fatalRecoveryCharges = 1;
    public float fatalRecoveryHealthFraction = 0.5f;

    public enum Defence { SIMPLE_DOMAIN, HOLLOW_WICKER_BASKET, FALLING_BLOSSOM_EMOTION, DOMAIN_AMPLIFICATION }

    public Mode mode(String move) { return moveModes.getOrDefault(move, Mode.DISABLED); }
    public void setTechnique(String id) {
        technique = AbilityCatalog.technique(id).key();
        moveModes.clear();
        // Don't automatically grant every move; creator must enable each one.
    }
    public void setMoveMode(String key, Mode mode) {
        if (AbilityCatalog.move(technique, key).isPresent()) moveModes.put(key, mode);
    }
    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        root.putString("technique", technique);
        root.putDouble("maxEnergy", maxEnergy);
        root.putDouble("regen", regenPerSecond);
        root.putBoolean("autoCombat", autoCombat);
        root.putBoolean("holdPositionWhenIdle", holdPositionWhenIdle);
        root.putBoolean("allowCombatDamage", allowCombatDamage);
        root.putBoolean("rct", rct);
        root.putBoolean("fatalRecovery", fatalRecovery);
        root.putInt("fatalCharges", fatalRecoveryCharges);
        root.putFloat("fatalHealth", fatalRecoveryHealthFraction);
        CompoundTag moves = new CompoundTag();
        moveModes.forEach((id, mode) -> moves.putString(id, mode.name()));
        root.put("moves", moves);
        CompoundTag def = new CompoundTag();
        defences.forEach((id, value) -> def.putBoolean(id.name(), value));
        root.put("defences", def);
        return root;
    }
    public static NpcProfile fromNbt(CompoundTag root) {
        NpcProfile p = new NpcProfile();
        p.setTechnique(root.getString("technique"));
        p.maxEnergy = finite(root.getDouble("maxEnergy"), 500, 1, 100000);
        p.regenPerSecond = finite(root.getDouble("regen"), 5, 0, 1000);
        p.autoCombat = !root.contains("autoCombat") || root.getBoolean("autoCombat");
        p.holdPositionWhenIdle = !root.contains("holdPositionWhenIdle") || root.getBoolean("holdPositionWhenIdle");
        p.allowCombatDamage = root.getBoolean("allowCombatDamage");
        p.rct = root.getBoolean("rct");
        p.fatalRecovery = root.getBoolean("fatalRecovery");
        p.fatalRecoveryCharges = Math.max(0, Math.min(1000, root.getInt("fatalCharges")));
        p.fatalRecoveryHealthFraction = (float)finite(root.getFloat("fatalHealth"),0.5,0.01,1);
        CompoundTag moves = root.getCompound("moves");
        for (String key : moves.getAllKeys()) {
            if (AbilityCatalog.move(p.technique, key).isEmpty()) continue;
            try { p.setMoveMode(key, Mode.valueOf(moves.getString(key))); }
            catch (IllegalArgumentException ignored) {}
        }
        CompoundTag def = root.getCompound("defences");
        for (Defence d : Defence.values()) p.defences.put(d, def.getBoolean(d.name()));
        return p;
    }
    private static double finite(double x, double fallback, double min, double max) {
        if (!Double.isFinite(x)) return fallback;
        return Math.max(min, Math.min(max,x));
    }
}
