package cn.qizhang.cnpcwizardsintegration.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkillBlockEditorRenderContractTest {
    @Test
    void customLabelsRenderAfterTheSingleBackgroundBlur() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/cn/qizhang/cnpcwizardsintegration/client/SkillBlockEditorScreen.java"),
                StandardCharsets.UTF_8);

        assertFalse(source.contains("public void render(DrawContext"));
        int override = source.indexOf("public void renderBackground(DrawContext");
        int vanillaBackground = source.indexOf("super.renderBackground(context", override);
        int customLabels = source.indexOf("renderResponsive(context)", override);
        assertTrue(override >= 0, "editor must own the background/layer order");
        assertTrue(vanillaBackground > override, "vanilla blur must render once");
        assertTrue(customLabels > vanillaBackground, "custom labels must be drawn after blur");
    }
}
