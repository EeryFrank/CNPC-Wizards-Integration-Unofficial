package cn.qizhang.cnpcwizardsintegration.api;

import java.util.Optional;

/**
 * Boundary between configuration/script adapters and the casting bridge.
 *
 * <p>Implementations may load profiles from files, scripts, or another registry. The public bridge
 * depends only on already validated {@link CastOptions} values.</p>
 */
@FunctionalInterface
public interface CastOptionsSource {
    /** Resolves a named profile without prescribing its storage format. */
    Optional<CastOptions> find(String profileId);
}
