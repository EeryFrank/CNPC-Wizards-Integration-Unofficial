package cn.qizhang.cnpcwizardsintegration.skillblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SkillBlockChoiceCatalogTest {
    private static final Set<String> CHOICE_PARAMETER_KEYS = Set.of(
            SkillBlockChoiceCatalog.SPELL_ID,
            SkillBlockChoiceCatalog.TARGET_STRATEGY,
            SkillBlockChoiceCatalog.STATUS_EFFECT_ID,
            SkillBlockChoiceCatalog.PARTICLE_ID,
            SkillBlockChoiceCatalog.SOUND_ID,
            SkillBlockChoiceCatalog.REPEAT_COUNT);

    @Test
    void everyCategoricalDefaultIsOfferedByTheSharedCatalog() {
        for (SkillBlockType type : SkillBlockType.values()) {
            for (Map.Entry<String, String> parameter : type.defaultParameters().entrySet()) {
                if (!CHOICE_PARAMETER_KEYS.contains(parameter.getKey())) {
                    continue;
                }
                List<SkillBlockChoiceCatalog.Choice> choices =
                        SkillBlockChoiceCatalog.choicesForParameter(parameter.getKey());
                assertFalse(choices.isEmpty(), () -> "missing choices for " + parameter.getKey());
                assertTrue(
                        choices.stream().anyMatch(choice -> choice.value().equals(parameter.getValue())),
                        () -> type + " default " + parameter + " is not selectable");
            }
        }
    }

    @Test
    void choiceValuesAreUniqueAndAliasesShareTheSameStatusEffectList() {
        for (String key : CHOICE_PARAMETER_KEYS) {
            assertUniqueValues(SkillBlockChoiceCatalog.choicesForParameter(key));
        }
        assertEquals(
                SkillBlockChoiceCatalog.statusEffectChoices(),
                SkillBlockChoiceCatalog.choicesForParameter(
                        SkillBlockChoiceCatalog.STATUS_EFFECT_ID_ALIAS));
        assertFalse(SkillBlockChoiceCatalog.isChoiceParameter("cooldown_ticks"));
        assertTrue(SkillBlockChoiceCatalog.choicesForParameter(null).isEmpty());
    }

    @Test
    void phaseOneSpellsAndSafetyListsAreComplete() {
        assertEquals(Set.of(
                        "wizards:fireball",
                        "wizards:frost_blizzard",
                        "elemental_wizards_rpg:aqua_bubble_beam",
                        "elemental_wizards_rpg:terra_earthquake"),
                values(SkillBlockChoiceCatalog.spellChoices()));
        assertEquals(Set.of("direct", "self", "none"),
                values(SkillBlockChoiceCatalog.targetStrategyChoices()));
        assertEquals(10, SkillBlockChoiceCatalog.particleChoices().size());
        assertEquals(8, SkillBlockChoiceCatalog.soundChoices().size());
        assertEquals(Set.of("2", "3", "4", "5", "8", "10", "12", "16"),
                values(SkillBlockChoiceCatalog.repeatCountChoices()));
        assertEquals(
                values(SkillBlockChoiceCatalog.particleChoices()),
                SkillBlockChoiceCatalog.allowedParticleIds());
        assertEquals(
                values(SkillBlockChoiceCatalog.soundChoices()),
                SkillBlockChoiceCatalog.allowedSoundIds());
    }

    @Test
    void paletteTypeGroupsAreCompleteAndDoNotOverlap() {
        assertEquals(Set.of(
                        SkillBlockType.CONDITION_DISTANCE,
                        SkillBlockType.CONDITION_VISIBLE),
                Set.copyOf(SkillBlockChoiceCatalog.conditionTypes()));
        assertEquals(Set.of(
                        SkillBlockType.HEAL_SELF,
                        SkillBlockType.HEAL_TARGET,
                        SkillBlockType.DAMAGE_TARGET,
                        SkillBlockType.KNOCKBACK_TARGET,
                        SkillBlockType.PULL_TARGET,
                        SkillBlockType.IGNITE_TARGET,
                        SkillBlockType.EXTINGUISH_SELF,
                        SkillBlockType.APPLY_STATUS_EFFECT),
                Set.copyOf(SkillBlockChoiceCatalog.abilityTypes()));
        assertEquals(Set.of(
                        SkillBlockType.SWING_MAIN_HAND,
                        SkillBlockType.SWING_OFF_HAND,
                        SkillBlockType.HURT_ANIMATION),
                Set.copyOf(SkillBlockChoiceCatalog.animationTypes()));
        assertEquals(Set.of(
                        SkillBlockType.PARTICLE_BURST,
                        SkillBlockType.PARTICLE_RING,
                        SkillBlockType.PLAY_SOUND),
                Set.copyOf(SkillBlockChoiceCatalog.effectTypes()));
        assertEquals(List.of(
                        SkillBlockType.WAIT_TICKS,
                        SkillBlockType.LOOP_START),
                SkillBlockChoiceCatalog.flowTypes());

        Set<SkillBlockType> all = new HashSet<>();
        assertAddsAllWithoutOverlap(all, SkillBlockChoiceCatalog.conditionTypes());
        assertAddsAllWithoutOverlap(all, SkillBlockChoiceCatalog.abilityTypes());
        assertAddsAllWithoutOverlap(all, SkillBlockChoiceCatalog.animationTypes());
        assertAddsAllWithoutOverlap(all, SkillBlockChoiceCatalog.effectTypes());
        assertAddsAllWithoutOverlap(all, SkillBlockChoiceCatalog.flowTypes());
    }

    @Test
    void choiceRejectsBlankMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkillBlockChoiceCatalog.Choice(" ", "label"));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillBlockChoiceCatalog.Choice("value", " "));
    }

    private static void assertUniqueValues(List<SkillBlockChoiceCatalog.Choice> choices) {
        assertEquals(choices.size(), values(choices).size());
    }

    private static Set<String> values(List<SkillBlockChoiceCatalog.Choice> choices) {
        Set<String> values = new HashSet<>();
        for (SkillBlockChoiceCatalog.Choice choice : choices) {
            values.add(choice.value());
        }
        return Set.copyOf(values);
    }

    private static void assertAddsAllWithoutOverlap(Set<SkillBlockType> all, List<SkillBlockType> additions) {
        for (SkillBlockType type : additions) {
            assertTrue(all.add(type), () -> type + " appears in more than one palette group");
        }
    }
}
