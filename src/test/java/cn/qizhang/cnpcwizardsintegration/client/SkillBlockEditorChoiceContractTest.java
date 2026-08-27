package cn.qizhang.cnpcwizardsintegration.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SkillBlockEditorChoiceContractTest {
    @Test
    void finiteValuesAndBlockVariantsUseVisibleSelectionScreens() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/cn/qizhang/cnpcwizardsintegration/client/SkillBlockEditorScreen.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("openBlockTypeChoices("));
        assertTrue(source.contains("openParameterChoices("));
        assertTrue(source.contains("SkillBlockChoiceCatalog.choicesForParameter"));
        assertFalse(source.contains("conditionCursor"));
        assertFalse(source.contains("abilityCursor"));
        assertFalse(source.contains("addNext("));
    }

    @Test
    void numericParametersRemainEditableTextFields() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/cn/qizhang/cnpcwizardsintegration/client/SkillBlockEditorScreen.java"),
                StandardCharsets.UTF_8);

        int noChoices = source.indexOf("if (choices.isEmpty())");
        int textField = source.indexOf("new TextFieldWidget(", noChoices);
        int choiceButton = source.indexOf("openParameterChoices(key, choices)", noChoices);
        assertTrue(noChoices >= 0);
        assertTrue(textField > noChoices);
        assertTrue(choiceButton > textField);
    }
}
