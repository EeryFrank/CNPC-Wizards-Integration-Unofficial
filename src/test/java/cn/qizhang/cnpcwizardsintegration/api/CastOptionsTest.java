package cn.qizhang.cnpcwizardsintegration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CastOptionsTest {
    @Test
    void defaultsUseNeutralMultipliersAndDirectTargeting() {
        CastOptions options = CastOptions.defaults("wizards:fireball");

        assertEquals("wizards:fireball", options.spellId().toString());
        assertEquals("direct", options.targetStrategyId());
        assertEquals(1.0D, options.spellPowerMultiplier());
        assertEquals(1.0D, options.damageMultiplier());
        assertEquals(1.0D, options.healingMultiplier());
        assertEquals(0, options.cooldownTicks());
    }

    @Test
    void builderCarriesAllConfigurableValues() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:aqua_bubble_beam")
                .targetStrategyId("nearest_ally_or_enemy")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(0.75D)
                .healingMultiplier(1.5D)
                .cooldownTicks(80)
                .build();

        assertEquals("nearest_ally_or_enemy", options.targetStrategyId());
        assertEquals(1.25D, options.spellPowerMultiplier());
        assertEquals(0.75D, options.damageMultiplier());
        assertEquals(1.5D, options.healingMultiplier());
        assertEquals(80, options.cooldownTicks());
    }

    @Test
    void invalidConfigurationFailsAtTheBoundary() {
        assertThrows(IllegalArgumentException.class, () -> CastOptions.defaults("not a spell id"));
        assertThrows(IllegalArgumentException.class, () -> CastOptions.defaults("fireball"));
        assertThrows(IllegalArgumentException.class, () -> CastOptions.builder("wizards:fireball")
                .targetStrategyId(" ")
                .build());
        assertThrows(IllegalArgumentException.class, () -> CastOptions.builder("wizards:fireball")
                .damageMultiplier(Double.NaN)
                .build());
        assertThrows(IllegalArgumentException.class, () -> CastOptions.builder("wizards:fireball")
                .healingMultiplier(-0.01D)
                .build());
        assertThrows(IllegalArgumentException.class, () -> CastOptions.builder("wizards:fireball")
                .spellPowerMultiplier(Double.POSITIVE_INFINITY)
                .build());
        assertThrows(IllegalArgumentException.class, () -> CastOptions.builder("wizards:fireball")
                .cooldownTicks(-1)
                .build());
    }
}
