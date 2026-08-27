package cn.qizhang.cnpcwizardsintegration.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure Java scheduler for one delayed full-charge release per caster. */
final class ChargeCastFlow<K, V> {
    private final Map<K, ActiveCharge<V>> active = new LinkedHashMap<>();

    synchronized StartResult start(K key, V payload, long startedAt, int durationTicks) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("durationTicks must be positive");
        }
        if (active.containsKey(key)) {
            return StartResult.BUSY;
        }
        active.put(key, new ActiveCharge<>(payload, startedAt, durationTicks));
        return StartResult.STARTED;
    }

    synchronized boolean isActive(K key) {
        return active.containsKey(key);
    }

    synchronized List<ActiveEntry<K, V>> activeEntries() {
        List<ActiveEntry<K, V>> entries = new ArrayList<>(active.size());
        active.forEach((key, charge) -> entries.add(new ActiveEntry<>(key, charge.payload)));
        return List.copyOf(entries);
    }

    synchronized Optional<Cancellation<V>> cancel(K key) {
        ActiveCharge<V> charge = active.remove(key);
        if (charge == null) {
            return Optional.empty();
        }
        return Optional.of(new Cancellation<>(charge.payload));
    }

    synchronized Completion<V> tick(K key, long currentTick) {
        ActiveCharge<V> charge = active.get(key);
        if (charge == null) {
            return null;
        }
        long elapsedTicks = Math.max(0L, currentTick - charge.startedAt);
        if (elapsedTicks < charge.durationTicks) {
            return null;
        }
        active.remove(key);
        return new Completion<>(charge.payload, elapsedTicks);
    }

    enum StartResult {
        STARTED,
        BUSY
    }

    record ActiveEntry<K, V>(K key, V payload) {
    }

    record Cancellation<V>(V payload) {
    }

    record Completion<V>(V payload, long elapsedTicks) {
    }

    private record ActiveCharge<V>(V payload, long startedAt, int durationTicks) {
    }
}
