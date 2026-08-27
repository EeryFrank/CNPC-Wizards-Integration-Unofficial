package cn.qizhang.cnpcwizardsintegration.skillblocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JSON-backed server repository for validated programs and entity bindings. */
public final class SkillBlueprintRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path storageFile;
    private final Map<String, SkillBlueprint> blueprints = new LinkedHashMap<>();
    private final Map<UUID, String> bindings = new LinkedHashMap<>();

    public SkillBlueprintRepository(Path storageFile) {
        this.storageFile = storageFile;
    }

    public synchronized void load() throws IOException {
        blueprints.clear();
        bindings.clear();
        if (!Files.isRegularFile(storageFile)) {
            return;
        }
        StorageModel model = GSON.fromJson(Files.readString(storageFile, StandardCharsets.UTF_8), StorageModel.class);
        if (model == null) {
            return;
        }
        if (model.blueprints() != null) {
            model.blueprints().forEach((id, blueprint) -> blueprints.put(
                    id,
                    new SkillBlueprint(blueprint.id(), blueprint.name(), blueprint.blocks())));
        }
        if (model.bindings() != null) {
            model.bindings().forEach((uuid, blueprintId) -> bindings.put(UUID.fromString(uuid), blueprintId));
        }
    }

    public synchronized void putAndBind(SkillBlueprint blueprint, UUID entityUuid) throws IOException {
        blueprints.put(blueprint.id(), blueprint);
        bindings.put(entityUuid, blueprint.id());
        save();
    }

    public synchronized Optional<SkillBlueprint> blueprintFor(UUID entityUuid) {
        String blueprintId = bindings.get(entityUuid);
        return Optional.ofNullable(blueprints.get(blueprintId));
    }

    public synchronized int blueprintCount() {
        return blueprints.size();
    }

    public synchronized int bindingCount() {
        return bindings.size();
    }

    private void save() throws IOException {
        Files.createDirectories(storageFile.getParent());
        Map<String, String> serializedBindings = new LinkedHashMap<>();
        bindings.forEach((uuid, blueprintId) -> serializedBindings.put(uuid.toString(), blueprintId));
        String json = GSON.toJson(new StorageModel(new LinkedHashMap<>(blueprints), serializedBindings));
        Path temporaryFile = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
        Files.writeString(temporaryFile, json, StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporaryFile,
                    storageFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException atomicMoveFailure) {
            Files.move(temporaryFile, storageFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record StorageModel(Map<String, SkillBlueprint> blueprints, Map<String, String> bindings) {
    }
}
