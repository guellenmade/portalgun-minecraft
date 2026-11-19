package com.portal1.core;

import com.portal1.Portal1Mod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Portal 1 surface portalability and placement validation rules.
 *
 * Matches Portal 1's %noportal VMT flag and CProp_Portal placement checks:
 * - Only solid full-face portalable surfaces (white panels, concrete, quartz, etc.) accept portals.
 * - Black surfaces, metal blocks, glass, non-solid blocks, and moving blocks reject portals.
 * - Placement checks backing surface at center and oval edges, attempts small edge nudges
 *   (sv_portal_placement_never_bump), verifies clear space in front, and prevents overlapping
 *   any other active portal.
 */
public final class PortalSurfaceRules {

    public static final TagKey<Block> PORTALABLE_BLOCKS = TagKey.create(Registries.BLOCK, Portal1Mod.id("portalable"));
    public static final TagKey<Block> NON_PORTALABLE_BLOCKS = TagKey.create(Registries.BLOCK, Portal1Mod.id("non_portalable"));

    /**
     * Minimum center-to-center distance between two coplanar portals on the same face
     * to prevent overlap (matching "Portal overlapped another portal." in server.dll).
     */
    public static final double MIN_PORTAL_SEPARATION_RIGHT = PortalBasis.HALF_WIDTH * 1.85;
    public static final double MIN_PORTAL_SEPARATION_UP = PortalBasis.HALF_HEIGHT * 1.85;

    public record ExistingPortalRef(UUID id, Vec3 center, Direction facing) {}

    private PortalSurfaceRules() {}

    /**
     * Portals stick to any full, solid, opaque building surface EXCEPT:
     * - Glass / transparent / non-full blocks (rejected by isSolidRender & isFaceSturdy)
     * - Interactive / special / bouncy blocks (slime, honey, crafting table, pistons, TNT, etc. in non_portalable)
     * - Any block with a BlockEntity (chests, furnaces, shulker boxes, beacons, modded machines)
     */
    public static boolean isBlockPortalable(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(NON_PORTALABLE_BLOCKS)) {
            return false;
        }
        if (state.hasBlockEntity()) {
            return false;
        }
        if (!state.canOcclude() || !state.isFaceSturdy(level, pos, face)) {
            return false;
        }
        return state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Checks whether a portal placed at `center` with `basis` has valid backing blocks
     * and unobstructed space immediately in front of the aperture.
     */
    public static boolean isValidPlacement(
        Level level,
        Vec3 center,
        PortalBasis basis,
        List<ExistingPortalRef> otherPortals,
        UUID ignorePortalId
    ) {
        // 1. Check backing surface behind center and 4 cardinal points on the oval perimeter
        double[][] samplePoints = {
            { 0.0, 0.0 },
            {  PortalBasis.HALF_WIDTH * 0.70, 0.0 },
            { -PortalBasis.HALF_WIDTH * 0.70, 0.0 },
            { 0.0,  PortalBasis.HALF_HEIGHT * 0.72 },
            { 0.0, -PortalBasis.HALF_HEIGHT * 0.72 }
        };

        for (double[] pt : samplePoints) {
            Vec3 behindPos = center
                .add(basis.right().scale(pt[0]))
                .add(basis.up().scale(pt[1]))
                .subtract(basis.forward().scale(0.15));
            BlockPos backingBlock = BlockPos.containing(behindPos);
            if (!isBlockPortalable(level, backingBlock, basis.facing())) {
                return false;
            }

            // Ensure the space immediately in front of the portal is passable (not inside a solid block)
            Vec3 frontPos = center
                .add(basis.right().scale(pt[0]))
                .add(basis.up().scale(pt[1]))
                .add(basis.forward().scale(0.20));
            BlockPos frontBlock = BlockPos.containing(frontPos);
            if (!level.getBlockState(frontBlock).getCollisionShape(level, frontBlock).isEmpty()) {
                return false;
            }
        }

        // 2. Check overlap with any existing portal on the same face (excluding the portal being relocated)
        for (ExistingPortalRef other : otherPortals) {
            if (other.id().equals(ignorePortalId)) {
                continue;
            }
            if (other.facing() != basis.facing()) {
                continue;
            }
            Vec3 diff = other.center().subtract(center);
            double distPlane = Math.abs(diff.dot(basis.forward()));
            if (distPlane < 0.35) {
                double distR = Math.abs(diff.dot(basis.right()));
                double distU = Math.abs(diff.dot(basis.up()));
                if (distR < MIN_PORTAL_SEPARATION_RIGHT && distU < MIN_PORTAL_SEPARATION_UP) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Attempts to place a portal at `hitPos` on `face`, nudging slightly along the surface
     * if the hit point is near a wall/floor edge (Portal 1 edge-bump behavior).
     */
    public static Optional<Vec3> findValidPlacement(
        Level level,
        Vec3 hitPos,
        BlockPos hitBlock,
        PortalBasis basis,
        List<ExistingPortalRef> otherPortals,
        UUID ignorePortalId
    ) {
        // Snap center tightly onto the face plane (+0.02 blocks out from block surface)
        Vec3 blockCenter = Vec3.atCenterOf(hitBlock);
        double planeCoord = blockCenter.dot(basis.forward()) + 0.52;
        double currentPlane = hitPos.dot(basis.forward());
        Vec3 snapped = hitPos.add(basis.forward().scale(planeCoord - currentPlane));

        // Try exact hit point first, then gentle nudges (up, down, left, right, diagonals)
        double[][] nudges = {
            { 0.0,  0.0 },
            { 0.0,  0.5 }, { 0.0, -0.5 },
            { 0.35, 0.0 }, { -0.35, 0.0 },
            { 0.0,  0.85 }, { 0.0, -0.85 },
            { 0.35, 0.5 }, { -0.35, 0.5 },
            { 0.35, -0.5 }, { -0.35, -0.5 }
        };

        for (double[] nudge : nudges) {
            Vec3 candidate = snapped
                .add(basis.right().scale(nudge[0]))
                .add(basis.up().scale(nudge[1]));
            if (isValidPlacement(level, candidate, basis, otherPortals, ignorePortalId)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }
}
