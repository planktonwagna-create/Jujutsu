package dev.jujutsunpc.mixin;

import dev.jujutsunpc.client.NpcAnimationState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CustomNPCs GBPort's ModelClassicPlayer overrides setupAnim after calling PlayerModel's
 * implementation. Apply the JJK pose *after* that custom override so its post-processing
 * doesn't immediately undo the animation. This optional client-only mixin targets the
 * actual SRG method name shipped by GBPort 1.20.1.
 */
@Pseudo
@Mixin(targets = "noppes.npcs.client.model.ModelClassicPlayer", remap = false)
public abstract class CustomNpcPlayerPoseMixin {
    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL"), require = 0, remap = false)
    private void jjknpc$afterCustomPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                        float ageInTicks, float netHeadYaw, float headPitch,
                                        CallbackInfo ci) {
        NpcAnimationState.apply(entity, (HumanoidModel<?>)(Object)this);
    }
}
