package cn.qizhang.cnpcwizardsintegration.client;

import cn.qizhang.cnpcwizardsintegration.skillblocks.BindSkillBlueprintPayload;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlock;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlockChoiceCatalog;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlockType;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlueprint;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlueprintCodec;
import cn.qizhang.cnpcwizardsintegration.skillblocks.SkillBlueprintValidator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

/** First-pass block editor: editable sequential blocks with visible connectors and server binding. */
public final class SkillBlockEditorScreen extends Screen {
    private static final int MAX_PARAMETER_FIELDS = 6;

    private final SkillBlueprintValidator validator = new SkillBlueprintValidator();
    private final List<SkillBlock> blocks = new ArrayList<>();
    private final TextFieldWidget[] parameterFields = new TextFieldWidget[MAX_PARAMETER_FIELDS];
    private final String[] parameterKeys = new String[MAX_PARAMETER_FIELDS];
    private final int boundEntityId;
    private final String boundEntityName;
    private final Screen parentScreen;
    private String blueprintId;
    private String blueprintName;
    private TextFieldWidget idField;
    private TextFieldWidget nameField;
    private int selectedIndex = -1;
    private int page;
    private SkillBlockEditorLayout.Layout layout;
    private boolean singlePanelFlowView = true;
    private String status = "从当前 NPC 的“编程技能”标签进入；编辑后保存并绑定";
    private int statusColor = 0xA8D8FF;

    public SkillBlockEditorScreen(int boundEntityId, String boundEntityName, Screen parentScreen) {
        super(Text.literal("CNPC Wizards 积木技能编辑器（初版）"));
        this.boundEntityId = boundEntityId;
        this.boundEntityName = boundEntityName;
        this.parentScreen = parentScreen;
        SkillBlueprint draft = ClientSkillBlueprintStore.loadOrStarter();
        blueprintId = draft.id();
        blueprintName = draft.name();
        blocks.addAll(draft.blocks());
        if (!blocks.isEmpty()) {
            selectedIndex = 0;
        }
    }

    @Override
    protected void init() {
        layout = SkillBlockEditorLayout.calculate(width, height, singlePanelFlowView);
        clampPage();
        for (int index = 0; index < parameterFields.length; index++) {
            parameterFields[index] = null;
            parameterKeys[index] = null;
        }

        SkillBlockEditorLayout.Rect idRect = layout.idField();
        idField = new TextFieldWidget(
                textRenderer,
                idRect.x(),
                idRect.y(),
                idRect.width(),
                idRect.height(),
                Text.literal("方案 ID"));
        idField.setMaxLength(64);
        idField.setText(blueprintId);
        addDrawableChild(idField);
        SkillBlockEditorLayout.Rect nameRect = layout.nameField();
        nameField = new TextFieldWidget(
                textRenderer,
                nameRect.x(),
                nameRect.y(),
                nameRect.width(),
                nameRect.height(),
                Text.literal("方案名称"));
        nameField.setMaxLength(40);
        nameField.setText(blueprintName);
        addDrawableChild(nameField);

        if (layout.mode() == SkillBlockEditorLayout.Mode.SINGLE_PANEL) {
            addButton(layout.flowTab(), singlePanelFlowView ? "流程 ✓" : "流程", button -> showSinglePanel(true));
            addButton(layout.editorTab(), singlePanelFlowView ? "积木与参数" : "积木与参数 ✓", button -> showSinglePanel(false));
        }

        if (layout.flowVisible()) {
            int pageSize = pageSize();
            int start = page * pageSize;
            int end = Math.min(blocks.size(), start + pageSize);
            for (int index = start; index < end; index++) {
                final int blockIndex = index;
                SkillBlock block = blocks.get(index);
                SkillBlockEditorLayout.Rect row = layout.blockRow(index - start);
                String label = textRenderer.trimToWidth(blockLabel(index, block), Math.max(1, row.width() - 8));
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(label),
                                button -> select(blockIndex))
                        .dimensions(row.x(), row.y(), row.width(), row.height())
                        .build());
            }

            List<SkillBlockEditorLayout.Rect> actions = layout.flowActions();
            addButton(actions.get(0), "上一页", button -> changePage(-1));
            addButton(actions.get(1), "下一页", button -> changePage(1));
            addButton(actions.get(2), "上移", button -> moveSelected(-1));
            addButton(actions.get(3), "下移", button -> moveSelected(1));
            addButton(actions.get(4), "删除", button -> deleteSelected());
            addButton(
                    actions.get(5),
                    layout.mode() == SkillBlockEditorLayout.Mode.SINGLE_PANEL ? "编辑参数" : "应用参数",
                    button -> {
                        if (layout.mode() == SkillBlockEditorLayout.Mode.SINGLE_PANEL) {
                            showSinglePanel(false);
                        }
                        else {
                            applyParameterFields();
                        }
                    });
        }

        if (layout.editorVisible()) {
            List<SkillBlockEditorLayout.Rect> palette = layout.paletteButtons();
            addButton(palette.get(0), "＋已有法术", button -> addBlock(SkillBlockType.CAST_SPELL));
            addButton(palette.get(1), "＋条件（选择）", button -> openBlockTypeChoices(
                    "选择条件积木", SkillBlockChoiceCatalog.conditionTypes()));
            addButton(palette.get(2), "＋流程（选择）", button -> openBlockTypeChoices(
                    "选择流程积木", SkillBlockChoiceCatalog.flowTypes()));
            addButton(palette.get(3), "＋能力（选择）", button -> openBlockTypeChoices(
                    "选择基础能力", SkillBlockChoiceCatalog.abilityTypes()));
            addButton(palette.get(4), "＋动作（选择）", button -> openBlockTypeChoices(
                    "选择动作", SkillBlockChoiceCatalog.animationTypes()));
            addButton(palette.get(5), "＋特效（选择）", button -> openBlockTypeChoices(
                    "选择特效", SkillBlockChoiceCatalog.effectTypes()));

            populateParameterFields();

            List<SkillBlockEditorLayout.Rect> editorActions = layout.editorActions();
            addButton(editorActions.get(0), "保存本地", button -> saveLocal());
            addButton(editorActions.get(1), "保存并绑定当前 NPC", button -> bindCurrentNpc());
            addButton(editorActions.get(2), "恢复示例", button -> resetStarter());
            addButton(editorActions.get(3), "关闭", button -> close());
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        renderResponsive(context);
    }

    private void renderResponsive(DrawContext context) {
        if (layout == null) {
            layout = SkillBlockEditorLayout.calculate(width, height, singlePanelFlowView);
        }
        fillPanel(context, layout.flowPanel(), 0xB0182230);
        if (layout.mode() == SkillBlockEditorLayout.Mode.TWO_PANEL) {
            fillPanel(context, layout.editorPanel(), 0xB01C2638);
        }
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFF);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("ID"),
                Math.max(layout.flowPanel().x() + 4, layout.idField().x() - 22),
                layout.headerLabelY(),
                0xB8C8D8);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("名称"),
                Math.max(layout.editorPanel().x() + 4, layout.nameField().x() - 34),
                layout.headerLabelY(),
                0xB8C8D8);

        if (layout.flowVisible()) {
            drawTrimmed(
                    context,
                    "顺序积木链（页 " + (page + 1) + " / " + (maximumPage() + 1) + "）",
                    layout.listArea().x(),
                    layout.sectionLabelY(),
                    layout.listArea().width(),
                    0xFFD46A);
            int start = page * pageSize();
            int end = Math.min(blocks.size(), start + pageSize());
            for (int index = start; index < end; index++) {
                SkillBlockEditorLayout.Rect row = layout.blockRow(index - start);
                int color = index == selectedIndex ? 0xFF55C8FF : colorFor(blocks.get(index).type());
                context.fill(layout.listArea().x() + 1, row.y() + 3, layout.listArea().x() + 6, row.y() + 18, color);
                if (index < blocks.size() - 1 && index < end - 1) {
                    int connectorX = row.x() + row.width() / 2;
                    context.fill(connectorX, row.bottom(), connectorX + 2, row.bottom() + 2, 0xFF7FA5C8);
                }
            }
        }

        if (layout.editorVisible()) {
            SkillBlock selected = selectedBlock();
            String selectedLabel = selected == null
                    ? "未选择积木"
                    : "当前：" + selected.type().category() + " / " + selected.type().displayName();
            int editorTextX = layout.paletteButtons().getFirst().x();
            int editorTextWidth = layout.editorPanel().right() - editorTextX - 10;
            if (layout.mode() == SkillBlockEditorLayout.Mode.SINGLE_PANEL) {
                drawTrimmed(context, status, editorTextX, layout.sectionLabelY(), editorTextWidth, statusColor);
            }
            else {
                drawTrimmed(
                        context,
                        "积木库：点击按钮加入该类别",
                        editorTextX,
                        layout.sectionLabelY(),
                        editorTextWidth,
                        0xFFD46A);
                drawTrimmed(
                        context,
                        "当前 NPC：" + boundEntityName,
                        editorTextX,
                        layout.npcLineY(),
                        editorTextWidth,
                        0x77FFAA);
            }
            drawTrimmed(
                    context,
                    selectedLabel,
                    editorTextX,
                    layout.selectedLineY(),
                    editorTextWidth,
                    0x7FE8FF);
            drawTrimmed(
                    context,
                    "参数（留空或非法值会被服务器拒绝）",
                    editorTextX,
                    layout.parameterTitleY(),
                    editorTextWidth,
                    0xC7D6E8);
            for (int index = 0; index < parameterFields.length; index++) {
                if (parameterKeys[index] != null) {
                    SkillBlockEditorLayout.Rect field = layout.parameterFields().get(index);
                    drawTrimmed(
                            context,
                            parameterDisplayName(parameterKeys[index]),
                            field.x(),
                            field.y() - 9,
                            field.width(),
                            0xAFC4D8);
                }
            }
            if (layout.statusLineY() >= 0) {
                drawTrimmed(
                        context,
                        status,
                        layout.editorPanel().x() + 10,
                        layout.statusLineY(),
                        layout.editorPanel().width() - 20,
                        statusColor);
            }
            if (layout.hintLineY() >= 0) {
                drawTrimmed(
                        context,
                        "草稿保存在客户端配置目录 · 伤害/治疗≤40 · 粒子≤100 · 绑定需 OP 2",
                        layout.editorPanel().x() + 10,
                        layout.hintLineY(),
                        layout.editorPanel().width() - 20,
                        0x8290A0);
            }
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        super.resize(client, width, height);
    }

    private void fillPanel(DrawContext context, SkillBlockEditorLayout.Rect panel, int color) {
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), color);
    }

    private void drawTrimmed(DrawContext context, String value, int x, int y, int maximumWidth, int color) {
        if (y < 0 || maximumWidth <= 0) {
            return;
        }
        context.drawTextWithShadow(
                textRenderer,
                Text.literal(textRenderer.trimToWidth(value, maximumWidth)),
                x,
                y,
                color);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null && parentScreen != null) {
            client.setScreen(parentScreen);
            return;
        }
        super.close();
    }

    private void addButton(
            SkillBlockEditorLayout.Rect rectangle,
            String label,
            ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(rectangle.x(), rectangle.y(), rectangle.width(), rectangle.height())
                .build());
    }

    private void showSinglePanel(boolean flowView) {
        if (layout == null || layout.mode() != SkillBlockEditorLayout.Mode.SINGLE_PANEL
                || singlePanelFlowView == flowView) {
            return;
        }
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        singlePanelFlowView = flowView;
        refreshWidgets();
    }

    private void select(int index) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        selectedIndex = index;
        refreshWidgets();
    }

    private void addBlock(SkillBlockType type) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        int requiredSlots = type == SkillBlockType.LOOP_START ? 2 : 1;
        if (blocks.size() + requiredSlots > SkillBlueprintValidator.MAX_BLOCKS) {
            setError("已达到 32 块上限");
            return;
        }
        int insert = Math.max(1, blocks.size() - 1);
        blocks.add(insert, SkillBlock.create(type));
        if (type == SkillBlockType.LOOP_START) {
            blocks.add(insert + 1, SkillBlock.create(SkillBlockType.LOOP_END));
        }
        selectedIndex = insert;
        page = selectedIndex / pageSize();
        refreshWidgets();
        setInfo(type == SkillBlockType.LOOP_START
                ? "已加入一对循环；请把需要重复的积木放在中间"
                : "已加入：" + type.displayName());
    }

    private void moveSelected(int direction) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        if (selectedIndex <= 0 || selectedIndex >= blocks.size() - 1) {
            setError("触发块和结束块不能移动");
            return;
        }
        int destination = selectedIndex + direction;
        if (destination <= 0 || destination >= blocks.size() - 1) {
            setError("不能越过触发块或结束块");
            return;
        }
        SkillBlock selected = blocks.remove(selectedIndex);
        blocks.add(destination, selected);
        selectedIndex = destination;
        page = selectedIndex / pageSize();
        refreshWidgets();
    }

    private void deleteSelected() {
        captureHeaderFields();
        if (selectedIndex <= 0 || selectedIndex >= blocks.size() - 1) {
            setError("触发块和结束块不能删除");
            return;
        }
        blocks.remove(selectedIndex);
        selectedIndex = Math.min(selectedIndex, blocks.size() - 1);
        page = selectedIndex / pageSize();
        refreshWidgets();
    }

    private void changePage(int delta) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        page = Math.max(0, Math.min(maximumPage(), page + delta));
        refreshWidgets();
    }

    private int pageSize() {
        return layout == null ? 8 : Math.max(1, layout.visibleRows());
    }

    private int maximumPage() {
        return Math.max(0, (blocks.size() - 1) / pageSize());
    }

    private void clampPage() {
        page = Math.max(0, Math.min(maximumPage(), page));
    }

    private void applyParameterFields() {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        refreshWidgets();
        setInfo("参数已应用到当前积木");
    }

    private void applyParameterFieldsWithoutRefresh() {
        SkillBlock selected = selectedBlock();
        if (selected == null) {
            return;
        }
        Map<String, String> updated = new LinkedHashMap<>(selected.parameters());
        for (int index = 0; index < parameterFields.length; index++) {
            if (parameterKeys[index] != null && parameterFields[index] != null) {
                updated.put(parameterKeys[index], parameterFields[index].getText().trim());
            }
        }
        blocks.set(selectedIndex, selected.withParameters(updated));
    }

    private void populateParameterFields() {
        for (int index = 0; index < parameterFields.length; index++) {
            parameterKeys[index] = null;
            parameterFields[index] = null;
        }
        SkillBlock selected = selectedBlock();
        if (selected == null) {
            return;
        }
        int index = 0;
        for (Map.Entry<String, String> parameter : selected.parameters().entrySet()) {
            if (index >= parameterFields.length) {
                break;
            }
            parameterKeys[index] = parameter.getKey();
            SkillBlockEditorLayout.Rect fieldRect = layout.parameterFields().get(index);
            List<SkillBlockChoiceCatalog.Choice> choices =
                    SkillBlockChoiceCatalog.choicesForParameter(parameter.getKey());
            if (choices.isEmpty()) {
                TextFieldWidget field = new TextFieldWidget(
                        textRenderer,
                        fieldRect.x(),
                        fieldRect.y(),
                        fieldRect.width(),
                        fieldRect.height(),
                        Text.empty());
                field.setMaxLength(128);
                field.setText(parameter.getValue());
                parameterFields[index] = field;
                addDrawableChild(field);
            }
            else {
                String key = parameter.getKey();
                String label = choiceDisplayName(choices, parameter.getValue()) + "  ▼";
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(textRenderer.trimToWidth(label, Math.max(1, fieldRect.width() - 8))),
                                button -> openParameterChoices(key, choices))
                        .dimensions(fieldRect.x(), fieldRect.y(), fieldRect.width(), fieldRect.height())
                        .build());
            }
            index++;
        }
    }

    private void openBlockTypeChoices(String title, List<SkillBlockType> types) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        if (client == null) {
            return;
        }
        List<SkillChoiceScreen.Entry<SkillBlockType>> entries = types.stream()
                .map(type -> new SkillChoiceScreen.Entry<>(type, type.displayName(), type.category()))
                .toList();
        client.setScreen(new SkillChoiceScreen<>(
                this,
                title,
                "点选后才会加入积木链；循环开始会自动配对循环结束",
                entries,
                this::addBlock));
    }

    private void openParameterChoices(String key, List<SkillBlockChoiceCatalog.Choice> choices) {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        if (client == null) {
            return;
        }
        List<SkillChoiceScreen.Entry<String>> entries = choices.stream()
                .map(choice -> new SkillChoiceScreen.Entry<>(
                        choice.value(), choice.displayName(), choice.value()))
                .toList();
        client.setScreen(new SkillChoiceScreen<>(
                this,
                "选择" + parameterDisplayName(key),
                "有限选项由客户端和服务端共用同一目录，避免填写错误",
                entries,
                value -> setParameterChoice(key, value)));
    }

    private void setParameterChoice(String key, String value) {
        SkillBlock selected = selectedBlock();
        if (selected == null) {
            return;
        }
        Map<String, String> updated = new LinkedHashMap<>(selected.parameters());
        updated.put(key, value);
        blocks.set(selectedIndex, selected.withParameters(updated));
        setInfo(parameterDisplayName(key) + "已选择：" + value);
        refreshWidgets();
    }

    private static String choiceDisplayName(List<SkillBlockChoiceCatalog.Choice> choices, String value) {
        return choices.stream()
                .filter(choice -> choice.value().equals(value))
                .map(SkillBlockChoiceCatalog.Choice::displayName)
                .findFirst()
                .orElse("当前值：" + value);
    }

    private static String parameterDisplayName(String key) {
        return switch (key) {
            case "spell_id" -> "法术";
            case "target_strategy" -> "目标方式";
            case "effect_id", "status_effect_id" -> "状态效果";
            case "particle_id" -> "粒子";
            case "sound_id" -> "声音";
            case "power_multiplier" -> "法术强度倍率";
            case "damage_multiplier" -> "伤害倍率";
            case "healing_multiplier" -> "治疗倍率";
            case "cooldown_ticks" -> "冷却（tick）";
            case "duration_ticks" -> "持续时间（tick）";
            case "amplifier" -> "效果等级";
            case "repeat_count" -> "循环次数";
            case "ticks" -> "等待（tick）";
            case "max_distance" -> "最大距离";
            case "amount" -> "数值";
            case "strength" -> "强度";
            case "seconds" -> "秒数";
            case "count" -> "数量";
            case "speed" -> "速度";
            case "radius" -> "半径";
            case "volume" -> "音量";
            case "pitch" -> "音调";
            default -> key;
        };
    }

    private void saveLocal() {
        try {
            SkillBlueprint blueprint = currentBlueprint();
            SkillBlueprintValidator.ValidationResult result = validator.validate(blueprint);
            if (!result.valid()) {
                setError("不能保存：" + result.summary());
                return;
            }
            ClientSkillBlueprintStore.save(blueprint);
            setInfo("草稿已保存");
        }
        catch (IllegalArgumentException | IOException error) {
            setError("保存失败：" + error.getMessage());
        }
    }

    private void bindCurrentNpc() {
        try {
            SkillBlueprint blueprint = currentBlueprint();
            SkillBlueprintValidator.ValidationResult result = validator.validate(blueprint);
            if (!result.valid()) {
                setError("不能绑定：" + result.summary());
                return;
            }
            if (client == null || client.world == null
                    || !(client.world.getEntityById(boundEntityId) instanceof LivingEntity living)
                    || living instanceof PlayerEntity) {
                setError("当前 NPC 已离开客户端世界，请返回 NPC 编辑页后重试");
                return;
            }
            if (!ClientPlayNetworking.canSend(BindSkillBlueprintPayload.ID)) {
                setError("当前服务器未开放积木绑定接口或版本不匹配");
                return;
            }
            ClientSkillBlueprintStore.save(blueprint);
            ClientPlayNetworking.send(new BindSkillBlueprintPayload(
                    living.getId(),
                    SkillBlueprintCodec.toJson(blueprint)));
            setInfo("绑定请求已发送；请查看聊天栏的服务端结果");
        }
        catch (IllegalArgumentException | IOException error) {
            setError("绑定失败：" + error.getMessage());
        }
    }

    private SkillBlueprint currentBlueprint() {
        captureHeaderFields();
        applyParameterFieldsWithoutRefresh();
        return new SkillBlueprint(blueprintId, blueprintName, blocks);
    }

    private void captureHeaderFields() {
        if (idField != null) {
            blueprintId = idField.getText().trim();
        }
        if (nameField != null) {
            blueprintName = nameField.getText().trim();
        }
    }

    private void resetStarter() {
        SkillBlueprint starter = SkillBlueprint.starter();
        blocks.clear();
        blocks.addAll(starter.blocks());
        blueprintId = starter.id();
        blueprintName = starter.name();
        selectedIndex = 0;
        page = 0;
        refreshWidgets();
        setInfo("已恢复受击火球示例");
    }

    private void refreshWidgets() {
        clearChildren();
        init();
    }

    private SkillBlock selectedBlock() {
        if (selectedIndex < 0 || selectedIndex >= blocks.size()) {
            return null;
        }
        return blocks.get(selectedIndex);
    }

    private static String blockLabel(int index, SkillBlock block) {
        String suffix = block.parameters().isEmpty()
                ? ""
                : "  " + block.parameters().entrySet().stream()
                        .limit(2)
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("");
        return String.format("%02d  [%s] %s%s", index + 1, block.type().category(), block.type().displayName(), suffix);
    }

    private static int colorFor(SkillBlockType type) {
        return switch (type.category()) {
            case "触发" -> 0xFFF4C95D;
            case "目标" -> 0xFF75B9FF;
            case "条件" -> 0xFFB088F9;
            case "法术" -> 0xFFFF6B9A;
            case "能力" -> 0xFF61D095;
            case "动作" -> 0xFFFF9F43;
            case "特效" -> 0xFF55DDE0;
            default -> 0xFFAAB6C4;
        };
    }

    private void setInfo(String message) {
        status = message;
        statusColor = 0x77FFAA;
    }

    private void setError(String message) {
        status = message;
        statusColor = 0xFF7777;
    }
}
