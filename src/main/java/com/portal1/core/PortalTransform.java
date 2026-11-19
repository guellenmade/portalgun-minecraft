package com.portal1.core;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Exact Portal 1 portal-to-portal transformation math (CProp_Portal::UpdatePortalTransformationMatrix).
 *
 * Entering portal A moving in direction -F_A exits portal B moving in direction +F_B.
 * This is a 180-degree rotation around U_A (mapping F_A -> -F_A, R_A -> -R_A, U_A -> +U_A)
 * followed by the change of basis from (F_A, U_A, R_A) to (F_B, U_B, R_B).
 * Because it is a pure rigid rotation (det = +1), speed |v| is strictly preserved.
 */
public final class PortalTransform {

    /**
     * Minimum upward exit speed (blocks/tick) when emerging upward out of a floor portal,
     * matching Portal 1's slight floor-exit boost so entities cleanly clear the portal lip.
     */
    public static final double MIN_FLOOR_EXIT_SPEED = 0.44;

    /**
     * Minimum forward exit speed (blocks/tick) when emerging from a wall portal so walking
     * slowly into a portal still pushes the entity cleanly out the exit side.
     */
    public static final double MIN_WALL_EXIT_SPEED = 0.22;

    private PortalTransform() {}

    /**
     * Transforms a direction or velocity vector from entry portal space to exit portal space.
     * Strictly preserves vector magnitude: |transformVector(v)| == |v|.
     */
    public static Vec3 transformVector(Vec3 v, PortalBasis entry, PortalBasis exit) {
        double compF = -v.dot(entry.forward());
        double compU = v.dot(entry.up());
        double compR = -v.dot(entry.right());

        return exit.forward().scale(compF)
            .add(exit.up().scale(compU))
            .add(exit.right().scale(compR));
    }

    /**
     * Transforms an entity's world position from entry portal to exit portal with safe clearance
     * so the entity's hitbox never clips into surrounding wall/floor/ceiling blocks on exit.
     */
    public static Vec3 transformPosition(
        Vec3 entityPos,
        Vec3 entryCenter,
        PortalBasis entry,
        Vec3 exitCenter,
        PortalBasis exit,
        double exitForwardOffset
    ) {
        Vec3 rel = entityPos.subtract(entryCenter);

        // Keep exit tightly centered inside the oval aperture to prevent snagging on adjacent solid blocks
        double maxR = 0.18;
        double maxU = exit.isWall() ? 0.15 : 0.18;
        double localR = Mth.clamp(rel.dot(entry.right()), -maxR, maxR);
        double localU = Mth.clamp(rel.dot(entry.up()), -maxU, maxU);

        // When transitioning between horizontal (floor/ceiling) and vertical (wall) portals,
        // center cleanly on the exit aperture so horizontal offset doesn't push feet into the floor.
        if (entry.isWall() != exit.isWall()) {
            localR = 0.0;
            localU = 0.0;
        }

        return exitCenter
            .add(exit.right().scale(-localR))
            .add(exit.up().scale(localU))
            .add(exit.forward().scale(exitForwardOffset));
    }

    /**
     * Applies Portal 1 exit velocity rules after rigid momentum transformation.
     * Full momentum is always preserved; only near-zero exit speeds receive a clean clearance push,
     * and tiny lateral noise when flinging from floor-to-wall is damped so exits fly straight.
     */
    public static Vec3 applyExitVelocityRules(Vec3 transformedVel, PortalBasis entry, PortalBasis exit) {
        Vec3 v = transformedVel;

        // When entering a floor/ceiling portal and exiting a wall portal, damp tiny horizontal approach drift
        // that mapped onto the wall's sideways (right) axis so jumping straight into a floor portal flings cleanly straight!
        if (!entry.isWall() && exit.isWall()) {
            double compF = v.dot(exit.forward());
            double compU = v.dot(exit.up());
            double compR = v.dot(exit.right());
            if (Math.abs(compR) < 0.25) {
                compR *= 0.15;
            }
            // Give a slight natural upward lift if exiting horizontally with no upward velocity, matching Portal 1 fling feel
            if (compF > 0.35 && compU < 0.08 && compU > -0.25) {
                compU = Math.max(compU, 0.10);
            }
            v = exit.forward().scale(compF).add(exit.up().scale(compU)).add(exit.right().scale(compR));
        }

        double forwardSpeed = v.dot(exit.forward());
        if (exit.isFloor() && forwardSpeed < MIN_FLOOR_EXIT_SPEED) {
            return v.add(exit.forward().scale(MIN_FLOOR_EXIT_SPEED - forwardSpeed));
        }
        if (exit.isWall() && forwardSpeed < MIN_WALL_EXIT_SPEED) {
            return v.add(exit.forward().scale(MIN_WALL_EXIT_SPEED - forwardSpeed));
        }
        return v;
    }

    /**
     * Legacy overload preserved for existing unit tests.
     */
    public static Vec3 applyExitVelocityRules(Vec3 transformedVel, PortalBasis exit) {
        double forwardSpeed = transformedVel.dot(exit.forward());
        if (exit.isFloor() && forwardSpeed < MIN_FLOOR_EXIT_SPEED) {
            return transformedVel.add(exit.forward().scale(MIN_FLOOR_EXIT_SPEED - forwardSpeed));
        }
        if (exit.isWall() && forwardSpeed < MIN_WALL_EXIT_SPEED) {
            return transformedVel.add(exit.forward().scale(MIN_WALL_EXIT_SPEED - forwardSpeed));
        }
        return transformedVel;
    }

    /**
     * Transforms Minecraft player look angles (yaw in degrees, pitch in degrees) through the portal.
     * Matches Portal 1's m_bFixEyeAnglesFromPortalling behavior: camera roll is kept upright (0),
     * while yaw and pitch accurately reflect the transformed forward look vector.
     */
    public static YawPitch transformYawPitch(float yawDeg, float pitchDeg, PortalBasis entry, PortalBasis exit) {
        Vec3 lookIn = lookVectorFromYawPitch(yawDeg, pitchDeg);
        Vec3 lookOut = transformVector(lookIn, entry, exit).normalize();
        return yawPitchFromLookVector(lookOut);
    }

    public static Vec3 lookVectorFromYawPitch(float yawDeg, float pitchDeg) {
        double yawRad = Math.toRadians(yawDeg);
        double pitchRad = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitchRad);
        double x = -Math.sin(yawRad) * cosPitch;
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * cosPitch;
        return new Vec3(x, y, z);
    }

    public static YawPitch yawPitchFromLookVector(Vec3 look) {
        Vec3 n = look.normalize();
        double horizontal = Math.sqrt(n.x * n.x + n.z * n.z);
        float pitch = (float) Math.toDegrees(-Math.atan2(n.y, horizontal));
        float yaw;
        if (horizontal < 1.0e-5) {
            yaw = 0.0f;
        } else {
            yaw = (float) Math.toDegrees(Math.atan2(-n.x, n.z));
        }
        return new YawPitch(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -89.5f, 89.5f));
    }

    public record YawPitch(float yaw, float pitch) {}
}
