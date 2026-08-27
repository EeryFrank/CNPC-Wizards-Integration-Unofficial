package cn.qizhang.cnpcwizardsintegration.client;

import cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlueprint;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlueprintCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Client-local draft storage. Binding still requires server-side validation and permission. */
final class ClientSkillBlueprintStore {
    private static final Path DRAFT_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CnPcWizardsIntegration.MOD_ID)
            .resolve("skill_block_draft.json");

    private ClientSkillBlueprintStore() {
    }

    static SkillBlueprint loadOrStarter() {
        try {
            if (Files.isRegularFile(DRAFT_FILE)) {
                return SkillBlueprintCodec.fromJson(Files.readString(DRAFT_FILE, StandardCharsets.UTF_8));
            }
        }
        catch (Exception ignored) {
            // The editor reports a fresh starter and lets the user overwrite a corrupt draft.
        }
        return SkillBlueprint.starter();
    }

    static void save(SkillBlueprint blueprint) throws IOException {
        Files.createDirectories(DRAFT_FILE.getParent());
        Files.writeString(DRAFT_FILE, SkillBlueprintCodec.toJson(blueprint), StandardCharsets.UTF_8);
    }

    static Path path() {
        return DRAFT_FILE;
    }
}
