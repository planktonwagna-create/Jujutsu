package dev.jujutsunpc.game;

import dev.jujutsunpc.config.NpcProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Invokes available ORIGINAL v51 defenses, each with its own persisted cooldown. */
public final class DomainDefense {
    private static MobEffect originalEffect(String name){
        return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft",name));
    }
    public static boolean targetHasDomain(LivingEntity target) {
        MobEffect effect=originalEffect("domain_expansion");
        return effect!=null && target.hasEffect(effect);
    }
    public static NpcProfile.Defence tryAntiDomain(LivingEntity npc,NpcProfile profile,
                                                   CompoundTag state,long now) {
        if(apply(npc,profile,state,now,NpcProfile.Defence.SIMPLE_DOMAIN,"simple_domain"))
            return NpcProfile.Defence.SIMPLE_DOMAIN;
        if(apply(npc,profile,state,now,NpcProfile.Defence.FALLING_BLOSSOM_EMOTION,"falling_blossom_emotion"))
            return NpcProfile.Defence.FALLING_BLOSSOM_EMOTION;
        if(apply(npc,profile,state,now,NpcProfile.Defence.DOMAIN_AMPLIFICATION,"domain_amplification"))
            return NpcProfile.Defence.DOMAIN_AMPLIFICATION;
        // Hollow Wicker Basket is a particle/entity-type tag in v51, not a registered
        // mob effect. We retain the cooldown editor entry but cannot activate it here.
        return null;
    }
    private static boolean apply(LivingEntity npc,NpcProfile profile,CompoundTag state,long now,
                                 NpcProfile.Defence type,String registryName){
        if(!profile.defences.getOrDefault(type,false))return false;
        if(now<state.getLong("defenceReady_"+type.name()))return false;
        MobEffect effect=originalEffect(registryName);
        if(effect==null||npc.hasEffect(effect))return false;
        if(!npc.addEffect(new MobEffectInstance(effect,100,0,false,true)))return false;
        state.putLong("defenceReady_"+type.name(),now+CastPolicy.secondsToTicks(profile.defenceCooldown(type)));
        return true;
    }
    private DomainDefense(){}
}
