package com.portal1.core;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Orthonormal basis (forward, up, right) for a Portal 1 oval portal.
 *
 * Dimensions derived from Portal 1's models/portals/portal1.mdl:
 *   Original hull: 64 HU wide (half=32) x 108 HU tall (half=54), aspect ratio 1 : 1.6875.
 *   Minecraft scale (40 HU = 1 block for 72 HU = 1.8 block player height):
 *   halfWidth = 0.59 blocks (width = 1.18), halfHeight = 1.00 blocks (height = 2.00), ratio 1 : 1.695.
 */
public record PortalBasis(Direction facing, Vec3 forward, Vec3 up, Vec3 right) {

    public static final double HALF_WIDTH = 0.59;
    public static final double HALF_HEIGHT = 1.00;
    public static final double PORTAL_THICKNESS = 0.08;
    public static final double FUNNEL_RADIUS = 1.15;
    public static final double FUNNEL_HEIGHT = 2.5;

    /**
     * Constructs an orthonormal right-handed portal basis from the attached block face
     * and the shooter's yaw (used to orient floor and ceiling portals, matching Portal 1).
     */
    public static PortalBasis fromFaceAndShooterYaw(Direction facing, float shooterYawDegrees) {
        Vec3 forward = Vec3.atLowerCornerOf(facing.getUnitVec3i()).normalize();
        Vec3 up;
        if (facing == Direction.UP || facing == Direction.DOWN) {
            // In Portal 1, floor/ceiling portals orient their 'up' (long axis) along the shooter's horizontal facing.
            Direction horizontal = Direction.fromYRot(shooterYawDegrees);
            up = Vec3.atLowerCornerOf(horizontal.getUnitVec3i()).normalize();
        } else {
            up = new Vec3(0.0, 1.0, 0.0);
        }
        return fromForwardAndUp(facing, forward, up);
    }

    public static PortalBasis fromForwardAndUp(Direction facing, Vec3 forward, Vec3 upHint) {
        Vec3 f = forward.normalize();
        // Orthogonalize up against forward
        Vec3 u = upHint.subtract(f.scale(upHint.dot(f)));
        if (u.lengthSqr() < 1.0e-6) {
            u = Math.abs(f.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(0, 0, -1);
            u = u.subtract(f.scale(u.dot(f)));
        }
        u = u.normalize();
        // Right-handed basis: right = up x forward
        Vec3 r = u.cross(f).normalize();
        return new PortalBasis(facing, f, u, r);
    }

    /**
     * Projects a world-space offset (point - portalCenter) into local portal coordinates:
     * x = right, y = up, z = forward (positive in front of portal, negative behind).
     */
    public Vec3 toLocal(Vec3 worldOffset) {
        return new Vec3(
            worldOffset.dot(right),
            worldOffset.dot(up),
            worldOffset.dot(forward)
        );
    }

    /**
     * Converts local portal coordinates (x=right, y=up, z=forward) to a world-space offset.
     */
    public Vec3 toWorldOffset(double localRight, double localUp, double localForward) {
        return right.scale(localRight)
            .add(up.scale(localUp))
            .add(forward.scale(localForward));
    }

    /**
     * Tests whether a local offset lies inside the oval aperture (x/a)^2 + (y/b)^2 <= 1.
     */
    public boolean isInsideOval(Vec3 localOffset, double marginScale) {
        double a = HALF_WIDTH * marginScale;
        double b = HALF_HEIGHT * marginScale;
        double nx = localOffset.x / a;
        double ny = localOffset.y / b;
        return (nx * nx + ny * ny) <= 1.0;
    }

    public boolean isFloor() {
        return facing == Direction.UP;
    }

    public boolean isCeiling() {
        return facing == Direction.DOWN;
    }

    public boolean isWall() {
        return !isFloor() && !isCeiling();
    }
}
