package com.aozainkmc.input.effect;

import com.aozainkmc.input.AozaiInkInput;
import com.aozainkmc.input.network.TalismanFormationPayload;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AozaiInkInput.MOD_ID)
public final class TalismanFormationEffect {
    public static final int SUCCESS_TICKS = 28;
    private static final double BROADCAST_DISTANCE_SQR = 64.0D * 64.0D;
    private static final List<PendingGrant> PENDING = new CopyOnWriteArrayList<>();

    private TalismanFormationEffect() {}

    public static void startSuccess(ServerPlayer owner, BlockPos pos, ItemStack stack) {
        broadcast(owner.serverLevel(), pos, new TalismanFormationPayload(pos, owner.getUUID(), false));
        PENDING.add(new PendingGrant(owner.serverLevel(), pos.immutable(), owner.getUUID(), stack.copy()));
    }

    public static void startChaos(ServerPlayer owner, BlockPos pos) {
        broadcast(owner.serverLevel(), pos, new TalismanFormationPayload(pos, owner.getUUID(), true));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PENDING.removeIf(PendingGrant::tick);
    }

    private static void broadcast(ServerLevel level, BlockPos pos, TalismanFormationPayload payload) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= BROADCAST_DISTANCE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static final class PendingGrant {
        private final ServerLevel level;
        private final BlockPos pos;
        private final UUID ownerId;
        private final ItemStack stack;
        private int age;

        private PendingGrant(ServerLevel level, BlockPos pos, UUID ownerId, ItemStack stack) {
            this.level = level;
            this.pos = pos;
            this.ownerId = ownerId;
            this.stack = stack;
        }

        private boolean tick() {
            if (++age < SUCCESS_TICKS) return false;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                if (!owner.getInventory().add(stack)) owner.drop(stack, false);
            } else {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.65D,
                    pos.getZ() + 0.5D, stack));
            }
            return true;
        }
    }
}
