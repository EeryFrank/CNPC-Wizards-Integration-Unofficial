package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Stateful, Minecraft-independent instruction cursor for bounded nested loops and wait resumes. */
final class SkillProgramCursor {
    static final int MAX_EXECUTED_STEPS = SkillBlueprintValidator.MAX_EXPANDED_STEPS;

    private final List<SkillBlock> blocks;
    private final Deque<LoopFrame> loops = new ArrayDeque<>();
    private int nextIndex = 1;
    private int executedSteps;

    SkillProgramCursor(List<SkillBlock> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    Step next() {
        if (nextIndex >= blocks.size()) {
            return Step.finished();
        }
        if (++executedSteps > MAX_EXECUTED_STEPS) {
            return Step.budgetExceeded();
        }
        int currentIndex = nextIndex++;
        SkillBlock block = blocks.get(currentIndex);
        if (block.type() == SkillBlockType.LOOP_START) {
            loops.push(new LoopFrame(nextIndex, Integer.parseInt(block.parameter("repeat_count"))));
            return Step.control(currentIndex);
        }
        if (block.type() == SkillBlockType.LOOP_END) {
            if (loops.isEmpty()) {
                return Step.invalid(currentIndex, "遇到没有循环开始的循环结束");
            }
            LoopFrame loop = loops.peek();
            if (loop.remainingIterations > 1) {
                loop.remainingIterations--;
                nextIndex = loop.bodyStartIndex;
            }
            else {
                loops.pop();
            }
            return Step.control(currentIndex);
        }
        if (block.type() == SkillBlockType.END && !loops.isEmpty()) {
            return Step.invalid(currentIndex, "循环未结束");
        }
        return Step.block(currentIndex, block);
    }

    int executedSteps() {
        return executedSteps;
    }

    record Step(Kind kind, int index, SkillBlock block, String error) {
        static Step block(int index, SkillBlock block) {
            return new Step(Kind.BLOCK, index, block, "");
        }

        static Step control(int index) {
            return new Step(Kind.CONTROL, index, null, "");
        }

        static Step finished() {
            return new Step(Kind.FINISHED, -1, null, "");
        }

        static Step budgetExceeded() {
            return new Step(Kind.BUDGET_EXCEEDED, -1, null, "积木执行步数超过安全上限");
        }

        static Step invalid(int index, String error) {
            return new Step(Kind.INVALID, index, null, error);
        }
    }

    enum Kind {
        BLOCK,
        CONTROL,
        FINISHED,
        BUDGET_EXCEEDED,
        INVALID
    }

    private static final class LoopFrame {
        private final int bodyStartIndex;
        private int remainingIterations;

        private LoopFrame(int bodyStartIndex, int remainingIterations) {
            this.bodyStartIndex = bodyStartIndex;
            this.remainingIterations = remainingIterations;
        }
    }
}
