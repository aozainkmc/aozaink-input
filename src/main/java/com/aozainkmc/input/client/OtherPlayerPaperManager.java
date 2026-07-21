package com.aozainkmc.input.client;

import com.aozainkmc.input.network.ClientboundPaperClosePayload;
import com.aozainkmc.input.network.ClientboundPaperOpenPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side registry of other players' currently open temporary paper planes.
 */
public final class OtherPlayerPaperManager {
    private static final Map<UUID, InkPlane> PAPERS = new HashMap<>();

    private OtherPlayerPaperManager() {}

    public static void onPaperOpen(ClientboundPaperOpenPayload payload) {
        PAPERS.put(payload.playerId(), new InkPlane(
            payload.center(), payload.normal(), payload.right(), payload.up(), payload.radius()));
    }

    public static void onPaperClose(ClientboundPaperClosePayload payload) {
        PAPERS.remove(payload.playerId());
    }

    public static void clear() {
        PAPERS.clear();
    }

    public static Map<UUID, InkPlane> papers() {
        return PAPERS;
    }
}
