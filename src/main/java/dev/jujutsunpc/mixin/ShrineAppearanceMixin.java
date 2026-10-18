package dev.jujutsunpc.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * JujutsuCraft v51 decides between two Malevolent Shrine appearance entities
 * at the original procedure's health-ratio branch. Only redirect that ONE
 * health query when the addon explicitly marks a Shrine cast in progress.
 * The NPC's real health, damage, and healing are never modified.
 */
@Pseudo
@Mixin(targets="net.mcreator.jujutsucraft.procedures.MalevolentShrineProcedure",remap=false)
public abstract class ShrineAppearanceMixin {
    @Redirect(
        method="execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
        at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/LivingEntity;m_21223_()F",
            remap=false),
        require=0,remap=false
    )
    private static float jjknpc$chooseOriginalShrineModel(LivingEntity caster){
        CompoundTag persistent=caster.getPersistentData();
        if(!persistent.contains("jujutsunpc_shrineCasting",8))
            return caster.getHealth();
        // First original model when above half HP; second original model below.
        return "MUTATED".equals(persistent.getString("jujutsunpc_shrineCasting"))
                ? 0.0f : caster.getMaxHealth();
    }
}
