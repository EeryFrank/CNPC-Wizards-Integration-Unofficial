package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class SpellRouteTableTest {
    @Test
    void fireballRouteReceivesTheResolvedDirectTargetAndCallerOptions() {
        String caster = new String("caster");
        String target = new String("target");
        CastOptions options = CastOptions.builder("wizards:fireball")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(1.5D)
                .cooldownTicks(30)
                .build();
        AtomicReference<String> routedCaster = new AtomicReference<>();
        AtomicReference<String> routedTarget = new AtomicReference<>();
        AtomicReference<String> routedTrace = new AtomicReference<>();
        AtomicReference<CastOptions> routedOptions = new AtomicReference<>();
        SpellRouteTable<String> routes = SpellRouteTable.<String>builder()
                .add(Identifier.of("wizards", "fireball"),
                        (traceId, actualCaster, actualTarget, actualOptions) -> {
                    routedTrace.set(traceId);
                    routedCaster.set(actualCaster);
                    routedTarget.set(actualTarget);
                    routedOptions.set(actualOptions);
                    return CastExecutionOutcome.accepted();
                })
                .build();

        CastExecutionOutcome result = routes.dispatch("trace-fireball", caster, target, options);

        assertTrue(result.isAccepted());
        assertEquals("trace-fireball", routedTrace.get());
        assertSame(caster, routedCaster.get());
        assertSame(target, routedTarget.get());
        assertSame(options, routedOptions.get());
    }

    @Test
    void aquaRouteReceivesCallerDamageHealingAndSupportOptions() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:aqua_bubble_beam")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(1.6D)
                .healingMultiplier(0.4D)
                .cooldownTicks(60)
                .build();
        AtomicReference<CastOptions> routedOptions = new AtomicReference<>();
        SpellRouteTable<String> routes = SpellRouteTable.<String>builder()
                .add(Identifier.of("elemental_wizards_rpg", "aqua_bubble_beam"),
                        (traceId, caster, target, actualOptions) -> {
                    routedOptions.set(actualOptions);
                    return CastExecutionOutcome.accepted();
                })
                .build();

        CastExecutionOutcome result = routes.dispatch("trace-aqua", "caster", "target", options);

        assertTrue(result.isAccepted());
        assertSame(options, routedOptions.get());
        assertEquals(1.6D, routedOptions.get().damageMultiplier());
        assertEquals(0.4D, routedOptions.get().healingMultiplier());
        assertEquals(60, routedOptions.get().cooldownTicks());
    }

    @Test
    void earthquakeRouteReceivesNoEntityTargetAndCallerOptions() {
        CastOptions options = CastOptions.builder("elemental_wizards_rpg:terra_earthquake")
                .targetStrategyId("none")
                .spellPowerMultiplier(1.25D)
                .damageMultiplier(1.6D)
                .cooldownTicks(80)
                .build();
        AtomicReference<String> routedTarget = new AtomicReference<>("not-dispatched");
        AtomicReference<CastOptions> routedOptions = new AtomicReference<>();
        SpellRouteTable<String> routes = SpellRouteTable.<String>builder()
                .add(Identifier.of("elemental_wizards_rpg", "terra_earthquake"),
                        (traceId, caster, target, actualOptions) -> {
                    routedTarget.set(target);
                    routedOptions.set(actualOptions);
                    return CastExecutionOutcome.accepted();
                })
                .build();

        CastExecutionOutcome result = routes.dispatch(
                "trace-earthquake",
                "caster",
                null,
                options);

        assertTrue(result.isAccepted());
        assertEquals(null, routedTarget.get());
        assertSame(options, routedOptions.get());
        assertEquals("none", routedOptions.get().targetStrategyId());
        assertEquals(1.6D, routedOptions.get().damageMultiplier());
        assertEquals(80, routedOptions.get().cooldownTicks());
    }

    @Test
    void duplicateSpellRoutesAreRejected() {
        SpellRouteTable.Builder<String> routes = SpellRouteTable.builder();
        Identifier fireball = Identifier.of("wizards", "fireball");
        routes.add(fireball, (traceId, caster, target, options) -> CastExecutionOutcome.accepted());

        assertThrows(
                IllegalArgumentException.class,
                () -> routes.add(
                        fireball,
                        (traceId, caster, target, options) -> CastExecutionOutcome.accepted()));
    }
}
