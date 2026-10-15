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
    private CompatCommands() {}
}
