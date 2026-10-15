# JAR inspection notes (exact user-provided files)

## JujutsuCraft-ver51-forge-1.20.1.jar
- Forge mod ID `jujutsucraft`, version 51, built using MCreator.
- `net.mcreator.jujutsucraft.procedures` contains 1,195 class files (includes generated inner classes).
- Confirmed via `javap -p`: `TechniqueBlueProcedure`, `TechniqueRedProcedure`, `HollowPurpleProcedure`, `DismantleProcedure` have static `execute(LevelAccessor,double,double,double,Entity)`; `CleaveProcedure` has `execute(LevelAccessor,Entity)`.
- JJK player energy and technique data live in `JujutsucraftModVariables$PlayerVariables`, not automatically in ordinary non-player NPCs. Hence matching an `execute` signature is **necessary but not sufficient**.
- The JAR contains 16 `data/jujutsucraft/worldgen/structure/*.json` structure definitions and 11 `data/jujutsucraft/recipes/*.json` recipes.
- `JujutsucraftConfig` has `doVanillaMobSpawning` and `forceRandomCursedTechnique`, not a confirmed disable-JJK-spawns flag.

## Easy NPC 7.15.0
- Mod ID `easy_npc`.
- `de.markusbordihn.easynpc.entity.easynpc.EasyNPC` is an interface for NPC mobs.
- `EasyNPCEntityTypes.isEasyNPCEntityType(EntityType, Level)` exists; prefer this or the interface over namespace-only detection after integration dependency setup.

## CustomNPCs GBPort 2026-07-11
- Mod ID `customnpcs`.
- `noppes.npcs.entity.EntityNPCInterface` extends `PathfinderMob`, with native targeting and combat handlers.

## Compatibility caution
Non-player JJK techniques require testing each procedure's assumptions; don't assume cast or animations work just because the reflection target exists.
