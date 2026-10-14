package dev.jujutsunpc.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

public final class NpcCompatibility {
    public static boolean isCompatible(LivingEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null) return false;
        if (id.getNamespace().equals("easy_npc")) return true;
        // CustomNPCs uses several concrete entity classes inheriting EntityNPCInterface.
        Class<?> type = entity.getClass();
        while (type != null) {
            if (type.getName().equals("noppes.npcs.entity.EntityNPCInterface")) return true;
            type = type.getSuperclass();
        }
        return false;
    }
    private NpcCompatibility() {}
}
