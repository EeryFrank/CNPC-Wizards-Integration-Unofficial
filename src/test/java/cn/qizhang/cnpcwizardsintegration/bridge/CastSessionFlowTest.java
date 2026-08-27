package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResult;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import cn.qizhang.cnpcwizardsintegration.api.PhaseOneSpellIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class CastSessionFlowTest {
    @Test
    void fireballAndFrostBlizzardShareOneSessionFlowAndTraceShape() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        List<String> routeDispatches = new ArrayList<>();
        List<CastTraceEvent> traces = new ArrayList<>();
        SpellRouteTable<TestEntity> routes = SpellRouteTable.<TestEntity>builder()
                .add(PhaseOneSpellIds.FIREBALL, (traceId, actualCaster, actualTarget, options) -> {
                    routeDispatches.add(traceId + ":" + options.spellId());
                    return CastExecutionOutcome.accepted();
                })
                .add(PhaseOneSpellIds.FROST_BLIZZARD,
                        (traceId, actualCaster, actualTarget, options) -> {
                    routeDispatches.add(traceId + ":" + options.spellId());
                    return CastExecutionOutcome.accepted();
                })
                .build();
        CastSessionFlow<TestEntity> session = flow(
                routes::dispatch,
                new AtomicLong(100L),
                new SequenceTraceIds(),
                traces::add);

        CastResult fireball = session.cast(
                caster,
                target,
                CastOptions.defaults(PhaseOneSpellIds.FIREBALL.toString()));
        CastResult frostBlizzard = session.cast(
                caster,
                target,
                CastOptions.defaults(PhaseOneSpellIds.FROST_BLIZZARD.toString()));

        assertTrue(fireball.accepted());
        assertTrue(frostBlizzard.accepted());
        assertEquals(List.of(
                "trace-1:wizards:fireball",
                "trace-2:wizards:frost_blizzard"), routeDispatches);
        assertSharedTraceSequence(traces, fireball.traceId(), PhaseOneSpellIds.FIREBALL.toString());
        assertSharedTraceSequence(
                traces,
                frostBlizzard.traceId(),
                PhaseOneSpellIds.FROST_BLIZZARD.toString());
    }

    @Test
    void acceptedCastUsesOneFlowAndStartsConfiguredCooldown() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        CastOptions options = CastOptions.builder("wizards:fireball")
                .cooldownTicks(20)
                .damageMultiplier(1.5D)
                .build();
        AtomicLong tick = new AtomicLong(100L);
        AtomicInteger traceSequence = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<TestEntity> dispatchedCaster = new AtomicReference<>();
        AtomicReference<TestEntity> dispatchedTarget = new AtomicReference<>();
        AtomicReference<CastOptions> dispatchedOptions = new AtomicReference<>();
        List<CastTraceEvent> traces = new ArrayList<>();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, actualOptions) -> {
                    executions.incrementAndGet();
                    dispatchedCaster.set(actualCaster);
                    dispatchedTarget.set(actualTarget);
                    dispatchedOptions.set(actualOptions);
                    return CastExecutionOutcome.accepted();
                },
                tick,
                () -> "trace-" + traceSequence.incrementAndGet(),
                traces::add);

        CastResult first = session.cast(caster, target, options);

        assertTrue(first.accepted());
        assertEquals("trace-1", first.traceId());
        assertSame(caster, dispatchedCaster.get());
        assertSame(target, dispatchedTarget.get());
        assertSame(options, dispatchedOptions.get());
        assertEquals(List.of(
                CastTraceStage.RECEIVED,
                CastTraceStage.VALIDATED,
                CastTraceStage.TARGET_RESOLVED,
                CastTraceStage.DISPATCHING,
                CastTraceStage.ACCEPTED), traces.stream().map(CastTraceEvent::stage).toList());

        CastResult blocked = session.cast(caster, target, options);
        assertEquals(CastResultCode.COOLDOWN_ACTIVE, blocked.code());
        assertEquals(1, executions.get());

        tick.set(120L);
        CastResult afterExpiry = session.cast(caster, target, options);
        assertTrue(afterExpiry.accepted());
        assertEquals(2, executions.get());
    }

    @Test
    void directStrategyRejectsMissingTargetBeforeDispatch() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        AtomicInteger executions = new AtomicInteger();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, options) -> {
                    executions.incrementAndGet();
                    return CastExecutionOutcome.accepted();
                },
                new AtomicLong(1L),
                () -> "trace-target",
                CastTraceSink.noop());

        CastResult result = session.cast(caster, null, CastOptions.defaults("wizards:fireball"));

        assertEquals(CastResultCode.TARGET_NOT_FOUND, result.code());
        assertEquals(0, executions.get());
    }

    @Test
    void noneStrategyDispatchesEarthquakeWithoutAnEntityTarget() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity ignoredDirectTarget = entity("00000000-0000-0000-0000-000000000002");
        AtomicReference<TestEntity> dispatchedTarget = new AtomicReference<>(ignoredDirectTarget);
        AtomicReference<CastOptions> dispatchedOptions = new AtomicReference<>();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, options) -> {
                    dispatchedTarget.set(actualTarget);
                    dispatchedOptions.set(options);
                    return CastExecutionOutcome.accepted();
                },
                new AtomicLong(1L),
                () -> "trace-earthquake",
                CastTraceSink.noop());
        CastOptions options = CastOptions.builder(PhaseOneSpellIds.TERRA_EARTHQUAKE)
                .targetStrategyId("none")
                .damageMultiplier(1.5D)
                .cooldownTicks(80)
                .build();

        CastResult result = session.cast(caster, ignoredDirectTarget, options);

        assertTrue(result.accepted());
        assertEquals(null, dispatchedTarget.get());
        assertSame(options, dispatchedOptions.get());
    }

    @Test
    void sameSpellReentryIsBlockedWithoutInvokingExecutorAgain() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        CastOptions options = CastOptions.defaults("wizards:fireball");
        AtomicReference<CastSessionFlow<TestEntity>> sessionRef = new AtomicReference<>();
        AtomicReference<CastResult> nestedResult = new AtomicReference<>();
        AtomicInteger executions = new AtomicInteger();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, actualOptions) -> {
                    executions.incrementAndGet();
                    nestedResult.set(sessionRef.get().cast(caster, target, options));
                    return CastExecutionOutcome.accepted();
                },
                new AtomicLong(1L),
                new SequenceTraceIds(),
                CastTraceSink.noop());
        sessionRef.set(session);

        CastResult outerResult = session.cast(caster, target, options);

        assertTrue(outerResult.accepted());
        assertEquals(CastResultCode.RECURSION_BLOCKED, nestedResult.get().code());
        assertEquals(1, executions.get());
    }

    @Test
    void differentSpellReentryReportsCasterBusy() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        CastOptions outerOptions = CastOptions.defaults("wizards:fireball");
        CastOptions nestedOptions = CastOptions.defaults("wizards:frost_blizzard");
        AtomicReference<CastSessionFlow<TestEntity>> sessionRef = new AtomicReference<>();
        AtomicReference<CastResult> nestedResult = new AtomicReference<>();
        AtomicInteger executions = new AtomicInteger();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, actualOptions) -> {
                    executions.incrementAndGet();
                    nestedResult.set(sessionRef.get().cast(caster, target, nestedOptions));
                    return CastExecutionOutcome.accepted();
                },
                new AtomicLong(1L),
                new SequenceTraceIds(),
                CastTraceSink.noop());
        sessionRef.set(session);

        CastResult outerResult = session.cast(caster, target, outerOptions);

        assertTrue(outerResult.accepted());
        assertEquals(CastResultCode.CASTER_BUSY, nestedResult.get().code());
        assertEquals(1, executions.get());
    }

    @Test
    void rejectedExecutorDoesNotStartCooldown() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        CastOptions options = CastOptions.builder("wizards:fireball").cooldownTicks(40).build();
        AtomicInteger executions = new AtomicInteger();
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, actualOptions) -> {
                    executions.incrementAndGet();
                    return CastExecutionOutcome.rejected(CastResultCode.UNSUPPORTED_SPELL, "not installed");
                },
                new AtomicLong(5L),
                new SequenceTraceIds(),
                CastTraceSink.noop());

        CastResult first = session.cast(caster, target, options);
        CastResult second = session.cast(caster, target, options);

        assertEquals(CastResultCode.UNSUPPORTED_SPELL, first.code());
        assertEquals(CastResultCode.UNSUPPORTED_SPELL, second.code());
        assertEquals(2, executions.get());
    }

    @Test
    void traceSinkFailureCannotChangeTheCastResult() {
        TestEntity caster = entity("00000000-0000-0000-0000-000000000001");
        TestEntity target = entity("00000000-0000-0000-0000-000000000002");
        CastSessionFlow<TestEntity> session = flow(
                (traceId, actualCaster, actualTarget, options) -> CastExecutionOutcome.accepted(),
                new AtomicLong(1L),
                () -> "trace-sink",
                event -> {
                    throw new IllegalStateException("logger unavailable");
                });

        CastResult result = session.cast(caster, target, CastOptions.defaults("wizards:fireball"));

        assertTrue(result.accepted());
        assertFalse(result.message().contains("logger"));
    }

    private static CastSessionFlow<TestEntity> flow(
            CastSessionFlow.Executor<TestEntity> executor,
            AtomicLong tick,
            java.util.function.Supplier<String> traceIds,
            CastTraceSink traceSink) {
        return new CastSessionFlow<>(
                executor,
                TargetStrategyRegistry.defaults(),
                new CooldownTracker(),
                new CastRecursionGuard(),
                TestEntity::id,
                TestEntity::player,
                ignored -> tick.get(),
                traceIds,
                traceSink);
    }

    private static TestEntity entity(String uuid) {
        return new TestEntity(UUID.fromString(uuid), false);
    }

    private static void assertSharedTraceSequence(
            List<CastTraceEvent> traces,
            String traceId,
            String spellId) {
        List<CastTraceEvent> matching = traces.stream()
                .filter(event -> event.traceId().equals(traceId))
                .toList();
        assertEquals(List.of(
                CastTraceStage.RECEIVED,
                CastTraceStage.VALIDATED,
                CastTraceStage.TARGET_RESOLVED,
                CastTraceStage.DISPATCHING,
                CastTraceStage.ACCEPTED), matching.stream().map(CastTraceEvent::stage).toList());
        assertTrue(matching.stream().allMatch(event -> spellId.equals(event.fields().get("spell_id"))));
        String stages = String.join(
                ">",
                matching.stream().map(event -> event.stage().name()).toList());
        System.out.printf(
                "TRACE_PROOF spell=%s trace=%s stages=%s route=SpellRouteTable%n",
                spellId,
                traceId,
                stages);
    }

    private record TestEntity(UUID id, boolean player) { }

    private static final class SequenceTraceIds implements java.util.function.Supplier<String> {
        private int sequence;

        @Override
        public String get() {
            sequence++;
            return "trace-" + sequence;
        }
    }
}
