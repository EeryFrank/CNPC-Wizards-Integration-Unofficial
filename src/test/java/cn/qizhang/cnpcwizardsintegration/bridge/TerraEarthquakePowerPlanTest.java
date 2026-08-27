package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import org.junit.jupiter.api.Test;

final class TerraEarthquakePowerPlanTest {
    @Test
    void callerSpellPowerAndDamageMultipliersComposeForTheSpawnContext() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:terra_earthquake")
                .targetStrategyId("none")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(1.6D)
                .healingMultiplier(0.4D)
                .build();

        TerraEarthquakePowerPlan plan = TerraEarthquakePowerPlan.from(options);

        assertEquals(2.0D, plan.damageScale());
    }

    @Test
    void healingMultiplierDoesNotAffectTheHarmfulEarthquakeScale() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:terra_earthquake")
                .targetStrategyId("none")
                .spellPowerMultiplier(0.5D)
                .damageMultiplier(1.5D)
                .healingMultiplier(0.0D)
                .build();

        assertEquals(0.75D, TerraEarthquakePowerPlan.from(options).damageScale());
    }
}
