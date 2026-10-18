package dev.jujutsunpc.game;

import java.util.*;

/**
 * v51's actual procedure names and execute signatures were checked against the installed JAR.
 * A method being present does NOT establish that its player-specific logic works with an NPC.
 * Domain methods remain experimental and manual-only pending in-game validation.
 */
public final class AbilityCatalog {
    public record Move(String key, String title, String procedure, int cost, int cooldownTicks,
                       double optimalMinRange, double optimalMaxRange,int skillId) {
        public Move(String key,String title,String procedure,int cost,int cooldownTicks,double min,double max){
            this(key,title,procedure,cost,cooldownTicks,min,max,-1);
        }
    }
    private static Move m(String key,String title,String procedure,int cost,int cooldown,double min,double max) {
        return new Move(key,title,procedure,cost,cooldown,min,max);
    }
    public static boolean isDomain(Move m) { return "domain".equals(m.key()) || m.key().startsWith("domain_"); }
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

    /**
     * Choose a matching animation from the 116 ORIGINAL ver51 humanoid animation
     * resources. This intentionally does not claim each MCreator skill has a
     * one-to-one player animation: native v51 dispatches many via separate
     * PlayAnimationEntity procedures. Categorized fallback poses are used when
     * no specific animation is mapped.
     */
    public static String animationFor(Move move,boolean charging) {
        if(charging && supportsCharge(move))return "charge1";
        if(isDomain(move))return "domain_expansion1";
        String known=originalAnimation(move.key());
        if(!known.isEmpty())return known;
        if(isBasic(move))return switch(move.key()){
            case "barrage" -> "punch_gomu_gatling1";
            case "knockback" -> "punch_overhead";
            default -> "punch_right";
        };
        String proc=move.procedure().replace("Procedure","").toLowerCase(Locale.ROOT);
        if(proc.contains("martialsolution")||proc.contains("kamutoke"))return "right_arm_up";
        if(proc.contains("gavel")||proc.contains("sword")||proc.contains("katana")
                ||proc.contains("weapon")||proc.contains("dragonbone")
                ||proc.contains("nanami"))return "sword_overhead";
        if(proc.contains("barrage")||proc.contains("consecutive")||proc.contains("continue")
                ||proc.contains("combo"))return "combo1";
        if(proc.contains("kick")||proc.contains("tackle"))return "kick_right";
        if(proc.contains("punch")||proc.contains("fist")||proc.contains("attack"))return "punch_right";
        if(proc.contains("boogie")||proc.contains("clap"))return "clap";
        if(proc.contains("speed")||proc.contains("projection"))return "speed_attack1";
        if(proc.contains("uzumaki")||proc.contains("sphere")||proc.contains("meteor")
                ||proc.contains("garuda")||proc.contains("energywave"))return "both_arm_front";
        if(proc.contains("summon")||proc.contains("divinedog")||proc.contains("nue")
                ||proc.contains("toad")||proc.contains("shikigami"))return "both_arm_ground";
        if(proc.contains("lightning")||proc.contains("thunder")||proc.contains("maximum")
                ||proc.contains("jacobs"))return "both_arm_up";
        if(proc.contains("red"))return "red";
        if(proc.contains("ice")||proc.contains("blood")||proc.contains("laser")
                ||proc.contains("flame")||proc.contains("slash")||proc.contains("dismantle")
                ||proc.contains("cleave")||proc.contains("piercing")||proc.contains("blast")
                ||proc.contains("projectile"))return "right_arm_front";
        return "right_arm_front";
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


    private static final String EXTRA_DISPATCH = """
Angel|angel|Angel|15=TechniqueJacobsLadder,20=AngelDomainExpansion
Chojuro|chojuro|Chojuro Zenin|5=HanamiSpear,9=ChojuroStoneHand,10=HanamiSpear3,20=OtherDomainExpansion
Choso|blood|Blood Manipulation|1005=TechniqueSlicingExorcism,1006=TechniqueConvergence,1007=PiercingBlood,1008=Supernova,1009=TechniqueFlowingRedScale,1016=DagonWaterSkill,1018=TechniqueWingKing,1019=KechizuPoizon,1020=ChosoDomainExpansion
Crystal|crystal|Crystal Curse|4=AttackOverhead,5=CrystalRoll,6=Crystal1,7=Crystal2,8=AttackOverhead,4708=Crystal3,9=AttackOverhead,4709=CrystalExplosion,10=CrystalBreath,15=CrystalSelfExplosion,20=OtherDomainExpansion
CursedSpeech|speech|Cursed Speech|304=Avoidance,309=CursedSpeech,320=InumakiDomainExpansion
DaburaKaraba|dabura|Dabura Karaba|4=AttackJump,5=DaburaLaser1,6=DaburaLaser2,7=DaburaLaser3,8=DaburaLaserTower,9=DaburaDarkness1,11=SkillDaburaCombo1,12=SkillDaburaCombo2,15=DaburaKick,20=DomainExpansionDabura
Dagon|dagon|Dagon|805=DagonWaterSkill,806=DagonFish1,807=DagonFish2,809=DeathSwarm,810=SummonBathynomus,820=HorizonOfTheCaptivatingSkandha
Dhruv|dhruv|Dhruv Lakdawalla|5=SkillDhruv1,6=SkillDhruv2,20=OtherDomainExpansion
Fushiguro|shadows|Ten Shadows|604=ReturnShadow,606=SummonDivineDog,607=SummonDivineDogTotality,608=SummonNue,609=SummonGreatSerpent,610=SummonToad,1007=SummonMaxElephant,612=SummonRabbitEscape,613=SummonRoundDeer,614=SummonPiercingOx,615=SummonTigerFuneral,617=SummonMergedBeastAgito,620=ChimeraShadowGarden
Geto|spirits|Cursed Spirit Manipulation|1810=GetoCancelTechnique,1813=Test2,1813=GetoSpawnCurseRandom,1815=SkillUzumaki,1816=SkillUzumakiMini,1819=GetoHappySet,1820=WombProfusion
Gojo|limitless|Limitless|204=KashimoRotateKick,205=Infinity,206=TechniqueBlue,207=TechniqueRed,208=TechniqueBluePunch,215=HollowPurple,220=UnlimitedVoid
Hakari|hakari|Kinji Hakari|4=SkillTakabaKick,5=SkillBall,6=SkillDoors,7=SkillConsecutiveEffects,20=IdleDeathGamble
Hanami|hanami|Hanami|1405=HanamiSpear,1406=WoodenBall,1410=HanamiSpear3,1415=HanamiFlowerOffering,1420=CeremonialSeaOfLight
Hazenoki|hazenoki|Iori Hazenoki|5=HazenokiEye,10=HazenokiEye,20=OtherDomainExpansion
Higuruma|higuruma|Hiromi Higuruma|5=GetGavel,6=AttackJump,9=GetGavel,8=AttackContinueHiguruma,8=SetAttributeMainhand,11=GetGavel,10=AttackStrongMahito,10=AttackOverheadHiguruma,19=SkillJudgement,20=DeadlySentencing
Ino|ino|Takuma Ino|5=SkillKaichi,6=SkillReiki,7=SkillKirin,8=SkillRyu,20=OtherDomainExpansion
Ishigori|granite|Cursed Energy Discharge|1205=GraniteBlast,1206=GraniteBlast2,1220=IshigoriDomainExpansion
Itadori|itadori|Yuji Itadori|5=DivergentFist,6=Attack4ConsecutiveAttacks,7=Dismantle,8=Cleave,13=PiercingBlood,14=SuperNovaItadori,18=DeepConcentrationItadori,19=TransformToSukuna,20=ItadoriDomainExpansion
Jjnichi|jinichi|Jinichi Zenin|4=AttackOverhead,5=TechniqueFistSingle,6=FistGun,7=TechniqueFist,8=TechniqueFoot,10=TechniqueFistMeteor,20=JinichiDomain
Jogo|jogo|Disaster Flames|405=JogoFlame1,406=JogoFlame2,407=TechniqueEmberInsects,408=JogoFlame3,409=JogoFlame4,410=ChojuroStoneHand,415=MaximumMeteor,420=CoffinOfTheIronMountain
Junpe|junpei|Junpei Yoshino|5=SkillMoonDregs,20=OtherDomainExpansion
Kaori|kaori|Kaori Itadori|5=SkillAntiGravity,10=SkillAntiGravityReversed
Kashimo|kashimo|Hajime Kashimo|703=KashimoRotateKick,704=AttackJump,705=LightningStrike,706=KashimoRotateKick,707=AttackJump,710=TurnUpTheVolume,715=TechniqueMythicalBeastAmber,716=TechniqueAh,717=TechniqueEnergyWave1,719=SkillKashimoKick,720=KashimoDomain,730=OtakeThunder,731=OtakeFlame
Kugisaki|kugisaki|Nobara Kugisaki|5=KugisakiNail,6=Hairpin,7=Hairpin,20=KugisakiDomainExpansion
Kurourushi|kurourushi|Kurourushi|4=AttackJump,5=Cockroach1,6=Cockroach2,7=Cockroach3,8=TechniqueEarthenInsectTrance,20=KurourushiDomainExpansion,30=SummonRat,31=RatExplode
Kusakabe|kusakabe|Atsuya Kusakabe|5=TechniqueKusakabe1,7=TechniqueIaiYuduki,20=OtherDomainExpansion
MeiMei|meimei|Mei Mei|1105=AttackJump,1106=Attack4ConsecutiveAttacks,1107=AttackStrongMahito,1115=TechniqueBlackBird,1120=MeimeiDomainExpansion
Mahito|idle|Idle Transfiguration|1503=AttackSpeed,1504=AttackOverhead,1505=IdleTransfiguration,1506=TechniqueMahitoHand3,1507=MahitoGun,1508=TechniqueBodyRepelSingle,1509=TechniqueBodyRepel,1510=TechniquePolymorphicSoulIsomer,1515=InstantSpiritBodyofDistortedKilling,1516=AttackStrongMahito,1520=SelfEmbodimentOfPerfection
Mahoraga|mahoraga|Mahoraga|1604=AttackJump,1605=AttackSpeed,1606=AttackOverhead,1607=TechniqueExplode,1608=StoneThrowing,1609=MahoragaSwimming,1615=AttackOverheadSuper,1619=MahoragaCutTheWorld,1620=OtherDomainExpansion
Miguel|miguel|Miguel Oduol|5=TechniquePrayerSong,6=Attack4ConsecutiveAttacks,7=KashimoRotateKick,8=MiguelKick,20=OtherDomainExpansion
Nanami|nanami|Kento Nanami|1305=WeaponNanamiRightClickedInAir,1306=AttackJump,1307=Attack4ConsecutiveAttacks,1310=Garagara,1320=NanamiDomainExpansion
Naoya|projection|Projection Sorcery|1905=SkillProjectionSorcery,1907=SpeedIsPower,1910=SkillExplodeAir,1915=AttackProjectionSorcery,1916=AttackTackleFlying,1920=TimeCellMoonPalace,1930=HighSpeedMove
Nishimiya|nishimiya|Momo Nishimiya|5=WindScythe,6=WindScythe2,10=WindScytheTornade,20=OtherDomainExpansion
Ogi|ogi|Ogi Zenin|5=TechniqueBlazingCourage,11=TechniqueOgi1,12=TechniqueOgi2,13=TechniqueOgi3,14=TechniqueOgi4,15=TechniqueOgi5,19=TechniqueOgi9,20=OtherDomainExpansion
Okkotsu|okkotsu|Yuta Okkotsu|503=AttackSpeed,504=AttackStrongOkkotsu,505=TechniqueImitation1,506=SkillDhruv3,507=ThinIceBreaker,510=TechniqueRika1,515=TechniquePureLove,520=AuthenticMutualLove,520=TechniqueRika2
RantaZenin|ranta|Ranta Zenin|10=SkillRantaEye,20=OtherDomainExpansion
ReggieStar|reggie|Reggie Star|5=ReceiptKitchenKnife,10=ReceiptCar,11=ReceiptTruck,15=ReceiptHouse,20=OtherDomainExpansion,30=BayerKnife
Rozetsu|rozetsu|Rozetsu|3=AttackJump,4=AttackOverhead,5=SkillRozetsuShikigami1,6=SkillRozetsuShikigami2,7=SkillRozetsuShikigami3,20=RozetsuDomainExpansion,30=TechniqueHanakoHand,31=TechniqueHanakoHand2
SmallpoxDeity|smallpox|Smallpox Deity|5=SkillStone,6=TechniqueSmallpoxDeityBreath,20=GraveyardDomain,30=SkillSoulExplosion
Sukuna|shrine|Shrine|105=Dismantle,106=Cleave,107=Open,111=SkillSukunaCombo1,112=SkillSukunaCombo2,113=SkillSukunaCombo3,120=MalevolentShrine,150=Zonba,151=TechniqueSkeleton1,152=TechniqueSkull,153=TechniqueScissors,154=SkillEnergyWave2,155=TechniqueTamamonoMae1,156=TechniqueTamamonomae2,157=TechniqueFoxFire,158=AttackPiecingOx,159=TechniqueMucus,160=SkillGanesha
Takaba|takaba|Fumihiko Takaba|1705=SkillComedian,1706=SkillTakabaKick,1710=TakabaLookOut,1715=SkillWiFi,1720=OtherDomainExpansion
TakakoUro|sky|Sky Manipulation|10=ThinIceBreaker,20=UroDomainExpansion
Todo|boogie|Boogie Woogie|2003=AttackOverhead,2004=AttackJump,2005=SkillBoogieWoogie,2006=Lariat,2007=Attack4ConsecutiveAttacks,2015=AttackTodoBlackFlash,2020=OtherDomainExpansion
Tsukumo|tsukumo|Yuki Tsukumo|904=AttackOverhead,905=TechniqueGarudaBall,906=TechniqueGaruda1,910=TechniqueStarRage,915=TechniqueStarRage,920=TsukumoDomainExpansion
Uraume|ice|Ice Formation|5=IceSpearSkill,6=UraumeSkill1,8=FrostCalm,9=Icefall,20=OtherDomainExpansion
Yaga|yaga|Masamichi Yaga|6=DollTackle,10=SummonDollsRain,15=SummonDollRainGiant,20=OtherDomainExpansion
Yorozu|construction|Construction|5=AttackOverhead,6=AttackSpeed,7=AttackJump,8=TechniqueLiquidArrow,9=TechniqueNeedle,12=TechniqueLiquidMetal,14=TechniqueInsectArmor,15=TechniqueTrueSphere,20=ThreefoldAffliction
    """;

    private static final Set<String> CHARGEABLE = new HashSet<>(Arrays.asList(("""
AttackOverhead AttackSpeed AttackStrongMahito AttackStrongOkkotsu AttackTodoBlackFlash BayerKnife ChojuroStoneHand Cleave Cockroach1 Cockroach3 Crystal1 Crystal2 Crystal3 CrystalBreath CrystalExplosion CrystalRoll CrystalSelfExplosion DaburaDarkness1 DaburaKick DaburaLaser1 DaburaLaser2 DaburaLaser3 DaburaLaserTower DagonFish1 DagonFish2 DagonWaterSkill DeathSwarm Dismantle DollTackle FistGun FrostCalm Garagara GetoCancelTechnique GraniteBlast2 GraniteBlast HanamiFlowerOffering HanamiSpear3 HollowPurple IceSpearSkill Icefall JogoFlame1 JogoFlame2 JogoFlame3 Lariat MahitoGun MahoragaSwimming Open OtakeFlame OtakeThunder PiercingBlood SkillAntiGravity SkillAntiGravityReversed SkillBoogieWoogie SkillEnergyWave2 SkillExplodeAir SkillGanesha SkillKaichi SkillKashimoKick SkillProjectionSorcery SkillRantaEye SkillRyu SkillSoulExplosion SkillStone SkillTakabaKick SkillUzumakiMini SkillUzumaki SkillWiFi SpeedIsPower StoneThrowing SummonBathynomus SummonDollRainGiant SummonDollsRain Supernova TechniqueAh TechniqueBlue TechniqueBluePunch TechniqueBodyRepel TechniqueBodyRepelSingle TechniqueEmberInsects TechniqueEnergyWave1 TechniqueExplode TechniqueFistMeteor TechniqueFist TechniqueFistSingle TechniqueFoot TechniqueFoxFire TechniqueGarudaBall TechniqueHanakoHand2 TechniqueHanakoHand TechniqueIaiYuduki TechniqueJacobsLadder TechniqueMahitoHand3 TechniqueMythicalBeastAmber TechniqueNeedle TechniqueOgi1 TechniqueOgi2 TechniqueOgi4 TechniqueOgi5 TechniqueOgi9 TechniquePureLove TechniqueRed TechniqueRika2 TechniqueSkeleton1 TechniqueSkull TechniqueSlicingExorcism TechniqueSmallpoxDeityBreath TechniqueStarRage Test2 ThinIceBreaker TransformToSukuna UraumeSkill1 WindScythe2 WindScythe WindScytheTornade Zonba
""").trim().split("\\s+")));
    private static final Set<String> CROUCHABLE = new HashSet<>(Arrays.asList(("""
AttackTodoBlackFlash Cleave DaburaLaser1 DagonWaterSkill Dismantle DollTackle HanamiSpear HollowPurple TechniqueBlue TechniqueBluePunch TechniqueEmberInsects TechniqueFistSingle TechniqueFoxFire TechniqueRika1 Test2 ThinIceBreaker Zonba
""").trim().split("\\s+")));
    public static boolean supportsCharge(Move move) {
        return CHARGEABLE.contains(move.procedure().replace("Procedure",""));
    }
    public static boolean supportsCrouch(Move move) {
        return CROUCHABLE.contains(move.procedure().replace("Procedure",""));
    }
    public static boolean isBasic(Move move) {
        return "attack".equals(move.key())||"knockback".equals(move.key())||"barrage".equals(move.key());
    }
    private static final Set<String> DOMAIN_PROCEDURES = new HashSet<>(Arrays.asList(("""
UnlimitedVoid MalevolentShrine ChimeraShadowGarden SelfEmbodimentOfPerfection ThreefoldAffliction CoffinOfTheIronMountain TimeCellMoonPalace WombProfusion IdleDeathGamble AuthenticMutualLove
HorizonOfTheCaptivatingSkandha CeremonialSeaOfLight DeadlySentencing GraveyardDomain OtherDomainExpansion ChosoDomainExpansion DomainExpansionDabura AngelDomainExpansion IshigoriDomainExpansion ItadoriDomainExpansion KashimoDomain KugisakiDomainExpansion KurourushiDomainExpansion JinichiDomain NanamiDomainExpansion RozetsuDomainExpansion TsukumoDomainExpansion UroDomainExpansion InumakiDomainExpansion
""").trim().split("\\s+")));
    private static String human(String proc) {
        String clean=proc.replace("Procedure","");
        clean=clean.replaceFirst("^(Technique|Skill)","");
        return clean.replaceAll("(?<=[a-z0-9])(?=[A-Z])"," ").trim();
    }
    public record TechData(String key,String title,List<Move> moves) {}

    public static final List<Technique> ALL;
    private static final Map<String,Technique> BY_KEY;
    static {
        LinkedHashMap<String,Technique> byKey=new LinkedHashMap<>();
        for(Technique t:List.of(NONE,LIMITLESS,SHRINE,BLOOD,SHADOWS,BOOGIE,SPEECH,SKY,ICE,IDLE,SPIRITS,GRANITE,CONSTRUCTION,ANGEL,JOGO,PROJECTION))
            byKey.put(t.key(),t);
        for(String row:EXTRA_DISPATCH.split("\\R")){
            row=row.trim();
            if(row.isEmpty())continue;
            String[] parts=row.split("\\|",4);
            if(parts.length!=4)continue;
            String techKey=parts[1],title=parts[2];
            Technique current=byKey.get(techKey);
            List<Move> merged=new ArrayList<>(current==null?List.of():current.moves());
            for(String raw:parts[3].split(",")){
                String[] moveBits=raw.trim().split("=",2);
                if(moveBits.length!=2)continue;
                int skill=Integer.parseInt(moveBits[0]);
                String procedure=moveBits[1]+"Procedure";
                int existing=-1;
                for(int i=0;i<merged.size();i++){
                    if(merged.get(i).procedure().equals(procedure)){
                        existing=i;break;
                    }
                }
                if(existing>=0){
                    Move old=merged.get(existing);
                    if(old.skillId()<0) merged.set(existing,new Move(old.key(),old.title(),old.procedure(),old.cost(),
                            old.cooldownTicks(),old.optimalMinRange(),old.optimalMaxRange(),skill));
                }else{
                    boolean domain=DOMAIN_PROCEDURES.contains(moveBits[1]);
                    boolean hasDomain=merged.stream().anyMatch(AbilityCatalog::isDomain);
                    String id=domain && !hasDomain?"domain":"skill_"+skill+"_"+moveBits[1].toLowerCase(Locale.ROOT);
                    merged.add(new Move(id,human(moveBits[1])+(domain?" (experimental)":""),procedure,0,
                            domain?1200:100,0,domain?20:18,skill));
                }
            }
            byKey.put(techKey,new Technique(techKey,title,List.copyOf(merged)));
        }
        // v51's original general combat choices (0,1,2) are available regardless of technique.
        List<Move> basics=List.of(
            new Move("attack","Attack","AttackWeakProcedure",0,20,0,4,0),
            new Move("knockback","Knockback Attack","AttackStrongProcedure",0,60,0,4,1),
            new Move("barrage","Barrage","AttackContinueProcedure",0,100,0,4,2));
        for(var pair:new ArrayList<>(byKey.entrySet())){
            List<Move> m=new ArrayList<>(pair.getValue().moves());
            m.addAll(basics);
            byKey.put(pair.getKey(),new Technique(pair.getKey(),pair.getValue().title(),List.copyOf(m)));
        }
        ALL=List.copyOf(byKey.values());
        BY_KEY=Collections.unmodifiableMap(byKey);
    }
    public static Technique technique(String id) { return BY_KEY.getOrDefault(id,NONE); }
    public static Optional<Move> move(String techniqueId,String moveId) {
        return technique(techniqueId).moves().stream().filter(m -> m.key().equals(moveId)).findFirst();
    }
    private AbilityCatalog() {}
}
