package cn.qizhang.cnpcwizardsintegration.skillblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillProgramCursorTest {
    @Test
    void repeatsBodyExactlyThreeTimes() {
        SkillBlock repeated = SkillBlock.create(SkillBlockType.DAMAGE_TARGET);
        SkillProgramCursor cursor = new SkillProgramCursor(List.of(
                SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 3),
                repeated,
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.END)));

        assertEquals(3, countBlock(cursor, repeated.id()));
    }

    @Test
    void nestedTwoByThreeLoopRunsBodySixTimes() {
        SkillBlock repeated = SkillBlock.create(SkillBlockType.PARTICLE_BURST);
        SkillProgramCursor cursor = new SkillProgramCursor(List.of(
                SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 2),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 3),
                repeated,
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.END)));

        assertEquals(6, countBlock(cursor, repeated.id()));
    }

    @Test
    void waitCanYieldAndResumeWithoutLosingLoopFrame() {
        SkillBlock wait = withInt(SkillBlockType.WAIT_TICKS, "ticks", 20);
        SkillProgramCursor cursor = new SkillProgramCursor(List.of(
                SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 3),
                wait,
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.END)));

        int waits = 0;
        while (true) {
            SkillProgramCursor.Step step = cursor.next();
            if (step.kind() == SkillProgramCursor.Kind.FINISHED) {
                break;
            }
            if (step.kind() == SkillProgramCursor.Kind.BLOCK
                    && step.block().type() == SkillBlockType.WAIT_TICKS) {
                waits++;
                // The server stops here and calls next() again on a later tick using this same cursor.
            }
        }
        assertEquals(3, waits);
    }

    @Test
    void runtimeBudgetStopsBypassedUnsafeProgram() {
        SkillProgramCursor cursor = new SkillProgramCursor(List.of(
                SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 16),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 16),
                SkillBlock.create(SkillBlockType.DAMAGE_TARGET),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.END)));

        SkillProgramCursor.Step last = null;
        for (int index = 0; index <= SkillProgramCursor.MAX_EXECUTED_STEPS; index++) {
            last = cursor.next();
        }
        assertEquals(SkillProgramCursor.Kind.BUDGET_EXCEEDED, last.kind());
    }

    private static int countBlock(SkillProgramCursor cursor, String id) {
        int count = 0;
        while (true) {
            SkillProgramCursor.Step step = cursor.next();
            if (step.kind() == SkillProgramCursor.Kind.FINISHED) {
                return count;
            }
            assertTrue(step.kind() == SkillProgramCursor.Kind.BLOCK
                    || step.kind() == SkillProgramCursor.Kind.CONTROL);
            if (step.block() != null && step.block().id().equals(id)) {
                count++;
            }
        }
    }

    private static SkillBlock withInt(SkillBlockType type, String key, int value) {
        SkillBlock block = SkillBlock.create(type);
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>(block.parameters());
        parameters.put(key, Integer.toString(value));
        return block.withParameters(parameters);
    }
}
