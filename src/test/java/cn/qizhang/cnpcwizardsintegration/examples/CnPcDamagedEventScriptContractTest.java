package cn.qizhang.cnpcwizardsintegration.examples;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CnPcDamagedEventScriptContractTest {
    private static final Path EXAMPLE = Path.of(
            System.getProperty("user.dir"),
            "examples",
            "cnpc",
            "damaged_fireball.js");

    @Test
    void damagedExampleUsesTheVerifiedCustomNpcsEventBoundary() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("function damaged(event)"));
        assertTrue(script.contains("npc.getMCEntity()"));
        assertTrue(script.contains("event.damageSource.getTrueSource()"));
        assertTrue(script.contains("source = event.source"));
        assertTrue(script.contains("noppes.npcs.api.entity.IEntityLiving"));
    }

    @Test
    void damagedExamplePassesCallerControlledOptionsThroughTheSharedBridge() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("CnPcWizardsIntegration.castingApi().cast("));
        assertTrue(script.contains("CastOptions.builder(CNPC_WIZARDS_CONFIG.spellId)"));
        assertTrue(script.contains(".targetStrategyId(CNPC_WIZARDS_CONFIG.targetStrategyId)"));
        assertTrue(script.contains(".spellPowerMultiplier(CNPC_WIZARDS_CONFIG.spellPowerMultiplier)"));
        assertTrue(script.contains(".damageMultiplier(CNPC_WIZARDS_CONFIG.damageMultiplier)"));
        assertTrue(script.contains(".healingMultiplier(CNPC_WIZARDS_CONFIG.healingMultiplier)"));
        assertTrue(script.contains(".cooldownTicks(CNPC_WIZARDS_CONFIG.cooldownTicks)"));
        assertTrue(script.contains("result.traceId()"));
    }

    @Test
    void damagedExampleRemembersTheAttackerAndRetriesAfterCooldown() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("function rememberTarget(npc, target)"));
        assertTrue(script.contains("npc.setAttackTarget(target)"));
        assertTrue(script.contains("function timer(event)"));
        assertTrue(script.contains("event.id !== CNPC_WIZARDS_TIMER_ID"));
        assertTrue(script.contains("retryIntervalTicks: 5"));
        assertTrue(script.contains("npc.getTimers().start("));
        assertTrue(script.contains("npc.getWorld().getEntity(targetUuid)"));
        assertTrue(script.contains("npc.canSeeEntity(target)"));
        assertTrue(script.contains("tryCastAtTarget(event.npc, target)"));
    }

    @Test
    void automaticRetrySuppressesExpectedTransientRejections() throws IOException {
        String script = Files.readString(EXAMPLE);

        assertTrue(script.contains("code === \"COOLDOWN_ACTIVE\""));
        assertTrue(script.contains("code === \"TARGET_NOT_FOUND\""));
        assertTrue(script.contains("code === \"CASTER_BUSY\""));
        assertTrue(script.contains("!result.accepted() && !isExpectedRetry(result)"));
    }
}
