package cn.qizhang.cnpcwizardsintegration.api;

import java.util.Set;
import net.minecraft.util.Identifier;

/** Namespaced IDs of the four phase-one demonstration spells. */
public final class PhaseOneSpellIds {
    public static final Identifier FIREBALL = Identifier.of("wizards", "fireball");
    public static final Identifier FROST_BLIZZARD = Identifier.of("wizards", "frost_blizzard");
    public static final Identifier AQUA_BUBBLE_BEAM =
            Identifier.of("elemental_wizards_rpg", "aqua_bubble_beam");
    public static final Identifier TERRA_EARTHQUAKE =
            Identifier.of("elemental_wizards_rpg", "terra_earthquake");

    public static final Set<Identifier> ALL = Set.of(
            FIREBALL,
            FROST_BLIZZARD,
            AQUA_BUBBLE_BEAM,
            TERRA_EARTHQUAKE);

    private PhaseOneSpellIds() {
    }
}
