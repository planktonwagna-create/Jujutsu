package dev.jujutsunpc.game;

import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * NPC-only, target-locked Kashimo Lightning Strike.
 *
 * JJK v51's LightningStrike/LightningStrike2 procedures emit their registered
 * blue-thunder particles along their directional attack path. The player's
 * native procedure relies on input/charge state not available to EasyNPC and
 * CustomNPCs. This bridge creates the visual path with those SAME original
 * particle types, over multiple server ticks, while keeping one direct
 * target-only damage hit at the end. No vanilla electric or end-rod particles.
 */
public final class KashimoLightningTrail {
    private static final String KEY="kashimoTravelingLightning";
    private static final int MAX_TICKS=18;
    private static final ResourceKey<DamageType> NATIVE_CURSE_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    new ResourceLocation("jujutsucraft","damage_curse"));
    private static final double MAX_RANGE_SQR=128d*128d;
    private static final ResourceLocation MINI=new ResourceLocation("jujutsucraft","particle_thunder_blue_mini");
    private static final ResourceLocation BLUE=new ResourceLocation("jujutsucraft","particle_thunder_blue");
    private static final ResourceLocation START_SOUND=new ResourceLocation("jujutsucraft","electric_shock");
    private static final ResourceLocation IMPACT_SOUND=new ResourceLocation("jujutsucraft","thunder_impact");

    public static boolean active(CompoundTag runtime){
        return runtime.contains(KEY,10);
    }

    /**
     * Returns true only after locking a living target and scheduling its arc.
     * Does not damage the enemy instantly; the original blue thunder moves
     * from the NPC towards that exact entity over several world ticks.
     */
    public static boolean begin(ServerLevel level,LivingEntity caster,LivingEntity target,
                                NpcProfile profile,AbilityCatalog.Move move,CompoundTag runtime){
        if(!(caster instanceof Mob) || target==null || !caster.isAlive()
                || !target.isAlive() || caster==target || target.level()!=level
                || caster.distanceToSqr(target)>MAX_RANGE_SQR || active(runtime))return false;

        Vec3 aim=target.getBoundingBox().getCenter();
        Vec3 direction=aim.subtract(caster.getEyePosition());
        if(direction.lengthSqr()<1.0e-6)return false;

        Vec3 origin=caster.getEyePosition().add(direction.normalize().scale(0.55));
        int flight=Mth.clamp((int)Math.ceil(origin.distanceTo(aim)/2.5d),5,MAX_TICKS);
        CompoundTag state=new CompoundTag();
        state.putUUID("victim",target.getUUID());
        state.putDouble("startX",origin.x);
        state.putDouble("startY",origin.y);
        state.putDouble("startZ",origin.z);
        state.putLong("began",level.getGameTime());
        state.putInt("flightTicks",flight);
        state.putString("move",move.key());
        runtime.put(KEY,state);
        sound(level,START_SOUND,origin,0.85f);
        return true;
    }

    /** Called once per living NPC server tick. No client-side ticking needed. */
    public static void tick(ServerLevel level,LivingEntity caster,NpcProfile profile,CompoundTag runtime){
        if(!active(runtime))return;
        CompoundTag arc=runtime.getCompound(KEY);
        Entity entity=arc.hasUUID("victim")?level.getEntity(arc.getUUID("victim")):null;
        if(!(entity instanceof LivingEntity target) || !target.isAlive()
                || !caster.isAlive() || target==caster || target.level()!=level
                ){
            runtime.remove(KEY);
            return;
        }

        SimpleParticleType blue=originalParticle(BLUE);
        SimpleParticleType mini=originalParticle(MINI);
        // Even if particles cannot be loaded, the locked-on hit still resolves.

        int flight=Math.max(1,arc.getInt("flightTicks"));
        long age=level.getGameTime()-arc.getLong("began");
        if(age<0)age=0;
        double endProgress=Mth.clamp((double)(age+1)/flight,0,1);
        double startProgress=Mth.clamp((double)age/flight,0,1);
        Vec3 origin=new Vec3(arc.getDouble("startX"),arc.getDouble("startY"),arc.getDouble("startZ"));
        // Recalculate destination every tick; the original target can be
        // moving or dodging but the bolt stays locked on the same entity.
        Vec3 destination=target.getBoundingBox().getCenter();
        Vec3 delta=destination.subtract(origin);
        if(delta.lengthSqr()<1.0e-6){runtime.remove(KEY);return;}

        Vec3 sideways=delta.cross(new Vec3(0,1,0));
        if(sideways.lengthSqr()<0.000001)sideways=delta.cross(new Vec3(1,0,0));
        sideways=sideways.normalize();
        Vec3 up=sideways.cross(delta.normalize()).normalize();
        // Less dense native particles; angular offsets avoid a straight beam.
        int samples=Mth.clamp((int)Math.ceil(delta.length()*(endProgress-startProgress)/0.68),2,10);
        for(int i=0;i<=samples;i++){
            double t=Mth.lerp((double)i/samples,startProgress,endProgress);
            Vec3 pos=point(origin,delta,sideways,up,t,caster.getId());
            if(mini!=null)level.sendParticles(mini,pos.x,pos.y,pos.z,1,0,0,0,0);
            if(blue!=null&&i%4==0)
                level.sendParticles(blue,pos.x,pos.y,pos.z,1,0,0,0,0);
        }
        if(mini!=null&&age>0){
            Vec3 tail=point(origin,delta,sideways,up,Math.max(0,endProgress-0.045),caster.getId());
            level.sendParticles(mini,tail.x,tail.y,tail.z,1,0,0,0,0);
        }

        if(endProgress<1)return;
        runtime.remove(KEY); // Prevent any duplicate impact on later ticks.
        if(blue!=null)level.sendParticles(blue,destination.x,destination.y,destination.z,
                3,Math.min(0.2,target.getBbWidth()/3),Math.min(0.23,target.getBbHeight()/3),0.15,0.005);
        if(mini!=null)level.sendParticles(mini,destination.x,destination.y,destination.z,
                6,0.16,0.2,0.16,0.01);
        sound(level,IMPACT_SOUND,destination,1f);
        // Damage ONLY the originally locked target, at the visual impact.
        if(caster instanceof Mob attacker){
            String moveKey=arc.getString("move");
            runtime.putString("currentMove",moveKey);
            runtime.putString("currentItemMoveKey","");
            runtime.putLong("currentMoveUntil",level.getGameTime()+2);
            double custom=profile.damage(moveKey);
            float damage=(float)(custom>=0?custom:12d);
            target.hurt(caster.damageSources().source(NATIVE_CURSE_DAMAGE,attacker),damage);
        }
    }

    /** Sharp, irregular corners on two axes; anchored at both endpoints. */
    private static Vec3 point(Vec3 origin,Vec3 delta,Vec3 sideways,Vec3 up,double t,int seed){
        double v=t*12;
        int node=Math.min(11,(int)Math.floor(v));
        double u=v-node;
        double envelope=Math.sin(Math.PI*t);
        double width=Math.min(0.75,Math.max(0.16,delta.length()*0.085));
        double h=Mth.lerp(u,jitter(node,seed,17),jitter(node+1,seed,17));
        double k=Mth.lerp(u,jitter(node,seed,59),jitter(node+1,seed,59));
        return origin.add(delta.scale(t))
                .add(sideways.scale(h*width*envelope))
                .add(up.scale(k*width*0.8*envelope));
    }
    private static double jitter(int node,int seed,int salt){
        double value=Math.sin(node*84.163+seed*29.47+salt*127.1)*43758.5453123;
        return (value-Math.floor(value))*2-1;
    }
    private static SimpleParticleType originalParticle(ResourceLocation id){
        return ForgeRegistries.PARTICLE_TYPES.getValue(id) instanceof SimpleParticleType particle
                ?particle:null;
    }
    private static void sound(ServerLevel level,ResourceLocation id,Vec3 pos,float volume){
        SoundEvent event=ForgeRegistries.SOUND_EVENTS.getValue(id);
        if(event!=null)level.playSound(null,pos.x,pos.y,pos.z,event,SoundSource.NEUTRAL,volume,1f);
    }
    private KashimoLightningTrail(){}
}
