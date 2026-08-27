package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class TargetStrategyRegistryTest {
    @Test
    void exposesOnlyTheBuiltInStrategySkeletons() {
        TargetStrategyRegistry<String> registry = TargetStrategyRegistry.defaults();

        assertEquals(Set.of("direct", "self", "none"), registry.ids());
        assertFalse(registry.find("nearest_enemy").isPresent());
    }

    @Test
    void rejectsDuplicateStrategyIds() {
        TargetStrategy<String> first = named("custom");
        TargetStrategy<String> second = named("custom");

        assertThrows(
                IllegalArgumentException.class,
                () -> new TargetStrategyRegistry<>(List.of(first, second)));
    }

    @Test
    void builtInsResolveDirectSelfAndDeliberateNoTarget() {
        TargetStrategyRegistry<String> registry = TargetStrategyRegistry.defaults();
        String caster = new String("caster");
        String target = new String("target");

        TargetResolution<String> direct = registry.find("direct").orElseThrow().resolve(
                caster,
                target,
                CastOptions.defaults("wizards:fireball"));
        TargetResolution<String> self = registry.find("self").orElseThrow().resolve(
                caster,
                target,
                CastOptions.builder("wizards:fireball").targetStrategyId("self").build());
        TargetResolution<String> none = registry.find("none").orElseThrow().resolve(
                caster,
                target,
                CastOptions.builder("wizards:fireball").targetStrategyId("none").build());

        assertSame(target, direct.target());
        assertSame(caster, self.target());
        assertTrue(none.resolved());
        assertNull(none.target());
    }

    private static TargetStrategy<String> named(String id) {
        return new TargetStrategy<>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public TargetResolution<String> resolve(
                    String caster,
                    String directTarget,
                    cn.qizhang.cnpcwizardsintegration.api.CastOptions options) {
                return TargetResolution.withoutEntityTarget();
            }
        };
    }
}
