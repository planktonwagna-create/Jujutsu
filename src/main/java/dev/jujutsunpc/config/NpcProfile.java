package dev.jujutsunpc.config;

import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.game.CastPolicy.Mode;
import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Persistent NPC configuration. Times are in real seconds (20 game ticks/second). */
public final class NpcProfile {
    public static final String ROOT = "jujutsunpccompat_profile";
    public String technique = "none";
    public EnumMap<Defence, Boolean> defences = new EnumMap<>(Defence.class);
    public Map<String, Mode> moveModes = new LinkedHashMap<>();
    public Map<String, Integer> moveCooldowns = new LinkedHashMap<>();
    public Map<String, Integer> moveCharges = new LinkedHashMap<>();
    public EnumMap<Defence, Integer> defenceCooldowns = new EnumMap<>(Defence.class);
    public boolean domainOpen = false; // Only meaningful for the Shrine technique in JJK v51.
    public int rctActiveSeconds = 8;
    public int rctCooldownSeconds = 20;
    public boolean autoCombat = true;
    public boolean holdPositionWhenIdle = true;
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
        moveCooldowns.clear();
        moveCharges.clear();
    }
    public void setMoveMode(String key, Mode mode) {
        if (AbilityCatalog.move(technique,key).isPresent()) moveModes.put(key,mode);
    }
    public int cooldown(String move) {
        return moveCooldowns.getOrDefault(move, AbilityCatalog.move(technique,move)
                .map(m -> Math.max(1,(m.cooldownTicks()+19)/20)).orElse(5));
    }
    public int charge(String move) { return moveCharges.getOrDefault(move,0); }
    public void setCooldown(String move, int seconds) {
        if (AbilityCatalog.move(technique,move).isPresent())
            moveCooldowns.put(move,clamp(seconds,0,3600));
    }
    public void setCharge(String move, int seconds) {
        if (AbilityCatalog.move(technique,move).isPresent())
            moveCharges.put(move,clamp(seconds,0,120));
    }
    public int defenceCooldown(Defence defence) {
        return defenceCooldowns.getOrDefault(defence,15);
    }
    public void setDefenceCooldown(Defence defence,int seconds) {
        defenceCooldowns.put(defence,clamp(seconds,0,3600));
    }
    public static boolean allowsOpenDomain(String technique) {
        return "shrine".equals(technique);
    }
    private static int clamp(int x,int min,int max) { return Math.max(min,Math.min(max,x)); }
    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        root.putString("technique",technique);
        root.putBoolean("autoCombat",autoCombat);
        root.putBoolean("holdPositionWhenIdle",holdPositionWhenIdle);
        root.putBoolean("allowCombatDamage",allowCombatDamage);
        root.putBoolean("rct",rct);
        root.putInt("rctActiveSeconds",rctActiveSeconds);
        root.putInt("rctCooldownSeconds",rctCooldownSeconds);
        root.putBoolean("domainOpen",domainOpen);
        root.putBoolean("fatalRecovery",fatalRecovery);
        root.putInt("fatalCharges",fatalRecoveryCharges);
        root.putFloat("fatalHealth",fatalRecoveryHealthFraction);
        CompoundTag moves = new CompoundTag();
        moveModes.forEach((id,mode) -> moves.putString(id,mode.name()));
        root.put("moves",moves);
        CompoundTag cooldowns=new CompoundTag();
        moveCooldowns.forEach(cooldowns::putInt);
        root.put("moveCooldowns",cooldowns);
        CompoundTag charges=new CompoundTag();
        moveCharges.forEach(charges::putInt);
        root.put("moveCharges",charges);
        CompoundTag def=new CompoundTag();
        defences.forEach((id,value) -> def.putBoolean(id.name(),value));
        root.put("defences",def);
        CompoundTag defCooldowns=new CompoundTag();
        defenceCooldowns.forEach((id,value) -> defCooldowns.putInt(id.name(),value));
        root.put("defenceCooldowns",defCooldowns);
        return root;
    }
    public static NpcProfile fromNbt(CompoundTag root) {
        NpcProfile p=new NpcProfile();
        p.setTechnique(root.getString("technique"));
        p.autoCombat=!root.contains("autoCombat")||root.getBoolean("autoCombat");
        p.holdPositionWhenIdle=!root.contains("holdPositionWhenIdle")||root.getBoolean("holdPositionWhenIdle");
        p.allowCombatDamage=root.getBoolean("allowCombatDamage");
        p.rct=root.getBoolean("rct");
        p.rctActiveSeconds=root.contains("rctActiveSeconds")
                ?clamp(root.getInt("rctActiveSeconds"),1,600):8;
        p.rctCooldownSeconds=root.contains("rctCooldownSeconds")
                ?clamp(root.getInt("rctCooldownSeconds"),0,3600):20;
        p.domainOpen=root.getBoolean("domainOpen");
        p.fatalRecovery=root.getBoolean("fatalRecovery");
        p.fatalRecoveryCharges=clamp(root.getInt("fatalCharges"),0,1000);
        float frac=root.getFloat("fatalHealth");
        p.fatalRecoveryHealthFraction=Float.isFinite(frac)?Math.max(0.01f,Math.min(1f,frac)):0.5f;
        CompoundTag moves=root.getCompound("moves");
        for(String id:moves.getAllKeys()) {
            if(AbilityCatalog.move(p.technique,id).isEmpty())continue;
            try { p.setMoveMode(id,Mode.valueOf(moves.getString(id))); }
            catch(IllegalArgumentException ignored) {}
        }
        CompoundTag cooldowns=root.getCompound("moveCooldowns");
        for(String id:cooldowns.getAllKeys())
            if(cooldowns.contains(id,3))p.setCooldown(id,cooldowns.getInt(id));
        CompoundTag charges=root.getCompound("moveCharges");
        for(String id:charges.getAllKeys())
            if(charges.contains(id,3))p.setCharge(id,charges.getInt(id));
        CompoundTag def=root.getCompound("defences");
        for(Defence d:Defence.values())p.defences.put(d,def.getBoolean(d.name()));
        CompoundTag defCooldowns=root.getCompound("defenceCooldowns");
        for(Defence d:Defence.values())
            if(defCooldowns.contains(d.name(),3))
                p.setDefenceCooldown(d,defCooldowns.getInt(d.name()));
        return p;
    }
}
