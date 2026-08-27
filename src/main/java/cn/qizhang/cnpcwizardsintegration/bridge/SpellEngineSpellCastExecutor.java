package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.PhaseOneSpellIds;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import net.minecraft.entity.LivingEntity;

/** Spell Engine-backed executor containing only adapters whose schedule nodes are complete. */
public final class SpellEngineSpellCastExecutor implements SpellCastExecutor {
    private final FrostBlizzardChannelManager frostBlizzardChannels;
    private final AquaBubbleBeamChannelManager aquaBubbleBeamChannels;
    private final TerraEarthquakeChargeManager terraEarthquakeCharges;
    private final SpellRouteTable<LivingEntity> routes;

    public SpellEngineSpellCastExecutor(CastTraceSink traceSink) {
        this.frostBlizzardChannels = new FrostBlizzardChannelManager(traceSink);
        this.aquaBubbleBeamChannels = new AquaBubbleBeamChannelManager(traceSink);
        this.terraEarthquakeCharges = new TerraEarthquakeChargeManager(traceSink);
        this.routes = SpellRouteTable.<LivingEntity>builder()
                .add(PhaseOneSpellIds.FIREBALL, new WizardsFireballSpellRoute())
                .add(PhaseOneSpellIds.FROST_BLIZZARD,
                        new WizardsFrostBlizzardSpellRoute(frostBlizzardChannels))
                .add(PhaseOneSpellIds.AQUA_BUBBLE_BEAM,
                        new ElementalWizardsAquaBubbleBeamSpellRoute(aquaBubbleBeamChannels))
                .add(PhaseOneSpellIds.TERRA_EARTHQUAKE,
                        new ElementalWizardsTerraEarthquakeSpellRoute(terraEarthquakeCharges))
                .build();
    }

    @Override
    public void initialize() {
        frostBlizzardChannels.initialize();
        aquaBubbleBeamChannels.initialize();
        terraEarthquakeCharges.initialize();
    }

    @Override
    public CastExecutionOutcome execute(CastExecution execution) {
        if (frostBlizzardChannels.isActive(execution.caster())
                || aquaBubbleBeamChannels.isActive(execution.caster())
                || terraEarthquakeCharges.isActive(execution.caster())) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.CASTER_BUSY,
                    "caster already has an active channeled spell");
        }
        return routes.dispatch(
                execution.traceId(),
                execution.caster(),
                execution.target(),
                execution.options());
    }

    @Override
    public boolean cancel(LivingEntity caster) {
        return frostBlizzardChannels.cancel(caster, "caller_requested")
                || aquaBubbleBeamChannels.cancel(caster, "caller_requested")
                || terraEarthquakeCharges.cancel(caster, "caller_requested");
    }
}
