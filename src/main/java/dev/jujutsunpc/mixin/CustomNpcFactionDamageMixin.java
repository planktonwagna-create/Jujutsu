package dev.jujutsunpc.mixin;

import dev.jujutsunpc.compat.NpcDamageCompatibility;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only bypass CustomNPCs' pre-vanilla friendly-player/same-faction early return.
 * Call PathfinderMob.hurt so normal vanilla/Forge damage rules handle the hit.
 *
 * Trade-off: native CustomNPCs DamagedEvent is not dispatched for this narrow
 * opt-in bypass path. Original NPC JAR is not modified.
 */
@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class CustomNpcFactionDamageMixin extends PathfinderMob {
    protected CustomNpcFactionDamageMixin(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void jjknpc$allowFactionCombat(DamageSource source, float amount,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (NpcDamageCompatibility.bypassCustomNpcFactionGate((LivingEntity)(Object)this, source)) {
            cir.setReturnValue(super.hurt(source, amount));
        }
    }
}
