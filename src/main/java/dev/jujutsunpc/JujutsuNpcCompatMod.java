package dev.jujutsunpc;

import dev.jujutsunpc.compat.NpcCompatibility;
import dev.jujutsunpc.game.NpcCombatEngine;
import dev.jujutsunpc.game.WorldRestrictions;
import dev.jujutsunpc.network.CompatNetwork;
import dev.jujutsunpc.command.CompatCommands;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(JujutsuNpcCompatMod.MOD_ID)
public final class JujutsuNpcCompatMod {
    public static final String MOD_ID="jujutsunpccompat";
    private static final ForgeConfigSpec SERVER_CONFIG;
    public static final ForgeConfigSpec.BooleanValue AUTO_LIMIT_ENABLED;
    public static final ForgeConfigSpec.IntValue AUTO_LIMIT_COUNT;
    public static final ForgeConfigSpec.IntValue AUTO_LIMIT_WINDOW;
    public static final ForgeConfigSpec.IntValue MELEE_BUFFER_TICKS;
    /** World/server settings: effective for every connected player. */
    public static final ForgeConfigSpec.BooleanValue NPC_HIT_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue PLAYER_HIT_SOUNDS;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("performance");
        AUTO_LIMIT_ENABLED = b.comment("Throttle normal automatic NPC casts; not player or manual casts.")
                .define("limitAutomaticCasts", false);
        AUTO_LIMIT_COUNT = b.defineInRange("automaticCastsPerWindow", 5, 1, 100);
        AUTO_LIMIT_WINDOW = b.defineInRange("windowSeconds", 10, 1, 60);
        b.pop();
        b.push("combat");
        MELEE_BUFFER_TICKS=b.comment(
                "Minimum spacing between ordinary NPC melee attacks, in ticks (20=1 second, 30=1.5 seconds, 40=2 seconds). Also staggers NPC-versus-NPC combat turns.")
                .defineInRange("meleeAttackBufferTicks",30,20,40);
        b.pop();
        b.push("sounds");
        NPC_HIT_SOUNDS=b.comment(
                "Play JujutsuCraft hit/reaction sounds when an Easy NPC or CustomNPCs mob is attacked. Applies to the entire server.")
                .define("npcHitSounds",true);
        PLAYER_HIT_SOUNDS=b.comment(
                "Play JujutsuCraft hit/reaction sounds when any player is attacked. Applies to the entire server.")
                .define("playerHitSounds",true);
        b.pop();
        SERVER_CONFIG = b.build();
    }
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Item> GREY_KASHIMO_HAIR=ITEMS.register(
            "kashimo_hair_grey",dev.jujutsunpc.item.GreyKashimoHairItem::new);
    public static final RegistryObject<Item> CONFIG_TOOL = ITEMS.register("editor_tool",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public JujutsuNpcCompatMod() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            (BuildCreativeModeTabContentsEvent event) -> {
                if(event.getTabKey().equals(CreativeModeTabs.COMBAT))
                    event.accept(GREY_KASHIMO_HAIR.get());
            });
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_CONFIG);
        CompatNetwork.init();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(dev.jujutsunpc.sound.JjkHitSoundPolicy.class);
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) ||
                !(event.getTarget() instanceof LivingEntity npc) || !NpcCompatibility.isCompatible(npc)) return;
        if (event.getHand()!=InteractionHand.MAIN_HAND || !player.getMainHandItem().is(CONFIG_TOOL.get())) return;
        if (CompatNetwork.canEdit(player)) CompatNetwork.open(player,npc);
        event.setCanceled(true);
    }
    @SubscribeEvent public void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) { NpcCombatEngine.tick(e); }
    @SubscribeEvent public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        dev.jujutsunpc.game.NpcCombatTuning.onHurt(e);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void attack(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        dev.jujutsunpc.game.NpcMeleeBridge.onAttack(e);
    }
    @SubscribeEvent public void damage(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        NpcCombatEngine.onDamage(e);
        dev.jujutsunpc.game.NpcMeleeBridge.onDamage(e);
    }
    @SubscribeEvent public void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event){
        if(event.getEntity() instanceof ServerPlayer player)CompatNetwork.syncHitSounds(player);
    }
    @SubscribeEvent public void spawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn e) { WorldRestrictions.preventNaturalJjkMobs(e); }
    @SubscribeEvent public void commands(RegisterCommandsEvent e) { CompatCommands.register(e.getDispatcher()); }
}
