package dev.jujutsunpc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jujutsunpc.compat.NpcCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-only humanoid pose adapter. Reads the REAL v51 animation JSON bundled in
 * the installed JujutsuCraft resource pack; never ships/copies upstream animation data.
 * The adapter supports bone rotations (constant or keyed vectors). GeckoLib entity-only
 * animations and custom-skeleton parts are NOT handled here.
 */
public final class NpcAnimationState {
    private static final Logger LOG = LogUtils.getLogger();
    private record Playback(String animation, long startedAtMs) {}
    private record Animation(double lengthSeconds, JsonObject bones) {}
    private static final Map<Integer, Playback> ACTIVE = new HashMap<>();
    private static final Map<String, Animation> CACHE = new HashMap<>();

    public static void play(int entityId, String name) {
        // Never let a server force arbitrary paths or oversized strings into a resource loader.
        if (!name.matches("[a-z0-9_]{1,48}")) return;
        ACTIVE.put(entityId, new Playback(name, System.currentTimeMillis()));
    }

    private static Animation load(String name) {
        Animation cached = CACHE.get(name);
        if (cached != null) return cached;
        ResourceLocation id = new ResourceLocation("jujutsucraft", "player_animation/" + name + ".json");
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return null;
            try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
                if (animations == null || animations.size() == 0) return null;
                JsonObject anim = animations.has(name) ? animations.getAsJsonObject(name)
                        : animations.entrySet().iterator().next().getValue().getAsJsonObject();
                Animation parsed = new Animation(anim.get("animation_length").getAsDouble(),
                        anim.getAsJsonObject("bones"));
                CACHE.put(name, parsed);
                return parsed;
            }
        } catch (Exception e) {
            LOG.debug("Can't read original JujutsuCraft humanoid animation {}", id, e);
            return null;
        }
    }

    private static boolean isCharging(String name){
        return "charge1".equals(name)||"charge2".equals(name)||"energy_charge".equals(name);
    }
    /** v51 JSON contains 0.25s attacks AND 180-360s permanent poses.
     * Normal attacks get a bounded display window; held-charge poses may remain
     * until the release animation arrives (max configurable 120s + buffer).
     */
    private static double displaySeconds(Playback active,Animation animation){
        return isCharging(active.animation()) ? 125d
                : Math.max(0.8d,Math.min(1.3d,animation.lengthSeconds()));
    }
    private static double sampledTime(double elapsed,Animation animation){
        return Math.max(0,Math.min(elapsed,Math.max(0,animation.lengthSeconds())));
    }
    public static void apply(LivingEntity npc, HumanoidModel<?> model) {
        if (!NpcCompatibility.isCompatible(npc)) return;
        Playback active = ACTIVE.get(npc.getId());
        if (active == null) return;
        Animation animation = load(active.animation());
        if (animation == null) { ACTIVE.remove(npc.getId()); return; }
        double time = (System.currentTimeMillis() - active.startedAtMs()) / 1000.0;
        if (time > displaySeconds(active,animation) || time < 0) {
            ACTIVE.remove(npc.getId()); return;
        }
        time=sampledTime(time,animation);
        JsonObject bones = animation.bones();
        if (bones == null) return;
        applyBone(bones, "head", model.head, time);
        applyBone(bones, "torso", model.body, time);
        applyBone(bones, "body", model.body, time);
        applyBone(bones, "right_arm", model.rightArm, time);
        applyBone(bones, "left_arm", model.leftArm, time);
        applyBone(bones, "right_leg", model.rightLeg, time);
        applyBone(bones, "left_leg", model.leftLeg, time);
        // PlayerModel has separate overlay geometry. Without copying rotations
        // its sleeves and jacket keep their idle poses and visually mask animations.
        if(model instanceof PlayerModel<?> player){
            player.hat.copyFrom(player.head);
            player.jacket.copyFrom(player.body);
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftPants.copyFrom(player.leftLeg);
            player.rightPants.copyFrom(player.rightLeg);
        }
    }

    /** Easy NPC's own model manager can own limbs independently of PlayerModel. */
    public static boolean applyParts(LivingEntity npc,ModelPart[] parts){
        if(!NpcCompatibility.isCompatible(npc)||parts==null||parts.length!=6)return false;
        Playback active=ACTIVE.get(npc.getId());
        if(active==null)return false;
        Animation animation=load(active.animation());
        if(animation==null)return false;
        double time=(System.currentTimeMillis()-active.startedAtMs())/1000.0;
        if(time<0||time>displaySeconds(active,animation)){
            ACTIVE.remove(npc.getId());
            return false;
        }
        time=sampledTime(time,animation);
        JsonObject bones=animation.bones();
        if(bones==null)return false;
        String[] keys={"head","body","right_arm","left_arm","right_leg","left_leg"};
        for(int i=0;i<keys.length;i++)if(parts[i]!=null)applyBone(bones,keys[i],parts[i],time);
        return true;
    }
    private static void applyBone(JsonObject bones, String name, ModelPart part, double time) {
        if (!bones.has(name)) return;
        JsonObject bone = bones.getAsJsonObject(name);
        if (!bone.has("rotation")) return;
        float[] rot = sampleRotation(bone.get("rotation"), time);
        if (rot == null) return;
        part.xRot = (float)Math.toRadians(rot[0]);
        part.yRot = (float)Math.toRadians(rot[1]);
        part.zRot = (float)Math.toRadians(rot[2]);
    }

    /** Handles vector, raw [x,y,z] and timestamp-keyed vector objects. */
    private static float[] sampleRotation(JsonElement value, double time) {
        if (value.isJsonArray()) return readVector(value.getAsJsonArray());
        if (!value.isJsonObject()) return null;
        JsonObject data = value.getAsJsonObject();
        if (data.has("vector")) return sampleRotation(data.get("vector"), time);
        double prev = Double.NEGATIVE_INFINITY, next = Double.POSITIVE_INFINITY;
        float[] before = null, after = null;
        for (var entry : data.entrySet()) {
            try {
                double t = Double.parseDouble(entry.getKey());
                float[] vec = sampleRotation(entry.getValue(), time);
                if (vec == null) continue;
                if (t <= time && t > prev) { prev = t; before = vec; }
                if (t >= time && t < next) { next = t; after = vec; }
            } catch (NumberFormatException ignored) { /* non-time interpolation metadata */ }
        }
        if (before == null) return after;
        if (after == null || next == prev) return before;
        float alpha = (float)((time-prev)/(next-prev));
        return new float[] {before[0]+(after[0]-before[0])*alpha,
                before[1]+(after[1]-before[1])*alpha,
                before[2]+(after[2]-before[2])*alpha};
    }
    private static float[] readVector(JsonArray array) {
        if (array.size() < 3) return null;
        try { return new float[] {array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat()}; }
        catch (NumberFormatException | IllegalStateException ex) { return null; }
    }
    private NpcAnimationState() {}
}
