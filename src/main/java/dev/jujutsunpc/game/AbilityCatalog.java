package dev.jujutsunpc.game;

import java.util.*;

/**
 * v51's actual procedure names and execute signatures were checked against the installed JAR.
 * A method being present does NOT establish that its player-specific logic works with an NPC.
 * Domain methods remain experimental and manual-only pending in-game validation.
 */
public final class AbilityCatalog {
    public record Move(String key, String title, String procedure, int cost, int cooldownTicks,
                       double optimalMinRange, double optimalMaxRange) {}
    private static Move m(String key,String title,String procedure,int cost,int cooldown,double min,double max) {
        return new Move(key,title,procedure,cost,cooldown,min,max);
    }
    public static boolean isDomain(Move m) { return "domain".equals(m.key()); }
    public static String originalAnimation(String moveKey) {
        return switch (moveKey) {
            case "red" -> "red";
            case "blue" -> "right_arm_front";
            case "purple" -> "both_arm_front";
            case "dismantle", "cleave" -> "punch_right";
            case "piercing_blood" -> "right_arm_front";
            case "supernova" -> "both_arm_front";
            case "boogie_woogie" -> "clap";
            case "thin_ice" -> "punch_right";
            case "domain" -> "domain_expansion1";
            case "dog", "nue", "elephant", "toad" -> "both_arm_front";
            default -> "";
        };
    }
    public record Technique(String key, String title, List<Move> moves) {}
    private static Technique t(String key,String title,Move... moves) {
        return new Technique(key,title,List.of(moves));
    }
    public static final Technique NONE=t("none","No technique");
    public static final Technique LIMITLESS=t("limitless","Limitless",
        m("blue","Blue","TechniqueBlueProcedure",35,90,3,17),
        m("red","Red","TechniqueRedProcedure",40,100,2,17),
        m("purple","Hollow Purple","HollowPurpleProcedure",100,260,7,29),
        m("domain","Unlimited Void (experimental)","UnlimitedVoidProcedure",250,1200,0,16));
    public static final Technique SHRINE=t("shrine","Shrine",
        m("dismantle","Dismantle","DismantleProcedure",25,65,2,19),
        m("cleave","Cleave","CleaveProcedure",20,55,0,4),
        m("domain","Malevolent Shrine (experimental)","MalevolentShrineProcedure",250,1200,0,22));
    public static final Technique BLOOD=t("blood","Blood Manipulation",
        m("convergence","Convergence","TechniqueConvergenceProcedure",18,70,0,18),
        m("piercing_blood","Piercing Blood","PiercingBloodProcedure",50,100,4,26),
        m("supernova","Supernova","SupernovaProcedure",65,140,3,18));
    public static final Technique SHADOWS=t("shadows","Ten Shadows",
        m("dog","Divine Dog","SummonDivineDogProcedure",45,180,3,24),
        m("nue","Nue","SummonNueProcedure",55,240,3,24),
        m("elephant","Max Elephant","SummonMaxElephantProcedure",95,320,5,25),
        m("toad","Toad","SummonToadProcedure",40,150,3,20),
        m("domain","Chimera Shadow Garden (experimental)","ChimeraShadowGardenProcedure",250,1200,0,16));
    public static final Technique BOOGIE=t("boogie","Boogie Woogie",
        m("boogie_woogie","Boogie Woogie","SkillBoogieWoogieProcedure",28,80,0,18));
    public static final Technique SPEECH=t("speech","Cursed Speech",
        m("cursed_speech","Cursed Speech","CursedSpeechProcedure",45,120,1,14));
    public static final Technique SKY=t("sky","Sky Manipulation",
        m("thin_ice","Thin Ice Breaker","ThinIceBreakerProcedure",36,95,0,7));
    public static final Technique ICE=t("ice","Ice Formation",
        m("frost_calm","Frost Calm","FrostCalmProcedure",50,110,3,18),
        m("ice_spear","Ice Spear","IceSpearSkillProcedure",35,85,4,25));
    public static final Technique IDLE=t("idle","Idle Transfiguration",
        m("transfiguration","Idle Transfiguration","IdleTransfigurationProcedure",55,110,0,4),
        m("isomer","Polymorphic Soul Isomer","TechniquePolymorphicSoulIsomerProcedure",65,180,2,17),
        m("domain","Self-Embodiment (experimental)","SelfEmbodimentOfPerfectionProcedure",250,1200,0,14));
    public static final Technique SPIRITS=t("spirits","Cursed Spirit Manipulation",
        m("uzumaki","Uzumaki","SkillUzumakiProcedure",110,300,5,28));
    public static final Technique GRANITE=t("granite","Cursed Energy Discharge",
        m("granite_blast","Granite Blast","GraniteBlastProcedure",50,110,4,25));
    public static final Technique CONSTRUCTION=t("construction","Construction",
        m("liquid_metal","Liquid Metal","TechniqueLiquidMetalProcedure",45,100,3,18),
        m("true_sphere","True Sphere","TechniqueTrueSphereProcedure",110,300,4,20),
        m("domain","Threefold Affliction (experimental)","ThreefoldAfflictionProcedure",250,1200,0,20));
    public static final Technique ANGEL=t("angel","Technique Extinguishment",
        m("jacobs_ladder","Jacob's Ladder","TechniqueJacobsLadderProcedure",85,220,3,25));
    public static final Technique JOGO=t("jogo","Disaster Flames",
        m("domain","Coffin of the Iron Mountain (experimental)","CoffinOfTheIronMountainProcedure",250,1200,0,16));
    public static final Technique PROJECTION=t("projection","Projection Sorcery",
        m("projection","Projection Sorcery","SkillProjectionSorceryProcedure",30,85,0,10),
        m("domain","Time Cell Moon Palace (experimental)","TimeCellMoonPalaceProcedure",250,1200,0,16));
    public static final List<Technique> ALL=List.of(NONE,LIMITLESS,SHRINE,BLOOD,SHADOWS,BOOGIE,
        SPEECH,SKY,ICE,IDLE,SPIRITS,GRANITE,CONSTRUCTION,ANGEL,JOGO,PROJECTION);
    private static final Map<String,Technique> BY_KEY;
    static {
        Map<String,Technique> map=new LinkedHashMap<>();
        for(Technique t:ALL)map.put(t.key(),t);
        BY_KEY=Collections.unmodifiableMap(map);
    }
    public static Technique technique(String id) { return BY_KEY.getOrDefault(id,NONE); }
    public static Optional<Move> move(String techniqueId,String moveId) {
        return technique(techniqueId).moves().stream().filter(m -> m.key().equals(moveId)).findFirst();
    }
    private AbilityCatalog() {}
}
