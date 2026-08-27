package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResult;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.util.Identifier;

/** Pure session state machine shared by the Minecraft facade and unit-test fixtures. */
final class CastSessionFlow<E> {
    private final Executor<E> executor;
    private final TargetStrategyRegistry<E> targetStrategies;
    private final CooldownTracker cooldowns;
    private final CastRecursionGuard recursionGuard;
    private final Function<E, UUID> entityId;
    private final Predicate<E> playerCheck;
    private final ToLongFunction<E> tickSource;
    private final Supplier<String> traceIdSource;
    private final CastTraceSink traceSink;

    CastSessionFlow(
            Executor<E> executor,
            TargetStrategyRegistry<E> targetStrategies,
            CooldownTracker cooldowns,
            CastRecursionGuard recursionGuard,
            Function<E, UUID> entityId,
            Predicate<E> playerCheck,
            ToLongFunction<E> tickSource,
            Supplier<String> traceIdSource,
            CastTraceSink traceSink) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.targetStrategies = Objects.requireNonNull(targetStrategies, "targetStrategies");
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.recursionGuard = Objects.requireNonNull(recursionGuard, "recursionGuard");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.playerCheck = Objects.requireNonNull(playerCheck, "playerCheck");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.traceIdSource = Objects.requireNonNull(traceIdSource, "traceIdSource");
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    Set<String> targetStrategyIds() {
        return targetStrategies.ids();
    }

    CastResult cast(E caster, E directTarget, CastOptions options) {
        String traceId = requireTraceId(traceIdSource.get());
        Map<String, String> fields = baseFields(caster, directTarget, options);
        trace(traceId, CastTraceStage.RECEIVED, fields);

        if (caster == null) {
            return reject(traceId, CastResultCode.INVALID_REQUEST, "caster is required", fields);
        }
        if (playerCheck.test(caster)) {
            return reject(traceId, CastResultCode.INVALID_REQUEST, "caster must be a non-player entity", fields);
        }
        if (options == null) {
            return reject(traceId, CastResultCode.INVALID_REQUEST, "options are required", fields);
        }

        TargetStrategy<E> strategy = targetStrategies.find(options.targetStrategyId()).orElse(null);
        if (strategy == null) {
            return reject(
                    traceId,
                    CastResultCode.INVALID_REQUEST,
                    "unknown target strategy: " + options.targetStrategyId(),
                    fields);
        }

        UUID casterId = requiredEntityId(caster);
        Identifier spellId = options.spellId();
        fields.put("caster_id", casterId.toString());
        fields.put("spell_id", spellId.toString());
        fields.put("target_strategy", strategy.id());
        trace(traceId, CastTraceStage.VALIDATED, fields);

        long currentTick;
        try {
            currentTick = tickSource.applyAsLong(caster);
        }
        catch (RuntimeException exception) {
            return fail(traceId, exception, fields);
        }

        long remaining = cooldowns.remainingTicks(casterId, spellId, currentTick);
        if (remaining > 0L) {
            fields.put("cooldown_remaining_ticks", Long.toString(remaining));
            return reject(
                    traceId,
                    CastResultCode.COOLDOWN_ACTIVE,
                    remaining + " cooldown ticks remaining",
                    fields);
        }

        CastRecursionGuard.EntryResult entry = recursionGuard.tryEnter(casterId, spellId);
        if (entry == CastRecursionGuard.EntryResult.RECURSION_BLOCKED) {
            return reject(
                    traceId,
                    CastResultCode.RECURSION_BLOCKED,
                    "recursive cast blocked for " + spellId,
                    fields);
        }
        if (entry == CastRecursionGuard.EntryResult.CASTER_BUSY) {
            return reject(
                    traceId,
                    CastResultCode.CASTER_BUSY,
                    "caster already has an active cast session",
                    fields);
        }

        try {
            TargetResolution<E> resolution = Objects.requireNonNull(
                    strategy.resolve(caster, directTarget, options),
                    "target strategy returned null");
            if (!resolution.resolved()) {
                return reject(traceId, resolution.failureCode(), resolution.diagnostic(), fields);
            }

            E resolvedTarget = resolution.target();
            fields.put("resolved_target_id", resolvedTarget == null
                    ? "none"
                    : requiredEntityId(resolvedTarget).toString());
            trace(traceId, CastTraceStage.TARGET_RESOLVED, fields);
            trace(traceId, CastTraceStage.DISPATCHING, fields);

            CastExecutionOutcome outcome = Objects.requireNonNull(
                    executor.execute(traceId, caster, resolvedTarget, options),
                    "spell executor returned null");
            if (!outcome.isAccepted()) {
                return reject(traceId, outcome.code(), outcome.diagnostic(), fields);
            }

            long acceptedTick = tickSource.applyAsLong(caster);
            cooldowns.start(casterId, spellId, acceptedTick, options.cooldownTicks());
            fields.put("cooldown_ticks", Integer.toString(options.cooldownTicks()));
            trace(traceId, CastTraceStage.ACCEPTED, fields);
            return CastResult.accepted(traceId);
        }
        catch (RuntimeException exception) {
            return fail(traceId, exception, fields);
        }
        finally {
            recursionGuard.exit(casterId, spellId);
        }
    }

    private CastResult fail(String traceId, RuntimeException exception, Map<String, String> fields) {
        String diagnostic = "bridge failure: " + exception.getClass().getSimpleName();
        fields.put("error_type", exception.getClass().getName());
        trace(traceId, CastTraceStage.FAILED, fields);
        return reject(traceId, CastResultCode.INTERNAL_ERROR, diagnostic, fields);
    }

    private CastResult reject(
            String traceId,
            CastResultCode code,
            String diagnostic,
            Map<String, String> fields) {
        fields.put("result_code", code.name());
        if (diagnostic != null && !diagnostic.isBlank()) {
            fields.put("diagnostic", diagnostic.trim());
        }
        trace(traceId, CastTraceStage.REJECTED, fields);
        return CastResult.rejected(code, traceId, diagnostic);
    }

    private void trace(String traceId, CastTraceStage stage, Map<String, String> fields) {
        try {
            traceSink.record(new CastTraceEvent(traceId, stage, fields));
        }
        catch (RuntimeException ignored) {
            // Diagnostics must never break or change a cast result.
        }
    }

    private Map<String, String> baseFields(E caster, E directTarget, CastOptions options) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (caster != null) {
            fields.put("caster_id", requiredEntityId(caster).toString());
        }
        if (directTarget != null) {
            fields.put("direct_target_id", requiredEntityId(directTarget).toString());
        }
        if (options != null) {
            fields.put("spell_id", options.spellId().toString());
            fields.put("target_strategy", options.targetStrategyId());
            fields.put("spell_power_multiplier", Double.toString(options.spellPowerMultiplier()));
            fields.put("damage_multiplier", Double.toString(options.damageMultiplier()));
            fields.put("healing_multiplier", Double.toString(options.healingMultiplier()));
            fields.put("cooldown_ticks", Integer.toString(options.cooldownTicks()));
        }
        return fields;
    }

    private UUID requiredEntityId(E entity) {
        return Objects.requireNonNull(entityId.apply(entity), "entityId returned null");
    }

    private static String requireTraceId(String value) {
        Objects.requireNonNull(value, "traceIdSource returned null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("traceIdSource returned a blank value");
        }
        return normalized;
    }

    @FunctionalInterface
    interface Executor<E> {
        CastExecutionOutcome execute(String traceId, E caster, E target, CastOptions options);
    }
}
