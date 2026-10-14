# JujutsuCraft NPC Compatibility — v0.2 integration source

**Minecraft 1.20.1 · Forge 47.4.26 · JujutsuCraft v51 · Easy NPC 7.15.0 · CustomNPCs GBPort**

> **Developer source only. This has NOT been compiled against Forge, boot-tested,
> or verified as a playable mod. There is no working mod JAR yet.**
> Your original JujutsuCraft, Easy NPC, and CustomNPCs mod JARs remain unmodified.

## What was integrated in v0.2

- Executes the actual installed **JujutsuCraft gameplay procedures** for an initial five moves, using reflection to avoid copying upstream classes. Experimental: a valid procedure invocation can still do nothing if that procedure expects JJK player-specific attributes, timers or flags.
- For manual **entity targeting**, temporarily swaps `Mob#getTarget()` while the JJK procedure runs and restores the NPC's own target afterward. Manual position/direction casts instead temporarily clear the NPC target to prevent accidental fallback. Procedures that defer target lookup until later are not fully supported yet.
- Streams NPC pose events over a Forge network channel. A **client-only HumanoidModel mixin** reads rotation keyframes directly from JujutsuCraft's original `assets/jujutsucraft/player_animation/*.json` files. Supports standard humanoid limbs on renderers using `HumanoidModel`; custom renderer skeletons, translation/position keys, accurate move-to-animation mapping for every move and synced ability timing still need work.
- Adds domain-threat detection for an NPC's existing hostile target when that target has JujutsuCraft's original `domain_expansion` effect. The handler **tries the future domain-clash hook first** (currently unimplemented), then applies available original anti-domain effects (`simple_domain`, `falling_blossom_emotion`, `domain_amplification`) if enabled. The real domain-clash implementation is **not working yet**. Hollow Wicker Basket's exact implementation has not been verified.
- Multi-page in-game editor includes one technique / per-move Auto, Trigger, Both, or Disabled; energy capacity and regeneration; RCT and fatal-hit recovery; and independently enabled available defensive effects.
- Optional **server-side automatic cast throttle** (`serverconfig`): default OFF. Manual casts and player techniques are exempt; domain counters aren't subject to this throttle.
- Suppresses *natural* and *chunk generation* JJK mob spawns, leaving commands, creative eggs and technique summons accessible.
- Datapack overrides replace the 16 JJK structure definitions with an empty-biome-tag gate, disable its 11 bundled crafting recipes, and empty its 30 bundled loot tables. **Other acquisition pathways, other mods' recipes, and dynamic structure generation are not proven blocked.**
- NPC runtime energy and cooldowns persist in Forge entity data. When data is copied onto an entity with a *new UUID*, runtime energy and charges initialize separately. Actual cloning by each upstream mod must be tested.

## How to build on Windows

1. Install **JDK 17** and **Gradle 8.8**. Network access is required on the first build so ForgeGradle can download Minecraft and Forge dependencies.
2. Open the extracted project folder. Double-click `build-windows.bat`, or run `gradle --no-daemon clean build` in Command Prompt.
3. **If and only if the build succeeds**, check `build/libs/` for `jujutsu-npc-compat-0.2.0-dev.jar` (possibly alongside another un-reobfuscated variant). It is still experimental; a successful Gradle build doesn't prove it works in game.
4. Create a **separate test Minecraft profile** with Forge 47.4.26, your three mods and their dependencies. Backup a test world. Put the compiled addon JAR there.
5. Give yourself the editor with `/give @s jujutsunpccompat:editor_tool`. In Creative mode or as an operator, use it on a compatible humanoid NPC.
6. Give it a technique and choose a move mode. Use a disposable mob and enable the original NPC AI target as needed. Check `latest.log` for procedure errors.

### Operator manual casts (energy/cooldown bypass)

```
/jjknpc cast <npc_selector> <move_key> entity <target_selector>
/jjknpc cast <npc_selector> <move_key> position <x> <y> <z>
/jjknpc cast <npc_selector> <move_key> direction <dx> <dy> <dz>
```

Every manual cast requires an explicit target, position or direction. Only moves enabled for Trigger/Both qualify. The original procedure may not support every target type.

### Local static checks

From the project folder, with the original three upstream JARs in one directory:

```
python tools/verify_upstream.py "C:\\path\\to\\mod-jars"
```

`bash tools/test_pure_java.sh` runs dependency-free decision-engine tests where Bash and the Java compiler are available.

## Known unimplemented / unverified commitments

**DO NOT describe the following as functioning:** full cursed-technique roster; actual NPC Domain Expansion creation/clashes; correct selection and playback of *every* original JJK animation (including custom skeletons and timing); fully original RCT/energy/Black Flash mechanics; Easy NPC dialogue-event integration; CustomNPCs script callbacks; all creative-only item acquisition restrictions; NPC clone/respawn testing; gameplay/renderer testing; Forge compile result. The first five move procedures are *invoked*, not confirmed to successfully cast from arbitrary NPCs.

Important original JJK finding: `PlayAnimationProcedure` in v51 checks for Player or GeckoLib `GeoEntity` and can skip standard humanoid NPCs. That is why a separate client pose adapter is needed. JJK player variables also aren't automatically available on generic NPCs.

**Next validation:** Run `gradle build` with JDK 17 on a networked PC; fix any compilation errors; launch test Forge with all dependencies and analyze `latest.log`; only then expand to domain ownership, domain clashes and move-by-move cast validation.
