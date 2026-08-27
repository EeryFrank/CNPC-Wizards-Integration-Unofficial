package cn.qizhang.cnpcwizardsintegration.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

final class CooldownTrackerTest {
    private static final UUID CASTER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Identifier FIREBALL = Identifier.of("wizards", "fireball");

    @Test
    void tracksRemainingTicksAndExpiresAtTheBoundary() {
        CooldownTracker tracker = new CooldownTracker();

        tracker.start(CASTER, FIREBALL, 100L, 20);

        assertEquals(20L, tracker.remainingTicks(CASTER, FIREBALL, 100L));
        assertEquals(1L, tracker.remainingTicks(CASTER, FIREBALL, 119L));
        assertEquals(0L, tracker.remainingTicks(CASTER, FIREBALL, 120L));
        assertEquals(0L, tracker.remainingTicks(CASTER, FIREBALL, 121L));
    }

    @Test
    void keysCooldownByBothCasterAndSpell() {
        CooldownTracker tracker = new CooldownTracker();
        UUID otherCaster = UUID.fromString("00000000-0000-0000-0000-000000000002");

        tracker.start(CASTER, FIREBALL, 50L, 10);

        assertEquals(0L, tracker.remainingTicks(otherCaster, FIREBALL, 50L));
        assertEquals(0L, tracker.remainingTicks(CASTER, Identifier.of("wizards", "frost_blizzard"), 50L));
    }
}
