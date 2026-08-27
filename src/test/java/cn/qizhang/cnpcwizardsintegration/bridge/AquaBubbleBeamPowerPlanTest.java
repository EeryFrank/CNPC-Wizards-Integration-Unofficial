package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import org.junit.jupiter.api.Test;

final class AquaBubbleBeamPowerPlanTest {
    @Test
    void callerDamageAndHealingMultipliersRemainIndependent() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:aqua_bubble_beam")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(1.6D)
                .healingMultiplier(0.4D)
                .build();

        AquaBubbleBeamPowerPlan plan = AquaBubbleBeamPowerPlan.from(options);

        assertEquals(2.0D, plan.damageScale());
        assertEquals(0.5D, plan.healingScale());
        assertEquals(0.5D, plan.supportScale());
    }

    @Test
    void disablingDamageDoesNotDisableHelpfulImpacts() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:aqua_bubble_beam")
                .spellPowerMultiplier(2.0D)
                .damageMultiplier(0.0D)
                .healingMultiplier(1.5D)
                .build();

        AquaBubbleBeamPowerPlan plan = AquaBubbleBeamPowerPlan.from(options);

        assertEquals(0.0D, plan.damageScale());
        assertEquals(3.0D, plan.healingScale());
        assertEquals(3.0D, plan.supportScale());
    }
}
