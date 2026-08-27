package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable strategy registry shared by every cast session. */
public final class TargetStrategyRegistry<E> {
    public static final String DIRECT = "direct";
    public static final String SELF = "self";
    public static final String NONE = "none";

    private final Map<String, TargetStrategy<E>> strategies;

    public TargetStrategyRegistry(Collection<? extends TargetStrategy<E>> strategies) {
        Objects.requireNonNull(strategies, "strategies");
        Map<String, TargetStrategy<E>> byId = new LinkedHashMap<>();
        for (TargetStrategy<E> strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            String id = normalizeId(strategy.id());
            if (byId.putIfAbsent(id, strategy) != null) {
                throw new IllegalArgumentException("Duplicate target strategy: " + id);
            }
        }
        this.strategies = Map.copyOf(byId);
    }

    public static <E> TargetStrategyRegistry<E> defaults() {
        return new TargetStrategyRegistry<>(List.of(
                new SimpleTargetStrategy<E>(DIRECT, (caster, directTarget, options) -> directTarget == null
                        ? TargetResolution.rejected(
                                CastResultCode.TARGET_NOT_FOUND,
                                "direct target strategy requires a target")
                        : TargetResolution.selected(directTarget)),
                new SimpleTargetStrategy<E>(SELF, (caster, directTarget, options) ->
                        TargetResolution.selected(caster)),
                new SimpleTargetStrategy<E>(NONE, (caster, directTarget, options) ->
                        TargetResolution.withoutEntityTarget())));
    }

    public Optional<TargetStrategy<E>> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(id.trim()));
    }

    public Set<String> ids() {
        return strategies.keySet();
    }

    private static String normalizeId(String value) {
        Objects.requireNonNull(value, "strategy id");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("strategy id must not be blank");
        }
        return normalized;
    }

    @FunctionalInterface
    private interface Resolver<E> {
        TargetResolution<E> resolve(E caster, E directTarget, CastOptions options);
    }

    private record SimpleTargetStrategy<E>(String id, Resolver<E> resolver) implements TargetStrategy<E> {
        private SimpleTargetStrategy {
            id = normalizeId(id);
            Objects.requireNonNull(resolver, "resolver");
        }

        @Override
        public TargetResolution<E> resolve(E caster, E directTarget, CastOptions options) {
            return resolver.resolve(caster, directTarget, options);
        }
    }
}
