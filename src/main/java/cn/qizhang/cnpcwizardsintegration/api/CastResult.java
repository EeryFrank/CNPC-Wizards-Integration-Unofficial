package cn.qizhang.cnpcwizardsintegration.api;

import java.util.Objects;

/** Machine-readable outcome returned by the public casting API. */
public record CastResult(CastResultCode code, String traceId, String message) {
    public CastResult {
        Objects.requireNonNull(code, "code");
        traceId = requireNonBlank(traceId, "traceId");
        message = normalize(message);
    }

    /** True only when the bridge accepted the request into its casting flow. */
    public boolean accepted() {
        return code == CastResultCode.ACCEPTED;
    }

    public static CastResult accepted(String traceId) {
        return new CastResult(CastResultCode.ACCEPTED, traceId, "");
    }

    public static CastResult rejected(CastResultCode code, String traceId, String message) {
        if (code == CastResultCode.ACCEPTED) {
            throw new IllegalArgumentException("Use accepted(traceId) for ACCEPTED results");
        }
        return new CastResult(code, traceId, message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
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
