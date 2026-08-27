package cn.qizhang.cnpcwizardsintegration;

import cn.qizhang.cnpcwizardsintegration.api.CastResultCode;
import cn.qizhang.cnpcwizardsintegration.api.NpcSpellCastingApi;
import cn.qizhang.cnpcwizardsintegration.bridge.CastExecutionOutcome;
import cn.qizhang.cnpcwizardsintegration.bridge.CastSession;
import cn.qizhang.cnpcwizardsintegration.bridge.CastTraceSink;
import cn.qizhang.cnpcwizardsintegration.bridge.SpellCastExecutor;
import cn.qizhang.cnpcwizardsintegration.bridge.SpellEngineSpellCastExecutor;
import cn.qizhang.cnpcwizardsintegration.skillblocks.ServerSkillBlockController;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CnPcWizardsIntegration implements ModInitializer {
    public static final String MOD_ID = "cnpc_wizards_integration";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final CastTraceSink TRACE_SINK = event -> LOGGER.info(
            "cast_trace trace={} stage={} fields={}",
            event.traceId(),
            event.stage(),
            event.fields());
    private static final SpellCastExecutor SPELL_EXECUTOR = createSpellExecutor();
    private static final CastSession CASTING_SESSION = CastSession.builder(SPELL_EXECUTOR)
            .traceSink(TRACE_SINK)
            .build();
    private static final ServerSkillBlockController SKILL_BLOCK_CONTROLLER = new ServerSkillBlockController(LOGGER);

    /** Shared bridge entry point used by Java and future script adapters. */
    public static NpcSpellCastingApi castingApi() {
        return CASTING_SESSION;
    }

    @Override
    public void onInitialize() {
        SPELL_EXECUTOR.initialize();
        SKILL_BLOCK_CONTROLLER.initialize();
        LOGGER.info(
                "CNPC Wizards Integration (Unofficial) {} initialized; target strategies={}",
                "0.0.1-Demo",
                CASTING_SESSION.targetStrategyIds());
    }

    private static SpellCastExecutor createSpellExecutor() {
        FabricLoader loader = FabricLoader.getInstance();
        boolean spellEngineLoaded = loader.isModLoaded("spell_engine");
        boolean wizardsLoaded = loader.isModLoaded("wizards");
        boolean elementalWizardsLoaded = loader.isModLoaded("elemental_wizards_rpg");
        if (spellEngineLoaded && (wizardsLoaded || elementalWizardsLoaded)) {
            try {
                LOGGER.info(
                        "Enabling Spell Engine adapters; wizards_loaded={} elemental_wizards_loaded={}",
                        wizardsLoaded,
                        elementalWizardsLoaded);
                return new SpellEngineSpellCastExecutor(TRACE_SINK);
            }
            catch (LinkageError error) {
                LOGGER.error("Spell Engine adapter could not be linked; using unsupported fallback", error);
            }
        }
        else {
            LOGGER.warn(
                    "Spell Engine or compatible spell content is unavailable; spell dispatch is disabled; "
                            + "spell_engine={} wizards={} elemental_wizards_rpg={}",
                    spellEngineLoaded,
                    wizardsLoaded,
                    elementalWizardsLoaded);
        }

        return execution -> CastExecutionOutcome.rejected(
                CastResultCode.UNSUPPORTED_SPELL,
                "Spell Engine adapter is not installed for " + execution.options().spellId());
    }
}
