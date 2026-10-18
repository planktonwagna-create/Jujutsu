package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import java.util.UUID;

/** Per-NPC, per-move damage and a targeted Kashimo charged-hit passive. */
public final class NpcCombatTuning {
    private static final String PASSIVE_KEY="jjknpc_kashimo_marks";
    public static void onNativeRangeAttack(Entity raw) {
        if(!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc))return;
        NpcProfile p=NpcStorage.profile(npc);
        CompoundTag tags=npc.getPersistentData();
        CompoundTag runtime=NpcStorage.runtime(npc,p);
        if(tags.getBoolean("DomainAttack")){
            if(p.domainSureHitDamage>=0)tags.putDouble("Damage",p.domainSureHitDamage);
            return;
        }
        String key=runtime.getString("currentMove");
        if(!key.isEmpty()){
            double damage=p.damage(key);
            if(damage>=0)tags.putDouble("Damage",damage);
        }
    }
    public static void onHurt(LivingHurtEvent e) {
        if(e.getAmount()<=0||e.getEntity().level().isClientSide)return;
        Entity original=e.getSource().getEntity();
        if(!(original instanceof LivingEntity attacker) || !NpcCompatibility.isCompatible(attacker)
                || !NpcStorage.configured(attacker))return;
        NpcProfile p=NpcStorage.profile(attacker);
        CompoundTag runtime=NpcStorage.runtime(attacker,p);
        String key=runtime.getString("currentMove");
        if(!key.isEmpty() && e.getEntity().level().getGameTime()<=runtime.getLong("currentMoveUntil")
                && !runtime.getBoolean("kashimoPassiveDamage")){
            double amount=p.damage(key);
            if(amount>=0)e.setAmount((float)amount);
        }
    }
    /** Marks a single target, and arcs lightning to THAT target after repeated attacks. */
    public static void afterHit(LivingDamageEvent e) {
        if(e.getAmount()<=0||!(e.getEntity().level() instanceof ServerLevel level))return;
        Entity raw=e.getSource().getEntity();
        if(!(raw instanceof LivingEntity npc)||!NpcCompatibility.isCompatible(npc)
                ||!NpcStorage.configured(npc))return;
        NpcProfile p=NpcStorage.profile(npc);
        if(!p.techniquePassives||!"kashimo".equals(p.technique))return;
        CompoundTag runtime=NpcStorage.runtime(npc,p);
        if(runtime.getBoolean("kashimoPassiveDamage"))return;
        LivingEntity target=e.getEntity();
        CompoundTag marks=target.getPersistentData().getCompound(PASSIVE_KEY);
        String id=npc.getUUID().toString();
        int total=marks.getInt(id)+1;
        if(total<3){
            marks.putInt(id,total);
            target.getPersistentData().put(PASSIVE_KEY,marks);
            return;
        }
        marks.remove(id);
        target.getPersistentData().put(PASSIVE_KEY,marks);
        if(level.getGameTime()<runtime.getLong("kashimoNextStrike"))return;
        runtime.putLong("kashimoNextStrike",level.getGameTime()+40);
        // No area damage or accidental damage to spectators/friendly bystanders.
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,target.getX(),
                target.getY()+target.getBbHeight()/2,target.getZ(),
                28,0.35,0.7,0.35,0.1);
        runtime.putBoolean("kashimoPassiveDamage",true);
        try{
            if(npc instanceof Mob mob)
                target.hurt(npc.damageSources().mobAttack(mob),Math.max(1,e.getAmount()));
        }finally{
            runtime.putBoolean("kashimoPassiveDamage",false);
        }
    }
    public static void backDash(ServerLevel level,LivingEntity npc,NpcProfile p,CompoundTag runtime) {
        if(!p.backDash || !(npc instanceof Mob mob)||mob.getTarget()==null
                || !mob.getTarget().isAlive())return;
        long now=level.getGameTime();
        if(now<runtime.getLong("backDashReady"))return;
        LivingEntity foe=mob.getTarget();
        double distance=npc.distanceTo(foe);
        if(distance>5 || distance<0.5)return;
        var away=npc.position().subtract(foe.position()).multiply(1,0,1);
        if(away.lengthSqr()<.0001)return;
        // Leave vertical velocity unchanged, avoid dashing into unloaded terrain.
        var next=npc.position().add(away.normalize().scale(1.7));
        if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(next)))return;
        npc.push(away.normalize().x*0.9,0.12,away.normalize().z*0.9);
        runtime.putLong("backDashReady",now+CastPolicy.secondsToTicks(p.backDashCooldownSeconds));
        dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc,"backstep");
    }
    private NpcCombatTuning(){}
}
