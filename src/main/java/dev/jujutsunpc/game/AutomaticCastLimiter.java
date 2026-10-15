package dev.jujutsunpc.game;

import dev.jujutsunpc.JujutsuNpcCompatMod;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional sliding-window throttle of ordinary *automatic* casts. Manual casts exempt. */
public final class AutomaticCastLimiter {
    private static final Map<ServerLevel, Deque<Long>> HISTORY = new WeakHashMap<>();
    public static synchronized boolean canBegin(ServerLevel level, long tick) {
        if (!JujutsuNpcCompatMod.AUTO_LIMIT_ENABLED.get()) return true;
        Deque<Long> recent = HISTORY.computeIfAbsent(level, k -> new ArrayDeque<>());
        long cutoff = tick - JujutsuNpcCompatMod.AUTO_LIMIT_WINDOW.get() * 20L;
        while (!recent.isEmpty() && recent.peekFirst() <= cutoff) recent.removeFirst();
        return recent.size() < JujutsuNpcCompatMod.AUTO_LIMIT_COUNT.get();
    }
    public static synchronized void didBegin(ServerLevel level, long tick) {
        if (JujutsuNpcCompatMod.AUTO_LIMIT_ENABLED.get())
            HISTORY.computeIfAbsent(level, k -> new ArrayDeque<>()).addLast(tick);
    }
    private AutomaticCastLimiter() {}
}
