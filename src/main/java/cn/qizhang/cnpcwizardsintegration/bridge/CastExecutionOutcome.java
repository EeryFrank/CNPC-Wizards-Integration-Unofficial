package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.Objects;

/** Adapter result before the session attaches its authoritative trace identifier. */
public record CastExecutionOutcome(CastResultCode code, String diagnostic) {
    public CastExecutionOutcome {
        Objects.requireNonNull(code, "code");
        diagnostic = diagnostic == null ? "" : diagnostic.trim();
    }

    public boolean isAccepted() {
        return code == CastResultCode.ACCEPTED;
    }

    public static CastExecutionOutcome accepted() {
        return new CastExecutionOutcome(CastResultCode.ACCEPTED, "");
    }

    public static CastExecutionOutcome rejected(CastResultCode code, String diagnostic) {
        if (code == CastResultCode.ACCEPTED) {
            throw new IllegalArgumentException("Use accepted() for ACCEPTED outcomes");
        }
        return new CastExecutionOutcome(code, diagnostic);
    }
}
