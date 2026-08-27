package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;

/** Resolves the entity target (or deliberate lack of one) before spell dispatch. */
public interface TargetStrategy<E> {
    String id();

    TargetResolution<E> resolve(E caster, E directTarget, CastOptions options);
}
