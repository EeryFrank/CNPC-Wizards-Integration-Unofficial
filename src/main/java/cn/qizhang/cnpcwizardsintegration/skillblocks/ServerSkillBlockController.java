package cn.qizhang.cnpcwizardsintegration.skillblocks;

import cn.qizhang.cnpcwizardsintegration.CnPcWizardsIntegration;
import cn.qizhang.cnpcwizardsintegration.api.CastOptions;
import cn.qizhang.cnpcwizardsintegration.api.CastResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;

/** Server-owned validation, binding, trigger handling and sequential block execution. */
public final class ServerSkillBlockController {
    private static final double MAX_BIND_DISTANCE_SQUARED = 12.0D * 12.0D;
    private static final int MAX_STEPS_PER_PROGRAM_PER_TICK = 64;
    private static final int MAX_TOTAL_STEPS_PER_SERVER_TICK = 2_048;
    private final Logger logger;
    private final SkillBlueprintValidator validator = new SkillBlueprintValidator();
    private final SkillBlueprintRepository repository;
    private final Map<UUID, RunningProgram> runningPrograms = new HashMap<>();

    public ServerSkillBlockController(Logger logger) {
        this.logger = logger;
        this.repository = new SkillBlueprintRepository(
                FabricLoader.getInstance().getConfigDir()
                        .resolve(CnPcWizardsIntegration.MOD_ID)
                        .resolve("skill_block_programs.json"));
    }

    public void initialize() {
        try {
            repository.load();
        }
        catch (Exception error) {
            logger.error("Could not load skill block programs; starting with an empty repository", error);
        }

        PayloadTypeRegistry.playC2S().register(BindSkillBlueprintPayload.ID, BindSkillBlueprintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BindSkillBlueprintPayload.ID, this::handleBindRequest);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof PlayerEntity)) {
                Entity attacker = source.getAttacker();
                if (!(attacker instanceof LivingEntity)) {
                    attacker = source.getSource();
                }
                if (attacker instanceof LivingEntity livingAttacker) {
                    start(entity, livingAttacker);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        logger.info(
                "Skill block runtime initialized; blueprints={} bindings={} block_types={}",
                repository.blueprintCount(),
                repository.bindingCount(),
                SkillBlockType.values().length);
    }

    private void handleBindRequest(BindSkillBlueprintPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 绑定 NPC 需要管理员权限"), false);
            return;
        }
        if (payload.blueprintJson().length() > SkillBlueprintValidator.MAX_JSON_LENGTH) {
            player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 方案数据过大"), false);
            return;
        }
        Entity entity = player.getWorld().getEntityById(payload.entityId());
        if (!(entity instanceof LivingEntity living) || entity instanceof PlayerEntity) {
            player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 请对准一个非玩家生物或 NPC"), false);
            return;
        }
        if (player.squaredDistanceTo(entity) > MAX_BIND_DISTANCE_SQUARED) {
            player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 目标距离超过 12 格"), false);
            return;
        }
        try {
            SkillBlueprint blueprint = SkillBlueprintCodec.fromJson(payload.blueprintJson());
            SkillBlueprintValidator.ValidationResult validation = validator.validate(blueprint);
            if (!validation.valid()) {
                player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 验证失败：" + validation.summary()), false);
                return;
            }
            repository.putAndBind(blueprint, living.getUuid());
            player.sendMessage(net.minecraft.text.Text.literal(
                    "[CNPC Wizards] 已保存“" + blueprint.name() + "”并绑定到 " + living.getName().getString()), false);
            logger.info(
                    "skill_blueprint_bound player={} entity={} entity_uuid={} blueprint={} blocks={}",
                    player.getGameProfile().getName(),
                    living.getName().getString(),
                    living.getUuid(),
                    blueprint.id(),
                    blueprint.blocks().size());
        }
        catch (RuntimeException | IOException error) {
            player.sendMessage(net.minecraft.text.Text.literal("[CNPC Wizards] 保存失败：" + error.getMessage()), false);
            logger.warn("Rejected skill blueprint bind request from {}", player.getGameProfile().getName(), error);
        }
    }

    private void start(LivingEntity caster, LivingEntity attacker) {
        if (runningPrograms.containsKey(caster.getUuid())) {
            return;
        }
        repository.blueprintFor(caster.getUuid()).ifPresent(blueprint -> {
            RunningProgram program = new RunningProgram(
                    blueprint,
                    caster,
                    attacker,
                    new SkillProgramCursor(blueprint.blocks()),
                    caster.getServer().getTicks() + 1L);
            runningPrograms.put(caster.getUuid(), program);
        });
    }

    private void tick(MinecraftServer server) {
        long tick = server.getTicks();
        int remainingServerBudget = MAX_TOTAL_STEPS_PER_SERVER_TICK;
        for (RunningProgram program : runningPrograms.values().toArray(RunningProgram[]::new)) {
            if (remainingServerBudget <= 0) {
                break;
            }
            if (program.resumeAtTick() <= tick) {
                int programBudget = Math.min(MAX_STEPS_PER_PROGRAM_PER_TICK, remainingServerBudget);
                remainingServerBudget -= executeUntilYield(program, tick, programBudget);
            }
        }
    }

    private int executeUntilYield(RunningProgram program, long tick, int stepBudget) {
        LivingEntity caster = program.caster();
        LivingEntity target = program.target();
        if (!caster.isAlive() || caster.isRemoved() || target == null || !target.isAlive() || target.isRemoved()) {
            runningPrograms.remove(caster.getUuid());
            return 0;
        }

        int consumed = 0;
        try {
            while (consumed < stepBudget) {
                SkillProgramCursor.Step step = program.cursor().next();
                if (step.kind() == SkillProgramCursor.Kind.FINISHED) {
                    runningPrograms.remove(caster.getUuid());
                    return consumed;
                }
                if (step.kind() == SkillProgramCursor.Kind.BUDGET_EXCEEDED
                        || step.kind() == SkillProgramCursor.Kind.INVALID) {
                    logger.warn(
                            "Stopped unsafe skill block program blueprint={} caster={} reason={}",
                            program.blueprint().id(),
                            caster.getUuid(),
                            step.error());
                    runningPrograms.remove(caster.getUuid());
                    return consumed;
                }
                consumed++;
                if (step.kind() == SkillProgramCursor.Kind.CONTROL) {
                    continue;
                }

                SkillBlock block = step.block();
                if (block.type() == SkillBlockType.WAIT_TICKS) {
                    long resume = tick + intParameter(block, "ticks");
                    runningPrograms.put(caster.getUuid(), program.withResumeAtTick(resume));
                    return consumed;
                }
                if (!executeBlock(block, caster, target)) {
                    runningPrograms.remove(caster.getUuid());
                    return consumed;
                }
            }
            runningPrograms.put(caster.getUuid(), program.withResumeAtTick(tick + 1L));
            return consumed;
        }
        catch (RuntimeException error) {
            logger.warn(
                    "Stopped invalid skill block program blueprint={} caster={} after_steps={}",
                    program.blueprint().id(),
                    caster.getUuid(),
                    program.cursor().executedSteps(),
                    error);
            runningPrograms.remove(caster.getUuid());
            return consumed;
        }
    }

    private boolean executeBlock(SkillBlock block, LivingEntity caster, LivingEntity target) {
        ServerWorld world = (ServerWorld) caster.getWorld();
        switch (block.type()) {
            case TARGET_ATTACKER, TRIGGER_DAMAGED -> {
                return true;
            }
            case CONDITION_DISTANCE -> {
                double maximum = doubleParameter(block, "max_distance");
                return caster.squaredDistanceTo(target) <= maximum * maximum;
            }
            case CONDITION_VISIBLE -> {
                return caster.canSee(target);
            }
            case CAST_SPELL -> {
                String targetStrategy = block.parameter("target_strategy");
                LivingEntity directTarget = "none".equals(targetStrategy) ? null : target;
                CastOptions options = CastOptions.builder(block.parameter("spell_id"))
                        .targetStrategyId(targetStrategy)
                        .spellPowerMultiplier(doubleParameter(block, "power_multiplier"))
                        .damageMultiplier(doubleParameter(block, "damage_multiplier"))
                        .healingMultiplier(doubleParameter(block, "healing_multiplier"))
                        .cooldownTicks(intParameter(block, "cooldown_ticks"))
                        .build();
                CastResult result = CnPcWizardsIntegration.castingApi().cast(caster, directTarget, options);
                logger.info(
                        "skill_block_cast blueprint_block={} caster={} spell={} result={} trace={}",
                        block.id(), caster.getUuid(), options.spellId(), result.code(), result.traceId());
                return result.accepted();
            }
            case HEAL_SELF -> caster.heal((float) doubleParameter(block, "amount"));
            case HEAL_TARGET -> target.heal((float) doubleParameter(block, "amount"));
            case DAMAGE_TARGET -> target.damage(caster.getDamageSources().magic(), (float) doubleParameter(block, "amount"));
            case KNOCKBACK_TARGET -> target.takeKnockback(
                    doubleParameter(block, "strength"),
                    caster.getX() - target.getX(),
                    caster.getZ() - target.getZ());
            case PULL_TARGET -> {
                Vec3d pull = caster.getPos().subtract(target.getPos()).normalize().multiply(doubleParameter(block, "strength"));
                target.addVelocity(pull.x, Math.max(0.08D, pull.y * 0.25D), pull.z);
            }
            case IGNITE_TARGET -> target.setOnFireFor(intParameter(block, "seconds"));
            case EXTINGUISH_SELF -> caster.extinguish();
            case APPLY_STATUS_EFFECT -> applyStatusEffect(block, target);
            case SWING_MAIN_HAND -> caster.swingHand(Hand.MAIN_HAND, true);
            case SWING_OFF_HAND -> caster.swingHand(Hand.OFF_HAND, true);
            case HURT_ANIMATION -> world.sendEntityStatus(caster, (byte) 2);
            case PARTICLE_BURST -> particleBurst(block, world, caster);
            case PARTICLE_RING -> particleRing(block, world, caster);
            case PLAY_SOUND -> playSound(block, world, caster);
            case END -> {
                return false;
            }
            case WAIT_TICKS -> throw new IllegalStateException("wait blocks are handled by the scheduler");
            case LOOP_START, LOOP_END -> throw new IllegalStateException("loop blocks are handled by the program cursor");
        }
        return true;
    }

    private static void applyStatusEffect(SkillBlock block, LivingEntity target) {
        Identifier id = Identifier.of(block.parameter("effect_id"));
        RegistryEntry.Reference<StatusEffect> effect = Registries.STATUS_EFFECT.getEntry(id)
                .orElseThrow(() -> new IllegalArgumentException("unknown status effect " + id));
        target.addStatusEffect(new StatusEffectInstance(
                effect,
                intParameter(block, "duration_ticks"),
                intParameter(block, "amplifier")));
    }

    private static void particleBurst(SkillBlock block, ServerWorld world, LivingEntity caster) {
        SimpleParticleType particle = (SimpleParticleType) Registries.PARTICLE_TYPE.get(Identifier.of(block.parameter("particle_id")));
        world.spawnParticles(
                particle,
                caster.getX(),
                caster.getBodyY(0.55D),
                caster.getZ(),
                intParameter(block, "count"),
                0.55D,
                0.7D,
                0.55D,
                doubleParameter(block, "speed"));
    }

    private static void particleRing(SkillBlock block, ServerWorld world, LivingEntity caster) {
        SimpleParticleType particle = (SimpleParticleType) Registries.PARTICLE_TYPE.get(Identifier.of(block.parameter("particle_id")));
        int count = intParameter(block, "count");
        double radius = doubleParameter(block, "radius");
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count;
            world.spawnParticles(
                    particle,
                    caster.getX() + Math.cos(angle) * radius,
                    caster.getY() + 0.15D,
                    caster.getZ() + Math.sin(angle) * radius,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private static void playSound(SkillBlock block, ServerWorld world, LivingEntity caster) {
        SoundEvent sound = Registries.SOUND_EVENT.get(Identifier.of(block.parameter("sound_id")));
        world.playSound(
                null,
                caster.getBlockPos(),
                sound,
                SoundCategory.HOSTILE,
                (float) doubleParameter(block, "volume"),
                (float) doubleParameter(block, "pitch"));
    }

    private static int intParameter(SkillBlock block, String key) {
        return Integer.parseInt(block.parameter(key));
    }

    private static double doubleParameter(SkillBlock block, String key) {
        return Double.parseDouble(block.parameter(key));
    }

    private record RunningProgram(
            SkillBlueprint blueprint,
            LivingEntity caster,
            LivingEntity target,
            SkillProgramCursor cursor,
            long resumeAtTick) {
        private RunningProgram withResumeAtTick(long resumeAtTick) {
            return new RunningProgram(blueprint, caster, target, cursor, resumeAtTick);
        }
    }
}
