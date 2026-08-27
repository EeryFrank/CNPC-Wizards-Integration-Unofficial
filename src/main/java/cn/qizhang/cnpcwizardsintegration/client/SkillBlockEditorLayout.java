package cn.qizhang.cnpcwizardsintegration.client;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for the editor so Minecraft GUI scaling cannot push widgets off-screen. */
public final class SkillBlockEditorLayout {
    public static final int BLOCK_ROW_HEIGHT = 21;
    public static final int BLOCK_ROW_STEP = 23;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private SkillBlockEditorLayout() {
    }

    public enum Mode {
        TWO_PANEL,
        SINGLE_PANEL
    }

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean fitsInside(int viewportWidth, int viewportHeight) {
            return isEmpty()
                    || (x >= 0 && y >= 0 && right() <= viewportWidth && bottom() <= viewportHeight);
        }
    }

    public record Layout(
            int viewportWidth,
            int viewportHeight,
            Mode mode,
            boolean flowVisible,
            boolean editorVisible,
            Rect flowPanel,
            Rect editorPanel,
            Rect idField,
            Rect nameField,
            Rect flowTab,
            Rect editorTab,
            Rect listArea,
            List<Rect> flowActions,
            List<Rect> paletteButtons,
            List<Rect> parameterFields,
            List<Rect> editorActions,
            int headerLabelY,
            int sectionLabelY,
            int npcLineY,
            int selectedLineY,
            int parameterTitleY,
            int statusLineY,
            int hintLineY,
            int visibleRows) {

        public Layout {
            flowActions = List.copyOf(flowActions);
            paletteButtons = List.copyOf(paletteButtons);
            parameterFields = List.copyOf(parameterFields);
            editorActions = List.copyOf(editorActions);
        }

        public Rect blockRow(int localIndex) {
            if (localIndex < 0 || localIndex >= visibleRows) {
                return Rect.EMPTY;
            }
            return new Rect(
                    listArea.x() + 10,
                    listArea.y() + localIndex * BLOCK_ROW_STEP,
                    Math.max(1, listArea.width() - 10),
                    BLOCK_ROW_HEIGHT);
        }

        public List<Rect> visibleWidgetRects() {
            List<Rect> result = new ArrayList<>();
            result.add(idField);
            result.add(nameField);
            if (mode == Mode.SINGLE_PANEL) {
                result.add(flowTab);
                result.add(editorTab);
            }
            if (flowVisible) {
                for (int index = 0; index < visibleRows; index++) {
                    result.add(blockRow(index));
                }
                result.addAll(flowActions);
            }
            if (editorVisible) {
                result.addAll(paletteButtons);
                result.addAll(parameterFields);
                result.addAll(editorActions);
            }
            return result.stream().filter(rect -> !rect.isEmpty()).toList();
        }
    }

    public static Layout calculate(int viewportWidth, int viewportHeight, boolean singlePanelFlowView) {
        int safeWidth = Math.max(320, viewportWidth);
        int safeHeight = Math.max(240, viewportHeight);
        boolean singlePanel = safeWidth < 600 || safeHeight < 330;
        return singlePanel
                ? calculateSinglePanel(safeWidth, safeHeight, singlePanelFlowView)
                : calculateTwoPanel(safeWidth, safeHeight);
    }

    private static Layout calculateTwoPanel(int width, int height) {
        int margin = 8;
        int panelTop = 18;
        int contentWidth = Math.min(1000, width - margin * 2);
        int contentX = (width - contentWidth) / 2;
        int panelHeight = height - panelTop - margin;
        int panelGap = 8;
        int availableWidth = contentWidth - panelGap;
        int minimumEditorWidth = 235;
        int flowWidth = clamp((int) Math.round(availableWidth * 0.54), 300, 430);
        flowWidth = Math.min(flowWidth, availableWidth - minimumEditorWidth);
        int editorWidth = availableWidth - flowWidth;
        Rect flowPanel = new Rect(contentX, panelTop, flowWidth, panelHeight);
        Rect editorPanel = new Rect(flowPanel.right() + panelGap, panelTop, editorWidth, panelHeight);

        int fieldY = panelTop + 12;
        Rect idField = new Rect(flowPanel.x() + 38, fieldY, flowPanel.width() - 48, 20);
        Rect nameField = new Rect(editorPanel.x() + 48, fieldY, editorPanel.width() - 58, 20);
        int sectionLabelY = panelTop + 40;

        List<Rect> flowActions = grid(
                flowPanel.x() + 10,
                flowPanel.bottom() - 54,
                flowPanel.width() - 20,
                3,
                2,
                BUTTON_HEIGHT,
                5);
        int listTop = sectionLabelY + 16;
        int listBottom = flowActions.getFirst().y() - 6;
        int visibleRows = Math.max(3, (listBottom - listTop) / BLOCK_ROW_STEP);
        Rect listArea = new Rect(
                flowPanel.x() + 10,
                listTop,
                flowPanel.width() - 20,
                visibleRows * BLOCK_ROW_STEP - 2);

        int editorInnerX = editorPanel.x() + 10;
        int editorInnerWidth = editorPanel.width() - 20;
        List<Rect> paletteButtons = grid(
                editorInnerX,
                sectionLabelY + 16,
                editorInnerWidth,
                3,
                2,
                BUTTON_HEIGHT,
                4);
        int paletteBottom = paletteButtons.getLast().bottom();
        int npcLineY = paletteBottom + 7;
        int selectedLineY = npcLineY + 12;
        int parameterTitleY = selectedLineY + 14;

        List<Rect> editorActions = grid(
                editorInnerX,
                editorPanel.bottom() - 54,
                editorInnerWidth,
                2,
                2,
                BUTTON_HEIGHT,
                5);
        int statusLineY = editorActions.getFirst().y() - 25;
        int hintLineY = statusLineY + 12;
        List<Rect> parameterFields = parameterGrid(
                editorInnerX,
                parameterTitleY + 13,
                editorInnerWidth,
                3,
                2,
                Math.max(24, (statusLineY - parameterTitleY - 13) / 2));

        return new Layout(
                width,
                height,
                Mode.TWO_PANEL,
                true,
                true,
                flowPanel,
                editorPanel,
                idField,
                nameField,
                Rect.EMPTY,
                Rect.EMPTY,
                listArea,
                flowActions,
                paletteButtons,
                parameterFields,
                editorActions,
                fieldY + 6,
                sectionLabelY,
                npcLineY,
                selectedLineY,
                parameterTitleY,
                statusLineY,
                hintLineY,
                visibleRows);
    }

    private static Layout calculateSinglePanel(int width, int height, boolean flowView) {
        int margin = 6;
        int panelTop = 18;
        Rect panel = new Rect(margin, panelTop, width - margin * 2, height - panelTop - margin);
        int innerX = panel.x() + 8;
        int innerWidth = panel.width() - 16;
        int fieldGap = 5;
        int labelAllowance = 28;
        int fieldWidth = (innerWidth - fieldGap) / 2;
        int fieldY = panelTop + 8;
        Rect idField = new Rect(innerX + labelAllowance, fieldY, fieldWidth - labelAllowance, 18);
        Rect nameField = new Rect(
                innerX + fieldWidth + fieldGap + labelAllowance,
                fieldY,
                innerWidth - fieldWidth - fieldGap - labelAllowance,
                18);

        int tabY = fieldY + 22;
        int tabWidth = (innerWidth - GAP) / 2;
        Rect flowTab = new Rect(innerX, tabY, tabWidth, 19);
        Rect editorTab = new Rect(innerX + tabWidth + GAP, tabY, innerWidth - tabWidth - GAP, 19);
        int sectionLabelY = tabY + 25;

        List<Rect> flowActions = grid(
                innerX,
                panel.bottom() - 46,
                innerWidth,
                3,
                2,
                19,
                4);
        int listTop = sectionLabelY + 15;
        int listBottom = flowActions.getFirst().y() - 5;
        int visibleRows = Math.max(3, (listBottom - listTop) / BLOCK_ROW_STEP);
        Rect listArea = new Rect(innerX, listTop, innerWidth, visibleRows * BLOCK_ROW_STEP - 2);

        List<Rect> paletteButtons = grid(
                innerX,
                sectionLabelY + 14,
                innerWidth,
                3,
                2,
                19,
                3);
        boolean veryShort = height < 260;
        int selectedLineY = veryShort ? -1 : paletteButtons.getLast().bottom() + 6;
        int parameterTitleY = veryShort
                ? paletteButtons.getLast().bottom() + 2
                : selectedLineY + 13;
        List<Rect> editorActions = grid(
                innerX,
                panel.bottom() - 42,
                innerWidth,
                2,
                2,
                19,
                3);
        int parameterTop = parameterTitleY + (veryShort ? 8 : 11);
        int parameterBottom = editorActions.getFirst().y() - 3;
        int parameterStep = Math.max(veryShort ? 27 : 22, (parameterBottom - parameterTop) / 2);
        List<Rect> parameterFields = parameterGrid(
                innerX,
                parameterTop,
                innerWidth,
                3,
                2,
                parameterStep);

        return new Layout(
                width,
                height,
                Mode.SINGLE_PANEL,
                flowView,
                !flowView,
                panel,
                panel,
                idField,
                nameField,
                flowTab,
                editorTab,
                listArea,
                flowActions,
                paletteButtons,
                parameterFields,
                editorActions,
                fieldY + 5,
                sectionLabelY,
                -1,
                selectedLineY,
                parameterTitleY,
                -1,
                -1,
                visibleRows);
    }

    private static List<Rect> grid(
            int x,
            int y,
            int width,
            int columns,
            int rows,
            int itemHeight,
            int rowGap) {
        int itemWidth = (width - GAP * (columns - 1)) / columns;
        List<Rect> result = new ArrayList<>(columns * rows);
        for (int index = 0; index < columns * rows; index++) {
            int column = index % columns;
            int row = index / columns;
            int itemX = x + column * (itemWidth + GAP);
            int actualWidth = column == columns - 1 ? x + width - itemX : itemWidth;
            result.add(new Rect(itemX, y + row * (itemHeight + rowGap), actualWidth, itemHeight));
        }
        return result;
    }

    private static List<Rect> parameterGrid(
            int x,
            int y,
            int width,
            int columns,
            int rows,
            int rowStep) {
        int itemWidth = (width - GAP * (columns - 1)) / columns;
        List<Rect> result = new ArrayList<>(columns * rows);
        for (int index = 0; index < columns * rows; index++) {
            int column = index % columns;
            int row = index / columns;
            int itemX = x + column * (itemWidth + GAP);
            int actualWidth = column == columns - 1 ? x + width - itemX : itemWidth;
            result.add(new Rect(itemX, y + row * rowStep + 9, actualWidth, 18));
        }
        return result;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
