package dev.jujutsunpc.mixin;

import com.mojang.logging.LogUtils;
import dev.jujutsunpc.client.NpcAnimationState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Method;

/**
 * Easy NPC adjusts its player-model bones in setupAnimationEnd. Reapply the
 * original JJK pose AFTER those adjustments, including visible outer layers.
 */
@Pseudo
@Mixin(targets="de.markusbordihn.easynpc.client.model.EasyNPCModel",remap=false)
public abstract class EasyNpcAnimationAfterMixin {
    private static final Logger LOG=LogUtils.getLogger();
    private static final String[] NAMES={"HEAD","BODY","RIGHT_ARM","LEFT_ARM","RIGHT_LEG","LEFT_LEG"};
    private static final String[] OVERLAYS={"HAT","BODY_JACKET","RIGHT_SLEEVE","LEFT_SLEEVE","RIGHT_PANTS","LEFT_PANTS"};
    private static Class<?> partType;
    private static Method getPart;
    private static Object[] partKeys;
    private static Object[] overlayKeys;
    private static boolean warned;

    private static void initialize(Object manager)throws ReflectiveOperationException{
        if(getPart!=null)return;
        partType=Class.forName("de.markusbordihn.easynpc.data.model.ModelPartType");
        getPart=manager.getClass().getMethod("getModelPart",partType);
        partKeys=new Object[NAMES.length];
        overlayKeys=new Object[OVERLAYS.length];
        for(int i=0;i<NAMES.length;i++){
            @SuppressWarnings({"unchecked","rawtypes"})
            Object key=Enum.valueOf((Class<Enum>)partType,NAMES[i]);
            partKeys[i]=key;
            @SuppressWarnings({"unchecked","rawtypes"})
            Object overlay=Enum.valueOf((Class<Enum>)partType,OVERLAYS[i]);
            overlayKeys[i]=overlay;
        }
    }

    @Inject(method="setupAnimationEnd(Lde/markusbordihn/easynpc/entity/easynpc/EasyNPC;Lde/markusbordihn/easynpc/client/model/EasyNPCModelManager;)V",
            at=@At("TAIL"),require=0,remap=false)
    private static void jjknpc$poseAfterEasyNpc(@Coerce Object npc,@Coerce Object manager,CallbackInfo ci){
        // EasyNPC objects themselves are LivingEntity mobs. The old reflective
        // getLivingEntity() call could fail silently on implementation classes.
        if(!(npc instanceof LivingEntity living)||manager==null)return;
        try{
            initialize(manager);
            ModelPart[] parts=new ModelPart[NAMES.length];
            ModelPart[] overlays=new ModelPart[OVERLAYS.length];
            for(int i=0;i<parts.length;i++){
                Object core=getPart.invoke(manager,partKeys[i]);
                parts[i]=core instanceof ModelPart part?part:null;
                Object shell=getPart.invoke(manager,overlayKeys[i]);
                overlays[i]=shell instanceof ModelPart part?part:null;
            }
            if(NpcAnimationState.applyParts(living,parts)){
                for(int i=0;i<parts.length;i++)
                    if(parts[i]!=null && overlays[i]!=null)
                        overlays[i].copyFrom(parts[i]);
            }
        }catch(ReflectiveOperationException|RuntimeException error){
            if(!warned){
                warned=true;
                LOG.warn("Jujutsu NPC: Easy NPC animation model-part binding failed; see renderer API",error);
            }
        }
    }
}
