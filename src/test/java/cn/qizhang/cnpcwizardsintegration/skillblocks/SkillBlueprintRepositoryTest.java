package cn.qizhang.cnpcwizardsintegration.skillblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SkillBlueprintRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesLoadsAndResolvesEntityBinding() throws Exception {
        Path storage = temporaryDirectory.resolve("skill_block_programs.json");
        UUID entityUuid = UUID.randomUUID();
        SkillBlueprintRepository first = new SkillBlueprintRepository(storage);
        first.putAndBind(SkillBlueprint.starter(), entityUuid);

        SkillBlueprintRepository second = new SkillBlueprintRepository(storage);
        second.load();

        assertEquals(1, second.blueprintCount());
        assertEquals(1, second.bindingCount());
        assertTrue(second.blueprintFor(entityUuid).isPresent());
        assertEquals("starter_fireball", second.blueprintFor(entityUuid).orElseThrow().id());
    }
}
