package cn.qizhang.cnpcwizardsintegration.api;

import net.minecraft.entity.LivingEntity;

/**
 * Stable entry point for requesting a spell cast by a non-player living entity.
 *
 * <p>The bridge implementation owns target resolution, cooldowns, recursion protection, and
 * Spell Engine integration. Callers supply the caster, an optional direct target, and all
 * configurable cast values through {@link CastOptions}. A {@code null} target is permitted for
 * target strategies that do not require a direct entity.</p>
 */
@FunctionalInterface
public interface NpcSpellCastingApi {
    /**
     * Requests a cast through the shared bridge flow.
     *
     * @param caster non-player living entity that owns the cast
     * @param target direct target, or {@code null} when allowed by the configured strategy
     * @param options resolved spell and tuning configuration
     * @return a machine-readable result; an accepted request may still represent a continuing cast
     */
    CastResult cast(LivingEntity caster, LivingEntity target, CastOptions options);

    /**
     * Cancels a continuing cast owned by the supplied non-player caster, when supported.
     *
     * @return {@code true} when an active cast was cancelled
     */
    default boolean cancel(LivingEntity caster) {
        return false;
    }
}
