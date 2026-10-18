package dev.jujutsunpc.mixin;

import dev.jujutsunpc.client.NpcAnimationState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

/** Apply the original v51 animation AFTER Easy NPC's ModelManager updates its bones. */
@Pseudo
@Mixin(targets="de.markusbordihn.easynpc.client.model.EasyNPCModel",remap=false)
public abstract class EasyNpcAnimationAfterMixin {
    @Inject(method="setupAnimationEnd(Lde/markusbordihn/easynpc/entity/easynpc/EasyNPC;Lde/markusbordihn/easynpc/client/model/EasyNPCModelManager;)V",
            at=@At("TAIL"),require=0,remap=false)
    private static void jjknpc$poseAfterEasyNpc(@Coerce Object npc,@Coerce Object manager,CallbackInfo ci) {
        try {
            Object living=npc.getClass().getMethod("getLivingEntity").invoke(npc);
            if(!(living instanceof LivingEntity entity))return;
            Class<?> type=Class.forName("de.markusbordihn.easynpc.data.model.ModelPartType");
            Method getter=manager.getClass().getMethod("getModelPart",type);
            String[] names={"HEAD","BODY","RIGHT_ARM","LEFT_ARM","RIGHT_LEG","LEFT_LEG"};
            ModelPart[] parts=new ModelPart[names.length];
            for(int i=0;i<names.length;i++){
                @SuppressWarnings({"unchecked","rawtypes"})
                Object partKey=Enum.valueOf((Class<Enum>)type,names[i]);
                Object raw=getter.invoke(manager,partKey);
                parts[i]=raw instanceof ModelPart part?part:null;
            }
            NpcAnimationState.applyParts(entity,parts);
        }catch(ReflectiveOperationException | RuntimeException ignored){
            // Optional Easy NPC fork may expose a different model-manager API.
        }
    }
}
