package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ChargeCastFlowTest {
    @Test
    void releasesOnlyAfterTheConfiguredFullChargeDuration() {
        ChargeCastFlow<String, String> flow = new ChargeCastFlow<>();

        assertEquals(ChargeCastFlow.StartResult.STARTED, flow.start("caster", "quake", 100L, 40));
        assertNull(flow.tick("caster", 139L));
        assertTrue(flow.isActive("caster"));

        ChargeCastFlow.Completion<String> completion = flow.tick("caster", 140L);

        assertEquals("quake", completion.payload());
        assertEquals(40L, completion.elapsedTicks());
        assertFalse(flow.isActive("caster"));
    }

    @Test
    void rejectsOverlapAndCanCancelBeforeRelease() {
        ChargeCastFlow<String, String> flow = new ChargeCastFlow<>();
        flow.start("caster", "first", 20L, 40);

        assertEquals(ChargeCastFlow.StartResult.BUSY, flow.start("caster", "second", 20L, 40));
        assertEquals("first", flow.cancel("caster").orElseThrow().payload());
        assertFalse(flow.isActive("caster"));
        assertNull(flow.tick("caster", 80L));
    }

    @Test
    void clockRollbackCannotReleaseTheChargeEarly() {
        ChargeCastFlow<String, String> flow = new ChargeCastFlow<>();
        flow.start("caster", "quake", 100L, 40);

        assertNull(flow.tick("caster", 90L));
        assertTrue(flow.isActive("caster"));
    }
}
