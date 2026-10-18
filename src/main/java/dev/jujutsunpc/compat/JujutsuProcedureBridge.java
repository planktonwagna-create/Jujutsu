package dev.jujutsunpc.compat;

import dev.jujutsunpc.game.AbilityCatalog;
import dev.jujutsunpc.game.ItemMoveCatalog;
import dev.jujutsunpc.config.NpcProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
        return tryCast(level,caster,move,aim,explicitTarget,null);
    }

    /**
     * Malevolent Shrine variant in v51 is chosen by SUKUNA_EFFECT inside the
     * original DomainExpansionCreateBarrierProcedure. Only expose this switch
     * for Shrine. The effect is temporarily added/hidden solely while the
     * procedure runs and the previous effect state is then restored.
     */
    public static boolean tryCast(ServerLevel level, LivingEntity caster,
                                  AbilityCatalog.Move move, Vec3 aim,
                                  LivingEntity explicitTarget, Boolean openShrine) {
        return tryCast(level,caster,move,aim,explicitTarget,openShrine,null);
    }

    public static boolean tryCast(ServerLevel level, LivingEntity caster,
                                  AbilityCatalog.Move move, Vec3 aim,
                                  LivingEntity explicitTarget, Boolean openShrine,
                                  NpcProfile.ShrineModel shrineModel) {
        return tryCast(level,caster,move,aim,explicitTarget,openShrine,shrineModel,false,false);
    }

    /** Simulate a real held Z input and native crouch branch for this procedure step. */
    public static boolean tryCast(ServerLevel level, LivingEntity caster,
                                  AbilityCatalog.Move move, Vec3 aim, LivingEntity explicitTarget,
                                  Boolean openShrine,NpcProfile.ShrineModel shrineModel,
                                  boolean pressHeld,boolean crouching) {
        if (aim == null || !Double.isFinite(aim.x) || !Double.isFinite(aim.y)
                || !Double.isFinite(aim.z)) return false;
        if (caster.position().distanceToSqr(aim) > 128 * 128) return false;
        if (explicitTarget != null && (explicitTarget.level() != level || !explicitTarget.isAlive())) return false;
        // Used only by the Malevolent Shrine appearance mixin, for the duration
        // of this particular original procedure call.
        final String shrineKey="jujutsunpc_shrineCasting";
        String oldShrineValue=caster.getPersistentData().getString(shrineKey);
        boolean hadShrineValue=caster.getPersistentData().contains(shrineKey,8);
        boolean shrineCast=shrineModel!=null
                && AbilityCatalog.isDomain(move)
                && "MalevolentShrineProcedure".equals(move.procedure());
        if(shrineCast)caster.getPersistentData().putString(shrineKey,shrineModel.name());
        CompoundTag state=caster.getPersistentData();
        boolean hadPress=state.contains("PRESS_Z",1);
        boolean oldPress=state.getBoolean("PRESS_Z");
        boolean oldSneaking=caster.isShiftKeyDown();
        boolean hadSkill=state.contains("skill",6);
        double oldSkill=state.getDouble("skill");
        if(move.skillId()>=0)state.putDouble("skill",move.skillId());
        state.putBoolean("PRESS_Z",pressHeld);
        caster.setShiftKeyDown(crouching);
        Mob mob = caster instanceof Mob m ? m : null;
        LivingEntity oldTarget = mob != null ? mob.getTarget() : null;
        MobEffect sukunaEffect=null;
        MobEffectInstance previousEffect=null;
        boolean addedEffect=false;
        boolean removedEffect=false;
        if(openShrine!=null && AbilityCatalog.isDomain(move)){
            sukunaEffect=ForgeRegistries.MOB_EFFECTS.getValue(
                    new ResourceLocation("jujutsucraft","sukuna_effect"));
            if(sukunaEffect!=null){
                previousEffect=caster.getEffect(sukunaEffect);
                if(openShrine && previousEffect==null){
                    addedEffect=caster.addEffect(new MobEffectInstance(sukunaEffect,4,0,false,false,false));
                }else if(!openShrine && previousEffect!=null){
                    removedEffect=caster.removeEffect(sukunaEffect);
                }
            }
        }
        try {
            caster.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
            // Critical for JJK procedures that read Mob#getTarget instead of facing.
            if (mob != null && oldTarget != explicitTarget) mob.setTarget(explicitTarget);
            Method method = findMethod(move.procedure());
            if (method == null) return false;
            // Original item-click procedures that accept an ItemStack must see
            // the held JJK item even when equipped in the NPC's offhand.
            ItemStack held=ItemMoveCatalog.available(caster,move)
                    ?ItemMoveCatalog.heldItem(caster):caster.getMainHandItem();
            Class<?>[] args=method.getParameterTypes();
            if(args.length==5){
                method.invoke(null,level,caster.getX(),caster.getY(),caster.getZ(),caster);
            }else if(args.length==2){
                method.invoke(null,level,caster);
            }else if(args.length==3 && args[2]==ItemStack.class){
                method.invoke(null,level,caster,held);
            }else if(args.length==6 && args[5]==ItemStack.class){
                method.invoke(null,level,caster.getX(),caster.getY(),caster.getZ(),caster,caster.getMainHandItem());
            }else if(args.length==7 && args[4]==Direction.class){
                method.invoke(null,level,caster.getX(),caster.getY(),caster.getZ(),
                        Direction.fromYRot(caster.getYRot()),caster,caster.getMainHandItem());
            }else return false;
            // Successful invocation does NOT prove the procedure actually launched an attack.
            return true;
        } catch (InvocationTargetException e) {
            LOG.warn("JujutsuCraft procedure {} threw for NPC {}", move.procedure(), caster.getUUID(), e.getTargetException());
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("JujutsuCraft procedure {} unavailable for NPC {}", move.procedure(), caster.getUUID(), e);
            return false;
        } finally {
            if(hadPress)state.putBoolean("PRESS_Z",oldPress);
            else state.remove("PRESS_Z");
            if(hadSkill)state.putDouble("skill",oldSkill);
            else state.remove("skill");
            caster.setShiftKeyDown(oldSneaking);
            if(shrineCast){
                if(hadShrineValue)caster.getPersistentData().putString(shrineKey,oldShrineValue);
                else caster.getPersistentData().remove(shrineKey);
            }
            if(sukunaEffect!=null){
                if(addedEffect)caster.removeEffect(sukunaEffect);
                if(removedEffect && previousEffect!=null)
                    caster.addEffect(new MobEffectInstance(previousEffect));
            }
            if (mob != null && mob.getTarget() != oldTarget) mob.setTarget(oldTarget);
        }
    }

    private static Method findMethod(String name) throws ReflectiveOperationException {
        Method cached=CACHE.get(name);
        if(cached!=null)return cached;
        Class<?> klass=Class.forName(PACKAGE+name);
        Method found=null;
        for(Method method:klass.getMethods()){
            if(!"execute".equals(method.getName())||!java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                continue;
            Class<?>[] args=method.getParameterTypes();
            if(args.length<2||args[0]!=LevelAccessor.class)continue;
            if(args.length==2 && args[1]==Entity.class){found=method;break;}
            if(args.length==3 && args[1]==Entity.class && args[2]==ItemStack.class){found=method;break;}
            if(args.length>=5 && args[1]==double.class && args[2]==double.class && args[3]==double.class){
                if(args.length==5 && args[4]==Entity.class){found=method;break;}
                if(args.length==6 && args[4]==Entity.class && args[5]==ItemStack.class){found=method;break;}
                if(args.length==7 && args[4]==Direction.class && args[5]==Entity.class
                        && args[6]==ItemStack.class){found=method;break;}
            }
        }
        if(found==null)throw new NoSuchMethodException(klass.getName()+".execute");
        CACHE.put(name,found);
        return found;
    }

    private JujutsuProcedureBridge() {}
}
