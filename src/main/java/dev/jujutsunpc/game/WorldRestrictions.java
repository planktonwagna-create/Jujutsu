package dev.jujutsunpc.game;

import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

public final class WorldRestrictions {
    /** Does not cancel summoning, creative eggs, commands, or NPC-created entities. */
    public static void preventNaturalJjkMobs(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType reason = event.getSpawnType();
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
        if (type != null && type.getNamespace().equals("jujutsucraft")) {
            event.setSpawnCancelled(true); // Critical: event.setCanceled(true) alone does not prevent spawning.
        }
    }
    private WorldRestrictions() {}
}
