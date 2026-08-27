package cn.qizhang.cnpcwizardsintegration.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure Java scheduler for fixed-duration channel pulses and cancellation. */
final class ChannelCastFlow<K, V> {
    private final Map<K, ActiveChannel<V>> active = new LinkedHashMap<>();

    synchronized StartResult start(
            K key,
            V payload,
            long startedAt,
            int durationTicks,
            int pulseCount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (pulseCount <= 0) {
            throw new IllegalArgumentException("pulseCount must be positive");
        }
        if (active.containsKey(key)) {
            return StartResult.BUSY;
        }
        active.put(key, new ActiveChannel<>(payload, startedAt, durationTicks, pulseCount));
        return StartResult.STARTED;
    }

    synchronized boolean isActive(K key) {
        return active.containsKey(key);
    }

    synchronized List<ActiveEntry<K, V>> activeEntries() {
        List<ActiveEntry<K, V>> entries = new ArrayList<>(active.size());
        active.forEach((key, state) -> entries.add(new ActiveEntry<>(key, state.payload)));
        return List.copyOf(entries);
    }

    synchronized Optional<Cancellation<V>> cancel(K key) {
        ActiveChannel<V> state = active.remove(key);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(new Cancellation<>(state.payload, state.nextPulseIndex));
    }

    synchronized TickResult<V> tick(K key, long currentTick) {
        ActiveChannel<V> state = active.get(key);
        if (state == null) {
            return null;
        }

        long elapsed = Math.max(0L, currentTick - state.startedAt);
        int firstPulseIndex = state.nextPulseIndex;
        while (state.nextPulseIndex < state.pulseCount
                && elapsed >= state.threshold(state.nextPulseIndex)) {
            state.nextPulseIndex++;
        }

        int duePulses = state.nextPulseIndex - firstPulseIndex;
        boolean completed = elapsed >= state.durationTicks
                && state.nextPulseIndex == state.pulseCount;
        if (completed) {
            active.remove(key);
        }
        return new TickResult<>(
                state.payload,
                firstPulseIndex,
                duePulses,
                state.nextPulseIndex,
                completed);
    }

    enum StartResult {
        STARTED,
        BUSY
    }

    record ActiveEntry<K, V>(K key, V payload) {
    }

    record Cancellation<V>(V payload, int deliveredPulses) {
    }

    record TickResult<V>(
            V payload,
            int firstPulseIndex,
            int duePulses,
            int deliveredPulses,
            boolean completed) {
    }

    private static final class ActiveChannel<V> {
        private final V payload;
        private final long startedAt;
        private final int durationTicks;
        private final int pulseCount;
        private int nextPulseIndex;

        private ActiveChannel(V payload, long startedAt, int durationTicks, int pulseCount) {
            this.payload = payload;
            this.startedAt = startedAt;
            this.durationTicks = durationTicks;
            this.pulseCount = pulseCount;
        }

        private float threshold(int zeroBasedPulseIndex) {
            float interval = (float) durationTicks / (float) pulseCount;
            return interval * (zeroBasedPulseIndex + 1) - interval * 0.5F;
        }
    }
}
