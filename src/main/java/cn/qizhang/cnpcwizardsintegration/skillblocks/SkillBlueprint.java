package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.List;
import java.util.Objects;

/** A named, editable and persistable sequential block program. */
public record SkillBlueprint(String id, String name, List<SkillBlock> blocks) {
    public SkillBlueprint {
        id = requireNonBlank(id, "id");
        name = requireNonBlank(name, "name");
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }

    public static SkillBlueprint starter() {
        return new SkillBlueprint(
                "starter_fireball",
                "受击火球连锁",
                List.of(
                        SkillBlock.create(SkillBlockType.TRIGGER_DAMAGED),
                        SkillBlock.create(SkillBlockType.TARGET_ATTACKER),
                        SkillBlock.create(SkillBlockType.CONDITION_DISTANCE),
                        SkillBlock.create(SkillBlockType.SWING_MAIN_HAND),
                        SkillBlock.create(SkillBlockType.PARTICLE_BURST),
                        SkillBlock.create(SkillBlockType.CAST_SPELL),
                        SkillBlock.create(SkillBlockType.PLAY_SOUND),
                        SkillBlock.create(SkillBlockType.END)));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
