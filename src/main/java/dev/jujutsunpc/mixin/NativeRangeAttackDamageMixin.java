package dev.jujutsunpc.mixin;

import dev.jujutsunpc.game.NpcCombatTuning;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes only NPC-caster native v51 RangeAttack damage inputs. */
@Pseudo
@Mixin(targets="net.mcreator.jujutsucraft.procedures.RangeAttackProcedure",remap=false)
public abstract class NativeRangeAttackDamageMixin {
    @Inject(method="execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
        at=@At("HEAD"),require=0,remap=false)
    private static void jjknpc$damage(LevelAccessor level,double x,double y,double z,Entity caster,CallbackInfo ci){
        NpcCombatTuning.onNativeRangeAttack(caster);
    }
}
