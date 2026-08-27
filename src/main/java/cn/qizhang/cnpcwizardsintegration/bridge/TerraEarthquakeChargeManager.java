package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_power.api.SpellPower;

/** Server-tick owner for one full-charge earthquake release per NPC caster. */
final class TerraEarthquakeChargeManager {
    private static final String EARTHQUAKE_ENTITY_ID = "elemental_wizards_rpg:earthquake";
    private static final float FULL_CHARGE_RATIO = 1.0F;

    private final CastTraceSink traceSink;
    private final ChargeCastFlow<UUID, ActiveCharge> flow = new ChargeCastFlow<>();
    private final AtomicBoolean initialized = new AtomicBoolean();

    TerraEarthquakeChargeManager(CastTraceSink traceSink) {
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    void initialize() {
        if (initialized.compareAndSet(false, true)) {
            ServerTickEvents.END_WORLD_TICK.register(this::tick);
            ServerLifecycleEvents.SERVER_STOPPING.register(server -> cancelAll("server_stopping"));
        }
    }

    boolean isActive(LivingEntity caster) {
        return caster != null && flow.isActive(caster.getUuid());
    }

    CastExecutionOutcome schedule(CastExecution execution) {
        LivingEntity caster = execution.caster();
        CastOptions options = execution.options();
        if (!TargetStrategyRegistry.NONE.equals(options.targetStrategyId())
                || execution.target() != null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.INVALID_REQUEST,
                    "elemental_wizards_rpg:terra_earthquake requires the none target strategy");
        }
        if (!(caster.getWorld() instanceof ServerWorld world)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.INVALID_REQUEST,
                    "elemental_wizards_rpg:terra_earthquake must be dispatched on the server");
        }

        RegistryEntry<Spell> spellEntry = SpellRegistry.from(world)
                .getEntry(options.spellId())
                .orElse(null);
        if (spellEntry == null || spellEntry.value().active == null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    "Spell Engine registry does not contain an active " + options.spellId());
        }

        Spell spell = spellEntry.value();
        Spell.Active.Cast.Charge chargeConfig = SpellHelper.chargeConfigOf(spell);
        if (spell.active.cast == null
                || spell.active.cast.resolvedType() != Spell.Active.Cast.Type.CHARGE
                || chargeConfig == null
                || chargeConfig.curve == null
                || chargeConfig.bonus == null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    options.spellId() + " is not a configured Spell Engine CHARGE spell");
        }
        if (!hasEarthquakeSpawn(spell)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    options.spellId() + " no longer spawns " + EARTHQUAKE_ENTITY_ID);
        }

        SpellCast.Duration duration = SpellHelper.getCastTimeDetails(caster, spell);
        int durationTicks = duration.length();
        float chargeOutput = chargeConfig.curve.apply(FULL_CHARGE_RATIO);
        if (durationTicks <= 0
                || !Float.isFinite(chargeOutput)
                || chargeOutput < chargeConfig.min_release_ratio) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    options.spellId() + " has no usable full-charge duration or modifier");
        }

        Spell.Modifier chargeModifier = SpellModifiers.scaledBy(chargeConfig.bonus, chargeOutput);
        double effectiveRange = SpellHelper.getRange(caster, spellEntry, chargeModifier)
                * caster.getScale();
        TerraEarthquakePowerPlan powerPlan = TerraEarthquakePowerPlan.from(options);
        ActiveCharge charge = new ActiveCharge(
                execution.traceId(),
                caster,
                options,
                powerPlan,
                world,
                spellEntry,
                durationTicks,
                chargeOutput,
                chargeModifier,
                effectiveRange);
        ChargeCastFlow.StartResult result = flow.start(
                caster.getUuid(),
                charge,
                world.getTime(),
                durationTicks);
        if (result == ChargeCastFlow.StartResult.BUSY) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.CASTER_BUSY,
                    "caster already has an active charged spell");
        }

        trace(charge, CastTraceStage.CHARGE_SCHEDULED, Map.of(
                "duration_ticks", Integer.toString(durationTicks),
                "cast_speed", Float.toString(duration.speed()),
                "charge_ratio", Float.toString(FULL_CHARGE_RATIO),
                "charge_output", Float.toString(chargeOutput),
                "base_range", Float.toString(spell.range),
                "charge_range_add", Float.toString(chargeModifier.range_add),
                "effective_range", Double.toString(effectiveRange),
                "damage_scale", Double.toString(powerPlan.damageScale())));
        return CastExecutionOutcome.accepted();
    }

    boolean cancel(LivingEntity caster, String reason) {
        if (caster == null) {
            return false;
        }
        return flow.cancel(caster.getUuid())
                .map(cancelled -> {
                    trace(cancelled.payload(), CastTraceStage.CHARGE_CANCELLED, Map.of(
                            "reason", reason));
                    return true;
                })
                .orElse(false);
    }

    private void cancelAll(String reason) {
        for (ChargeCastFlow.ActiveEntry<UUID, ActiveCharge> entry : flow.activeEntries()) {
            cancel(entry.payload().caster(), reason);
        }
    }

    private void tick(ServerWorld world) {
        for (ChargeCastFlow.ActiveEntry<UUID, ActiveCharge> entry : flow.activeEntries()) {
            ActiveCharge charge = entry.payload();
            if (charge.world() != world) {
                continue;
            }

            String cancellation = invalidationReason(charge);
            if (cancellation != null) {
                cancel(charge.caster(), cancellation);
                continue;
            }

            ChargeCastFlow.Completion<ActiveCharge> completion = flow.tick(
                    entry.key(),
                    world.getTime());
            if (completion == null) {
                continue;
            }

            try {
                if (release(charge)) {
                    trace(charge, CastTraceStage.CHARGE_RELEASED, Map.of(
                            "elapsed_ticks", Long.toString(completion.elapsedTicks()),
                            "spawn_entity", EARTHQUAKE_ENTITY_ID,
                            "center", "caster",
                            "effective_range", Double.toString(charge.effectiveRange()),
                            "damage_scale", Double.toString(charge.powerPlan().damageScale())));
                }
                else {
                    trace(charge, CastTraceStage.CHARGE_FAILED, Map.of(
                            "reason", "spell_engine_spawn_rejected"));
                }
            }
            catch (RuntimeException exception) {
                trace(charge, CastTraceStage.CHARGE_FAILED, Map.of(
                        "reason", "spawn_exception",
                        "error_type", exception.getClass().getName()));
            }
        }
    }

    private static boolean release(ActiveCharge charge) {
        Spell spell = charge.spellEntry().value();
        SpellPower.Result basePower = SpellPower.getSpellPower(spell.school, charge.caster());
        SpellPower.Result scaledPower = new SpellPower.Result(
                basePower.school(),
                basePower.baseValue() * charge.powerPlan().damageScale(),
                basePower.criticalChance(),
                basePower.criticalDamage());
        SpellHelper.ImpactContext context = new SpellHelper.ImpactContext(
                1.0F,
                1.0F,
                charge.caster().getPos(),
                scaledPower,
                SpellHelper.focusMode(spell),
                0)
                .chargeModifier(charge.chargeModifier())
                .charge(charge.chargeOutput());
        return SpellHelper.performImpacts(
                charge.world(),
                charge.caster(),
                charge.caster(),
                charge.caster(),
                charge.spellEntry(),
                spell.impacts,
                context,
                true,
                Spell.Impact.Action.Type.SPAWN);
    }

    private static String invalidationReason(ActiveCharge charge) {
        if (!charge.caster().isAlive() || charge.caster().isRemoved()) {
            return "caster_unavailable";
        }
        if (charge.caster().getWorld() != charge.world()) {
            return "world_changed";
        }
        return null;
    }

    private static boolean hasEarthquakeSpawn(Spell spell) {
        if (spell.impacts == null) {
            return false;
        }
        for (Spell.Impact impact : spell.impacts) {
            if (impact == null
                    || impact.action == null
                    || impact.action.type != Spell.Impact.Action.Type.SPAWN
                    || impact.action.spawns == null) {
                continue;
            }
            for (Spell.Impact.Action.Spawn spawn : impact.action.spawns) {
                if (spawn != null
                        && EARTHQUAKE_ENTITY_ID.equals(spawn.entity_type_id)
                        && spawn.time_to_live_seconds > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void trace(ActiveCharge charge, CastTraceStage stage, Map<String, String> extraFields) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("caster_id", charge.caster().getUuidAsString());
        fields.put("target_id", "none");
        fields.put("spell_id", charge.options().spellId().toString());
        fields.put("target_strategy", TargetStrategyRegistry.NONE);
        fields.put("lifecycle", "charge");
        fields.put("aoe_center", "caster");
        fields.putAll(extraFields);
        try {
            traceSink.record(new CastTraceEvent(charge.traceId(), stage, fields));
        }
        catch (RuntimeException ignored) {
            // Diagnostics must not interrupt an active charge or release.
        }
    }

    private record ActiveCharge(
            String traceId,
            LivingEntity caster,
            CastOptions options,
            TerraEarthquakePowerPlan powerPlan,
            ServerWorld world,
            RegistryEntry<Spell> spellEntry,
            int durationTicks,
            float chargeOutput,
            Spell.Modifier chargeModifier,
            double effectiveRange) {
    }
}
