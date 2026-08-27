package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;

/** Caller-controlled harmful power scale passed into the earthquake spawn context. */
record TerraEarthquakePowerPlan(double damageScale) {
    static TerraEarthquakePowerPlan from(CastOptions options) {
        Objects.requireNonNull(options, "options");
        return new TerraEarthquakePowerPlan(
                options.spellPowerMultiplier() * options.damageMultiplier());
    }
}
