package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResult;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import cn.qizhang.cnpcwizardsintegration.api.PhaseOneSpellIds;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class PhaseOneConfigurationRegressionTest {
    private static final List<CastTraceStage> ACCEPTED_STAGES = List.of(
            CastTraceStage.RECEIVED,
            CastTraceStage.VALIDATED,
            CastTraceStage.TARGET_RESOLVED,
            CastTraceStage.DISPATCHING,
            CastTraceStage.ACCEPTED);

    @Test
    void allPhaseOneRoutesPreserveDistinctCallerConfigurationCooldownAndTrace() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000101");
        TestEntity directTarget = entity("00000000-0000-0000-0000-000000000102");
        List<CastOptions> configurations = List.of(
                configured(PhaseOneSpellIds.FIREBALL, "direct", 1.11D, 1.21D, 0.31D, 17),
                configured(PhaseOneSpellIds.FROST_BLIZZARD, "direct", 1.12D, 1.22D, 0.32D, 23),
                configured(PhaseOneSpellIds.AQUA_BUBBLE_BEAM, "direct", 1.13D, 1.23D, 0.33D, 29),
                configured(PhaseOneSpellIds.TERRA_EARTHQUAKE, "none", 1.14D, 1.24D, 0.34D, 31));
        Map<Identifier, CastOptions> dispatchedOptions = new LinkedHashMap<>();
        Map<Identifier, TestEntity> dispatchedTargets = new LinkedHashMap<>();
        SpellRouteTable.Builder<TestEntity> routeBuilder = SpellRouteTable.builder();
        for (Identifier spellId : PhaseOneSpellIds.ALL) {
            routeBuilder.add(spellId, (traceId, actualCaster, actualTarget, actualOptions) -> {
                assertSame(caster, actualCaster);
                dispatchedOptions.put(actualOptions.spellId(), actualOptions);
                dispatchedTargets.put(actualOptions.spellId(), actualTarget);
                return CastExecutionOutcome.accepted();
            });
        }

        List<CastTraceEvent> traces = new ArrayList<>();
        AtomicInteger traceSequence = new AtomicInteger();
        AtomicLong tick = new AtomicLong(500L);
        CastSessionFlow<TestEntity> session = new CastSessionFlow<>(
                routeBuilder.build()::dispatch,
                TargetStrategyRegistry.defaults(),
                new CooldownTracker(),
                new CastRecursionGuard(),
                TestEntity::id,
                TestEntity::player,
                ignored -> tick.get(),
                () -> "config-trace-" + traceSequence.incrementAndGet(),
                traces::add);

        Map<Identifier, CastResult> acceptedResults = new LinkedHashMap<>();
        for (CastOptions configuration : configurations) {
            CastResult result = session.cast(caster, directTarget, configuration);
            assertTrue(result.accepted());
            acceptedResults.put(configuration.spellId(), result);
            assertSame(configuration, dispatchedOptions.get(configuration.spellId()));
            if (configuration.spellId().equals(PhaseOneSpellIds.TERRA_EARTHQUAKE)) {
                assertNull(dispatchedTargets.get(configuration.spellId()));
            }
            else {
                assertSame(directTarget, dispatchedTargets.get(configuration.spellId()));
            }
        }

        for (CastOptions configuration : configurations) {
            CastResult blocked = session.cast(caster, directTarget, configuration);
            assertEquals(CastResultCode.COOLDOWN_ACTIVE, blocked.code());
            CastTraceEvent blockedTrace = finalTrace(traces, blocked.traceId());
            assertEquals(
                    Integer.toString(configuration.cooldownTicks()),
                    blockedTrace.fields().get("cooldown_remaining_ticks"));

            CastResult accepted = acceptedResults.get(configuration.spellId());
            List<CastTraceEvent> acceptedTrace = trace(traces, accepted.traceId());
            assertEquals(ACCEPTED_STAGES, acceptedTrace.stream().map(CastTraceEvent::stage).toList());
            CastTraceEvent finalAcceptedTrace = acceptedTrace.getLast();
            assertConfigurationFields(configuration, finalAcceptedTrace);
            System.out.printf(
                    "CONFIG_PROOF spell=%s trace=%s target_strategy=%s "
                            + "spell_power_multiplier=%s damage_multiplier=%s healing_multiplier=%s "
                            + "cooldown_ticks=%d cooldown_blocked=true stages=%s route=SpellRouteTable%n",
                    configuration.spellId(),
                    accepted.traceId(),
                    configuration.targetStrategyId(),
                    Double.toString(configuration.spellPowerMultiplier()),
                    Double.toString(configuration.damageMultiplier()),
                    Double.toString(configuration.healingMultiplier()),
                    configuration.cooldownTicks(),
                    String.join(">", ACCEPTED_STAGES.stream().map(Enum::name).toList()));
        }
    }

    private static CastOptions configured(
            Identifier spellId,
            String targetStrategy,
            double spellPowerMultiplier,
            double damageMultiplier,
            double healingMultiplier,
            int cooldownTicks) {
        return CastOptions.builder(spellId)
                .targetStrategyId(targetStrategy)
                .spellPowerMultiplier(spellPowerMultiplier)
                .damageMultiplier(damageMultiplier)
                .healingMultiplier(healingMultiplier)
                .cooldownTicks(cooldownTicks)
                .build();
    }

    private static void assertConfigurationFields(
            CastOptions configuration,
            CastTraceEvent trace) {
        assertEquals(configuration.spellId().toString(), trace.fields().get("spell_id"));
        assertEquals(configuration.targetStrategyId(), trace.fields().get("target_strategy"));
        assertEquals(
                Double.toString(configuration.spellPowerMultiplier()),
                trace.fields().get("spell_power_multiplier"));
        assertEquals(
                Double.toString(configuration.damageMultiplier()),
                trace.fields().get("damage_multiplier"));
        assertEquals(
                Double.toString(configuration.healingMultiplier()),
                trace.fields().get("healing_multiplier"));
        assertEquals(
                Integer.toString(configuration.cooldownTicks()),
                trace.fields().get("cooldown_ticks"));
    }

    private static List<CastTraceEvent> trace(List<CastTraceEvent> traces, String traceId) {
        return traces.stream().filter(event -> event.traceId().equals(traceId)).toList();
    }

    private static CastTraceEvent finalTrace(List<CastTraceEvent> traces, String traceId) {
        return trace(traces, traceId).getLast();
    }

    private static TestEntity entity(String uuid) {
        return new TestEntity(UUID.fromString(uuid), false);
    }

    private record TestEntity(UUID id, boolean player) { }
}
