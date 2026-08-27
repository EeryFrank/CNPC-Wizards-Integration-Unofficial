package cn.qizhang.cnpcwizardsintegration.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillBlockEditorLayoutTest {
    @Test
    void reportedAutoScaleViewportFitsWithoutClipping() {
        SkillBlockEditorLayout.Layout layout = SkillBlockEditorLayout.calculate(640, 400, true);

        assertEquals(SkillBlockEditorLayout.Mode.TWO_PANEL, layout.mode());
        assertAllVisibleWidgetsFit(layout);
        assertEquals(640, layout.viewportWidth());
        assertEquals(400, layout.viewportHeight());
        assertTrue(layout.editorPanel().right() <= 632);
        assertTrue(layout.flowPanel().bottom() <= 392);
    }

    @Test
    void guiScaleAndBoundaryViewportsFit() {
        int[][] viewports = {
            {639, 399},
            {640, 360},
            {640, 400},
            {853, 533},
            {960, 540},
            {1280, 800},
            {2559, 1599},
            {480, 270},
            {320, 240}
        };

        for (int[] viewport : viewports) {
            assertAllVisibleWidgetsFit(SkillBlockEditorLayout.calculate(viewport[0], viewport[1], true));
            assertAllVisibleWidgetsFit(SkillBlockEditorLayout.calculate(viewport[0], viewport[1], false));
        }
    }

    @Test
    void visibleControlsNeverOverlap() {
        int[][] viewports = {
            {640, 400},
            {640, 360},
            {853, 533},
            {480, 270},
            {320, 240}
        };

        for (int[] viewport : viewports) {
            assertNoWidgetOverlap(SkillBlockEditorLayout.calculate(viewport[0], viewport[1], true));
            assertNoWidgetOverlap(SkillBlockEditorLayout.calculate(viewport[0], viewport[1], false));
        }
    }

    @Test
    void dynamicPageCapacityCoversMaximumProgram() {
        int[][] viewports = {{320, 240}, {480, 270}, {640, 400}, {853, 533}};

        for (int[] viewport : viewports) {
            SkillBlockEditorLayout.Layout layout = SkillBlockEditorLayout.calculate(viewport[0], viewport[1], true);
            assertTrue(layout.visibleRows() >= 3);
            int maximumPage = (32 - 1) / layout.visibleRows();
            int coveredBlocks = (maximumPage + 1) * layout.visibleRows();
            assertTrue(coveredBlocks >= 32);
        }
    }

    private static void assertAllVisibleWidgetsFit(SkillBlockEditorLayout.Layout layout) {
        assertTrue(layout.flowPanel().fitsInside(layout.viewportWidth(), layout.viewportHeight()));
        assertTrue(layout.editorPanel().fitsInside(layout.viewportWidth(), layout.viewportHeight()));
        for (SkillBlockEditorLayout.Rect rectangle : layout.visibleWidgetRects()) {
            assertTrue(
                    rectangle.fitsInside(layout.viewportWidth(), layout.viewportHeight()),
                    () -> rectangle + " exceeds " + layout.viewportWidth() + "x" + layout.viewportHeight());
        }
    }

    private static void assertNoWidgetOverlap(SkillBlockEditorLayout.Layout layout) {
        List<SkillBlockEditorLayout.Rect> rectangles = layout.visibleWidgetRects();
        for (int left = 0; left < rectangles.size(); left++) {
            for (int right = left + 1; right < rectangles.size(); right++) {
                SkillBlockEditorLayout.Rect first = rectangles.get(left);
                SkillBlockEditorLayout.Rect second = rectangles.get(right);
                assertFalse(
                        intersects(first, second),
                        () -> first + " overlaps " + second + " at "
                                + layout.viewportWidth() + "x" + layout.viewportHeight());
            }
        }
    }

    private static boolean intersects(SkillBlockEditorLayout.Rect first, SkillBlockEditorLayout.Rect second) {
        return first.x() < second.right()
                && first.right() > second.x()
                && first.y() < second.bottom()
                && first.bottom() > second.y();
    }
}
