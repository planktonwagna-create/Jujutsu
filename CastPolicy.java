package dev.jujutsunpc.game;

/** Pure Java, unit-testable rules; no Minecraft dependencies. */
public final class CastPolicy {
    public enum Mode { DISABLED, AUTOMATIC, TRIGGERED, BOTH;
        public boolean automatic() { return this == AUTOMATIC || this == BOTH; }
        public boolean triggered() { return this == TRIGGERED || this == BOTH; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public static boolean canCast(Mode mode, boolean manual, double energy, double cost,
                                  long tick, long cooldownReadyTick) {
        if (manual) return mode.triggered(); // Explicit manual casts ALWAYS bypass energy and cooldowns.
        return mode.automatic() && energy >= cost && tick >= cooldownReadyTick;
    }
    private CastPolicy() {}
}
