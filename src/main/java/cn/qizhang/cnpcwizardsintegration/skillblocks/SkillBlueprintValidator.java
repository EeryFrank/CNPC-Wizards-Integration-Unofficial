package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Identifier;

/** Server-authoritative validation and safety limits for player-authored block programs. */
public final class SkillBlueprintValidator {
    public static final int MAX_JSON_LENGTH = 32_768;
    public static final int MAX_BLOCKS = 32;
    public static final int MAX_LOOP_REPEATS = 16;
    public static final int MAX_LOOP_NESTING = 3;
    public static final int MAX_EXPANDED_STEPS = 256;
    public static final int MAX_EXPANDED_WAIT_TICKS = 12_000;

    public ValidationResult validate(SkillBlueprint blueprint) {
        List<String> errors = new ArrayList<>();
        if (blueprint == null) {
            return ValidationResult.invalid(List.of("方案不能为空"));
        }
        if (blueprint.id().length() > 64 || !blueprint.id().matches("[a-z0-9_.-]+")) {
            errors.add("方案 ID 只能包含小写字母、数字、点、下划线或短横线，且不超过 64 字符");
        }
        if (blueprint.name().length() > 40) {
            errors.add("方案名称不得超过 40 字符");
        }
        List<SkillBlock> blocks = blueprint.blocks();
        if (blocks.size() < 3 || blocks.size() > MAX_BLOCKS) {
            errors.add("积木数量必须在 3 到 " + MAX_BLOCKS + " 之间");
            return new ValidationResult(errors);
        }
        if (blocks.getFirst().type() != SkillBlockType.TRIGGER_DAMAGED) {
            errors.add("第一块必须是“受到攻击时”触发块");
        }
        if (blocks.getLast().type() != SkillBlockType.END) {
            errors.add("最后一块必须是“结束”块");
        }

        Set<String> ids = new HashSet<>();
        Deque<LoopScope> loops = new ArrayDeque<>();
        int waits = 0;
        int multiplier = 1;
        int expandedSteps = 0;
        int expandedWaitTicks = 0;
        for (int index = 0; index < blocks.size(); index++) {
            SkillBlock block = blocks.get(index);
            if (!ids.add(block.id())) {
                errors.add("存在重复积木 ID: " + block.id());
            }
            if (index > 0 && block.type() == SkillBlockType.TRIGGER_DAMAGED) {
                errors.add("触发块只能出现在首位");
            }
            if (index < blocks.size() - 1 && block.type() == SkillBlockType.END) {
                errors.add("结束块只能出现在末位");
            }
            if (block.type() == SkillBlockType.WAIT_TICKS && ++waits > 8) {
                errors.add("等待块最多 8 个");
            }
            validateBlock(block, errors);
            if (block.type() == SkillBlockType.LOOP_START) {
                int repeatCount = parsedInt(block.parameters(), "repeat_count", 0);
                loops.push(new LoopScope(index, repeatCount));
                if (loops.size() > MAX_LOOP_NESTING) {
                    errors.add("循环最多嵌套 " + MAX_LOOP_NESTING + " 层");
                }
                multiplier = safeMultiply(multiplier, Math.max(1, repeatCount));
            }
            expandedSteps = safeAdd(expandedSteps, multiplier);
            if (block.type() == SkillBlockType.WAIT_TICKS) {
                expandedWaitTicks = safeAdd(
                        expandedWaitTicks,
                        safeMultiply(parsedInt(block.parameters(), "ticks", 0), multiplier));
            }
            if (block.type() == SkillBlockType.LOOP_END) {
                if (loops.isEmpty()) {
                    errors.add("循环结束前缺少循环开始");
                }
                else {
                    LoopScope scope = loops.pop();
                    if (index == scope.startIndex() + 1) {
                        errors.add("循环体不能为空");
                    }
                    multiplier = Math.max(1, multiplier / Math.max(1, scope.repeatCount()));
                }
            }
        }
        if (!loops.isEmpty()) {
            errors.add("存在未闭合的循环开始");
        }
        if (expandedSteps > MAX_EXPANDED_STEPS) {
            errors.add("循环展开后最多执行 " + MAX_EXPANDED_STEPS + " 步");
        }
        if (expandedWaitTicks > MAX_EXPANDED_WAIT_TICKS) {
            errors.add("循环展开后的累计等待最多 " + MAX_EXPANDED_WAIT_TICKS + " tick");
        }
        return new ValidationResult(errors);
    }

    private static void validateBlock(SkillBlock block, List<String> errors) {
        Map<String, String> parameters = block.parameters();
        try {
            switch (block.type()) {
                case CONDITION_DISTANCE -> boundedDouble(parameters, "max_distance", 1.0D, 64.0D);
                case CAST_SPELL -> {
                    identifier(parameters, "spell_id");
                    String targetStrategy = required(parameters, "target_strategy");
                    if (!Set.of("direct", "self", "none").contains(targetStrategy)) {
                        throw new IllegalArgumentException("target_strategy 仅支持 direct/self/none");
                    }
                    boundedDouble(parameters, "power_multiplier", 0.0D, 10.0D);
                    boundedDouble(parameters, "damage_multiplier", 0.0D, 10.0D);
                    boundedDouble(parameters, "healing_multiplier", 0.0D, 10.0D);
                    boundedInt(parameters, "cooldown_ticks", 0, 72_000);
                }
                case WAIT_TICKS -> boundedInt(parameters, "ticks", 1, 1_200);
                case LOOP_START -> boundedInt(parameters, "repeat_count", 2, MAX_LOOP_REPEATS);
                case HEAL_SELF, HEAL_TARGET, DAMAGE_TARGET -> boundedDouble(parameters, "amount", 0.0D, 40.0D);
                case KNOCKBACK_TARGET, PULL_TARGET -> boundedDouble(parameters, "strength", 0.0D, 3.0D);
                case IGNITE_TARGET -> boundedInt(parameters, "seconds", 0, 10);
                case APPLY_STATUS_EFFECT -> {
                    identifier(parameters, "effect_id");
                    boundedInt(parameters, "duration_ticks", 1, 1_200);
                    boundedInt(parameters, "amplifier", 0, 4);
                }
                case PARTICLE_BURST -> {
                    allowed(parameters, "particle_id", SkillBlockChoiceCatalog.allowedParticleIds());
                    boundedInt(parameters, "count", 1, 100);
                    boundedDouble(parameters, "speed", 0.0D, 1.0D);
                }
                case PARTICLE_RING -> {
                    allowed(parameters, "particle_id", SkillBlockChoiceCatalog.allowedParticleIds());
                    boundedInt(parameters, "count", 4, 100);
                    boundedDouble(parameters, "radius", 0.2D, 8.0D);
                }
                case PLAY_SOUND -> {
                    allowed(parameters, "sound_id", SkillBlockChoiceCatalog.allowedSoundIds());
                    boundedDouble(parameters, "volume", 0.0D, 4.0D);
                    boundedDouble(parameters, "pitch", 0.5D, 2.0D);
                }
                default -> {
                }
            }
        }
        catch (IllegalArgumentException error) {
            errors.add(block.type().displayName() + ": " + error.getMessage());
        }
    }

    private static String required(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少参数 " + key);
        }
        return value.trim();
    }

    private static void identifier(Map<String, String> parameters, String key) {
        String value = required(parameters, key);
        if (value.indexOf(':') <= 0 || Identifier.tryParse(value) == null) {
            throw new IllegalArgumentException(key + " 必须是含命名空间的有效 ID");
        }
    }

    private static void allowed(Map<String, String> parameters, String key, Set<String> allowed) {
        String value = required(parameters, key);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(key + " 不在初版安全白名单中");
        }
    }

    private static int boundedInt(Map<String, String> parameters, String key, int minimum, int maximum) {
        try {
            int value = Integer.parseInt(required(parameters, key));
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(key + " 必须在 " + minimum + " 到 " + maximum + " 之间");
            }
            return value;
        }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " 必须是整数");
        }
    }

    private static int parsedInt(Map<String, String> parameters, String key, int fallback) {
        try {
            return Integer.parseInt(parameters.getOrDefault(key, ""));
        }
        catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static int safeMultiply(int left, int right) {
        long value = (long) left * right;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int safeAdd(int left, int right) {
        long value = (long) left + right;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private record LoopScope(int startIndex, int repeatCount) {
    }

    private static double boundedDouble(Map<String, String> parameters, String key, double minimum, double maximum) {
        try {
            double value = Double.parseDouble(required(parameters, key));
            if (!Double.isFinite(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(key + " 必须在 " + minimum + " 到 " + maximum + " 之间");
            }
            return value;
        }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " 必须是数字");
        }
    }

    public record ValidationResult(List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(errors);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public String summary() {
            return valid() ? "验证通过" : String.join("；", errors);
        }
    }
}
