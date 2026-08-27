package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import java.util.Objects;
import net.minecraft.entity.LivingEntity;

/** Validated inputs handed from the shared session flow to a spell adapter. */
public record CastExecution(
        String traceId,
        LivingEntity caster,
        LivingEntity target,
        CastOptions options) {
    public CastExecution {
        traceId = requireNonBlank(traceId, "traceId");
        Objects.requireNonNull(caster, "caster");
        Objects.requireNonNull(options, "options");
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
