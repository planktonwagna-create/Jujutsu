package dev.jujutsunpc.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

public final class NpcStorage {
    public static final String STATE = "jujutsunpccompat_runtime";
    public static boolean configured(LivingEntity npc) {
        return npc.getPersistentData().contains(NpcProfile.ROOT, 10);
    }
    public static NpcProfile profile(LivingEntity npc) {
        return configured(npc) ? NpcProfile.fromNbt(npc.getPersistentData().getCompound(NpcProfile.ROOT))
                               : new NpcProfile();
    }
    public static void save(LivingEntity npc, NpcProfile p) {
        npc.getPersistentData().put(NpcProfile.ROOT, p.toNbt());
        initRuntimeIfClone(npc,p);
    }
    public static CompoundTag runtime(LivingEntity npc, NpcProfile p) {
        initRuntimeIfClone(npc, p);
        return npc.getPersistentData().getCompound(STATE);
    }
    // Loading the same UUID preserves resources. Cloning to a new UUID gets fresh resources.
    public static void initRuntimeIfClone(LivingEntity npc, NpcProfile p) {
        CompoundTag tag = npc.getPersistentData().getCompound(STATE);
        if (!tag.getString("npcUuid").equals(npc.getUUID().toString())) {
            tag = new CompoundTag();
            tag.putString("npcUuid", npc.getUUID().toString());
            tag.putDouble("energy", p.maxEnergy);
            tag.putInt("fatalChargesLeft", p.fatalRecoveryCharges);
            npc.getPersistentData().put(STATE, tag);
        }
    }
    private NpcStorage() {}
}
