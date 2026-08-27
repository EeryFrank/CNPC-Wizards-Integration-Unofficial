package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;

/** Caller-controlled power scales for bubble beam's harmful and helpful impacts. */
record AquaBubbleBeamPowerPlan(
        double damageScale,
        double healingScale,
        double supportScale) {
    static AquaBubbleBeamPowerPlan from(CastOptions options) {
        Objects.requireNonNull(options, "options");
        double spellPowerScale = options.spellPowerMultiplier();
        double helpfulScale = spellPowerScale * options.healingMultiplier();
        return new AquaBubbleBeamPowerPlan(
                spellPowerScale * options.damageMultiplier(),
                helpfulScale,
                helpfulScale);
    }
}
