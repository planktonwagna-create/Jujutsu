package dev.jujutsunpc.mixin;

import dev.jujutsunpc.client.NpcAnimationState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs after vanilla pose setup, before NPC humanoid geometry is rendered. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidPoseMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void jjknpc$applyOriginalPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                          float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        NpcAnimationState.apply(entity, (HumanoidModel<?>)(Object)this);
    }
}
