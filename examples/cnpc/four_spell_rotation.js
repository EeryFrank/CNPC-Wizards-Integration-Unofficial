/*
 * CNPC Wizards Integration (Unofficial)
 * 四技能自动轮换示例：首次受击后锁定攻击者，按独立冷却轮换施法。
 */

var CnPcWizardsIntegration = Java.type(
    "cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration"
);
var CastOptions = Java.type(
    "cn.qizhang.cnpcwizardsintegration.api.CastOptions"
);
var IEntityLiving = Java.type("noppes.npcs.api.entity.IEntityLiving");

// 20 tick 约等于 1 秒。普通使用只需要修改这个配置块。
var CNPC_WIZARDS_CONFIG = {
    // 每 5 tick 检查一次目标与技能状态。
    retryIntervalTicks: 5,
    // 两次成功开始施法至少间隔 20 tick，避免技能瞬间一起释放。
    minimumActionIntervalTicks: 20,
    spells: [
        {
            name: "火球术",
            spellId: "wizards:fireball",
            targetStrategyId: "direct",
            spellPowerMultiplier: 1.0,
            damageMultiplier: 1.0,
            healingMultiplier: 1.0,
            cooldownTicks: 60,
            maxTargetDistance: 64.0,
            requireLineOfSight: true
        },
        {
            name: "泡沫光线",
            spellId: "elemental_wizards_rpg:aqua_bubble_beam",
            targetStrategyId: "direct",
            spellPowerMultiplier: 1.0,
            damageMultiplier: 1.0,
            healingMultiplier: 1.0,
            cooldownTicks: 300,
            maxTargetDistance: 7.0,
            requireLineOfSight: true
        },
        {
            name: "暴风雪",
            spellId: "wizards:frost_blizzard",
            targetStrategyId: "direct",
            spellPowerMultiplier: 1.0,
            damageMultiplier: 1.0,
            healingMultiplier: 1.0,
            cooldownTicks: 320,
            maxTargetDistance: 32.0,
            requireLineOfSight: true
        },
        {
            name: "地震术",
            spellId: "elemental_wizards_rpg:terra_earthquake",
            targetStrategyId: "none",
            spellPowerMultiplier: 1.0,
            damageMultiplier: 1.0,
            healingMultiplier: 1.0,
            cooldownTicks: 700,
            maxTargetDistance: 24.0,
            requireLineOfSight: false
        }
    ]
};

var CNPC_WIZARDS_TIMER_ID = 913401;
var CNPC_WIZARDS_TARGET_KEY = "cnpc_wizards_integration.target_uuid";
var CNPC_WIZARDS_ROTATION_KEY = "cnpc_wizards_integration.rotation_index";
var CNPC_WIZARDS_NEXT_ACTION_KEY = "cnpc_wizards_integration.next_action_tick";

function isLivingTarget(entity) {
    return entity !== null
        && entity instanceof IEntityLiving
        && entity.isAlive();
}

function resolveLivingAttacker(event) {
    var source = null;
    if (event.damageSource !== null) {
        source = event.damageSource.getTrueSource();
    }
    if (source === null) {
        source = event.source;
    }
    return isLivingTarget(source) ? source : null;
}

function resetRotation(npc) {
    npc.getTempdata().put(CNPC_WIZARDS_ROTATION_KEY, 0);
    npc.getTempdata().put(CNPC_WIZARDS_NEXT_ACTION_KEY, 0);
}

function rememberTarget(npc, target) {
    var data = npc.getTempdata();
    var targetUuid = String(target.getUUID());
    var changed = !data.has(CNPC_WIZARDS_TARGET_KEY)
        || String(data.get(CNPC_WIZARDS_TARGET_KEY)) !== targetUuid;

    data.put(CNPC_WIZARDS_TARGET_KEY, targetUuid);
    npc.setAttackTarget(target);
    if (changed) {
        resetRotation(npc);
    }
}

function clearCombatState(npc) {
    var data = npc.getTempdata();
    data.remove(CNPC_WIZARDS_TARGET_KEY);
    data.remove(CNPC_WIZARDS_ROTATION_KEY);
    data.remove(CNPC_WIZARDS_NEXT_ACTION_KEY);
    if (npc.getTimers().has(CNPC_WIZARDS_TIMER_ID)) {
        npc.getTimers().stop(CNPC_WIZARDS_TIMER_ID);
    }
}

function ensureRetryTimer(npc) {
    if (!npc.getTimers().has(CNPC_WIZARDS_TIMER_ID)) {
        npc.getTimers().start(
            CNPC_WIZARDS_TIMER_ID,
            CNPC_WIZARDS_CONFIG.retryIntervalTicks,
            true
        );
    }
}

function resolveRememberedTarget(npc) {
    var currentTarget = npc.getAttackTarget();
    if (isLivingTarget(currentTarget)) {
        rememberTarget(npc, currentTarget);
        return currentTarget;
    }

    var data = npc.getTempdata();
    if (!data.has(CNPC_WIZARDS_TARGET_KEY)) {
        return null;
    }

    var targetUuid = String(data.get(CNPC_WIZARDS_TARGET_KEY));
    var rememberedTarget = npc.getWorld().getEntity(targetUuid);
    if (!isLivingTarget(rememberedTarget)) {
        clearCombatState(npc);
        return null;
    }

    npc.setAttackTarget(rememberedTarget);
    return rememberedTarget;
}

function distanceSquared(first, second) {
    var dx = first.getX() - second.getX();
    var dy = first.getY() - second.getY();
    var dz = first.getZ() - second.getZ();
    return dx * dx + dy * dy + dz * dz;
}

function canAttemptSpell(npc, target, spell) {
    if (!isLivingTarget(target)) {
        return false;
    }
    if (spell.requireLineOfSight && !npc.canSeeEntity(target)) {
        return false;
    }
    if (spell.maxTargetDistance > 0.0
            && distanceSquared(npc, target)
                > spell.maxTargetDistance * spell.maxTargetDistance) {
        return false;
    }
    return true;
}

function createCastOptions(spell) {
    return CastOptions.builder(spell.spellId)
        .targetStrategyId(spell.targetStrategyId)
        .spellPowerMultiplier(spell.spellPowerMultiplier)
        .damageMultiplier(spell.damageMultiplier)
        .healingMultiplier(spell.healingMultiplier)
        .cooldownTicks(spell.cooldownTicks)
        .build();
}

function tryCastSpell(npc, target, spell) {
    var bridgeTarget = spell.targetStrategyId === "none"
        ? null
        : target.getMCEntity();

    try {
        var result = CnPcWizardsIntegration.castingApi().cast(
            npc.getMCEntity(),
            bridgeTarget,
            createCastOptions(spell)
        );
        var code = String(result.code());

        if (result.accepted()) {
            return "ACCEPTED";
        }
        if (code === "CASTER_BUSY" || code === "RECURSION_BLOCKED") {
            return "BUSY";
        }
        if (code === "COOLDOWN_ACTIVE" || code === "TARGET_NOT_FOUND") {
            return "RETRY";
        }

        log("[CNPC Wizards] spell=" + spell.spellId
            + " rejected code=" + result.code()
            + " trace=" + result.traceId()
            + " message=" + result.message());
        return "RETRY";
    } catch (error) {
        log("[CNPC Wizards] spell=" + spell.spellId
            + " script error: " + error);
        return "RETRY";
    }
}

function readRotationIndex(npc) {
    var data = npc.getTempdata();
    if (!data.has(CNPC_WIZARDS_ROTATION_KEY)) {
        return 0;
    }
    var index = Number(data.get(CNPC_WIZARDS_ROTATION_KEY));
    if (!isFinite(index) || index < 0) {
        return 0;
    }
    return Math.floor(index) % CNPC_WIZARDS_CONFIG.spells.length;
}

function isActionWindowReady(npc, now) {
    var data = npc.getTempdata();
    if (!data.has(CNPC_WIZARDS_NEXT_ACTION_KEY)) {
        return true;
    }
    return now >= Number(data.get(CNPC_WIZARDS_NEXT_ACTION_KEY));
}

function runSpellRotation(npc, target) {
    var spells = CNPC_WIZARDS_CONFIG.spells;
    if (spells.length === 0) {
        return;
    }

    var now = Number(npc.getWorld().getTotalTime());
    if (!isActionWindowReady(npc, now)) {
        return;
    }

    var startIndex = readRotationIndex(npc);
    for (var offset = 0; offset < spells.length; offset++) {
        var index = (startIndex + offset) % spells.length;
        var spell = spells[index];
        if (!canAttemptSpell(npc, target, spell)) {
            continue;
        }

        var outcome = tryCastSpell(npc, target, spell);
        if (outcome === "BUSY") {
            return;
        }
        if (outcome === "ACCEPTED") {
            npc.getTempdata().put(
                CNPC_WIZARDS_ROTATION_KEY,
                (index + 1) % spells.length
            );
            npc.getTempdata().put(
                CNPC_WIZARDS_NEXT_ACTION_KEY,
                now + CNPC_WIZARDS_CONFIG.minimumActionIntervalTicks
            );
            return;
        }
    }
}

function damaged(event) {
    if (event === null || event.npc === null) {
        return;
    }

    var attacker = resolveLivingAttacker(event);
    if (attacker === null
            || String(attacker.getUUID()) === String(event.npc.getUUID())) {
        return;
    }

    rememberTarget(event.npc, attacker);
    ensureRetryTimer(event.npc);
    runSpellRotation(event.npc, attacker);
}

function timer(event) {
    if (event === null || event.npc === null
            || event.id !== CNPC_WIZARDS_TIMER_ID) {
        return;
    }

    var target = resolveRememberedTarget(event.npc);
    if (target === null) {
        clearCombatState(event.npc);
        return;
    }

    runSpellRotation(event.npc, target);
}

function died(event) {
    if (event !== null && event.npc !== null) {
        clearCombatState(event.npc);
    }
}
