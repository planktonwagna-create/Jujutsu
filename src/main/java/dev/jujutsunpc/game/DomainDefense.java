package dev.jujutsunpc.game;

import dev.jujutsunpc.config.NpcProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses ORIGINAL JujutsuCraft effects; never substitutes custom anti-domain damage rules. */
public final class DomainDefense {
    private static MobEffect originalEffect(String name) {
        return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("jujutsucraft", name));
    }
    public static boolean targetHasDomain(LivingEntity target) {
        MobEffect effect = originalEffect("domain_expansion");
        return effect != null && target.hasEffect(effect);
    }
    /**
     * TODO: Proper domain-clash execution needs a validated JujutsuCraft NPC caster path.
     * Do not claim domain success by merely setting the domain effect.
     */
    public static boolean tryOriginalDomain(ServerLevel level, LivingEntity npc, NpcProfile profile) {
        return false;
    }
    /** Try defensive effects that exist in v51 and are independently enabled. */
    public static boolean tryAntiDomain(LivingEntity npc, NpcProfile profile) {
        if (apply(npc, profile, NpcProfile.Defence.SIMPLE_DOMAIN, "simple_domain")) return true;
        if (apply(npc, profile, NpcProfile.Defence.FALLING_BLOSSOM_EMOTION, "falling_blossom_emotion")) return true;
        if (apply(npc, profile, NpcProfile.Defence.DOMAIN_AMPLIFICATION, "domain_amplification")) return true;
        // No verified v51 Hollow Wicker Basket registry effect; do not substitute another power.
        return false;
    }
    private static boolean apply(LivingEntity npc, NpcProfile profile, NpcProfile.Defence choice, String id) {
        if (!profile.defences.getOrDefault(choice, false)) return false;
        MobEffect effect = originalEffect(id);
        if (effect == null) return false;
        if (npc.hasEffect(effect)) return true;
        return npc.addEffect(new MobEffectInstance(effect, 100, 0, false, true));
    }
    private DomainDefense() {}
}
