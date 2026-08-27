package cn.qizhang.cnpcwizardsintegration.examples;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CnPcFourSpellRotationScriptContractTest {
    private static final Path EXAMPLE = Path.of(
            System.getProperty("user.dir"),
            "examples",
            "cnpc",
            "four_spell_rotation.js");

    @Test
    void rotationContainsAllPhaseOneSpellsAndExpectedCooldowns() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("spellId: \"wizards:fireball\""));
        assertTrue(script.contains("spellId: \"elemental_wizards_rpg:aqua_bubble_beam\""));
        assertTrue(script.contains("spellId: \"wizards:frost_blizzard\""));
        assertTrue(script.contains("spellId: \"elemental_wizards_rpg:terra_earthquake\""));
        assertTrue(script.contains("cooldownTicks: 60"));
        assertTrue(script.contains("cooldownTicks: 300"));
        assertTrue(script.contains("cooldownTicks: 320"));
        assertTrue(script.contains("cooldownTicks: 700"));
    }

    @Test
    void rotationUsesOneSharedBridgeWithTargetAndAoeStrategies() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("function runSpellRotation(npc, target)"));
        assertTrue(script.contains("minimumActionIntervalTicks: 20"));
        assertTrue(script.contains("targetStrategyId: \"direct\""));
        assertTrue(script.contains("targetStrategyId: \"none\""));
        assertTrue(script.contains("spell.targetStrategyId === \"none\""));
        assertTrue(script.contains("? null"));
        assertTrue(script.contains("CnPcWizardsIntegration.castingApi().cast("));
        assertTrue(script.contains("if (outcome === \"ACCEPTED\")"));
        assertTrue(script.contains("(index + 1) % spells.length"));
    }

    @Test
    void rotationRequiresAValidRememberedCombatTarget() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("function damaged(event)"));
        assertTrue(script.contains("function timer(event)"));
        assertTrue(script.contains("npc.setAttackTarget(target)"));
        assertTrue(script.contains("npc.getWorld().getEntity(targetUuid)"));
        assertTrue(script.contains("distanceSquared(npc, target)"));
        assertTrue(script.contains("npc.canSeeEntity(target)"));
        assertTrue(script.contains("clearCombatState(event.npc)"));
    }
}
