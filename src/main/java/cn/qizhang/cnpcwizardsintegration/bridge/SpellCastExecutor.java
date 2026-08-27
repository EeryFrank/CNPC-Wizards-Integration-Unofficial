package cn.qizhang.cnpcwizardsintegration.bridge;

import net.minecraft.entity.LivingEntity;

/** Future Spell Engine adapters implement only this final dispatch boundary. */
@FunctionalInterface
public interface SpellCastExecutor {
    CastExecutionOutcome execute(CastExecution execution);

    /** Registers any server lifecycle hooks required by this executor. */
    default void initialize() {
    }

    /** Cancels an executor-owned continuing cast, if one exists. */
    default boolean cancel(LivingEntity caster) {
        return false;
    }
}
