package cn.qizhang.cnpcwizardsintegration.api;

import java.util.Objects;
import net.minecraft.util.Identifier;

/**
 * Immutable, caller-controlled inputs for one spell cast.
 *
 * <p>Spell behavior must consume these values rather than embed demo-specific damage, healing,
 * power, cooldown, or targeting constants in the bridge.</p>
 */
public record CastOptions(
        Identifier spellId,
        String targetStrategyId,
        double spellPowerMultiplier,
        double damageMultiplier,
        double healingMultiplier,
        int cooldownTicks) {
    public static final String DEFAULT_TARGET_STRATEGY_ID = "direct";

    public CastOptions {
        Objects.requireNonNull(spellId, "spellId");
        targetStrategyId = requireNonBlank(targetStrategyId, "targetStrategyId");
        spellPowerMultiplier = requireFiniteNonNegative(spellPowerMultiplier, "spellPowerMultiplier");
        damageMultiplier = requireFiniteNonNegative(damageMultiplier, "damageMultiplier");
        healingMultiplier = requireFiniteNonNegative(healingMultiplier, "healingMultiplier");
        if (cooldownTicks < 0) {
            throw new IllegalArgumentException("cooldownTicks must be non-negative");
        }
    }

    /** Creates neutral options for a namespaced Minecraft spell ID. */
    public static CastOptions defaults(String spellId) {
        return builder(spellId).build();
    }

    /** Starts a builder with neutral multipliers and no additional cooldown. */
    public static Builder builder(String spellId) {
        return new Builder(parseSpellId(spellId));
    }

    /** Starts a builder with neutral multipliers and no additional cooldown. */
    public static Builder builder(Identifier spellId) {
        return new Builder(spellId);
    }

    private static Identifier parseSpellId(String spellId) {
        String value = requireNonBlank(spellId, "spellId");
        if (value.indexOf(':') <= 0) {
            throw new IllegalArgumentException("spellId must include an explicit namespace: " + value);
        }
        Identifier identifier = Identifier.tryParse(value);
        if (identifier == null) {
            throw new IllegalArgumentException("spellId must be a valid namespaced identifier: " + value);
        }
        return identifier;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static double requireFiniteNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    /** Builder intended for configuration and future script adapters. */
    public static final class Builder {
        private final Identifier spellId;
        private String targetStrategyId = DEFAULT_TARGET_STRATEGY_ID;
        private double spellPowerMultiplier = 1.0D;
        private double damageMultiplier = 1.0D;
        private double healingMultiplier = 1.0D;
        private int cooldownTicks;

        private Builder(Identifier spellId) {
            this.spellId = Objects.requireNonNull(spellId, "spellId");
        }

        public Builder targetStrategyId(String targetStrategyId) {
            this.targetStrategyId = targetStrategyId;
            return this;
        }

        public Builder spellPowerMultiplier(double spellPowerMultiplier) {
            this.spellPowerMultiplier = spellPowerMultiplier;
            return this;
        }

        public Builder damageMultiplier(double damageMultiplier) {
            this.damageMultiplier = damageMultiplier;
            return this;
        }

        public Builder healingMultiplier(double healingMultiplier) {
            this.healingMultiplier = healingMultiplier;
            return this;
        }

        public Builder cooldownTicks(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        public CastOptions build() {
            return new CastOptions(
                    spellId,
                    targetStrategyId,
                    spellPowerMultiplier,
                    damageMultiplier,
                    healingMultiplier,
                    cooldownTicks);
        }
    }
}
