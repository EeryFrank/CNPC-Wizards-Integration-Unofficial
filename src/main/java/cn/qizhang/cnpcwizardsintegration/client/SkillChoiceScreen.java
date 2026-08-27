package cn.qizhang.cnpcwizardsintegration.client;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Small responsive selection page shared by block-type and categorical-parameter choices. */
final class SkillChoiceScreen<T> extends Screen {
    record Entry<T>(T value, String label, String detail) {
        Entry {
            Objects.requireNonNull(value, "value");
            label = Objects.requireNonNull(label, "label");
            detail = detail == null ? "" : detail;
        }
    }

    private final Screen parent;
    private final List<Entry<T>> entries;
    private final Consumer<T> selection;
    private final String hint;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int page;
    private int pageSize = 1;

    SkillChoiceScreen(
            Screen parent,
            String title,
            String hint,
            List<Entry<T>> entries,
            Consumer<T> selection) {
        super(Text.literal(title));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.hint = hint == null ? "" : hint;
        this.entries = List.copyOf(entries);
        this.selection = Objects.requireNonNull(selection, "selection");
    }

    @Override
    protected void init() {
        int margin = Math.max(6, Math.min(16, width / 30));
        panelWidth = Math.min(Math.max(220, width - margin * 2), 720);
        panelHeight = Math.min(Math.max(150, height - margin * 2), 430);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int innerWidth = panelWidth - 20;
        int columns = innerWidth >= 570 ? 3 : innerWidth >= 350 ? 2 : 1;
        int gap = 6;
        int buttonWidth = (innerWidth - gap * (columns - 1)) / columns;
        int buttonHeight = 24;
        int startY = panelTop + 45;
        int availableHeight = Math.max(buttonHeight, panelHeight - 82);
        int visibleRows = Math.max(1, availableHeight / (buttonHeight + gap));
        pageSize = columns * visibleRows;
        int maximumPage = maximumPage();
        page = Math.max(0, Math.min(page, maximumPage));
        int firstEntry = page * pageSize;
        int lastEntry = Math.min(entries.size(), firstEntry + pageSize);

        for (int index = firstEntry; index < lastEntry; index++) {
            Entry<T> entry = entries.get(index);
            int visibleIndex = index - firstEntry;
            int column = visibleIndex % columns;
            int row = visibleIndex / columns;
            int x = panelLeft + 10 + column * (buttonWidth + gap);
            int y = startY + row * (buttonHeight + gap);
            String fullLabel = entry.detail().isBlank()
                    ? entry.label()
                    : entry.label() + " · " + entry.detail();
            String label = textRenderer.trimToWidth(fullLabel, Math.max(1, buttonWidth - 8));
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> choose(entry))
                    .dimensions(x, y, buttonWidth, buttonHeight)
                    .build());
        }

        int closeWidth = Math.min(110, Math.max(60, (innerWidth - gap * 2) / 3));
        int actionY = panelTop + panelHeight - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal("上一页"), button -> changePage(-1))
                .dimensions(panelLeft + 10, actionY, closeWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("下一页"), button -> changePage(1))
                .dimensions(panelLeft + 10 + closeWidth + gap, actionY, closeWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(panelLeft + panelWidth - closeWidth - 10, actionY, closeWidth, 20)
                .build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xE0182230);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(textRenderer.trimToWidth(hint, Math.max(1, panelWidth - 20))),
                width / 2,
                panelTop + 27,
                0xA8D8FF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void choose(Entry<T> entry) {
        selection.accept(entry.value());
        close();
    }

    private int maximumPage() {
        return Math.max(0, (entries.size() - 1) / Math.max(1, pageSize));
    }

    private void changePage(int delta) {
        int updated = Math.max(0, Math.min(maximumPage(), page + delta));
        if (updated == page) {
            return;
        }
        page = updated;
        clearChildren();
        init();
    }
}
