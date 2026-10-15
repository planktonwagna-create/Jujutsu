package dev.jujutsunpc.client;

import dev.jujutsunpc.config.NpcProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Client-only entry point; invoked through DistExecutor. */
public final class ClientEditorBridge {
    public static void open(int id, CompoundTag tag) {
        Minecraft.getInstance().setScreen(new NpcEditorScreen(id,NpcProfile.fromNbt(tag)));
    }
    private ClientEditorBridge() {}
}
