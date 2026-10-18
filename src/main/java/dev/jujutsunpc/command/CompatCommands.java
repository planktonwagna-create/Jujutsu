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
import dev.jujutsunpc.game.ItemMoveCatalog;
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
        root.then(Commands.literal("version").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Jujutsu NPC Compat v0.13 | full-body native poses with position and timing"
            ),false);
            return 1;
        }));
        root.then(Commands.literal("inspect").then(Commands.argument("npc",EntityArgument.entity())
                .executes(ctx -> inspect(ctx.getSource(),EntityArgument.getEntity(ctx,"npc")))));
        root.then(Commands.literal("animate").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.argument("animation",StringArgumentType.word())
                .executes(ctx -> animate(ctx.getSource(),EntityArgument.getEntity(ctx,"npc"),
                        StringArgumentType.getString(ctx,"animation"))))));
        root.then(Commands.literal("duel")
                .then(Commands.argument("first", EntityArgument.entity())
                .then(Commands.argument("second", EntityArgument.entity())
                .executes(ctx -> duel(ctx.getSource(),
                        EntityArgument.getEntity(ctx,"first"), EntityArgument.getEntity(ctx,"second"))))));
        root.then(Commands.literal("duelstop").then(Commands.argument("npc",EntityArgument.entity())
                .executes(ctx -> duelstop(ctx.getSource(),EntityArgument.getEntity(ctx,"npc")))));
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
        if(move==null)move=ItemMoveCatalog.equipped(npc).stream()
                .filter(m->m.key().equals(moveId)).findFirst().orElse(null);
        if (move == null) return 0;
        if (!NpcCombatEngine.cast(level,npc,p,move,target,explicitTarget,true)) {
            source.sendFailure(Component.literal("Move unavailable: check its mode, cooldown, current charge, or target."));
            return 0;
        }
        final AbilityCatalog.Move castMove=move;
        source.sendSuccess(() -> Component.literal("Scheduled JJK " + castMove.title()
                + " with " + npc.getName().getString()),true);
        return 1;
    }
    private static int inspect(CommandSourceStack source, Entity raw) {
        if (!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)) {
            source.sendFailure(Component.literal("That entity isn't a supported Easy NPC or CustomNPCs entity."));
            return 0;
        }
        NpcProfile profile = NpcStorage.profile(npc);
        net.minecraft.nbt.CompoundTag runtime = NpcStorage.runtime(npc, profile);
        net.minecraft.nbt.CompoundTag original = npc.getPersistentData();
        StringBuilder details = new StringBuilder("JJK NPC diagnostic: ")
                .append(npc.getName().getString())
                .append(" | type=").append(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(npc.getType()))
                .append(" | health=").append(String.format(java.util.Locale.ROOT,"%.1f/%.1f",npc.getHealth(),npc.getMaxHealth()))
                .append(" | invulnerable=").append(npc.isInvulnerable())
                .append(" | configured=").append(NpcStorage.configured(npc))
                .append(" | recovery=").append(profile.fatalRecovery)
                .append(" | idleHold=").append(profile.holdPositionWhenIdle)
                .append(" | allowCombatDamage=").append(profile.allowCombatDamage)
                .append(" | duel=").append(dev.jujutsunpc.game.NpcMeleeBridge.dueling(npc))
                .append(" | domainHealthThreshold=").append(profile.domainHealthThreshold).append("%")
                .append(" | sureHitDamage=").append(profile.domainSureHitDamage<0?"native":profile.domainSureHitDamage)
                .append(" | backdash=").append(profile.backDash)
                .append(" | backdashCooldown=").append(profile.backDashCooldownSeconds).append("s")
                .append(" | domainBarrier=").append(profile.domainOpen ? "open" : "closed")
                .append(" | shrineAppearance=").append(profile.shrineModel.name())
                .append(" | RCT active=").append(profile.rctActiveSeconds).append("s")
                .append(" | RCT cooldown=").append(profile.rctCooldownSeconds).append("s")
                .append(" | JJK cnt1=").append(Math.round(original.getDouble("cnt1")))
                .append(" cnt3=").append(Math.round(original.getDouble("cnt3")))
                .append(" | active=").append(runtime.contains("activeJjkMove", 10)
                    ? runtime.getCompound("activeJjkMove").getString("move") + "/" +
                    (runtime.getCompound("activeJjkMove").getBoolean("charging") ? "charging" : "casting")
                    + "/" + runtime.getCompound("activeJjkMove").getInt("remaining") + " steps"
                    : "none");
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
    /** Starts a symmetric, opt-in NPC-vs-NPC fight without changing native factions or healing. */
    private static int duel(CommandSourceStack source, Entity first, Entity second) {
        if (first == second || !(first instanceof LivingEntity a) || !(second instanceof LivingEntity b)
                || !(a instanceof net.minecraft.world.entity.Mob)
                || !(b instanceof net.minecraft.world.entity.Mob)
                || !NpcCompatibility.isCompatible(a) || !NpcCompatibility.isCompatible(b)
                || a.level() != b.level() || a.distanceToSqr(b) > 4096) {
            source.sendFailure(Component.literal("Choose two distinct nearby Easy NPC or CustomNPCs mobs in the same level."));
            return 0;
        }
        NpcProfile pa = NpcStorage.profile(a);
        NpcProfile pb = NpcStorage.profile(b);
        pa.allowCombatDamage = true;
        pb.allowCombatDamage = true;
        NpcStorage.save(a,pa);
        NpcStorage.save(b,pb);
        dev.jujutsunpc.game.NpcMeleeBridge.setDuel(a,b);
        dev.jujutsunpc.game.NpcMeleeBridge.setDuel(b,a);
        source.sendSuccess(() -> Component.literal("NPC duel started: " + a.getName().getString()
                + " vs " + b.getName().getString() + ". Both can take combat damage; healing unchanged."),true);
        return 1;
    }
    private static int duelstop(CommandSourceStack source, Entity raw) {
        if (!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)) {
            source.sendFailure(Component.literal("Not a supported NPC."));
            return 0;
        }
        dev.jujutsunpc.game.NpcMeleeBridge.stopDuelPair(npc);
        source.sendSuccess(() -> Component.literal("Stopped linked NPC duel. Damage settings are preserved."),true);
        return 1;
    }
    private CompatCommands() {}
}
