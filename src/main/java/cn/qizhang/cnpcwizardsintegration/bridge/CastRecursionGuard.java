package cn.qizhang.cnpcwizardsintegration.bridge;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.util.Identifier;

/** Prevents recursive re-entry and overlapping casts for one NPC. */
public final class CastRecursionGuard {
    private final ConcurrentMap<UUID, Identifier> activeSpellByCaster = new ConcurrentHashMap<>();

    public EntryResult tryEnter(UUID casterId, Identifier spellId) {
        Objects.requireNonNull(casterId, "casterId");
        Objects.requireNonNull(spellId, "spellId");
        Identifier activeSpell = activeSpellByCaster.putIfAbsent(casterId, spellId);
        if (activeSpell == null) {
            return EntryResult.ENTERED;
        }
        return activeSpell.equals(spellId)
                ? EntryResult.RECURSION_BLOCKED
                : EntryResult.CASTER_BUSY;
    }

    public void exit(UUID casterId, Identifier spellId) {
        activeSpellByCaster.remove(
                Objects.requireNonNull(casterId, "casterId"),
                Objects.requireNonNull(spellId, "spellId"));
    }

    public enum EntryResult {
        ENTERED,
        RECURSION_BLOCKED,
        CASTER_BUSY
    }
}
