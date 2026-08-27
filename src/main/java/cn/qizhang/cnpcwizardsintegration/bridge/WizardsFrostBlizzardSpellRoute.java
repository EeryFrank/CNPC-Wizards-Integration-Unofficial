package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;
import net.minecraft.entity.LivingEntity;

/** Continuing, explicit-target channel path for {@code wizards:frost_blizzard}. */
final class WizardsFrostBlizzardSpellRoute implements SpellRouteTable.SpellRoute<LivingEntity> {
    private final FrostBlizzardChannelManager channels;

    WizardsFrostBlizzardSpellRoute(FrostBlizzardChannelManager channels) {
        this.channels = Objects.requireNonNull(channels, "channels");
    }

    @Override
    public CastExecutionOutcome cast(
            String traceId,
            LivingEntity caster,
            LivingEntity target,
            CastOptions options) {
        return channels.schedule(new CastExecution(traceId, caster, target, options));
    }
}
