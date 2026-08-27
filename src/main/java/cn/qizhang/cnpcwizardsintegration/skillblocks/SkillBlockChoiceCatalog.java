package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared, dependency-free catalog for values that should be selected instead of typed.
 *
 * <p>The client editor and the server validator intentionally consume the same particle and sound
 * lists so a value offered by the editor cannot drift away from the server safety policy.</p>
 */
public final class SkillBlockChoiceCatalog {
    public static final String SPELL_ID = "spell_id";
    public static final String TARGET_STRATEGY = "target_strategy";
    public static final String STATUS_EFFECT_ID = "effect_id";
    public static final String STATUS_EFFECT_ID_ALIAS = "status_effect_id";
    public static final String PARTICLE_ID = "particle_id";
    public static final String SOUND_ID = "sound_id";
    public static final String REPEAT_COUNT = "repeat_count";

    private static final List<Choice> SPELLS = List.of(
            choice("wizards:fireball", "火球术"),
            choice("wizards:frost_blizzard", "暴风雪"),
            choice("elemental_wizards_rpg:aqua_bubble_beam", "泡泡光线"),
            choice("elemental_wizards_rpg:terra_earthquake", "地震术"));

    private static final List<Choice> TARGET_STRATEGIES = List.of(
            choice("direct", "直接目标"),
            choice("self", "施法者自身"),
            choice("none", "无实体目标"));

    private static final List<Choice> STATUS_EFFECTS = List.of(
            choice("minecraft:speed", "速度"),
            choice("minecraft:slowness", "缓慢"),
            choice("minecraft:strength", "力量"),
            choice("minecraft:weakness", "虚弱"),
            choice("minecraft:regeneration", "生命恢复"),
            choice("minecraft:resistance", "抗性提升"),
            choice("minecraft:fire_resistance", "火焰抗性"),
            choice("minecraft:absorption", "伤害吸收"),
            choice("minecraft:jump_boost", "跳跃提升"),
            choice("minecraft:invisibility", "隐身"),
            choice("minecraft:glowing", "发光"),
            choice("minecraft:poison", "中毒"),
            choice("minecraft:wither", "凋零"));

    private static final List<Choice> PARTICLES = List.of(
            choice("minecraft:flame", "火焰"),
            choice("minecraft:snowflake", "雪花"),
            choice("minecraft:bubble", "气泡"),
            choice("minecraft:cloud", "云雾"),
            choice("minecraft:crit", "暴击"),
            choice("minecraft:enchant", "附魔"),
            choice("minecraft:happy_villager", "快乐村民"),
            choice("minecraft:heart", "爱心"),
            choice("minecraft:soul_fire_flame", "灵魂火焰"),
            choice("minecraft:witch", "巫女魔法"));

    private static final List<Choice> SOUNDS = List.of(
            choice("minecraft:entity.blaze.shoot", "烈焰人发射"),
            choice("minecraft:entity.evoker.cast_spell", "唤魔者施法"),
            choice("minecraft:entity.illusioner.cast_spell", "幻术师施法"),
            choice("minecraft:entity.player.attack.sweep", "玩家横扫"),
            choice("minecraft:block.amethyst_block.chime", "紫水晶鸣响"),
            choice("minecraft:block.fire.extinguish", "火焰熄灭"),
            choice("minecraft:block.glass.break", "玻璃破碎"),
            choice("minecraft:entity.generic.explode", "通用爆炸"));

    private static final List<Choice> REPEAT_COUNTS = List.of(
            choice("2", "2 次"),
            choice("3", "3 次"),
            choice("4", "4 次"),
            choice("5", "5 次"),
            choice("8", "8 次"),
            choice("10", "10 次"),
            choice("12", "12 次"),
            choice("16", "16 次"));

    private static final Map<String, List<Choice>> PARAMETER_CHOICES = parameterChoices();
    private static final Set<String> PARTICLE_IDS = valuesOf(PARTICLES);
    private static final Set<String> SOUND_IDS = valuesOf(SOUNDS);

    private SkillBlockChoiceCatalog() {
    }

    public static List<Choice> choicesForParameter(String parameterKey) {
        if (parameterKey == null) {
            return List.of();
        }
        return PARAMETER_CHOICES.getOrDefault(parameterKey.trim(), List.of());
    }

    public static boolean isChoiceParameter(String parameterKey) {
        return !choicesForParameter(parameterKey).isEmpty();
    }

    public static List<Choice> spellChoices() {
        return SPELLS;
    }

    public static List<Choice> targetStrategyChoices() {
        return TARGET_STRATEGIES;
    }

    public static List<Choice> statusEffectChoices() {
        return STATUS_EFFECTS;
    }

    public static List<Choice> particleChoices() {
        return PARTICLES;
    }

    public static List<Choice> soundChoices() {
        return SOUNDS;
    }

    public static List<Choice> repeatCountChoices() {
        return REPEAT_COUNTS;
    }

    public static Set<String> allowedParticleIds() {
        return PARTICLE_IDS;
    }

    public static Set<String> allowedSoundIds() {
        return SOUND_IDS;
    }

    public static List<SkillBlockType> conditionTypes() {
        return List.of(
                SkillBlockType.CONDITION_DISTANCE,
                SkillBlockType.CONDITION_VISIBLE);
    }

    public static List<SkillBlockType> abilityTypes() {
        return List.of(
                SkillBlockType.HEAL_SELF,
                SkillBlockType.HEAL_TARGET,
                SkillBlockType.DAMAGE_TARGET,
                SkillBlockType.KNOCKBACK_TARGET,
                SkillBlockType.PULL_TARGET,
                SkillBlockType.IGNITE_TARGET,
                SkillBlockType.EXTINGUISH_SELF,
                SkillBlockType.APPLY_STATUS_EFFECT);
    }

    public static List<SkillBlockType> animationTypes() {
        return List.of(
                SkillBlockType.SWING_MAIN_HAND,
                SkillBlockType.SWING_OFF_HAND,
                SkillBlockType.HURT_ANIMATION);
    }

    public static List<SkillBlockType> effectTypes() {
        return List.of(
                SkillBlockType.PARTICLE_BURST,
                SkillBlockType.PARTICLE_RING,
                SkillBlockType.PLAY_SOUND);
    }

    public static List<SkillBlockType> flowTypes() {
        return List.of(
                SkillBlockType.WAIT_TICKS,
                SkillBlockType.LOOP_START);
    }

    private static Map<String, List<Choice>> parameterChoices() {
        Map<String, List<Choice>> choices = new LinkedHashMap<>();
        choices.put(SPELL_ID, SPELLS);
        choices.put(TARGET_STRATEGY, TARGET_STRATEGIES);
        choices.put(STATUS_EFFECT_ID, STATUS_EFFECTS);
        choices.put(STATUS_EFFECT_ID_ALIAS, STATUS_EFFECTS);
        choices.put(PARTICLE_ID, PARTICLES);
        choices.put(SOUND_ID, SOUNDS);
        choices.put(REPEAT_COUNT, REPEAT_COUNTS);
        return Map.copyOf(choices);
    }

    private static Set<String> valuesOf(List<Choice> choices) {
        Set<String> values = new LinkedHashSet<>();
        for (Choice choice : choices) {
            if (!values.add(choice.value())) {
                throw new IllegalStateException("duplicate choice value: " + choice.value());
            }
        }
        return Set.copyOf(values);
    }

    private static Choice choice(String value, String displayName) {
        return new Choice(value, displayName);
    }

    public record Choice(String value, String displayName) {
        public Choice {
            value = requireNonBlank(value, "choice value");
            displayName = requireNonBlank(displayName, "choice displayName");
        }

        private static String requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            String normalized = value.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return normalized;
        }
    }
}
