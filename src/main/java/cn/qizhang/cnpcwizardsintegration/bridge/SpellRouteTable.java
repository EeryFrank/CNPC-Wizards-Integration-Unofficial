package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.util.Identifier;

/** Routes validated session inputs to one spell-specific adapter. */
final class SpellRouteTable<E> {
    private final Map<Identifier, SpellRoute<E>> routes;

    private SpellRouteTable(Map<Identifier, SpellRoute<E>> routes) {
        this.routes = Map.copyOf(routes);
    }

    static <E> Builder<E> builder() {
        return new Builder<>();
    }

    CastExecutionOutcome dispatch(String traceId, E caster, E target, CastOptions options) {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(options, "options");
        SpellRoute<E> route = routes.get(options.spellId());
        if (route == null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    "No spell route is registered for " + options.spellId());
        }
        return Objects.requireNonNull(
                route.cast(traceId, caster, target, options),
                "spell route returned null");
    }

    @FunctionalInterface
    interface SpellRoute<E> {
        CastExecutionOutcome cast(String traceId, E caster, E target, CastOptions options);
    }

    static final class Builder<E> {
        private final Map<Identifier, SpellRoute<E>> routes = new LinkedHashMap<>();

        Builder<E> add(Identifier spellId, SpellRoute<E> route) {
            Objects.requireNonNull(spellId, "spellId");
            Objects.requireNonNull(route, "route");
            if (routes.putIfAbsent(spellId, route) != null) {
                throw new IllegalArgumentException("Duplicate spell route: " + spellId);
            }
            return this;
        }

        SpellRouteTable<E> build() {
            return new SpellRouteTable<>(routes);
        }
    }
}
