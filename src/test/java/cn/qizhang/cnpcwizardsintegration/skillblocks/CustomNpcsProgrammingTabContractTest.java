package cn.qizhang.cnpcwizardsintegration.skillblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class CustomNpcsProgrammingTabContractTest {
    @Test
    void fabricMetadataRegistersOptionalClientMixinAndCustomNpcsSuggestion() throws Exception {
        JsonObject metadata = readJson("fabric.mod.json");

        assertTrue(metadata.getAsJsonArray("mixins").asList().stream()
                .anyMatch(value -> value.getAsString().equals("cnpc_wizards_integration.client.mixins.json")));
        assertEquals(">=1.0.0", metadata.getAsJsonObject("suggests").get("customnpcs").getAsString());
    }

    @Test
    void mixinConfigTargetsOnlyTheClientMenuExtension() throws Exception {
        JsonObject mixins = readJson("cnpc_wizards_integration.client.mixins.json");

        assertTrue(mixins.get("required").getAsBoolean() == false);
        assertEquals("GuiNpcMenuMixin", mixins.getAsJsonArray("client").get(0).getAsString());
        assertEquals(0, mixins.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
    }

    @Test
    void chineseTabLabelIsPackaged() throws Exception {
        JsonObject language = readJson("assets/cnpc_wizards_integration/lang/zh_cn.json");

        assertEquals(
                "编程技能",
                language.get("menu.cnpc_wizards_integration.programming_skills").getAsString());
    }

    private static JsonObject readJson(String resource) throws Exception {
        try (var stream = CustomNpcsProgrammingTabContractTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + resource);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
