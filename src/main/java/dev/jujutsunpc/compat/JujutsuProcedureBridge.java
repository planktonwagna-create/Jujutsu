package dev.jujutsunpc.compat;

import dev.jujutsunpc.game.AbilityCatalog;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Executes an actual v51 procedure. The original JJK procedure, not this addon, decides
 * damage, particles and projectile effects. Individual procedures may reject NPC casters.
 *
 * For an explicit entity cast, temporarily substitute Mob#getTarget while executing the
 * procedure; restore the NPC's original AI target before returning. For a position or
 * direction cast, temporarily clear the native target to avoid silently redirecting a
 * manual cast at a different enemy. This only covers synchronous access to getTarget().
 */
public final class JujutsuProcedureBridge {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String PACKAGE = "net.mcreator.jujutsucraft.procedures.";
    private static final Map<String, Method> CACHE = new ConcurrentHashMap<>();

    public static boolean tryCast(ServerLevel level, LivingEntity caster,
                                  AbilityCatalog.Move move, Vec3 aim, LivingEntity explicitTarget) {
        if (aim == null || !Double.isFinite(aim.x) || !Double.isFinite(aim.y)
                || !Double.isFinite(aim.z)) return false;
        if (caster.position().distanceToSqr(aim) > 128 * 128) return false;
        if (explicitTarget != null && (explicitTarget.level() != level || !explicitTarget.isAlive())) return false;
        Mob mob = caster instanceof Mob m ? m : null;
        LivingEntity oldTarget = mob != null ? mob.getTarget() : null;
        try {
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
            // Critical for JJK procedures that read Mob#getTarget instead of facing.
            if (mob != null && oldTarget != explicitTarget) mob.setTarget(explicitTarget);
            Method method = findMethod(move.procedure());
            if (method == null) return false;
            if (method.getParameterCount() == 5) {
                method.invoke(null, level, caster.getX(), caster.getY(), caster.getZ(), caster);
            } else if (method.getParameterCount() == 2) {
                method.invoke(null, level, caster);
            } else return false;
            // Successful invocation does NOT prove the procedure actually launched an attack.
            return true;
        } catch (InvocationTargetException e) {
            LOG.warn("JujutsuCraft procedure {} threw for NPC {}", move.procedure(), caster.getUUID(), e.getTargetException());
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("JujutsuCraft procedure {} unavailable for NPC {}", move.procedure(), caster.getUUID(), e);
            return false;
        } finally {
            if (mob != null && mob.getTarget() != oldTarget) mob.setTarget(oldTarget);
        }
    }

    private static Method findMethod(String name) throws ReflectiveOperationException {
        Method cached = CACHE.get(name);
        if (cached != null) return cached;
        Class<?> klass = Class.forName(PACKAGE + name);
        Method method;
        try { method = klass.getMethod("execute", LevelAccessor.class, double.class, double.class, double.class, Entity.class); }
        catch (NoSuchMethodException ex) { method = klass.getMethod("execute", LevelAccessor.class, Entity.class); }
        CACHE.put(name, method);
        return method;
    }

    private JujutsuProcedureBridge() {}
}
