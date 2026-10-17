package dev.jujutsunpc.game;

/** Cooldown-only casting policy. Seconds are stored in the NPC profile. */
public final class CastPolicy {
    public enum Mode { DISABLED, AUTOMATIC, TRIGGERED, BOTH;
        public boolean automatic(){return this==AUTOMATIC||this==BOTH;}
        public boolean triggered(){return this==TRIGGERED||this==BOTH;}
        public Mode next(){return values()[(ordinal()+1)%values().length];}
    }
    /** Both automatic and operator-triggered moves observe the same per-move cooldown. */
    public static boolean canCast(Mode mode,boolean manual,long now,long readyTick) {
        return (manual?mode.triggered():mode.automatic()) && now>=readyTick;
    }
    public static long secondsToTicks(int seconds) {
        return Math.max(0,Math.min(3600,seconds))*20L;
    }
    private CastPolicy(){}
}
