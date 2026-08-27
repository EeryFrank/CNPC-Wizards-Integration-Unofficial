package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.Objects;

/** Target-strategy outcome; a successful area cast may intentionally have no entity target. */
public record TargetResolution<E>(
        boolean resolved,
        E target,
        CastResultCode failureCode,
        String diagnostic) {
    public TargetResolution {
        diagnostic = diagnostic == null ? "" : diagnostic.trim();
        if (resolved && failureCode != null) {
            throw new IllegalArgumentException("Resolved targets cannot carry a failure code");
        }
        if (!resolved) {
            Objects.requireNonNull(failureCode, "failureCode");
            if (failureCode == CastResultCode.ACCEPTED) {
                throw new IllegalArgumentException("Target failure cannot use ACCEPTED");
            }
        }
    }

    public static <E> TargetResolution<E> selected(E target) {
        return new TargetResolution<>(true, Objects.requireNonNull(target, "target"), null, "");
    }

    public static <E> TargetResolution<E> withoutEntityTarget() {
        return new TargetResolution<>(true, null, null, "");
    }

    public static <E> TargetResolution<E> rejected(CastResultCode code, String diagnostic) {
        return new TargetResolution<>(false, null, code, diagnostic);
    }
}
