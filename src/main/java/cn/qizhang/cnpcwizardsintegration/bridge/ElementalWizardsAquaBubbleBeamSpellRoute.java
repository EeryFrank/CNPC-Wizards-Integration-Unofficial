package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;
import net.minecraft.entity.LivingEntity;

/** Continuing area-channel path for {@code elemental_wizards_rpg:aqua_bubble_beam}. */
final class ElementalWizardsAquaBubbleBeamSpellRoute
        implements SpellRouteTable.SpellRoute<LivingEntity> {
    private final AquaBubbleBeamChannelManager channels;

    ElementalWizardsAquaBubbleBeamSpellRoute(AquaBubbleBeamChannelManager channels) {
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
