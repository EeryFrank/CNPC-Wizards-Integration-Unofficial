package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_power.api.SpellPower;

/** Server-tick owner for one active aqua-bubble-beam channel per NPC caster. */
final class AquaBubbleBeamChannelManager {
    private final CastTraceSink traceSink;
    private final ChannelCastFlow<UUID, ActiveChannel> flow = new ChannelCastFlow<>();
    private final AtomicBoolean initialized = new AtomicBoolean();

    AquaBubbleBeamChannelManager(CastTraceSink traceSink) {
        this.traceSink = Objects.requireNonNull(traceSink, "traceSink");
    }

    void initialize() {
        if (initialized.compareAndSet(false, true)) {
            ServerTickEvents.END_WORLD_TICK.register(this::tick);
            ServerLifecycleEvents.SERVER_STOPPING.register(
                    server -> cancelAll("server_stopping"));
        }
    }

    boolean isActive(LivingEntity caster) {
        return caster != null && flow.isActive(caster.getUuid());
    }

    CastExecutionOutcome schedule(CastExecution execution) {
        LivingEntity caster = execution.caster();
        LivingEntity target = execution.target();
        CastOptions options = execution.options();
        if (target == null || !target.isAlive() || target.isRemoved()) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "elemental_wizards_rpg:aqua_bubble_beam requires a living direct aim target");
        }
        if (!(caster.getWorld() instanceof ServerWorld world)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.INVALID_REQUEST,
                    "elemental_wizards_rpg:aqua_bubble_beam must be dispatched on the server");
        }
        if (target.getWorld() != world) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "elemental_wizards_rpg:aqua_bubble_beam target must be in the caster's world");
        }

        RegistryEntry<Spell> spellEntry = SpellRegistry.from(world)
                .getEntry(options.spellId())
                .orElse(null);
        if (spellEntry == null || spellEntry.value().active == null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    "Spell Engine registry does not contain an active " + options.spellId());
        }

        Spell spell = spellEntry.value();
        if (!SpellHelper.isChanneled(spell)
                || spell.active.cast.resolvedType() != Spell.Active.Cast.Type.CHANNEL
                || spell.target == null
                || spell.target.type != Spell.Target.Type.AREA) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    options.spellId() + " is not a Spell Engine CHANNEL/AREA spell");
        }
        if (!hasRequiredImpactLayout(spell)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    options.spellId() + " no longer has HEAL, DAMAGE, and caster-support impacts");
        }
        if (!withinRange(caster, target, spellEntry)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "elemental_wizards_rpg:aqua_bubble_beam aim target is outside spell range");
        }

        List<Entity> initialTargets = findAreaTargets(caster, spellEntry);
        if (initialTargets.stream().noneMatch(candidate -> candidate == target)) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "elemental_wizards_rpg:aqua_bubble_beam aim target is outside its area cone");
        }

        SpellCast.Duration duration = SpellHelper.getCastTimeDetails(caster, spell);
        int durationTicks = duration.length();
        int pulseCount = SpellHelper.channelTicks(caster, spellEntry);
        if (durationTicks <= 0 || pulseCount <= 0) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    "elemental_wizards_rpg:aqua_bubble_beam has no usable channel duration or pulses");
        }

        AquaBubbleBeamPowerPlan powerPlan = AquaBubbleBeamPowerPlan.from(options);
        ActiveChannel channel = new ActiveChannel(
                execution.traceId(),
                caster,
                target,
                options,
                powerPlan,
                world,
                spellEntry,
                durationTicks,
                pulseCount);
        ChannelCastFlow.StartResult result = flow.start(
                caster.getUuid(),
                channel,
                world.getTime(),
                durationTicks,
                pulseCount);
        if (result == ChannelCastFlow.StartResult.BUSY) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.CASTER_BUSY,
                    "caster already has an active channeled spell");
        }

        trace(channel, CastTraceStage.CHANNEL_SCHEDULED, Map.of(
                "duration_ticks", Integer.toString(durationTicks),
                "pulse_count", Integer.toString(pulseCount),
                "cast_speed", Float.toString(duration.speed()),
                "damage_scale", Double.toString(powerPlan.damageScale()),
                "healing_scale", Double.toString(powerPlan.healingScale()),
                "support_scale", Double.toString(powerPlan.supportScale())));
        return CastExecutionOutcome.accepted();
    }

    boolean cancel(LivingEntity caster, String reason) {
        if (caster == null) {
            return false;
        }
        return flow.cancel(caster.getUuid())
                .map(cancelled -> {
                    trace(cancelled.payload(), CastTraceStage.CHANNEL_CANCELLED, Map.of(
                            "reason", reason,
                            "delivered_pulses", Integer.toString(cancelled.deliveredPulses())));
                    return true;
                })
                .orElse(false);
    }

    private void cancelAll(String reason) {
        for (ChannelCastFlow.ActiveEntry<UUID, ActiveChannel> entry : flow.activeEntries()) {
            cancel(entry.payload().caster(), reason);
        }
    }

    private void tick(ServerWorld world) {
        for (ChannelCastFlow.ActiveEntry<UUID, ActiveChannel> entry : flow.activeEntries()) {
            ActiveChannel channel = entry.payload();
            if (channel.world() != world) {
                continue;
            }

            String cancellation = invalidationReason(channel);
            if (cancellation != null) {
                cancel(channel.caster(), cancellation);
                continue;
            }

            ChannelCastFlow.TickResult<ActiveChannel> result = flow.tick(entry.key(), world.getTime());
            if (result == null) {
                continue;
            }

            boolean failed = false;
            for (int offset = 0; offset < result.duePulses(); offset++) {
                int zeroBasedPulse = result.firstPulseIndex() + offset;
                try {
                    PulseDelivery delivery = deliverPulse(channel, zeroBasedPulse);
                    trace(channel, CastTraceStage.CHANNEL_PULSE, Map.of(
                            "pulse_index", Integer.toString(zeroBasedPulse + 1),
                            "pulse_count", Integer.toString(channel.pulseCount()),
                            "selected_targets", Integer.toString(delivery.selectedTargets()),
                            "damage_targets", Integer.toString(delivery.damageTargets()),
                            "healing_targets", Integer.toString(delivery.healingTargets()),
                            "support_applied", Boolean.toString(delivery.supportApplied())));
                }
                catch (RuntimeException exception) {
                    failed = true;
                    trace(channel, CastTraceStage.CHANNEL_FAILED, Map.of(
                            "reason", "impact_exception",
                            "error_type", exception.getClass().getName(),
                            "pulse_index", Integer.toString(zeroBasedPulse + 1)));
                    break;
                }
            }

            if (failed) {
                flow.cancel(entry.key());
            }
            else if (result.completed()) {
                trace(channel, CastTraceStage.CHANNEL_COMPLETED, Map.of(
                        "delivered_pulses", Integer.toString(result.deliveredPulses())));
            }
        }
    }

    private PulseDelivery deliverPulse(ActiveChannel channel, int zeroBasedPulse) {
        Spell spell = channel.spellEntry().value();
        SpellTarget.FocusMode focusMode = SpellHelper.focusMode(spell);
        SpellPower.Result basePower = SpellPower.getSpellPower(spell.school, channel.caster());
        SpellHelper.ImpactContext damageContext = impactContext(
                spell,
                basePower,
                channel.powerPlan().damageScale(),
                focusMode,
                zeroBasedPulse);
        SpellHelper.ImpactContext healingContext = impactContext(
                spell,
                basePower,
                channel.powerPlan().healingScale(),
                focusMode,
                zeroBasedPulse);
        SpellHelper.ImpactContext supportContext = impactContext(
                spell,
                basePower,
                channel.powerPlan().supportScale(),
                focusMode,
                zeroBasedPulse);

        List<Entity> targets = findAreaTargets(channel.caster(), channel.spellEntry());
        int damageTargets = 0;
        int healingTargets = 0;
        for (Entity target : targets) {
            if (EntityRelations.actionAllowed(
                    focusMode,
                    SpellTarget.Intent.HARMFUL,
                    channel.caster(),
                    target)
                    && performImpactType(
                            channel,
                            target,
                            damageContext.position(target.getPos()),
                            Spell.Impact.Action.Type.DAMAGE)) {
                damageTargets++;
            }
            if (EntityRelations.actionAllowed(
                    focusMode,
                    SpellTarget.Intent.HELPFUL,
                    channel.caster(),
                    target)
                    && performImpactType(
                            channel,
                            target,
                            healingContext.position(target.getPos()),
                            Spell.Impact.Action.Type.HEAL)) {
                healingTargets++;
            }
        }

        boolean supportApplied = !targets.isEmpty() && performImpactType(
                channel,
                channel.caster(),
                supportContext.position(channel.caster().getPos()),
                Spell.Impact.Action.Type.STATUS_EFFECT);
        return new PulseDelivery(targets.size(), damageTargets, healingTargets, supportApplied);
    }

    private static boolean performImpactType(
            ActiveChannel channel,
            Entity target,
            SpellHelper.ImpactContext context,
            Spell.Impact.Action.Type actionType) {
        Spell spell = channel.spellEntry().value();
        return SpellHelper.performImpacts(
                channel.world(),
                channel.caster(),
                target,
                target,
                channel.spellEntry(),
                spell.impacts,
                context,
                true,
                actionType);
    }

    private static SpellHelper.ImpactContext impactContext(
            Spell spell,
            SpellPower.Result basePower,
            double callerScale,
            SpellTarget.FocusMode focusMode,
            int zeroBasedPulse) {
        SpellPower.Result scaledPower = new SpellPower.Result(
                basePower.school(),
                basePower.baseValue() * callerScale,
                basePower.criticalChance(),
                basePower.criticalDamage());
        return new SpellHelper.ImpactContext(
                SpellHelper.channelValueMultiplier(spell),
                1.0F,
                null,
                scaledPower,
                focusMode,
                zeroBasedPulse);
    }

    private static List<Entity> findAreaTargets(
            LivingEntity caster,
            RegistryEntry<Spell> spellEntry) {
        return SpellTarget.findTargets(
                        caster,
                        spellEntry,
                        SpellTarget.SearchResult.empty(),
                        true)
                .entities()
                .stream()
                .filter(entity -> entity != null && entity.isAlive() && !entity.isRemoved())
                .distinct()
                .toList();
    }

    private static boolean hasRequiredImpactLayout(Spell spell) {
        boolean damage = false;
        boolean heal = false;
        boolean casterSupport = false;
        for (Spell.Impact impact : spell.impacts) {
            if (impact == null || impact.action == null || impact.action.type == null) {
                continue;
            }
            damage |= impact.action.type == Spell.Impact.Action.Type.DAMAGE;
            heal |= impact.action.type == Spell.Impact.Action.Type.HEAL;
            casterSupport |= impact.action.type == Spell.Impact.Action.Type.STATUS_EFFECT
                    && impact.action.apply_to_caster;
        }
        return damage && heal && casterSupport;
    }

    private String invalidationReason(ActiveChannel channel) {
        if (!channel.caster().isAlive() || channel.caster().isRemoved()) {
            return "caster_unavailable";
        }
        if (!channel.aimTarget().isAlive() || channel.aimTarget().isRemoved()) {
            return "aim_target_unavailable";
        }
        if (channel.caster().getWorld() != channel.world()
                || channel.aimTarget().getWorld() != channel.world()) {
            return "world_changed";
        }
        if (!withinRange(channel.caster(), channel.aimTarget(), channel.spellEntry())) {
            return "aim_target_out_of_range";
        }
        return null;
    }

    private static boolean withinRange(
            LivingEntity caster,
            LivingEntity target,
            RegistryEntry<Spell> spellEntry) {
        double range = SpellHelper.getRange(caster, spellEntry) * caster.getScale();
        return caster.squaredDistanceTo(target) <= range * range;
    }

    private void trace(ActiveChannel channel, CastTraceStage stage, Map<String, String> extraFields) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("caster_id", channel.caster().getUuidAsString());
        fields.put("target_id", channel.aimTarget().getUuidAsString());
        fields.put("spell_id", channel.options().spellId().toString());
        fields.put("lifecycle", "channel");
        fields.put("impact_mode", "damage_healing_support_split");
        fields.putAll(extraFields);
        try {
            traceSink.record(new CastTraceEvent(channel.traceId(), stage, fields));
        }
        catch (RuntimeException ignored) {
            // Diagnostics must not interrupt an active channel.
        }
    }

    private record PulseDelivery(
            int selectedTargets,
            int damageTargets,
            int healingTargets,
            boolean supportApplied) {
    }

    private record ActiveChannel(
            String traceId,
            LivingEntity caster,
            LivingEntity aimTarget,
            CastOptions options,
            AquaBubbleBeamPowerPlan powerPlan,
            ServerWorld world,
            RegistryEntry<Spell> spellEntry,
            int durationTicks,
            int pulseCount) {
    }
}
