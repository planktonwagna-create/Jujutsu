package dev.jujutsunpc.game;

import java.util.Map;

/**
 * Whitelist of animations verified in JCraft 0.18.2.
 * They are read from the INSTALLED JCraft resource pack at runtime.
 * This addon must never copy or redistribute another mod's animation JSON.
 *
 * Network keys deliberately contain only lowercase letters/underscores:
 * CompatNetwork's PlayNpcAnimation packet validates that format.
 */
public final class JcraftAnimationRegistry {
    public record Clip(String resourcePath,String animation,String originalFallback) {}
    private static final Map<String,Clip> CLIPS=Map.ofEntries(
        Map.entry("jc_br_gut",new Clip("player_animation/spec/brawler.animation.json","br.gut","punch_right")),
        Map.entry("jc_br_upct",new Clip("player_animation/spec/brawler.animation.json","br.upct","punch_overhead")),
        Map.entry("jc_br_3hit",new Clip("player_animation/spec/brawler.animation.json","br.3hit","punch_gomu_gatling1")),
        Map.entry("jc_br_low",new Clip("player_animation/spec/brawler.animation.json","br.low","kick_right")),
        Map.entry("jc_br_kck",new Clip("player_animation/spec/brawler.animation.json","br.kck","kick_rotate1")),
        Map.entry("jc_br_lkk",new Clip("player_animation/spec/brawler.animation.json","br.lkk","kick_right")),
        Map.entry("jc_vm_5hit",new Clip("player_animation/spec/vampire.animation.json","vm.5hit","combo1")),
        Map.entry("jc_vm_axe",new Clip("player_animation/spec/vampire.animation.json","vm.axe","kick_right")),
        Map.entry("jc_an_slsh",new Clip("player_animation/spec/anubis.animation.json","an.slsh","sword_overhead")),
        Map.entry("jc_an_swp",new Clip("player_animation/spec/anubis.animation.json","an.swp","sword_overhead")),
        Map.entry("jc_an_usa",new Clip("player_animation/spec/anubis.animation.json","an.usa","sword_overhead")),
        Map.entry("jc_an_2hit",new Clip("player_animation/spec/anubis.animation.json","an.2hit","sword_overhead")),
        Map.entry("jc_an_3hit",new Clip("player_animation/spec/anubis.animation.json","an.3hit","sword_overhead")),
        Map.entry("jc_dash",new Clip("player_animation/dashes.json","dash","speed_attack1")),
        Map.entry("jc_bdash",new Clip("player_animation/dashes.json","bdash","backstep"))
    );

    public static Clip find(String networkId) {
        return CLIPS.get(networkId);
    }
    private JcraftAnimationRegistry(){}
}
