package dev.jujutsunpc.game;

import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.config.NpcProfile;
import dev.jujutsunpc.config.NpcStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Move damage, targeted Kashimo lightning and backdash. No passive system. */
public final class NpcCombatTuning {
    /** The original RangeAttack procedure reads Damage from the caster's persistent data. */
    public static void onNativeRangeAttack(Entity raw) {
        if(!(raw instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)
                || !NpcStorage.configured(npc))return;
        NpcProfile p=NpcStorage.profile(npc);
        CompoundTag tags=npc.getPersistentData();
        CompoundTag runtime=NpcStorage.runtime(npc,p);
        if(tags.getBoolean("DomainAttack")) {
            if(p.domainSureHitDamage>=0)tags.putDouble("Damage",p.domainSureHitDamage);
            return;
        }
        String key=runtime.getString("currentMove");
        if(!key.isEmpty() && npc.level().getGameTime()<=runtime.getLong("currentMoveUntil")){
            String itemKey=runtime.getString("currentItemMoveKey");
            double amount=itemKey.isEmpty()?p.damage(key):p.itemDamage(itemKey);
            if(amount>=0)tags.putDouble("Damage",amount);
        }
    }
    public static void onHurt(LivingHurtEvent event) {
        if(event.getAmount()<=0 || event.getEntity().level().isClientSide)return;
        Entity source=event.getSource().getEntity();
        if(!(source instanceof LivingEntity npc)||!NpcCompatibility.isCompatible(npc)
                ||!NpcStorage.configured(npc))return;
        NpcProfile p=NpcStorage.profile(npc);
        CompoundTag runtime=NpcStorage.runtime(npc,p);
        String key=runtime.getString("currentMove");
        if(key.isEmpty() || npc.level().getGameTime()>runtime.getLong("currentMoveUntil"))return;
        String itemKey=runtime.getString("currentItemMoveKey");
        double damage=itemKey.isEmpty()?p.damage(key):p.itemDamage(itemKey);
        if(damage>=0)event.setAmount((float)damage);
    }

    /**
     * Kashimo Lightning Strike: instead of stacking passive marks, strike the
     * current locked-on LivingEntity directly. The spell never sweeps other
     * entities and does not depend on a previous melee hit.
     */
    public static boolean lightningStrike(ServerLevel level,LivingEntity caster,LivingEntity target,
                                         NpcProfile profile,AbilityCatalog.Move move) {
        if(target==null || !target.isAlive() || target==caster || target.level()!=level
                || caster.distanceToSqr(target)>128d*128d)return false;
        float damage=(float)(profile.damage(move.key())>=0?profile.damage(move.key()):12d);
        if(!(caster instanceof Mob attacker))return false;
        // v0.12 invented vanilla ELECTRIC_SPARK + floating END_ROD clouds.
        // Those particles are NOT Kashimo's v51 lightning. Use the actual
        // registered JujutsuCraft blue-thunder effect instead.
        boolean hit=target.hurt(caster.damageSources().mobAttack(attacker),damage);
        if(!hit)return true; // still targeted; vanilla hit immunity may block
        double x=target.getX(),y=target.getY()+target.getBbHeight()/2,z=target.getZ();
        var original=ForgeRegistries.PARTICLE_TYPES.getValue(
                new ResourceLocation("jujutsucraft","particle_thunder_blue"));
        if(original instanceof SimpleParticleType thunder){
            level.sendParticles(thunder,x,y,z,4,.15,.22,.15,.04);
        }
        SoundEvent sound=ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("jujutsucraft","thunder_impact"));
        if(sound!=null){
            level.playSound(null,x,y,z,sound,SoundSource.NEUTRAL,1.0f,1.0f);
        }
        return true;
    }

    public static void backDash(ServerLevel level,LivingEntity npc,NpcProfile profile,CompoundTag runtime){
        if(!profile.backDash || !(npc instanceof Mob mob) || mob.getTarget()==null
                || !mob.getTarget().isAlive())return;
        long now=level.getGameTime();
        if(now<runtime.getLong("backDashReady"))return;
        LivingEntity foe=mob.getTarget();
        double dist=npc.distanceTo(foe);
        if(dist<0.5 || dist>5)return;
        var dir=npc.position().subtract(foe.position()).multiply(1,0,1);
        if(dir.lengthSqr()<.0001)return;
        var destination=npc.position().add(dir.normalize().scale(1.7));
        if(!level.hasChunkAt(BlockPos.containing(destination)))return;
        npc.push(dir.normalize().x*.9,.12,dir.normalize().z*.9);
        runtime.putLong("backDashReady",now+CastPolicy.secondsToTicks(profile.backDashCooldownSeconds));
        dev.jujutsunpc.network.CompatNetwork.broadcastAnimation(npc,"backstep");
    }
    private NpcCombatTuning(){}
}
