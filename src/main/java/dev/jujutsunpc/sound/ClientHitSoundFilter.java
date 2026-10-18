package dev.jujutsunpc.sound;

import dev.jujutsunpc.JujutsuNpcCompatMod;
import dev.jujutsunpc.compat.NpcCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Fallback for sounds produced directly on the client by original JJK code.
 * Forge also synchronizes the SERVER config values to connected clients;
 * this filter has no player-local preferences.
 */
@Mod.EventBusSubscriber(modid=JujutsuNpcCompatMod.MOD_ID,
        value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientHitSoundFilter {
    @SubscribeEvent
    public static void onSound(PlaySoundEvent event){
        SoundInstance sound=event.getSound();
        if(sound==null || !JjkHitSoundPolicy.isJjkHitSound(sound.getLocation()))return;
        if(ClientHitSoundState.npcEnabled() && ClientHitSoundState.playerEnabled())return;
        var world=Minecraft.getInstance().level;
        if(world==null)return;
        Vec3 pos=new Vec3(sound.getX(),sound.getY(),sound.getZ());
        LivingEntity closest=null;
        double minimum=Double.MAX_VALUE;
        for(LivingEntity entity:world.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos,pos).inflate(2.4),LivingEntity::isAlive)){
            if(!(entity instanceof Player) && !NpcCompatibility.isCompatible(entity))continue;
            double d=entity.position().add(0,entity.getBbHeight()/2,0).distanceToSqr(pos);
            if(d<minimum){minimum=d;closest=entity;}
        }
        if(closest==null || minimum>2.4*2.4)return;
        boolean suppress=(closest instanceof Player && !ClientHitSoundState.playerEnabled())
                || (NpcCompatibility.isCompatible(closest) && !ClientHitSoundState.npcEnabled());
        if(suppress)event.setSound(null);
    }
    private ClientHitSoundFilter(){}
}
