package cn.qizhang.cnpcwizardsintegration.bridge;

import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import java.util.List;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellHelper;
import net.spell_power.api.SpellPower;

/** Explicit-entity delivery path for {@code wizards:fireball}. */
final class WizardsFireballSpellRoute implements SpellRouteTable.SpellRoute<LivingEntity> {
    @Override
    public CastExecutionOutcome cast(
            String traceId,
            LivingEntity caster,
            LivingEntity target,
            CastOptions options) {
        if (target == null || !target.isAlive()) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "wizards:fireball requires a living direct target");
        }

        World world = caster.getWorld();
        if (world.isClient()) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.INVALID_REQUEST,
                    "wizards:fireball must be dispatched on the server");
        }
        if (target.getWorld() != world) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "wizards:fireball target must be in the caster's world");
        }

        RegistryEntry<Spell> spellEntry = SpellRegistry.from(world)
                .getEntry(options.spellId())
                .orElse(null);
        if (spellEntry == null || spellEntry.value().active == null) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.UNSUPPORTED_SPELL,
                    "Spell Engine registry does not contain an active " + options.spellId());
        }

        double range = SpellHelper.getRange(caster, spellEntry) * caster.getScale();
        if (caster.squaredDistanceTo(target) > range * range) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "wizards:fireball direct target is outside spell range");
        }

        Spell spell = spellEntry.value();
        SpellPower.Result basePower = SpellPower.getSpellPower(spell.school, caster);
        double powerScale = options.spellPowerMultiplier() * options.damageMultiplier();
        SpellPower.Result scaledPower = new SpellPower.Result(
                basePower.school(),
                basePower.baseValue() * powerScale,
                basePower.criticalChance(),
                basePower.criticalDamage());
        SpellHelper.ImpactContext context = new SpellHelper.ImpactContext()
                .power(scaledPower)
                .target(SpellHelper.focusMode(spell));

        boolean delivered = SpellHelper.deliver(
                world,
                spellEntry,
                caster,
                List.of(new SpellHelper.DeliveryTarget(target, context)),
                context,
                target.getPos(),
                null);
        if (!delivered) {
            return CastExecutionOutcome.rejected(
                    CastResultCode.TARGET_NOT_FOUND,
                    "Spell Engine rejected the wizards:fireball direct target");
        }
        return CastExecutionOutcome.accepted();
    }
}
