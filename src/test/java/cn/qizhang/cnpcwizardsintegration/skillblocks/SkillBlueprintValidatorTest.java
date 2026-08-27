package cn.qizhang.cnpcwizardsintegration.skillblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillBlueprintValidatorTest {
    private final SkillBlueprintValidator validator = new SkillBlueprintValidator();

    @Test
    void starterBlueprintRoundTripsAndValidates() {
        SkillBlueprint starter = SkillBlueprint.starter();
        SkillBlueprint decoded = SkillBlueprintCodec.fromJson(SkillBlueprintCodec.toJson(starter));

        assertEquals(starter.id(), decoded.id());
        assertEquals(starter.blocks().size(), decoded.blocks().size());
        assertTrue(validator.validate(decoded).valid());
    }

    @Test
    void rejectsUnsafeDirectDamage() {
        List<SkillBlock> blocks = new ArrayList<>(SkillBlueprint.starter().blocks());
        SkillBlock unsafe = SkillBlock.create(SkillBlockType.DAMAGE_TARGET);
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>(unsafe.parameters());
        parameters.put("amount", "200");
        blocks.add(blocks.size() - 1, unsafe.withParameters(parameters));

        SkillBlueprintValidator.ValidationResult result = validator.validate(
                new SkillBlueprint("unsafe", "unsafe", blocks));

        assertFalse(result.valid());
        assertTrue(result.summary().contains("amount"));
    }

    @Test
    void exposesExpandedAbilityAnimationAndEffectBlocks() {
        assertEquals("能力", SkillBlockType.PULL_TARGET.category());
        assertEquals("动作", SkillBlockType.SWING_OFF_HAND.category());
        assertEquals("特效", SkillBlockType.PARTICLE_RING.category());
        assertTrue(SkillBlockType.values().length >= 20);
    }

    @Test
    void acceptsBalancedLoopAndKeepsOldSequentialJsonCompatible() {
        List<SkillBlock> blocks = new ArrayList<>(SkillBlueprint.starter().blocks());
        int end = blocks.size() - 1;
        blocks.add(end, withInt(SkillBlockType.LOOP_START, "repeat_count", 3));
        blocks.add(end + 1, SkillBlock.create(SkillBlockType.SWING_OFF_HAND));
        blocks.add(end + 2, SkillBlock.create(SkillBlockType.LOOP_END));

        SkillBlueprint loop = new SkillBlueprint("balanced_loop", "平衡循环", blocks);
        assertTrue(validator.validate(loop).valid());
        assertTrue(validator.validate(SkillBlueprintCodec.fromJson(
                SkillBlueprintCodec.toJson(SkillBlueprint.starter()))).valid());
    }

    @Test
    void rejectsOrphanUnclosedAndEmptyLoops() {
        assertInvalidLoop(List.of(SkillBlock.create(SkillBlockType.LOOP_END)), "缺少循环开始");
        assertInvalidLoop(List.of(withInt(SkillBlockType.LOOP_START, "repeat_count", 3)), "未闭合");
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 3),
                SkillBlock.create(SkillBlockType.LOOP_END)), "不能为空");
    }

    @Test
    void rejectsUnsafeLoopCountsNestingExpansionAndWaitTime() {
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 1),
                SkillBlock.create(SkillBlockType.SWING_MAIN_HAND),
                SkillBlock.create(SkillBlockType.LOOP_END)), "repeat_count");
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 17),
                SkillBlock.create(SkillBlockType.SWING_MAIN_HAND),
                SkillBlock.create(SkillBlockType.LOOP_END)), "repeat_count");
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 2),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 2),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 2),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 2),
                SkillBlock.create(SkillBlockType.SWING_MAIN_HAND),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END)), "嵌套");
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 16),
                withInt(SkillBlockType.LOOP_START, "repeat_count", 16),
                SkillBlock.create(SkillBlockType.SWING_MAIN_HAND),
                SkillBlock.create(SkillBlockType.LOOP_END),
                SkillBlock.create(SkillBlockType.LOOP_END)), "展开后");
        assertInvalidLoop(List.of(
                withInt(SkillBlockType.LOOP_START, "repeat_count", 16),
                withInt(SkillBlockType.WAIT_TICKS, "ticks", 1200),
                SkillBlock.create(SkillBlockType.LOOP_END)), "累计等待");
    }

    private void assertInvalidLoop(List<SkillBlock> middle, String expected) {
        List<SkillBlock> blocks = new ArrayList<>();
        blocks.add(SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED));
        blocks.addAll(middle);
        blocks.add(SkillBlock.create(SkillBlockType.END));
        SkillBlueprintValidator.ValidationResult result = validator.validate(
                new SkillBlueprint("invalid_loop", "非法循环", blocks));
        assertFalse(result.valid());
        assertTrue(result.summary().contains(expected), result::summary);
    }

    private static SkillBlock withInt(SkillBlockType type, String key, int value) {
        SkillBlock block = SkillBlock.create(type);
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>(block.parameters());
        parameters.put(key, Integer.toString(value));
        return block.withParameters(parameters);
    }
}
