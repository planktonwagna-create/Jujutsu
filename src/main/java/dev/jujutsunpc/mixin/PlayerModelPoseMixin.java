package dev.jujutsunpc.mixin;

import dev.jujutsunpc.client.NpcAnimationState;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Easy NPC humanoids render through PlayerModel, which completes its own
 * post-super pose logic after HumanoidModel.setupAnim. Apply the JJK animation
 * after that logic, then synchronize the sleeve/jacket overlay model parts.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at=@At("TAIL"),require=0)
    private void jjknpc$afterPlayerPose(LivingEntity entity,float limbSwing,float limbSwingAmount,
                                        float ageInTicks,float netHeadYaw,float headPitch,
                                        CallbackInfo info) {
        NpcAnimationState.apply(entity,(PlayerModel<?>)(Object)this);
    }
}
