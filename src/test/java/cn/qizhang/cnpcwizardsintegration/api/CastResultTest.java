package cn.qizhang.cnpcwizardsintegration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CastResultTest {
    @Test
    void acceptedResultIsExplicit() {
        CastResult result = CastResult.accepted("trace-1");

        assertTrue(result.accepted());
        assertEquals(CastResultCode.ACCEPTED, result.code());
        assertEquals("trace-1", result.traceId());
    }

    @Test
    void rejectedResultRetainsMachineCodeAndDiagnostic() {
        CastResult result = CastResult.rejected(
                CastResultCode.COOLDOWN_ACTIVE,
                " trace-2 ",
                " 20 ticks remaining ");

        assertFalse(result.accepted());
        assertEquals(CastResultCode.COOLDOWN_ACTIVE, result.code());
        assertEquals("trace-2", result.traceId());
        assertEquals("20 ticks remaining", result.message());
    }

    @Test
    void acceptedCodeCannotBeCreatedThroughRejectedFactory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CastResult.rejected(CastResultCode.ACCEPTED, "trace-3", "wrong factory"));
    }

    @Test
    void everyResultRequiresATraceIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> CastResult.accepted(" "));
    }
}
