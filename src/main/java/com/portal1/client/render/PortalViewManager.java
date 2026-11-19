package com.portal1.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.portal1.Portal1Mod;
import com.portal1.core.PortalBasis;
import com.portal1.core.PortalTransform;
import com.portal1.entity.PortalEntity;
import com.portal1.entity.WeightedCubeEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Generates live, perspective-correct see-through portal views for linked PortalEntities.
 *
 * For every pixel inside the oval aperture, casts a 3D ray from the viewer's eye through that
 * point on the entry portal, transforms both ray origin and direction through PortalTransform
 * into the linked portal's world space, and performs a fast DDA voxel + entity raycast into
 * ClientLevel. This yields real line-of-sight with true 3D parallax into the destination chamber
 * with zero GPU pipeline recursion or shader incompatibilities.
 */
public final class PortalViewManager {

    private static final int TEX_W = 64;
    private static final int TEX_H = 96;
    private static final double MAX_TRACE_DIST = 32.0;

    private static final class Entry {
        final Identifier textureId;
        final DynamicTexture texture;
        long lastUpdateTick = -100;
        long lastSeenTick = 0;
        Vec3 lastCamPos = Vec3.ZERO;

        Entry(int entityId) {
            this.textureId = Portal1Mod.id("dynamic/portal_view_" + entityId);
            this.texture = new DynamicTexture(() -> "portal1_view_" + entityId, TEX_W, TEX_H, false);
            Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(this.textureId);
            this.texture.close();
        }
    }

    private static final Map<Integer, Entry> VIEWS = new HashMap<>();

    private PortalViewManager() {}

    public static Identifier getOrUpdateViewTexture(PortalEntity portal, Vec3 cameraPos) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Portal1Mod.id("textures/entity/portal_ring.png");
        }

        long gameTime = level.getGameTime();
        Entry entry = VIEWS.computeIfAbsent(portal.getId(), Entry::new);
        entry.lastSeenTick = gameTime;

        // Smooth every-tick update when camera moves (20 Hz parallax), throttled to 10 Hz when stationary
        boolean camMoved = cameraPos.distanceToSqr(entry.lastCamPos) > 0.0025;
        if (camMoved || (gameTime - entry.lastUpdateTick) >= 2) {
            entry.lastUpdateTick = gameTime;
            entry.lastCamPos = cameraPos;
            renderViewIntoTexture(level, portal, cameraPos, entry.texture);
        }

        return entry.textureId;
    }

    public static void tickCleanup() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clearAll();
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<Integer, Entry>> it = VIEWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            if (now - e.getValue().lastSeenTick > 40) {
                e.getValue().close();
                it.remove();
            }
        }
    }

    public static void clearAll() {
        for (Entry e : VIEWS.values()) {
            e.close();
        }
        VIEWS.clear();
    }

    private static void renderViewIntoTexture(
        ClientLevel level,
        PortalEntity portal,
        Vec3 cameraPos,
        DynamicTexture texture
    ) {
        NativeImage img = texture.getPixels();
        if (img == null) {
            return;
        }

        boolean linked = portal.isLinked();
        int rimColor = portal.getColorRgb();
        int rimR = (rimColor >> 16) & 0xFF;
        int rimG = (rimColor >> 8) & 0xFF;
        int rimB = rimColor & 0xFF;
        float time = (level.getGameTime() % 200) * 0.15f;

        if (!linked) {
            // Unlinked Portal 1 behavior: opaque swirling vortex in owner's portal color
            for (int py = 0; py < TEX_H; py++) {
                double ny = 1.0 - 2.0 * ((py + 0.5) / TEX_H);
                for (int px = 0; px < TEX_W; px++) {
                    double nx = 2.0 * ((px + 0.5) / TEX_W) - 1.0;
                    double r2 = nx * nx + ny * ny;
                    if (r2 > 1.0) {
                        img.setPixel(px, py, 0x00000000);
                        continue;
                    }
                    double r = Math.sqrt(r2);
                    double angle = Math.atan2(ny, nx);
                    double swirl = 0.55 + 0.45 * Math.sin(angle * 3.0 + r * 6.0 - time);
                    double edgeGlow = r > 0.78 ? 1.0 : (0.35 + 0.55 * swirl);
                    int cr = Mth.clamp((int) (rimR * edgeGlow + (r > 0.88 ? 60 : 0)), 0, 255);
                    int cg = Mth.clamp((int) (rimG * edgeGlow + (r > 0.88 ? 60 : 0)), 0, 255);
                    int cb = Mth.clamp((int) (rimB * edgeGlow + (r > 0.88 ? 60 : 0)), 0, 255);
                    img.setPixel(px, py, (0xFF << 24) | (cr << 16) | (cg << 8) | cb);
                }
            }
            texture.upload();
            return;
        }

        PortalBasis entryBasis = portal.getPortalBasis();
        PortalBasis exitBasis = portal.getLinkedPortalBasis();
        Vec3 entryCenter = portal.position();
        Vec3 exitCenter = portal.getLinkedPos();

        // Gather visible entities near the exit portal so you can see players & Weighted Cubes through portals!
        AABB exitEntityBox = new AABB(exitCenter, exitCenter).inflate(14.0);
        List<Entity> remoteEntities = level.getEntities(
            (Entity) null,
            exitEntityBox,
            e -> e.isAlive() && (e instanceof Player || e instanceof WeightedCubeEntity)
        );

        // Sky tint based on world time of day
        float dayFactor = 0.92f;
        int skyR = (int) (135 * dayFactor);
        int skyG = (int) (185 * dayFactor);
        int skyB = (int) (245 * dayFactor);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int py = 0; py < TEX_H; py++) {
            // ny = +1 at top, -1 at bottom
            double ny = 1.0 - 2.0 * ((py + 0.5) / TEX_H);
            for (int px = 0; px < TEX_W; px++) {
                double nx = 2.0 * ((px + 0.5) / TEX_W) - 1.0;
                double r2 = nx * nx + ny * ny;
                if (r2 > 1.0) {
                    img.setPixel(px, py, 0x00000000);
                    continue;
                }

                // Glowing Portal 1 oval border rim with smooth anti-aliased outer edge
                if (r2 > 0.78) {
                    double r = Math.sqrt(r2);
                    double angle = Math.atan2(ny, nx);
                    double pulse = 0.82 + 0.18 * Math.sin(angle * 4.0 - time * 1.4);
                    int alpha = r > 0.94 ? Mth.clamp((int) ((1.0 - r) / 0.06 * 255.0), 0, 255) : 255;
                    int cr = Mth.clamp((int) (rimR * pulse + 40), 0, 255);
                    int cg = Mth.clamp((int) (rimG * pulse + 40), 0, 255);
                    int cb = Mth.clamp((int) (rimB * pulse + 40), 0, 255);
                    img.setPixel(px, py, (alpha << 24) | (cr << 16) | (cg << 8) | cb);
                    continue;
                }

                // Point on entry portal plane in world space
                Vec3 localPoint = entryBasis.toWorldOffset(nx * PortalBasis.HALF_WIDTH, ny * PortalBasis.HALF_HEIGHT, 0.0);
                Vec3 worldPoint = entryCenter.add(localPoint);

                // Ray from viewer camera through this pixel on the entry portal
                Vec3 rayDirIn = worldPoint.subtract(cameraPos);
                if (rayDirIn.lengthSqr() < 1.0e-6) {
                    rayDirIn = entryBasis.forward().scale(-1.0);
                } else {
                    rayDirIn = rayDirIn.normalize();
                }

                // Transform ray origin & direction into exit portal's world space
                Vec3 rayDirOut = PortalTransform.transformVector(rayDirIn, entryBasis, exitBasis).normalize();
                Vec3 rayOriginOut = exitCenter
                    .add(exitBasis.right().scale(-nx * PortalBasis.HALF_WIDTH))
                    .add(exitBasis.up().scale(ny * PortalBasis.HALF_HEIGHT))
                    .add(exitBasis.forward().scale(0.08));

                int argb = traceWorldPixel(level, rayOriginOut, rayDirOut, remoteEntities, cursor, skyR, skyG, skyB, dayFactor);
                img.setPixel(px, py, argb);
            }
        }

        texture.upload();
    }

    private static int traceWorldPixel(
        ClientLevel level,
        Vec3 origin,
        Vec3 dir,
        List<Entity> remoteEntities,
        BlockPos.MutableBlockPos cursor,
        int skyR, int skyG, int skyB,
        float dayFactor
    ) {
        // 1. Check remote entities (players, Weighted Cubes) for line-of-sight visibility
        double entityHitDist = MAX_TRACE_DIST;
        int entityColor = -1;
        if (!remoteEntities.isEmpty()) {
            Vec3 end = origin.add(dir.scale(MAX_TRACE_DIST));
            for (Entity ent : remoteEntities) {
                var hit = ent.getBoundingBox().clip(origin, end);
                if (hit.isPresent()) {
                    double d = hit.get().distanceTo(origin);
                    if (d < entityHitDist) {
                        entityHitDist = d;
                        entityColor = (ent instanceof WeightedCubeEntity) ? 0xFFE2E6EC : 0xFF55AAEE;
                    }
                }
            }
        }

        // 2. Fast 3D DDA voxel traversal through ClientLevel
        int x = Mth.floor(origin.x);
        int y = Mth.floor(origin.y);
        int z = Mth.floor(origin.z);

        int stepX = dir.x >= 0 ? 1 : -1;
        int stepY = dir.y >= 0 ? 1 : -1;
        int stepZ = dir.z >= 0 ? 1 : -1;

        double tDeltaX = Math.abs(dir.x) < 1.0e-6 ? 1.0e9 : Math.abs(1.0 / dir.x);
        double tDeltaY = Math.abs(dir.y) < 1.0e-6 ? 1.0e9 : Math.abs(1.0 / dir.y);
        double tDeltaZ = Math.abs(dir.z) < 1.0e-6 ? 1.0e9 : Math.abs(1.0 / dir.z);

        double tMaxX = Math.abs(dir.x) < 1.0e-6 ? 1.0e9 : ((stepX > 0 ? (x + 1 - origin.x) : (origin.x - x)) * tDeltaX);
        double tMaxY = Math.abs(dir.y) < 1.0e-6 ? 1.0e9 : ((stepY > 0 ? (y + 1 - origin.y) : (origin.y - y)) * tDeltaY);
        double tMaxZ = Math.abs(dir.z) < 1.0e-6 ? 1.0e9 : ((stepZ > 0 ? (z + 1 - origin.z) : (origin.z - z)) * tDeltaZ);

        double t = 0.0;
        Direction hitFace = Direction.UP;

        for (int step = 0; step < 48 && t < MAX_TRACE_DIST; step++) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir() && state.canOcclude()) {
                if (entityColor != -1 && entityHitDist < t) {
                    return entityColor;
                }
                int baseRgb = state.getMapColor(level, cursor).col;
                if (baseRgb == 0) {
                    baseRgb = 0x808080;
                }
                float faceShade = switch (hitFace) {
                    case UP -> 1.0f;
                    case DOWN -> 0.55f;
                    case NORTH, SOUTH -> 0.82f;
                    case EAST, WEST -> 0.72f;
                };
                float shade = faceShade * (0.45f + 0.55f * dayFactor);
                float fog = (float) Mth.clamp(t / MAX_TRACE_DIST, 0.0, 0.65);

                int br = (int) (((baseRgb >> 16) & 0xFF) * shade);
                int bg = (int) (((baseRgb >> 8) & 0xFF) * shade);
                int bb = (int) ((baseRgb & 0xFF) * shade);

                int r = (int) Mth.lerp(fog, br, skyR);
                int g = (int) Mth.lerp(fog, bg, skyG);
                int b = (int) Mth.lerp(fog, bb, skyB);
                return (0xFF << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255);
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    t = tMaxX;
                    tMaxX += tDeltaX;
                    x += stepX;
                    hitFace = stepX > 0 ? Direction.WEST : Direction.EAST;
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    hitFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    t = tMaxY;
                    tMaxY += tDeltaY;
                    y += stepY;
                    hitFace = stepY > 0 ? Direction.DOWN : Direction.UP;
                } else {
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    hitFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
                }
            }
        }

        if (entityColor != -1) {
            return entityColor;
        }
        // Sky gradient fallback
        float upBoost = (float) Mth.clamp(dir.y * 0.25 + 0.85, 0.65, 1.1);
        int sr = Mth.clamp((int) (skyR * upBoost), 0, 255);
        int sg = Mth.clamp((int) (skyG * upBoost), 0, 255);
        int sb = Mth.clamp((int) (skyB * upBoost), 0, 255);
        return (0xFF << 24) | (sr << 16) | (sg << 8) | sb;
    }
}
