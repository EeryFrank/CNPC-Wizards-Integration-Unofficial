package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ChannelCastFlowTest {
    @Test
    void twelvePulsesUseSpellEngineCenteredIntervalsAndFinishAtDuration() {
        ChannelCastFlow<String, String> flow = new ChannelCastFlow<>();
        assertEquals(
                ChannelCastFlow.StartResult.STARTED,
                flow.start("caster", "frost", 100L, 160, 12));

        List<Integer> pulseTicks = new ArrayList<>();
        for (int tick = 100; tick <= 260; tick++) {
            ChannelCastFlow.TickResult<String> result = flow.tick("caster", tick);
            assertNotNull(result);
            for (int pulse = 0; pulse < result.duePulses(); pulse++) {
                pulseTicks.add(tick - 100);
            }
            if (tick < 260) {
                assertFalse(result.completed());
            }
        }

        assertEquals(List.of(7, 20, 34, 47, 60, 74, 87, 100, 114, 127, 140, 154), pulseTicks);
        assertFalse(flow.isActive("caster"));
        assertNull(flow.tick("caster", 261L));
    }

    @Test
    void aCasterCannotStartASecondChannelUntilCancelled() {
        ChannelCastFlow<String, String> flow = new ChannelCastFlow<>();
        assertEquals(
                ChannelCastFlow.StartResult.STARTED,
                flow.start("caster", "first", 0L, 40, 4));
        assertEquals(
                ChannelCastFlow.StartResult.BUSY,
                flow.start("caster", "second", 0L, 40, 4));

        ChannelCastFlow.Cancellation<String> cancelled = flow.cancel("caster").orElseThrow();
        assertEquals("first", cancelled.payload());
        assertEquals(0, cancelled.deliveredPulses());
        assertEquals(
                ChannelCastFlow.StartResult.STARTED,
                flow.start("caster", "second", 10L, 40, 4));
    }

    @Test
    void cancellationReportsAlreadyDeliveredPulses() {
        ChannelCastFlow<String, String> flow = new ChannelCastFlow<>();
        flow.start("caster", "frost", 0L, 80, 4);

        ChannelCastFlow.TickResult<String> tick = flow.tick("caster", 31L);
        assertEquals(2, tick.duePulses());

        ChannelCastFlow.Cancellation<String> cancelled = flow.cancel("caster").orElseThrow();
        assertEquals(2, cancelled.deliveredPulses());
        assertFalse(flow.isActive("caster"));
    }

    @Test
    void aLateServerTickClaimsEveryDuePulseWithoutCompletingEarly() {
        ChannelCastFlow<String, String> flow = new ChannelCastFlow<>();
        flow.start("caster", "frost", 500L, 160, 12);

        ChannelCastFlow.TickResult<String> late = flow.tick("caster", 640L);
        assertEquals(11, late.duePulses());
        assertEquals(11, late.deliveredPulses());
        assertFalse(late.completed());

        ChannelCastFlow.TickResult<String> completed = flow.tick("caster", 660L);
        assertEquals(1, completed.duePulses());
        assertEquals(12, completed.deliveredPulses());
        assertTrue(completed.completed());
    }
}
