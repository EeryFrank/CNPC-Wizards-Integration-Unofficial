/*
 * CNPC Wizards Integration (Unofficial)
 * 把完整文件粘贴到一个 CustomNPCs NPC 的 ECMAScript 编辑器中。
 *
 * 行为：NPC 首次受击后记住攻击者并立即尝试施法；之后只要目标仍存活、
 * 可见且桥接层判定可以命中，就在冷却结束后自动再次施法。
 */

var CnPcWizardsIntegration = Java.type(
    "cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration"
);
var CastOptions = Java.type(
    "cn.qizhang.cnpcwizardsintegration.api.CastOptions"
);
var IEntityLiving = Java.type("noppes.npcs.api.entity.IEntityLiving");

// 只需要修改此配置块；20 tick 约等于 1 秒。
var CNPC_WIZARDS_CONFIG = {
    spellId: "wizards:fireball",
    targetStrategyId: "direct",
    spellPowerMultiplier: 1.0,
    damageMultiplier: 1.0,
    healingMultiplier: 1.0,
    cooldownTicks: 40,

    // 每隔多少 tick 检查一次目标。5 tick 约等于 0.25 秒。
    retryIntervalTicks: 5,
    // 直线索敌法术默认要求 NPC 能看见目标。
    requireLineOfSight: true
};

// 同一 NPC 的其他脚本如果也使用计时器，请避免复用这个 ID。
var CNPC_WIZARDS_TIMER_ID = 913401;
var CNPC_WIZARDS_TARGET_KEY = "cnpc_wizards_integration.target_uuid";

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
    if (!isLivingTarget(source)) {
        return null;
    }
    return source;
}

function rememberTarget(npc, target) {
    npc.getTempdata().put(CNPC_WIZARDS_TARGET_KEY, String(target.getUUID()));
    npc.setAttackTarget(target);
}

function clearRememberedTarget(npc) {
    npc.getTempdata().remove(CNPC_WIZARDS_TARGET_KEY);
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
        npc.getTempdata().put(
            CNPC_WIZARDS_TARGET_KEY,
            String(currentTarget.getUUID())
        );
        return currentTarget;
    }

    var data = npc.getTempdata();
    if (!data.has(CNPC_WIZARDS_TARGET_KEY)) {
        return null;
    }

    var targetUuid = String(data.get(CNPC_WIZARDS_TARGET_KEY));
    var rememberedTarget = npc.getWorld().getEntity(targetUuid);
    if (!isLivingTarget(rememberedTarget)) {
        clearRememberedTarget(npc);
        return null;
    }

    npc.setAttackTarget(rememberedTarget);
    return rememberedTarget;
}

function createCastOptions() {
    return CastOptions.builder(CNPC_WIZARDS_CONFIG.spellId)
        .targetStrategyId(CNPC_WIZARDS_CONFIG.targetStrategyId)
        .spellPowerMultiplier(CNPC_WIZARDS_CONFIG.spellPowerMultiplier)
        .damageMultiplier(CNPC_WIZARDS_CONFIG.damageMultiplier)
        .healingMultiplier(CNPC_WIZARDS_CONFIG.healingMultiplier)
        .cooldownTicks(CNPC_WIZARDS_CONFIG.cooldownTicks)
        .build();
}

function isExpectedRetry(result) {
    var code = String(result.code());
    return code === "COOLDOWN_ACTIVE"
        || code === "TARGET_NOT_FOUND"
        || code === "CASTER_BUSY";
}

function tryCastAtTarget(npc, target) {
    if (!isLivingTarget(target)) {
        return;
    }
    if (CNPC_WIZARDS_CONFIG.requireLineOfSight
            && !npc.canSeeEntity(target)) {
        return;
    }

    try {
        var result = CnPcWizardsIntegration.castingApi().cast(
            npc.getMCEntity(),
            target.getMCEntity(),
            createCastOptions()
        );

        if (!result.accepted() && !isExpectedRetry(result)) {
            log("[CNPC Wizards] rejected code=" + result.code()
                + " trace=" + result.traceId()
                + " message=" + result.message());
        }
    } catch (error) {
        log("[CNPC Wizards] script error: " + error);
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
    tryCastAtTarget(event.npc, attacker);
}

function timer(event) {
    if (event === null || event.npc === null
            || event.id !== CNPC_WIZARDS_TIMER_ID) {
        return;
    }

    var target = resolveRememberedTarget(event.npc);
    if (target === null) {
        clearRememberedTarget(event.npc);
        return;
    }

    tryCastAtTarget(event.npc, target);
}

function died(event) {
    if (event !== null && event.npc !== null) {
        clearRememberedTarget(event.npc);
    }
}
