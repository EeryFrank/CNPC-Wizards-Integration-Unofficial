package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class CastRecursionGuardTest {
    private static final UUID CASTER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Identifier FIREBALL = Identifier.of("wizards", "fireball");
    private static final Identifier BLIZZARD = Identifier.of("wizards", "frost_blizzard");

    @Test
    void distinguishesSameSpellRecursionFromAnotherActiveSpell() {
        CastRecursionGuard guard = new CastRecursionGuard();

        assertEquals(CastRecursionGuard.EntryResult.ENTERED, guard.tryEnter(CASTER, FIREBALL));
        assertEquals(CastRecursionGuard.EntryResult.RECURSION_BLOCKED, guard.tryEnter(CASTER, FIREBALL));
        assertEquals(CastRecursionGuard.EntryResult.CASTER_BUSY, guard.tryEnter(CASTER, BLIZZARD));

        guard.exit(CASTER, FIREBALL);
        assertEquals(CastRecursionGuard.EntryResult.ENTERED, guard.tryEnter(CASTER, BLIZZARD));
    }
}
