package cn.qizhang.cnpcwizardsintegration.bridge;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.util.Identifier;

/** Thread-safe, server-tick cooldown state keyed by caster and spell. */
public final class CooldownTracker {
    private final ConcurrentMap<Key, Long> expiresAt = new ConcurrentHashMap<>();

    public long remainingTicks(UUID casterId, Identifier spellId, long currentTick) {
        Key key = new Key(casterId, spellId);
        Long expiry = expiresAt.get(key);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - currentTick;
        if (remaining <= 0L) {
            expiresAt.remove(key, expiry);
            return 0L;
        }
        return remaining;
    }

    public void start(UUID casterId, Identifier spellId, long currentTick, int durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be non-negative");
        }
        Key key = new Key(casterId, spellId);
        if (durationTicks == 0) {
            expiresAt.remove(key);
            return;
        }
        long expiry = currentTick > Long.MAX_VALUE - durationTicks
                ? Long.MAX_VALUE
                : currentTick + durationTicks;
        expiresAt.put(key, expiry);
    }

    private record Key(UUID casterId, Identifier spellId) {
        private Key {
            Objects.requireNonNull(casterId, "casterId");
            Objects.requireNonNull(spellId, "spellId");
        }
    }
}
