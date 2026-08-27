package cn.qizhang.cnpcwizardsintegration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class PhaseOneSpellIdsTest {
    @Test
    void exposesExactlyTheScheduledDemoSpellIds() {
        Set<String> actual = PhaseOneSpellIds.ALL.stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(Set.of(
                "wizards:fireball",
                "wizards:frost_blizzard",
                "elemental_wizards_rpg:aqua_bubble_beam",
                "elemental_wizards_rpg:terra_earthquake"), actual);
    }
}
