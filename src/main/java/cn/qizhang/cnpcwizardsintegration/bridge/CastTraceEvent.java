package cn.qizhang.cnpcwizardsintegration.bridge;

import java.util.Map;
import java.util.Objects;

/** Structured trace event emitted by one cast session. */
public record CastTraceEvent(String traceId, CastTraceStage stage, Map<String, String> fields) {
    public CastTraceEvent {
        traceId = requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(stage, "stage");
        fields = Map.copyOf(Objects.requireNonNull(fields, "fields"));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
