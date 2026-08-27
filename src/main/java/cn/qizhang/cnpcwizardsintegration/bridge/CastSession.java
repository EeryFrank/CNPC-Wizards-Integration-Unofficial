package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResult;
import cn.qizhang.cnpcwizardsintegration.api.NpcSpellCastingApi;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Minecraft-facing facade over the shared cast-session state machine. */
public final class CastSession implements NpcSpellCastingApi {
    private final SpellCastExecutor executor;
    private final CastSessionFlow<LivingEntity> flow;

    private CastSession(Builder builder) {
        this.executor = builder.executor;
        this.flow = new CastSessionFlow<>(
                (traceId, caster, target, options) -> builder.executor.execute(
                        new CastExecution(traceId, caster, target, options)),
                builder.targetStrategies,
                builder.cooldowns,
                builder.recursionGuard,
                LivingEntity::getUuid,
                entity -> entity instanceof PlayerEntity,
                builder.tickSource,
                builder.traceIdSource,
                builder.traceSink);
    }

    public static Builder builder(SpellCastExecutor executor) {
        return new Builder(executor);
    }

    public Set<String> targetStrategyIds() {
        return flow.targetStrategyIds();
    }

    @Override
    public CastResult cast(LivingEntity caster, LivingEntity directTarget, CastOptions options) {
        return flow.cast(caster, directTarget, options);
    }

    @Override
    public boolean cancel(LivingEntity caster) {
        if (caster == null || caster instanceof PlayerEntity) {
            return false;
        }
        return executor.cancel(caster);
    }

    public static final class Builder {
        private final SpellCastExecutor executor;
        private TargetStrategyRegistry<LivingEntity> targetStrategies = TargetStrategyRegistry.defaults();
        private CooldownTracker cooldowns = new CooldownTracker();
        private CastRecursionGuard recursionGuard = new CastRecursionGuard();
        private ToLongFunction<LivingEntity> tickSource = entity -> entity.getWorld().getTime();
        private Supplier<String> traceIdSource = () -> UUID.randomUUID().toString();
        private CastTraceSink traceSink = CastTraceSink.noop();

        private Builder(SpellCastExecutor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
        }

        public Builder targetStrategies(TargetStrategyRegistry<LivingEntity> targetStrategies) {
            this.targetStrategies = Objects.requireNonNull(targetStrategies, "targetStrategies");
            return this;
        }

        public Builder cooldowns(CooldownTracker cooldowns) {
            this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
            return this;
        }

        public Builder recursionGuard(CastRecursionGuard recursionGuard) {
            this.recursionGuard = Objects.requireNonNull(recursionGuard, "recursionGuard");
            return this;
        }

        public Builder tickSource(ToLongFunction<LivingEntity> tickSource) {
            this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
            return this;
        }

        public Builder traceIdSource(Supplier<String> traceIdSource) {
            this.traceIdSource = Objects.requireNonNull(traceIdSource, "traceIdSource");
            return this;
        }

        public Builder traceSink(CastTraceSink traceSink) {
            this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
            return this;
        }

        public CastSession build() {
            return new CastSession(this);
        }
    }
}
