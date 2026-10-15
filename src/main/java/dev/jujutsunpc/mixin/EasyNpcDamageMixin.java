package dev.jujutsunpc.mixin;

import dev.jujutsunpc.compat.NpcDamageCompatibility;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Easy NPC's PathfinderMobRaw.isInvulnerableTo delegates to native combat flags,
 * defaulting to invulnerable and not attackable by players. Only when explicitly
 * enabled for the particular victim, allow incoming player/NPC combat through.
 */
@Pseudo
@Mixin(targets = "de.markusbordihn.easynpc.api.npc.raw.PathfinderMobRaw", remap = false)
public abstract class EasyNpcDamageMixin {
    @Inject(method = "m_6673_(Lnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void jjknpc$allowCombatDamage(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && NpcDamageCompatibility.bypassEasyNpcDamageGate((LivingEntity)(Object)this, source)) {
            cir.setReturnValue(false);
        }
    }
}
