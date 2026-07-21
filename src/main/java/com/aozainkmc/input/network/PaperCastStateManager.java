package com.aozainkmc.input.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side registry of players currently writing on temporary paper planes.
 * Used to broadcast paper visibility to nearby players.
 */
public final class PaperCastStateManager {
    private static final double BROADCAST_RANGE_SQR = 64.0 * 64.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private PaperCastStateManager() {}

    public static void open(ServerPlayer player, ServerboundPaperOpenPayload payload) {
        if (!player.getMainHandItem().is(Items.PAPER)) return;
        Session session = new Session(payload.center(), payload.normal(), payload.right(), payload.up(), payload.radius());
        SESSIONS.put(player.getUUID(), session);
        broadcastToNearby(player, new ClientboundPaperOpenPayload(
            player.getUUID(), session.center, session.normal, session.right, session.up, session.radius));
    }

    public static void close(ServerPlayer player) {
        Session removed = SESSIONS.remove(player.getUUID());
        if (removed != null) {
            broadcastToNearby(player, new ClientboundPaperClosePayload(player.getUUID()));
        }
    }

    public static void onPlayerLoggedIn(ServerPlayer player) {
        Vec3 pos = player.position();
        for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
            if (entry.getKey().equals(player.getUUID())) continue;
            Session session = entry.getValue();
            if (session.center.distanceToSqr(pos) <= BROADCAST_RANGE_SQR) {
                PacketDistributor.sendToPlayer(player, new ClientboundPaperOpenPayload(
                    entry.getKey(), session.center, session.normal, session.right, session.up, session.radius));
            }
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        close(player);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    private static void broadcastToNearby(ServerPlayer source, CustomPacketPayload payload) {
        Vec3 pos = source.position();
        for (ServerPlayer other : source.serverLevel().players()) {
            if (other == source) continue;
            if (other.position().distanceToSqr(pos) <= BROADCAST_RANGE_SQR) {
                PacketDistributor.sendToPlayer(other, payload);
            }
        }
    }

    private record Session(Vec3 center, Vec3 normal, Vec3 right, Vec3 up, float radius) {}
}
