# CustomNPCs ECMAScript quick start

This directory contains source examples for CNPC Wizards Integration (Unofficial). Nothing here is
automatically copied into a client or server instance.

## Minimum call

In a CustomNPCs NPC script editor, select ECMAScript and paste the following. It handles the NPC
`damaged(event)` callback and asks the shared bridge to cast a neutral, direct-target fireball at a
living true damage source.

```javascript
var Integration = Java.type("cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration");
var CastOptions = Java.type("cn.qizhang.cnpcwizardsintegration.api.CastOptions");
var IEntityLiving = Java.type("noppes.npcs.api.entity.IEntityLiving");

function damaged(event) {
    var attacker = event.damageSource === null
        ? event.source
        : event.damageSource.getTrueSource();
    if (attacker === null || !(attacker instanceof IEntityLiving)) {
        return;
    }
    var result = Integration.castingApi().cast(
        event.npc.getMCEntity(),
        attacker.getMCEntity(),
        CastOptions.defaults("wizards:fireball")
    );
    if (!result.accepted()) {
        log("[CNPC Wizards] " + result.code() + " trace=" + result.traceId());
    }
}
```

The minimum snippet above makes one request per damage event. The fuller
[`damaged_fireball.js`](damaged_fireball.js) example additionally remembers the attacker, starts a
short repeating timer, and retries the same shared bridge after cooldown while the target remains
alive and visible. It also adds a direct-source fallback, configurable spell power, damage, healing,
cooldown and retry values, and guarded diagnostic logging.

[`four_spell_rotation.js`](four_spell_rotation.js) uses the same remembered-target/timer pattern but
rotates all four phase-one spells. It gives each spell an independent cooldown, checks its configured
distance and line-of-sight rule, sends `null` for the earthquake `none` strategy, and accepts at most
one new cast per action window. Use either the single-spell script or the rotation script on an NPC,
not both together.

## Configuration notes

- `spellId` must include its namespace. Phase-one IDs are `wizards:fireball`,
  `wizards:frost_blizzard`, `elemental_wizards_rpg:aqua_bubble_beam`, and
  `elemental_wizards_rpg:terra_earthquake`.
- Fireball, frost blizzard, and bubble beam use `targetStrategyId: "direct"`. Terra earthquake uses
  `targetStrategyId: "none"` and passes `null` as the direct target.
- `cooldownTicks` is owned by the shared bridge and is keyed by NPC plus spell ID. Twenty ticks are
  approximately one second when the server is running at 20 TPS.
- `retryIntervalTicks` controls how frequently the script rechecks the remembered target. It does
  not replace or bypass the bridge cooldown.
- `requireLineOfSight` prevents automatic retry while the NPC cannot see the remembered target.
- The four-spell defaults are fireball `60` ticks (3 s), bubble beam `300` ticks (15 s), frost
  blizzard `320` ticks (16 s), and terra earthquake `700` ticks (35 s). The rotation also enforces a
  20-tick minimum interval between accepted cast starts.
- `CastResult.accepted()` means the bridge accepted the request. Continuing casts can still be
  cancelled or fail later; use the returned result code/trace ID and the server `cast_trace` log for
  diagnosis.

## Validation boundary

Automated checks used during development proved that this file parsed with the Nashorn engine bundled in the tested
CustomNPCs JAR, referenced the timer/target APIs present in that JAR, and called the shared Java
bridge with all configuration values. Actual NPC event firing, remembered-target behavior,
line-of-sight handling, spell visuals/audio, damage, healing, status effects, cooldown timing, and
automatic continuing-cast behavior remain
`NEEDS_MANUAL_VALIDATION` until the example is deliberately installed and exercised in game.
