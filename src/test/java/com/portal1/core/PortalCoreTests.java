package com.portal1.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class PortalCoreTests {

    private static final double EPS = 1.0e-5;

    @Test
    void floorToWallFling_preservesFullMomentumAndRedirectsOutWall() {
        PortalBasis floorEntry = PortalBasis.fromFaceAndShooterYaw(Direction.UP, 0.0f);
        PortalBasis eastWallExit = PortalBasis.fromFaceAndShooterYaw(Direction.EAST, 0.0f);

        // Falling straight down into floor portal at 1.6 blocks/tick
        Vec3 inVel = new Vec3(0.0, -1.6, 0.0);
        Vec3 outVel = PortalTransform.transformVector(inVel, floorEntry, eastWallExit);

        assertEquals(inVel.length(), outVel.length(), EPS, "Speed must be strictly preserved");
        assertEquals(1.6, outVel.x, EPS, "Should fling straight out of east wall (+X)");
        assertEquals(0.0, outVel.y, EPS);
        assertEquals(0.0, outVel.z, EPS);

        Vec3 afterRules = PortalTransform.applyExitVelocityRules(outVel, eastWallExit);
        assertEquals(1.6, afterRules.x, EPS, "High-speed fling should not be clamped or reduced");
    }

    @Test
    void floorToFloorPortal_shootsEntityStraightUpWithSameSpeed() {
        PortalBasis floorA = PortalBasis.fromFaceAndShooterYaw(Direction.UP, 0.0f);
        PortalBasis floorB = PortalBasis.fromFaceAndShooterYaw(Direction.UP, 0.0f);

        Vec3 inVel = new Vec3(0.0, -1.25, 0.0);
        Vec3 outVel = PortalTransform.transformVector(inVel, floorA, floorB);

        assertEquals(0.0, outVel.x, EPS);
        assertEquals(1.25, outVel.y, EPS, "Falling into floor A must shoot upward out of floor B");
        assertEquals(0.0, outVel.z, EPS);
    }

    @Test
    void floorToCeilingPortal_preservesDownwardInfiniteFallLoop() {
        PortalBasis floorA = PortalBasis.fromFaceAndShooterYaw(Direction.UP, 0.0f);
        PortalBasis ceilingB = PortalBasis.fromFaceAndShooterYaw(Direction.DOWN, 0.0f);

        Vec3 inVel = new Vec3(0.0, -2.0, 0.0);
        Vec3 outVel = PortalTransform.transformVector(inVel, floorA, ceilingB);

        assertEquals(0.0, outVel.x, EPS);
        assertEquals(-2.0, outVel.y, EPS, "Falling into floor A must keep falling down out of ceiling B");
        assertEquals(0.0, outVel.z, EPS);
    }

    @Test
    void wallToPerpendicularWall_rotatesVelocityAndLookDirection() {
        PortalBasis southWallEntry = PortalBasis.fromFaceAndShooterYaw(Direction.SOUTH, 0.0f); // normal +Z
        PortalBasis eastWallExit = PortalBasis.fromFaceAndShooterYaw(Direction.EAST, 0.0f);    // normal +X

        // Walking north (-Z) straight into south-facing wall
        Vec3 inVel = new Vec3(0.0, 0.0, -0.5);
        Vec3 outVel = PortalTransform.transformVector(inVel, southWallEntry, eastWallExit);

        assertEquals(0.5, outVel.x, EPS, "Entering south-facing wall must exit east (+X)");
        assertEquals(0.0, outVel.y, EPS);
        assertEquals(0.0, outVel.z, EPS);

        // Looking straight north (yaw = 180, pitch = 0) into south-facing wall -> should look east (yaw = -90, pitch = 0)
        PortalTransform.YawPitch transformedLook = PortalTransform.transformYawPitch(180.0f, 0.0f, southWallEntry, eastWallExit);
        assertEquals(-90.0f, transformedLook.yaw(), 0.1f);
        assertEquals(0.0f, transformedLook.pitch(), 0.1f);
    }

    @Test
    void basisIsAlwaysOrthonormalRightHanded() {
        for (Direction dir : Direction.values()) {
            for (float yaw : new float[] { 0f, 45f, 90f, 180f, -90f }) {
                PortalBasis b = PortalBasis.fromFaceAndShooterYaw(dir, yaw);
                assertEquals(1.0, b.forward().length(), EPS);
                assertEquals(1.0, b.up().length(), EPS);
                assertEquals(1.0, b.right().length(), EPS);
                assertEquals(0.0, b.forward().dot(b.up()), EPS);
                assertEquals(0.0, b.forward().dot(b.right()), EPS);
                assertEquals(0.0, b.up().dot(b.right()), EPS);
                assertEquals(1.0, b.up().cross(b.forward()).dot(b.right()), EPS);
            }
        }
    }

    @Test
    void multiplayerIsolation_twoPlayersFourPortalsUniqueColorsAndCrossTraversal() {
        PlayerPortalRegistry registry = new PlayerPortalRegistry();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();

        // Player A (or singleplayer) gets classic Portal 1 Blue/Orange
        var resA1 = registry.registerPortal(playerA, false, a1);
        assertEquals(PortalColorScheme.CLASSIC, resA1.colorScheme());
        assertNull(resA1.replacedPortalId());
        assertNull(resA1.partnerPortalId());

        var resA2 = registry.registerPortal(playerA, true, a2);
        assertEquals(a1, resA2.partnerPortalId());

        // Player B gets a distinct unique color scheme and NEVER interferes with Player A
        var resB1 = registry.registerPortal(playerB, false, b1);
        assertNotEquals(PortalColorScheme.CLASSIC, resB1.colorScheme(), "Second player must get a unique color scheme");
        assertNull(resB1.replacedPortalId());

        var resB2 = registry.registerPortal(playerB, true, b2);
        assertEquals(b1, resB2.partnerPortalId());

        // All 4 portals coexist and link strictly to their own owner's partner
        assertEquals(a2, registry.getPartnerPortal(a1).orElseThrow());
        assertEquals(a1, registry.getPartnerPortal(a2).orElseThrow());
        assertEquals(b2, registry.getPartnerPortal(b1).orElseThrow());
        assertEquals(b1, registry.getPartnerPortal(b2).orElseThrow());

        // Relocating Player A's primary portal only replaces a1, never touches Player B's portals
        UUID a1New = UUID.randomUUID();
        var resA1New = registry.registerPortal(playerA, false, a1New);
        assertEquals(a1, resA1New.replacedPortalId());
        assertEquals(a2, resA1New.partnerPortalId());
        assertTrue(registry.getPartnerPortal(a1).isEmpty());
        assertEquals(b2, registry.getPartnerPortal(b1).orElseThrow(), "Player B's portals must remain untouched");
        assertEquals(b1, registry.getPartnerPortal(b2).orElseThrow());
    }

    @Test
    void fourPortalGuns_createFourIndependentColorCodedPairsWithPersistentEndpoints() {
        PlayerPortalRegistry registry = new PlayerPortalRegistry();
        UUID player = UUID.randomUUID();
        PortalBasis north = PortalBasis.fromFaceAndShooterYaw(Direction.NORTH, 180f);

        UUID[] primaries = new UUID[4];
        UUID[] secondaries = new UUID[4];
        PortalColorScheme[] schemes = new PortalColorScheme[4];

        for (int ch = 0; ch < 4; ch++) {
            primaries[ch] = UUID.randomUUID();
            secondaries[ch] = UUID.randomUUID();
            schemes[ch] = registry.getOrAssignColorScheme(player, ch);

            var ep1 = PlayerPortalRegistry.PortalEndpoint.of(primaries[ch], new Vec3(ch * 100.0, 64.0, 0.0), north, schemes[ch].primaryRgb());
            var ep2 = PlayerPortalRegistry.PortalEndpoint.of(secondaries[ch], new Vec3(ch * 100.0, 64.0, 5000.0), north, schemes[ch].secondaryRgb());

            var r1 = registry.registerPortal(player, ch, false, ep1);
            var r2 = registry.registerPortal(player, ch, true, ep2);
            assertNull(r1.replacedPortalId());
            assertEquals(primaries[ch], r2.partnerPortalId());
        }

        // All 4 gun channels must have distinct color schemes
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                assertNotEquals(schemes[i], schemes[j], "Channel " + i + " and " + j + " must have distinct colors");
            }
        }

        // All 4 pairs coexist and preserve distant (5000-block) partner endpoint metadata without chunk loading
        for (int ch = 0; ch < 4; ch++) {
            var partnerOfPrimary = registry.getPartnerEndpoint(primaries[ch]).orElseThrow();
            assertEquals(secondaries[ch], partnerOfPrimary.portalId());
            assertEquals(5000.0, partnerOfPrimary.z(), EPS);
            assertEquals(primaries[ch], registry.getPartnerEndpoint(secondaries[ch]).orElseThrow().portalId());
        }
    }
}
