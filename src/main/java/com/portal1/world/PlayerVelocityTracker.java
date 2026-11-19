package com.portal1.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Tracks true per-tick velocity for every ServerPlayer globally (not just near portals).
 *
 * Because Minecraft player movement is client-authoritative, ServerPlayer.getDeltaMovement()
 * is frequently stale on the server. Tracking actual frame-to-frame position deltas globally
 * ensures that falling into a floor portal or looping through floor-to-ceiling portals
 * preserves and continuously accumulates full momentum up to terminal velocity.
 */
public final class PlayerVelocityTracker {

    private static final double MAX_VALID_SPEED_SQR = 49.0; // 7 blocks/tick cap to ignore teleports

    private static final Map<UUID, Vec3> PREV_POS = new HashMap<>();
    private static final Map<UUID, Vec3> CURRENT_VEL = new HashMap<>();
    private static final Map<UUID, Long> IGNORE_DELTA_UNTIL = new HashMap<>();

    private PlayerVelocityTracker() {}

    public static void tickServer(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            Vec3 curPos = player.position();
            Vec3 prev = PREV_POS.put(id, curPos);

            Long ignoreUntil = IGNORE_DELTA_UNTIL.get(id);
            if (ignoreUntil != null && now <= ignoreUntil) {
                continue;
            }

            if (prev != null) {
                Vec3 delta = curPos.subtract(prev);
                if (delta.lengthSqr() < MAX_VALID_SPEED_SQR) {
                    // Only overwrite if the player actually moved this tick, or decay smoothly
                    if (delta.lengthSqr() > 1.0e-5) {
                        CURRENT_VEL.put(id, delta);
                    } else {
                        Vec3 old = CURRENT_VEL.getOrDefault(id, Vec3.ZERO);
                        CURRENT_VEL.put(id, old.scale(0.5));
                    }
                }
            }
        }
    }

    public static Vec3 getVelocity(ServerPlayer player) {
        Vec3 tracked = CURRENT_VEL.get(player.getUUID());
        Vec3 raw = player.getDeltaMovement();
        if (tracked == null) {
            return raw;
        }
        return tracked.lengthSqr() >= raw.lengthSqr() ? tracked : raw;
    }

    /**
     * Updates tracker state immediately on portal teleport so the destination tick starts
     * cleanly from the transformed exit velocity and ignores the teleport position jump.
     */
    public static void onTeleport(ServerPlayer player, Vec3 destPos, Vec3 exitVelocity, long gameTime) {
        UUID id = player.getUUID();
        PREV_POS.put(id, destPos);
        CURRENT_VEL.put(id, exitVelocity);
        IGNORE_DELTA_UNTIL.put(id, gameTime + 1L);
    }

    public static void onDisconnect(UUID playerId) {
        PREV_POS.remove(playerId);
        CURRENT_VEL.remove(playerId);
        IGNORE_DELTA_UNTIL.remove(playerId);
    }
}
