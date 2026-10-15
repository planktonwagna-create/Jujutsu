# Registry crash mitigation (v0.2.1-dev)

The 0.2 build replaced JujutsuCraft's original worldgen/structure definitions with new jigsaw objects referencing an empty biome tag. These files are suspected of causing the registry-loading crash on world selection; a `latest.log` is needed to verify the exact codec failure.

This patch removes the 16 replacement structure definitions and their empty biome tag. It instead adds 16 **worldgen/structure_set** overrides copied from the original JujutsuCraft v51 JSON, setting each placement frequency to 0.0. This disables random structure placement without replacing registrations, resource keys, or template pools.

Verify in game using a backup/test profile and new world. Player and NPC casts, summons, and creative content should remain available; the compiled mod has not been fully tested in game.
