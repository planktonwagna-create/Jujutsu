package dev.jujutsunpc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.game.NpcCombatEngine;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** No fallback to the NPC's current combat target for manual casts. */
public final class CompatCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jjknpc")
                .requires(source -> source.hasPermission(2));
        var cast = Commands.literal("cast");
        var npc = Commands.argument("npc",EntityArgument.entity());
        var move = Commands.argument("move",StringArgumentType.word());
        move.then(Commands.literal("entity").then(Commands.argument("target",EntityArgument.entity())
            .executes(ctx -> cast(ctx.getSource(),EntityArgument.getEntity(ctx,"npc"),
                    StringArgumentType.getString(ctx,"move"),
                    EntityArgument.getEntity(ctx,"target")))));
        move.then(Commands.literal("position").then(Commands.argument("xyz",Vec3Argument.vec3())
            .executes(ctx -> cast(ctx.getSource(),EntityArgument.getEntity(ctx,"npc"),
                    StringArgumentType.getString(ctx,"move"),Vec3Argument.getVec3(ctx,"xyz")))));
        move.then(Commands.literal("direction").then(Commands.argument("dx",DoubleArgumentType.doubleArg(-1,1))
            .then(Commands.argument("dy",DoubleArgumentType.doubleArg(-1,1))
            .then(Commands.argument("dz",DoubleArgumentType.doubleArg(-1,1))
            .executes(ctx -> castDirection(ctx.getSource(),EntityArgument.getEntity(ctx,"npc"),
                    StringArgumentType.getString(ctx,"move"),new Vec3(
                    DoubleArgumentType.getDouble(ctx,"dx"),DoubleArgumentType.getDouble(ctx,"dy"),
                    DoubleArgumentType.getDouble(ctx,"dz"))))))));
        npc.then(move);
        cast.then(npc);
        root.then(cast);
        root.then(Commands.literal("inspect").then(Commands.argument("npc",EntityArgument.entity())
                .executes(ctx -> inspect(ctx.getSource(),EntityArgument.getEntity(ctx,"npc")))));
        root.then(Commands.literal("animate").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.argument("animation",StringArgumentType.word())
                .executes(ctx -> animate(ctx.getSource(),EntityArgument.getEntity(ctx,"npc"),
                        StringArgumentType.getString(ctx,"animation"))))));
        dispatcher.register(root);
    }
    private static int cast(CommandSourceStack source, Entity rawNpc, String move, Entity explicitTarget) {
        if (!(explicitTarget instanceof LivingEntity living) || !living.isAlive()) return 0;
        return castInternal(source, rawNpc, move, living.getBoundingBox().getCenter(), living);
    }
    private static int castDirection(CommandSourceStack source, Entity rawNpc, String move, Vec3 direction) {
        if (direction.lengthSqr() < 0.00001) return 0;
        return cast(source,rawNpc,move,rawNpc.getEyePosition().add(direction.normalize().scale(16)));
    }
    private static int cast(CommandSourceStack source,Entity entity,String moveId,Vec3 target) {
        return castInternal(source,entity,moveId,target,null);
    }
    private static int castInternal(CommandSourceStack source,Entity entity,String moveId,
                                    Vec3 target,LivingEntity explicitTarget) {
        if (!(entity instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)
                || !(npc.level() instanceof ServerLevel level) || !NpcStorage.configured(npc)) return 0;
        NpcProfile p = NpcStorage.profile(npc);
        AbilityCatalog.Move move = AbilityCatalog.move(p.technique,moveId).orElse(null);
        if (move == null) return 0;
        if (!NpcCombatEngine.cast(level,npc,p,move,target,explicitTarget,true)) return 0;
        source.sendSuccess(() -> Component.literal("Attempted JJK " + move.title()
                + " with " + npc.getName().getString()),true);
        return 1;
    }
    private static int inspect(CommandSourceStack source, Entity raw) {
        if (!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)) {
            source.sendFailure(Component.literal("That entity isn't a supported Easy NPC or CustomNPCs entity."));
            return 0;
        }
        NpcProfile profile = NpcStorage.profile(npc);
        StringBuilder details = new StringBuilder("JJK NPC diagnostic: ")
                .append(npc.getName().getString())
                .append(" | type=").append(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(npc.getType()))
                .append(" | health=").append(String.format(java.util.Locale.ROOT,"%.1f/%.1f",npc.getHealth(),npc.getMaxHealth()))
                .append(" | invulnerable=").append(npc.isInvulnerable())
                .append(" | configured=").append(NpcStorage.configured(npc))
                .append(" | recovery=").append(profile.fatalRecovery)
                .append(" | idleHold=").append(profile.holdPositionWhenIdle);
        if (npc instanceof net.minecraft.world.entity.Mob mob) {
            details.append(" | noAI=").append(mob.isNoAi())
                    .append(" | target=").append(mob.getTarget() == null ? "none" : mob.getTarget().getName().getString());
        }
        // CustomNPCs filters friendly-faction player hits *before* vanilla hurt.
        // This check reports that original setting; it never changes faction or damage rules.
        try {
            Class<?> type = npc.getClass();
            while (type != null && !type.getName().equals("noppes.npcs.entity.EntityNPCInterface")) type = type.getSuperclass();
            if (type != null) {
                Object faction = type.getField("faction").get(npc);
                net.minecraft.world.entity.player.Player player = source.getPlayerOrException();
                boolean friendly = (boolean) faction.getClass()
                        .getMethod("isFriendlyToPlayer", net.minecraft.world.entity.player.Player.class)
                        .invoke(faction, player);
                details.append(" | factionFriendlyToYou=").append(friendly);
            }
        } catch (ReflectiveOperationException | com.mojang.brigadier.exceptions.CommandSyntaxException ignored) {
            // Some NPC forks hide their faction API.
        }
        source.sendSuccess(() -> Component.literal(details.toString()),false);
        return 1;
    }
    private static int animate(CommandSourceStack source, Entity raw, String animation) {
        if (!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc) || !animation.matches("[a-z0-9_]{1,48}")) {
            source.sendFailure(Component.literal("Invalid NPC or animation name."));
            return 0;
        }
        dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc,animation);
        source.sendSuccess(() -> Component.literal("Sent original JJK animation " + animation
                + " to nearby clients; visibility depends on NPC renderer support."),false);
        return 1;
    }
    private CompatCommands() {}
}
