package dev.jujutsunpc.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.game.JcraftAnimationRegistry;
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
import java.util.WeakHashMap;

/**
 * Client-only humanoid pose adapter. Reads the REAL v51 animation JSON bundled in
 * the installed JujutsuCraft resource pack; never ships/copies upstream animation data.
 * The adapter supports bone rotations (constant or keyed vectors). GeckoLib entity-only
 * animations and custom-skeleton parts are NOT handled here.
 */
public final class NpcAnimationState {
    private static final Logger LOG = LogUtils.getLogger();
    private record Playback(String animation, long startedAtMs) {}
    private record Animation(double lengthSeconds,double lastKeyframeSeconds,JsonObject bones,boolean jcraft) {}
    private record PartOffset(float baseX,float baseY,float baseZ,
                              float changedX,float changedY,float changedZ) {}
    /** Avoid accumulating translations if an NPC renderer doesn't reset pivots each frame. */
    private static final Map<ModelPart,PartOffset> LAST_POSITIONS=new WeakHashMap<>();
    private static final Map<Integer, Playback> ACTIVE = new HashMap<>();
    private static final Map<String, Animation> CACHE = new HashMap<>();

    public static void play(int entityId, String name) {
        // Never let a server force arbitrary paths or oversized strings into a resource loader.
        if (!name.matches("[a-z0-9_]{1,48}")) return;
        ACTIVE.put(entityId, new Playback(name, System.currentTimeMillis()));
    }

    /**
     * Read JCraft directly from the installed mod's resource pack. This file
     * does not ship or copy any JCraft animation assets. Fall back to original
     * JujutsuCraft if JCraft isn't installed on this client.
     */
    private static Animation load(String name) {
        Animation cached=CACHE.get(name);
        if(cached!=null)return cached;
        JcraftAnimationRegistry.Clip external=JcraftAnimationRegistry.find(name);
        Animation result;
        if(external!=null){
            result=readAnimation(new ResourceLocation("jcraft",external.resourcePath()),
                    external.animation(),true);
            if(result==null && !external.originalFallback().isEmpty())
                result=readAnimation(new ResourceLocation("jujutsucraft",
                        "player_animation/"+external.originalFallback()+".json"),
                        external.originalFallback(),false);
        }else{
            result=readAnimation(new ResourceLocation("jujutsucraft",
                    "player_animation/"+name+".json"),name,false);
        }
        if(result!=null)CACHE.put(name,result);
        return result;
    }

    private static Animation readAnimation(ResourceLocation path,String clipName,boolean jcraft){
        try {
            var res=Minecraft.getInstance().getResourceManager().getResource(path);
            if(res.isEmpty())return null;
            try(var reader=new InputStreamReader(res.get().open(),StandardCharsets.UTF_8)){
                JsonObject root=JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject animations=root.getAsJsonObject("animations");
                if(animations==null || animations.size()==0)return null;
                if(jcraft && !animations.has(clipName))return null;
                JsonObject anim=animations.has(clipName)
                        ? animations.getAsJsonObject(clipName)
                        : animations.entrySet().iterator().next().getValue().getAsJsonObject();
                JsonObject bones=anim.getAsJsonObject("bones");
                if(bones==null)return null;
                double length=anim.has("animation_length")
                        ?anim.get("animation_length").getAsDouble():0.7;
                return new Animation(length,findFinalKeyframe(bones),bones,jcraft);
            }
        }catch(Exception error){
            LOG.debug("Could not read original {} animation {}",jcraft?"JCraft":"JujutsuCraft",path,error);
            return null;
        }
    }

    private static double findFinalKeyframe(JsonObject bones){
        if(bones==null)return 0;
        double end=0;
        for(var entry:bones.entrySet()){
            JsonObject bone=entry.getValue().isJsonObject()?entry.getValue().getAsJsonObject():null;
            if(bone==null)continue;
            for(String property:new String[]{"rotation","position"}){
                JsonElement data=bone.get(property);
                if(data==null||!data.isJsonObject())continue;
                for(var sample:data.getAsJsonObject().entrySet()){
                    try{end=Math.max(end,Double.parseDouble(sample.getKey()));}
                    catch(NumberFormatException ignored){}
                }
            }
        }
        return Math.min(8,end);
    }
    private static boolean isCharging(String name){
        return "charge1".equals(name)||"charge2".equals(name)||"energy_charge".equals(name);
    }
    /** Source files can claim 180s while holding their final attack keyframe
     * at 0.125s. A clip's real motion is its keyframe timeline, not that claim.
     */
    private static double displaySeconds(Playback playback,Animation animation){
        if(animation.jcraft())return Math.max(0.25d,Math.min(4d,animation.lengthSeconds()+0.08d));
        if(isCharging(playback.animation()))return 125d;
        double motion=animation.lastKeyframeSeconds();
        // Native punch impact keyframes are ~0.08s apart. v0.13 stretched
        // 0.2s of choreography to 0.6s, making each pose look held/stiff.
        return motion<=0 ? 0.4d : Math.max(0.42d,Math.min(1.65d,motion*1.12d+0.18d));
    }
    private static double sampleTime(Playback playback,Animation animation,double elapsed){
        if(animation.jcraft())return Math.max(0,Math.min(elapsed,animation.lengthSeconds()));
        double end=animation.lastKeyframeSeconds();
        if(end<=0)return 0;
        if(isCharging(playback.animation()))return Math.min(end,elapsed);
        // Advance across actual native keyframes at an almost-linear rate.
        // Easing the entire clip made the impact occur far too late.
        double progress=Math.max(0,Math.min(1,elapsed/(displaySeconds(playback,animation)*0.83d)));
        return progress*end;
    }
    private static float weight(Playback playback,Animation animation,double elapsed){
        if(animation.jcraft()){
            float entering=(float)Math.min(1,Math.max(0,elapsed/0.075d));
            float leaving=(float)Math.min(1,Math.max(0,(displaySeconds(playback,animation)-elapsed)/0.14d));
            return entering*leaving;
        }
        float into=(float)Math.min(1,Math.max(0,elapsed/0.055d));
        if(isCharging(playback.animation()))return into;
        double remain=displaySeconds(playback,animation)-elapsed;
        return into*(float)Math.min(1,Math.max(0,remain/0.11d));
    }
    private static void resetLastPosition(ModelPart part){
        if(part==null)return;
        PartOffset previous=LAST_POSITIONS.remove(part);
        if(previous==null)return;
        // Vanilla or EasyNPC may have reset/customized the base pose since
        // our previous frame. Restore only when our previous translation remains.
        if(Math.abs(part.x-previous.changedX())<0.001f
                && Math.abs(part.y-previous.changedY())<0.001f
                && Math.abs(part.z-previous.changedZ())<0.001f){
            part.x=previous.baseX();part.y=previous.baseY();part.z=previous.baseZ();
        }
    }

    public static void apply(LivingEntity npc,HumanoidModel<?> model){
        if(!NpcCompatibility.isCompatible(npc))return;
        ModelPart[] limbs={model.head,model.body,model.rightArm,model.leftArm,
                model.rightLeg,model.leftLeg};
        for(ModelPart part:limbs)resetLastPosition(part);
        Playback playing=ACTIVE.get(npc.getId());
        if(playing==null)return;
        Animation animation=load(playing.animation());
        if(animation==null){ACTIVE.remove(npc.getId());return;}
        double elapsed=(System.currentTimeMillis()-playing.startedAtMs())/1000d;
        if(elapsed<0||elapsed>displaySeconds(playing,animation)){
            ACTIVE.remove(npc.getId());return;
        }
        float alpha=weight(playing,animation,elapsed);
        double time=sampleTime(playing,animation,elapsed);
        applyComposite(animation,limbs,time,alpha);
        if(model instanceof PlayerModel<?> player){
            player.hat.copyFrom(player.head);
            player.jacket.copyFrom(player.body);
            player.leftSleeve.copyFrom(player.leftArm);
            player.rightSleeve.copyFrom(player.rightArm);
            player.leftPants.copyFrom(player.leftLeg);
            player.rightPants.copyFrom(player.rightLeg);
        }
    }

    /** Optional Easy NPC humanoid pose adapter. True only with active animation. */
    public static boolean applyParts(LivingEntity npc,ModelPart[] limbs){
        if(!NpcCompatibility.isCompatible(npc)||limbs==null||limbs.length!=6)return false;
        for(ModelPart part:limbs)resetLastPosition(part);
        Playback playing=ACTIVE.get(npc.getId());
        if(playing==null)return false;
        Animation animation=load(playing.animation());
        if(animation==null){ACTIVE.remove(npc.getId());return false;}
        double elapsed=(System.currentTimeMillis()-playing.startedAtMs())/1000d;
        if(elapsed<0||elapsed>displaySeconds(playing,animation)){
            ACTIVE.remove(npc.getId());return false;
        }
        applyComposite(animation,limbs,sampleTime(playing,animation,elapsed),
                weight(playing,animation,elapsed));
        return true;
    }

    /**
     * GeckoLib's 'body' is a root transform and 'torso' is the upper-body
     * transform. Minecraft's HumanoidModel has one body part, so COMBINE
     * them instead of applying 'body' last and erasing the torso action.
     */
    private static void applyComposite(Animation animation,ModelPart[] limbs,double t,float alpha){
        JsonObject bones=animation.bones();
        if(bones==null||alpha<=0)return;
        boolean jc=animation.jcraft();
        applyBone(bones,"head",null,limbs[0],t,alpha);
        // JCraft's player skeleton calls its body bone 'torso'; native JJK
        // keeps the root 'body' plus a distinct 'torso' motion.
        applyBone(bones,jc?"torso":"body",jc?null:"torso",limbs[1],t,alpha);
        applyBone(bones,jc?"rightArm":"right_arm",null,limbs[2],t,alpha);
        applyBone(bones,jc?"leftArm":"left_arm",null,limbs[3],t,alpha);
        applyBone(bones,jc?"rightLeg":"right_leg",null,limbs[4],t,alpha);
        applyBone(bones,jc?"leftLeg":"left_leg",null,limbs[5],t,alpha);
    }

    private static float[] vector(JsonObject bones,String name,String property,double time){
        if(name==null||!bones.has(name)||!bones.get(name).isJsonObject())return null;
        JsonObject bone=bones.getAsJsonObject(name);
        if(!bone.has(property))return null;
        return sampleRotation(bone.get(property),time);
    }
    private static float[] sum(float[] primary,float[] secondary){
        if(primary==null)return secondary;
        if(secondary==null)return primary;
        return new float[]{primary[0]+secondary[0],primary[1]+secondary[1],
                           primary[2]+secondary[2]};
    }
    private static float interp(float start,float end,float amount){
        return start+(end-start)*amount;
    }
    private static float displacement(float[] current,float[] base,int index){
        return current==null?0:(current[index]-(base==null?0:base[index]));
    }
    private static void applyBone(JsonObject bones,String name,String secondary,ModelPart part,
                                  double time,float weight){
        if(part==null)return;
        float[] rotation=sum(vector(bones,name,"rotation",time),
                             vector(bones,secondary,"rotation",time));
        if(rotation!=null){
            // Respect an NPC's normal idle/walking pose during the blend-in/out.
            part.xRot=interp(part.xRot,(float)Math.toRadians(rotation[0]),weight);
            part.yRot=interp(part.yRot,(float)Math.toRadians(rotation[1]),weight);
            part.zRot=interp(part.zRot,(float)Math.toRadians(rotation[2]),weight);
        }
        float[] now=sum(vector(bones,name,"position",time),
                        vector(bones,secondary,"position",time));
        if(now==null)return;
        float[] start=sum(vector(bones,name,"position",0),
                          vector(bones,secondary,"position",0));
        // Use RELATIVE movement so GeckoLib's bone-pivot offsets at frame 0
        // don't shift the whole NPC model (e.g. permanent 4px head drift).
        float dx=Math.max(-8,Math.min(8,displacement(now,start,0)))*weight;
        float dy=Math.max(-8,Math.min(8,displacement(now,start,1)))*weight;
        float dz=Math.max(-8,Math.min(8,displacement(now,start,2)))*weight;
        float bx=part.x,by=part.y,bz=part.z;
        part.x=bx+dx;
        part.y=by-dy; // Gecko positions Y upward; Minecraft ModelPart Y downward
        part.z=bz+dz;
        LAST_POSITIONS.put(part,new PartOffset(bx,by,bz,part.x,part.y,part.z));
    }

    /** Bedrock/JCraft keyframe channels: vectors plus easing on the arrival frame. */
    private static float[] sampleRotation(JsonElement value,double time) {
        if(value.isJsonArray())return readVector(value.getAsJsonArray());
        if(!value.isJsonObject())return null;
        JsonObject data=value.getAsJsonObject();
        if(data.has("vector"))return sampleRotation(data.get("vector"),time);

        double prev=Double.NEGATIVE_INFINITY,next=Double.POSITIVE_INFINITY;
        float[] before=null,after=null;
        String arrivingEasing="linear";
        for(var key:data.entrySet()){
            try{
                double t=Double.parseDouble(key.getKey());
                float[] vector=sampleRotation(key.getValue(),time);
                if(vector==null)continue;
                if(t<=time && t>prev){
                    prev=t;
                    before=vector;
                }
                if(t>=time && t<next){
                    next=t;
                    after=vector;
                    arrivingEasing=(key.getValue().isJsonObject()
                            &&key.getValue().getAsJsonObject().has("easing"))
                            ?key.getValue().getAsJsonObject().get("easing").getAsString():"linear";
                }
            }catch(NumberFormatException ignored){
                // Non-time interpolation metadata isn't a keyframe.
            }
        }
        if(before==null)return after;
        if(after==null||next==prev)return before;
        float alpha=ease((float)((time-prev)/(next-prev)),arrivingEasing);
        return new float[]{
            before[0]+(after[0]-before[0])*alpha,
            before[1]+(after[1]-before[1])*alpha,
            before[2]+(after[2]-before[2])*alpha
        };
    }

    /** Easing names used in JCraft 0.18.2's real player-animation files. */
    private static float ease(float v,String name){
        double x=Math.max(0,Math.min(1,v));
        double c1=1.70158,c3=c1+1,c2=c1*1.525;
        return (float)switch(name){
            case "easeInSine" -> 1-Math.cos(x*Math.PI/2);
            case "easeOutSine" -> Math.sin(x*Math.PI/2);
            case "easeInOutSine" -> -(Math.cos(Math.PI*x)-1)/2;
            case "easeOutCubic" -> 1-Math.pow(1-x,3);
            case "easeOutBack" -> 1+c3*Math.pow(x-1,3)+c1*Math.pow(x-1,2);
            case "easeInOutBack" -> x<0.5
                    ?Math.pow(2*x,2)*((c2+1)*2*x-c2)/2
                    :(Math.pow(2*x-2,2)*((c2+1)*(2*x-2)+c2)+2)/2;
            case "easeInOutCirc" -> x<0.5
                    ?(1-Math.sqrt(1-Math.pow(2*x,2)))/2
                    :(Math.sqrt(1-Math.pow(-2*x+2,2))+1)/2;
            case "easeOutBounce" -> bounceOut(x);
            default -> x;
        };
    }
    private static double bounceOut(double t){
        double n=7.5625,d=2.75;
        if(t<1/d)return n*t*t;
        if(t<2/d){t-=1.5/d;return n*t*t+0.75;}
        if(t<2.5/d){t-=2.25/d;return n*t*t+0.9375;}
        t-=2.625/d;
        return n*t*t+0.984375;
    }

    private static float[] readVector(JsonArray array) {
        if (array.size() < 3) return null;
        try { return new float[] {array.get(0).getAsFloat(),array.get(1).getAsFloat(),array.get(2).getAsFloat()}; }
        catch (NumberFormatException | IllegalStateException ex) { return null; }
    }
    private NpcAnimationState() {}
}
