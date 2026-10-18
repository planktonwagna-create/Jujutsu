package dev.jujutsunpc.network;

import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import dev.jujutsunpc.client.ClientEditorBridge;
import dev.jujutsunpc.JujutsuNpcCompatMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public final class CompatNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("jujutsunpccompat","config"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    public record OpenEditor(int id, CompoundTag config) {}
    public record SaveEditor(int id, CompoundTag config) {}
    public record PlayNpcAnimation(int id, String animation) {}
    public record HitSoundPolicy(boolean npc,boolean player) {}

    public static void init() {
        CHANNEL.messageBuilder(OpenEditor.class,0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b) -> { b.writeVarInt(m.id()); b.writeNbt(m.config()); })
            .decoder(b -> new OpenEditor(b.readVarInt(), b.readNbt()))
            .consumerMainThread((m, context) -> {
                DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> ClientEditorBridge.open(m.id(),m.config()));
                context.get().setPacketHandled(true);
            }).add();
        CHANNEL.messageBuilder(PlayNpcAnimation.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b) -> { b.writeVarInt(m.id()); b.writeUtf(m.animation(), 64); })
            .decoder(b -> new PlayNpcAnimation(b.readVarInt(), b.readUtf(64)))
            .consumerMainThread((m, context) -> {
                DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> dev.jujutsunpc.client.NpcAnimationState.play(m.id(), m.animation()));
                context.get().setPacketHandled(true);
            }).add();
        CHANNEL.messageBuilder(HitSoundPolicy.class,3,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeBoolean(m.npc());b.writeBoolean(m.player());})
            .decoder(b->new HitSoundPolicy(b.readBoolean(),b.readBoolean()))
            .consumerMainThread((m,context)->{
                DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    ()->()->dev.jujutsunpc.sound.ClientHitSoundState.sync(m.npc(),m.player()));
                context.get().setPacketHandled(true);
            }).add();
        CHANNEL.messageBuilder(SaveEditor.class,1,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b) -> { b.writeVarInt(m.id()); b.writeNbt(m.config()); })
            .decoder(b -> new SaveEditor(b.readVarInt(),b.readNbt()))
            .consumerMainThread((m,context) -> {
                ServerPlayer player = context.get().getSender();
                if (player != null && canEdit(player) && m.config() != null && m.config().size() <= 32) {
                    Entity entity = player.level().getEntity(m.id());
                    if (entity instanceof LivingEntity npc && NpcCompatibility.isCompatible(npc)
                           && player.distanceToSqr(npc) <= 100) {
                        NpcProfile sanitized = NpcProfile.fromNbt(m.config());
                        NpcStorage.save(npc,sanitized);
                    }
                }
                context.get().setPacketHandled(true);
            }).add();
    }
    public static void syncHitSounds(ServerPlayer player){
        CHANNEL.sendTo(new HitSoundPolicy(JujutsuNpcCompatMod.NPC_HIT_SOUNDS.get(),
                JujutsuNpcCompatMod.PLAYER_HIT_SOUNDS.get()),
                player.connection.connection,NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void syncHitSoundsToAll(net.minecraft.server.MinecraftServer server){
        for(ServerPlayer p:server.getPlayerList().getPlayers())syncHitSounds(p);
    }
    public static boolean canEdit(ServerPlayer player) {
        return player.isCreative() || player.hasPermissions(2);
    }
    public static void open(ServerPlayer player, LivingEntity npc) {
        if (!canEdit(player) || !NpcCompatibility.isCompatible(npc)
                || player.distanceToSqr(npc) > 100) return;
        CHANNEL.sendTo(new OpenEditor(npc.getId(),NpcStorage.profile(npc).toNbt()),
                       player.connection.connection,NetworkDirection.PLAY_TO_CLIENT);
    }
    public static void broadcastAnimation(LivingEntity npc, String animation) {
        if (!npc.level().isClientSide && !animation.isBlank()) {
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> npc),
                    new PlayNpcAnimation(npc.getId(), animation));
        }
    }
    public static void saveFromClient(int npc, NpcProfile profile) {
        CHANNEL.sendToServer(new SaveEditor(npc,profile.toNbt()));
    }
    private CompatNetwork() {}
}
