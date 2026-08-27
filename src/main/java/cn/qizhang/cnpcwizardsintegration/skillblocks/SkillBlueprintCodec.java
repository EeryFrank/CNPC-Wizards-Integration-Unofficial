package cn.qizhang.cnpcwizardsintegration.skillblocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Shared JSON codec used by local drafts, network transfer and server persistence. */
public final class SkillBlueprintCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SkillBlueprintCodec() {
    }

    public static String toJson(SkillBlueprint blueprint) {
        return GSON.toJson(blueprint);
    }

    public static SkillBlueprint fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("blueprint JSON must not be blank");
        }
        SkillBlueprint blueprint = GSON.fromJson(json, SkillBlueprint.class);
        if (blueprint == null) {
            throw new IllegalArgumentException("blueprint JSON produced no object");
        }
        return new SkillBlueprint(blueprint.id(), blueprint.name(), blueprint.blocks());
    }
}
