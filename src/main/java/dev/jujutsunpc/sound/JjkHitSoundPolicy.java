package dev.jujutsunpc.sound;

import dev.jujutsunpc.JujutsuNpcCompatMod;
import dev.jujutsunpc.compat.NpcCompatibility;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-authoritative JujutsuCraft hit-sound suppression. Forge cancels audio
 * BEFORE it is sent to tracking players, so everybody hears the same policy.
 * The hit classification is based on the ACTUAL recipient of LivingAttackEvent,
 * not on the attacker or the listener's local preferences.
 */
public final class JjkHitSoundPolicy {
    private static final Set<String> HIT_IDS=Set.of(
        "gacon", "punch", "strong_hit", "critical", "crush", "glass_crash",
        "guard", "guard_just", "guard_sword"
    );
    private record Hit(Vec3 position,long tick,boolean player,boolean npc){}
    private static final Map<Level,ArrayDeque<Hit>> RECENT=new IdentityHashMap<>();

    public static boolean isJjkHitSound(ResourceLocation key){
        return key!=null && "jujutsucraft".equals(key.getNamespace())
                && HIT_IDS.contains(key.getPath());
    }
    public static boolean suppressFor(Entity entity){
        if(entity instanceof Player)return !JujutsuNpcCompatMod.PLAYER_HIT_SOUNDS.get();
        return entity instanceof LivingEntity living && NpcCompatibility.isCompatible(living)
                && !JujutsuNpcCompatMod.NPC_HIT_SOUNDS.get();
    }
    private static boolean shouldSilence(boolean player,boolean npc){
        return player&&!JujutsuNpcCompatMod.PLAYER_HIT_SOUNDS.get()
                || npc&&!JujutsuNpcCompatMod.NPC_HIT_SOUNDS.get();
    }
    private static boolean relevant(PlayLevelSoundEvent event){
        if(event.getSound()==null)return false;
        SoundEvent sound=event.getSound().value();
        return isJjkHitSound(ForgeRegistries.SOUND_EVENTS.getKey(sound));
    }

    /**
     * Run before JJK's normal-priority LivingAttackEvent procedure plays gacon.
     * Attacks that were blocked entirely may still be recorded, but that is
     * preferable to leaking server-wide hit sounds after an attack attempt.
     */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        LivingEntity victim=event.getEntity();
        boolean player=victim instanceof Player;
        boolean npc=NpcCompatibility.isCompatible(victim);
        if(!player&&!npc)return;
        ArrayDeque<Hit> hits=RECENT.computeIfAbsent(level,ignored->new ArrayDeque<>());
        long now=level.getGameTime();
        while(!hits.isEmpty()&&(hits.size()>=96||now-hits.peekFirst().tick()>4))
            hits.removeFirst();
        hits.addLast(new Hit(victim.position().add(0,victim.getBbHeight()*0.5,0),
                now,player,npc));
    }
    private static boolean matchesRecent(ServerLevel level,Vec3 center){
        ArrayDeque<Hit> hits=RECENT.get(level);
        if(hits==null)return false;
        long now=level.getGameTime();
        Hit nearest=null;
        double smallest=Double.MAX_VALUE;
        for(Hit hit:hits){
            if(now-hit.tick()<0||now-hit.tick()>3)continue;
            double distance=hit.position().distanceToSqr(center);
            if(distance<smallest){smallest=distance;nearest=hit;}
        }
        return nearest!=null && smallest<=2.5*2.5
                && shouldSilence(nearest.player(),nearest.npc());
    }

    @SubscribeEvent
    public static void onSoundAtPosition(PlayLevelSoundEvent.AtPosition event){
        if(!(event.getLevel() instanceof ServerLevel level)||!relevant(event))return;
        if(matchesRecent(level,event.getPosition()))event.setCanceled(true);
    }
    @SubscribeEvent
    public static void onSoundAtEntity(PlayLevelSoundEvent.AtEntity event){
        if(!(event.getLevel() instanceof ServerLevel level)||!relevant(event))return;
        if(suppressFor(event.getEntity())
                || matchesRecent(level,event.getEntity().position()))event.setCanceled(true);
    }

    private JjkHitSoundPolicy(){}
}
