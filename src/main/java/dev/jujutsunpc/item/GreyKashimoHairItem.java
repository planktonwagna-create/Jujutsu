package dev.jujutsunpc.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

/**
 * Original Kashimo head geometry with grey instead of cyan hair textures.
 * No dependency on the upstream class at compile-time; loaded when equipped.
 */
public final class GreyKashimoHairItem extends ArmorItem {
    public GreyKashimoHairItem(){
        super(ArmorMaterials.LEATHER,ArmorItem.Type.HELMET,new Item.Properties());
    }
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,String type){
        return "jujutsunpccompat:textures/models/armor/kashimo_hair_grey.png";
    }
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer){
        consumer.accept(new IClientItemExtensions(){
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,
                       EquipmentSlot slot,HumanoidModel<?> original){
                try {
                    Class<?> model=Class.forName("net.mcreator.jujutsucraft.client.model.Modelclothes_kashimo");
                    Field layerField=model.getField("LAYER_LOCATION");
                    ModelLayerLocation layer=(ModelLayerLocation)layerField.get(null);
                    ModelPart root=Minecraft.getInstance().getEntityModels().bakeLayer(layer);
                    Constructor<?> constructor=model.getConstructor(ModelPart.class);
                    Object instance=constructor.newInstance(root);
                    ModelPart head=(ModelPart)model.getField("head").get(instance);
                    ModelPart empty=new ModelPart(Collections.emptyList(),Collections.emptyMap());
                    ModelPart wrapper=new ModelPart(Collections.emptyList(),Map.of(
                        "head",head,"hat",empty,
                        "body",new ModelPart(Collections.emptyList(),Collections.emptyMap()),
                        "right_arm",new ModelPart(Collections.emptyList(),Collections.emptyMap()),
                        "left_arm",new ModelPart(Collections.emptyList(),Collections.emptyMap()),
                        "right_leg",new ModelPart(Collections.emptyList(),Collections.emptyMap()),
                        "left_leg",new ModelPart(Collections.emptyList(),Collections.emptyMap())
                    ));
                    HumanoidModel<LivingEntity> result=new HumanoidModel<>(wrapper);
                    result.crouching=entity.isCrouching();
                    result.young=original.young;
                    result.riding=entity.isPassenger();
                    return result;
                }catch(ReflectiveOperationException | RuntimeException ex){
                    return original;
                }
            }
        });
    }
}
