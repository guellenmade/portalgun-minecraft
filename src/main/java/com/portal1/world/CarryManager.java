package com.portal1.world;

import com.portal1.entity.WeightedCubeEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Portal 1 CPlayerPickupController recreation.
 *
 * Allows players to pick up and carry Weighted Storage Cubes smoothly in front of them
 * using the Use/Carry key, and gently drop them. No Gravity Gun features (no punt/launch,
 * no long-range pull) — strictly faithful to Portal 1's pickup behavior.
 */
public final class CarryManager {

    public static final double PICKUP_REACH = 3.6;
    public static final double HOLD_DISTANCE = 2.1;
    public static final double MAX_HOLD_STRETCH = 4.5;
    private static final double SPRING_GAIN = 0.48;
    private static final double MAX_FOLLOW_SPEED = 0.9;

    // Maps player UUID -> carried WeightedCubeEntity UUID
    private static final Map<UUID, UUID> CARRIED_BY_PLAYER = new HashMap<>();

    private CarryManager() {}

    public static void toggleCarry(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (CARRIED_BY_PLAYER.containsKey(playerId)) {
            dropCarried(player);
            return;
        }
        tryPickup(player);
    }

    public static boolean isCarrying(UUID playerId) {
        return CARRIED_BY_PLAYER.containsKey(playerId);
    }

    public static WeightedCubeEntity getCarriedCube(ServerPlayer player) {
        UUID cubeId = CARRIED_BY_PLAYER.get(player.getUUID());
        if (cubeId == null) {
            return null;
        }
        Entity entity = player.level().getEntity(cubeId);
        if (entity instanceof WeightedCubeEntity cube && cube.isAlive()) {
            return cube;
        }
        CARRIED_BY_PLAYER.remove(player.getUUID());
        return null;
    }

    public static void dropCarried(ServerPlayer player) {
        UUID cubeId = CARRIED_BY_PLAYER.remove(player.getUUID());
        if (cubeId == null) {
            return;
        }
        Entity entity = player.level().getEntity(cubeId);
        if (entity instanceof WeightedCubeEntity cube) {
            cube.setCarrier(null);
            // Gentle drop — NO punt impulse, faithful to Portal 1
            cube.setDeltaMovement(cube.getDeltaMovement().scale(0.35));
            player.level().playSound(null, cube.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.35f, 0.75f);
        }
    }

    public static void forceDropCube(WeightedCubeEntity cube) {
        UUID carrier = cube.getCarrierUuid();
        if (carrier != null) {
            CARRIED_BY_PLAYER.remove(carrier);
            cube.setCarrier(null);
        }
    }

    private static void tryPickup(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 reachEnd = eyePos.add(lookVec.scale(PICKUP_REACH));

        // Raycast blocks first so we don't pick up cubes through solid walls
        BlockHitResult blockHit = level.clip(new ClipContext(
            eyePos, reachEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ));
        double maxDist = blockHit.getType() == HitResult.Type.BLOCK
            ? blockHit.getLocation().distanceTo(eyePos)
            : PICKUP_REACH;

        AABB searchBox = new AABB(eyePos, reachEnd).inflate(0.75);
        List<WeightedCubeEntity> cubes = level.getEntitiesOfClass(WeightedCubeEntity.class, searchBox);

        WeightedCubeEntity best = null;
        double bestDist = maxDist;
        for (WeightedCubeEntity cube : cubes) {
            if (!cube.isAlive() || cube.getCarrierUuid() != null) {
                continue;
            }
            var hit = cube.getBoundingBox().inflate(0.25).clip(eyePos, reachEnd);
            if (hit.isPresent()) {
                double d = hit.get().distanceTo(eyePos);
                if (d < bestDist) {
                    bestDist = d;
                    best = cube;
                }
            }
        }

        if (best != null) {
            CARRIED_BY_PLAYER.put(player.getUUID(), best.getUUID());
            best.setCarrier(player.getUUID());
            level.playSound(null, best.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.45f, 1.35f);
        }
    }

    public static void tickServer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, UUID>> it = CARRIED_BY_PLAYER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            ServerLevel level = player.level();
            Entity entity = level.getEntity(entry.getValue());
            if (!(entity instanceof WeightedCubeEntity cube) || !cube.isAlive()) {
                it.remove();
                continue;
            }

            Vec3 eyePos = player.getEyePosition();
            Vec3 look = player.getLookAngle();
            Vec3 desiredCenter = eyePos.add(look.scale(HOLD_DISTANCE));

            // Stop hold point slightly before solid blocks so the cube doesn't jam into walls
            BlockHitResult wallHit = level.clip(new ClipContext(
                eyePos, desiredCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
            ));
            if (wallHit.getType() == HitResult.Type.BLOCK) {
                double hitDist = Math.max(1.1, wallHit.getLocation().distanceTo(eyePos) - 0.55);
                desiredCenter = eyePos.add(look.scale(hitDist));
            }

            // Target is cube's bottom-center position (cube height = 0.85)
            Vec3 targetPos = new Vec3(desiredCenter.x, desiredCenter.y - WeightedCubeEntity.CUBE_SIZE * 0.5, desiredCenter.z);
            Vec3 diff = targetPos.subtract(cube.position());

            // Auto-drop if stuck far behind geometry (matches Portal 1 pickup tether break)
            if (diff.length() > MAX_HOLD_STRETCH) {
                cube.setCarrier(null);
                it.remove();
                continue;
            }

            Vec3 vel = diff.scale(SPRING_GAIN);
            if (vel.length() > MAX_FOLLOW_SPEED) {
                vel = vel.normalize().scale(MAX_FOLLOW_SPEED);
            }
            cube.setDeltaMovement(vel);
            cube.setYRot(player.getYRot());
            cube.resetFallDistance();
        }
    }

    public static void onPlayerDisconnect(UUID playerId) {
        CARRIED_BY_PLAYER.remove(playerId);
    }
}
