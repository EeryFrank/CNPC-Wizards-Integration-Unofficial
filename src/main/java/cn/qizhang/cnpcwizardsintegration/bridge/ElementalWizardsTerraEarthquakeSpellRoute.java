package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;
import net.minecraft.entity.LivingEntity;

/** Full-charge, caster-centered route for {@code elemental_wizards_rpg:terra_earthquake}. */
final class ElementalWizardsTerraEarthquakeSpellRoute
        implements SpellRouteTable.SpellRoute<LivingEntity> {
    private final TerraEarthquakeChargeManager charges;

    ElementalWizardsTerraEarthquakeSpellRoute(TerraEarthquakeChargeManager charges) {
        this.charges = Objects.requireNonNull(charges, "charges");
    }

    @Override
    public CastExecutionOutcome cast(
            String traceId,
            LivingEntity caster,
            LivingEntity target,
            CastOptions options) {
        return charges.schedule(new CastExecution(traceId, caster, target, options));
    }
}
