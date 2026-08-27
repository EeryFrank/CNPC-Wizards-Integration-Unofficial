package cn.qizhang.cnpcwizardsintegration.skillblocks;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** One editable node in a sequential skill block chain. */
public record SkillBlock(String id, SkillBlockType type, Map<String, String> parameters) {
    public SkillBlock {
        id = requireNonBlank(id, "id");
        Objects.requireNonNull(type, "type");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(parameters, "parameters")));
    }

    public static SkillBlock create(SkillBlockType type) {
        return new SkillBlock(UUID.randomUUID().toString(), type, type.defaultParameters());
    }

    public SkillBlock withParameters(Map<String, String> updatedParameters) {
        return new SkillBlock(id, type, new LinkedHashMap<>(updatedParameters));
    }

    public String parameter(String key) {
        return parameters.getOrDefault(key, "");
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
