package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/** Block types exposed by the first visual skill composer. */
public enum SkillBlockType {
    TRIGGER_DAMAGED("触发", "受到攻击时", params()),
    TARGET_ATTACKER("目标", "锁定攻击者", params()),
    CONDITION_DISTANCE("条件", "目标距离", params("max_distance", "32")),
    CONDITION_VISIBLE("条件", "目标可见", params()),
    CAST_SPELL("法术", "调用已有法术", params(
            "spell_id", "wizards:fireball",
            "target_strategy", "direct",
            "power_multiplier", "1.0",
            "damage_multiplier", "1.0",
            "healing_multiplier", "1.0",
            "cooldown_ticks", "60")),
    WAIT_TICKS("流程", "等待", params("ticks", "20")),
    LOOP_START("流程", "循环开始", params("repeat_count", "3")),
    LOOP_END("流程", "循环结束", params()),
    HEAL_SELF("能力", "治疗自己", params("amount", "4")),
    HEAL_TARGET("能力", "治疗目标", params("amount", "4")),
    DAMAGE_TARGET("能力", "直接伤害", params("amount", "4")),
    KNOCKBACK_TARGET("能力", "击退目标", params("strength", "1.0")),
    PULL_TARGET("能力", "拉近目标", params("strength", "0.8")),
    IGNITE_TARGET("能力", "点燃目标", params("seconds", "3")),
    EXTINGUISH_SELF("能力", "熄灭自己", params()),
    APPLY_STATUS_EFFECT("能力", "施加状态效果", params(
            "effect_id", "minecraft:slowness",
            "duration_ticks", "100",
            "amplifier", "0")),
    SWING_MAIN_HAND("动作", "主手挥动", params()),
    SWING_OFF_HAND("动作", "副手挥动", params()),
    HURT_ANIMATION("动作", "受击动作", params()),
    PARTICLE_BURST("特效", "粒子爆发", params(
            "particle_id", "minecraft:flame",
            "count", "24",
            "speed", "0.08")),
    PARTICLE_RING("特效", "粒子圆环", params(
            "particle_id", "minecraft:snowflake",
            "count", "32",
            "radius", "1.8")),
    PLAY_SOUND("特效", "播放声音", params(
            "sound_id", "minecraft:entity.blaze.shoot",
            "volume", "1.0",
            "pitch", "1.0")),
    END("流程", "结束", params());

    private final String category;
    private final String displayName;
    private final Map<String, String> defaultParameters;

    SkillBlockType(String category, String displayName, Map<String, String> defaultParameters) {
        this.category = category;
        this.displayName = displayName;
        this.defaultParameters = Collections.unmodifiableMap(new LinkedHashMap<>(defaultParameters));
    }

    public String category() {
        return category;
    }

    public String displayName() {
        return displayName;
    }

    public Map<String, String> defaultParameters() {
        return new LinkedHashMap<>(defaultParameters);
    }

    private static Map<String, String> params(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("parameter defaults must be key/value pairs");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(entries[index], entries[index + 1]);
        }
        return result;
    }
}
