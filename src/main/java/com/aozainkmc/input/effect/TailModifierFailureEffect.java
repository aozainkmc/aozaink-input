package com.aozainkmc.input.effect;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.signal.InputSignals;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class TailModifierFailureEffect {
    private static final int PHASE_TICKS = 10;
    private static final int PHASE_COUNT = 6;
    private static final int WARNING_TICKS = PHASE_TICKS * PHASE_COUNT;
    private static final float EXPLOSION_RADIUS = 5.0f;
    private static final DustParticleOptions CINNABAR_DUST =
        new DustParticleOptions(new Vector3f(0.9f, 0.02f, 0.01f), 1.3f);
    private static final List<Instance> ACTIVE = new CopyOnWriteArrayList<>();

    private TailModifierFailureEffect() {}

    public static void start(ServerPlayer owner, BlockPos pos) {
        ACTIVE.add(new Instance(owner.serverLevel(), pos.immutable(), owner.getUUID()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ACTIVE.isEmpty()) {
            ACTIVE.removeIf(Instance::tick);
        }
    }

    private static final class Instance {
        private final ServerLevel level;
        private final BlockPos pos;
        private final UUID ownerId;
        private int age;

        private Instance(ServerLevel level, BlockPos pos, UUID ownerId) {
            this.level = level;
            this.pos = pos;
            this.ownerId = ownerId;
        }

        private boolean tick() {
            if (!level.hasChunkAt(pos)) {
                return true;
            }
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.65;
            double z = pos.getZ() + 0.5;
            if (age >= WARNING_TICKS) {
                explodeWithoutBlockDamage(x, y, z);
                return true;
            }

            if (age % PHASE_TICKS == 0) {
                level.playSound(null, x, y, z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.7f, 0.75f + age * 0.01f);
            }
            spawnPhaseParticles(x, y, z, age);
            age++;
            return false;
        }

        private void spawnPhaseParticles(double x, double y, double z, int age) {
            int phase = age / PHASE_TICKS;
            double progress = (age % PHASE_TICKS) / (double) PHASE_TICKS;
            boolean expanding = phase == 1 || phase == 3 || phase == 5;
            double radius = switch (phase) {
                case 0 -> 0.08 + 0.16 * (1.0 - progress);
                case 1 -> 0.08 + 0.42 * progress;
                case 2 -> 0.08 + 0.42 * (1.0 - progress);
                case 3 -> 0.08 + 0.92 * progress;
                case 4 -> 0.08 + 0.92 * (1.0 - progress);
                default -> 0.08 + 1.42 * progress;
            };

            int count = expanding ? 42 : 26;
            for (int i = 0; i < count; i++) {
                double u = level.random.nextDouble() * 2.0 - 1.0;
                double theta = level.random.nextDouble() * Math.PI * 2.0;
                double ring = Math.sqrt(Math.max(0.0, 1.0 - u * u));
                double r = radius * (0.8 + level.random.nextDouble() * 0.2);
                double px = x + Math.cos(theta) * ring * r;
                double py = y + u * r;
                double pz = z + Math.sin(theta) * ring * r;
                level.sendParticles(CINNABAR_DUST, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        private void explodeWithoutBlockDamage(double x, double y, double z) {
            Vec3 center = new Vec3(x, y, z);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            boolean escaped = owner != null && owner.position().distanceTo(center) > EXPLOSION_RADIUS;
            boolean hitOwner = false;
            boolean killedEnemy = false;
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            AABB box = new AABB(
                x - EXPLOSION_RADIUS, y - EXPLOSION_RADIUS, z - EXPLOSION_RADIUS,
                x + EXPLOSION_RADIUS, y + EXPLOSION_RADIUS, z + EXPLOSION_RADIUS
            );
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                Vec3 hitCenter = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
                double distance = hitCenter.distanceTo(center);
                if (distance > EXPLOSION_RADIUS) continue;

                double exposure = 1.0 - distance / EXPLOSION_RADIUS;
                float damage = (float) ((exposure * exposure + exposure) * 0.5 * 7.0 * EXPLOSION_RADIUS + 1.0);
                boolean enemy = entity instanceof Enemy;
                entity.hurt(level.damageSources().explosion(null, null), damage);
                if (entity == owner) {
                    hitOwner = true;
                }
                if (enemy && !entity.isAlive()) {
                    killedEnemy = true;
                }

                Vec3 away = hitCenter.subtract(center);
                if (away.lengthSqr() > 0.0001) {
                    Vec3 knockback = away.normalize().scale(1.2 * exposure);
                    entity.push(knockback.x, knockback.y * 0.6 + 0.1 * exposure, knockback.z);
                }
            }
            if (owner != null) {
                InputSignals.tailModifierChaos(owner, escaped, hitOwner, killedEnemy);
            }
        }
    }
}
