# v0.2 implementation and verification status

## Implemented in source (not compiled/tested in Minecraft)

- [x] Forge source layout + mod dependencies.
- [x] Source-level classifier for CustomNPCs classes and Easy NPC entity namespace.
- [x] Server-authorized in-game editor; one innate technique, individually enabled moves.
- [x] Original v51 procedure reflection invoker for five starter moves; invocation unverified for real NPC casters.
- [x] Explicit entity/position/direction manual trigger commands; entity target temporarily set and restored.
- [x] A HumanoidModel mixin and network events consuming animation JSON shipped by JujutsuCraft (rotation bones only).
- [x] Domain-effect detection on native NPC combat target and original-effect anti-domain fallback.
- [x] Optional globally configured throttle for ordinary automatic casts; exempt manual casts.
- [x] Source-level NPC energy/cooldown tracking, baseline RCT healing, fatal-hit recovery.
- [x] Source-level per-entity config persistence with new-UUID runtime reset.
- [x] Natural/chunk generation JJK spawn suppression preserving non-natural spawn reasons.
- [x] JJK data overrides: 16 structure definitions, 11 recipes and 30 native loot tables.
- [x] Static verification of upstream JARs and all 61 JSON files, 8 pure combat policy assertions.

## Not implemented / not proven

- [ ] Forge compilation in working Gradle environment (not available offline here).
- [ ] Game startup with full dependencies, no load/mixin errors, working editor UI.
- [ ] Per-move confirmation that JujutsuCraft creates attack effects, damages targets and respects animation timing for both NPCs.
- [ ] Domain Expansion creation, ownership and real clash/defense decision (domain-first is only a stub).
- [ ] Hollow Wicker Basket behavior, domain move selection, per-domain conditions and cooldowns.
- [ ] Black Flash and all JJK abilities, more than Limitless/Shrine.
- [ ] Original JJK RCT/energy mechanics (currently addon energy and basic heal).
- [ ] Original animations for every move; complete bone/keyframe coverage and non-HumanoidModel renderers.
- [ ] Easy NPC dialogue/action native bridge and CustomNPCs scripting-specific bridge.
- [ ] Proven clone and respawn preservation through each NPC mod's actual copy/save API.
- [ ] Total creative-only item restrictions, including runtime drops, other mods' recipes/trades, world containers.
- [ ] Verify worldgen structure overrides win reload priority in new chunks.
- [ ] Domain counter performance and multiplayer synchronization tests.
