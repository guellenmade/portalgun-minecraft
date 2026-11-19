package com.portal1.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Pure multiplayer + multi-gun portal ownership, linking, and lightweight endpoint registry.
 *
 * Key properties:
 * - Each (ownerId, channel) pair is completely independent (4 Portal Gun channels: 0..3).
 * - Firing a gun only replaces that player's own portal on that gun's channel; it NEVER touches
 *   other channels or other players' portals.
 * - Stores lightweight PortalEndpoint metadata (position, facing, up vector, color) for every active
 *   portal so linked portals continue working across unloaded chunks WITHOUT force-loading chunks
 *   or ticking machines/farms.
 */
public final class PlayerPortalRegistry {

    public static final int MAX_CHANNELS = 4;

    public record PairKey(UUID ownerId, int channel) {}

    public record PortalEndpoint(
        UUID portalId,
        double x, double y, double z,
        int facing3D,
        double upX, double upY, double upZ,
        int colorRgb
    ) {
        public Vec3 position() {
            return new Vec3(x, y, z);
        }

        public PortalBasis basis() {
            Direction dir = Direction.from3DDataValue(facing3D);
            return PortalBasis.fromForwardAndUp(dir, Vec3.atLowerCornerOf(dir.getUnitVec3i()), new Vec3(upX, upY, upZ));
        }

        public static PortalEndpoint of(UUID portalId, Vec3 pos, PortalBasis basis, int colorRgb) {
            return new PortalEndpoint(
                portalId,
                pos.x, pos.y, pos.z,
                basis.facing().get3DDataValue(),
                basis.up().x, basis.up().y, basis.up().z,
                colorRgb
            );
        }
    }

    public record PortalPair(PortalEndpoint primary, PortalEndpoint secondary, int colorIndex) {
        public UUID primaryId() {
            return primary != null ? primary.portalId() : null;
        }

        public UUID secondaryId() {
            return secondary != null ? secondary.portalId() : null;
        }

        public PortalPair withPortal(boolean isSecondary, PortalEndpoint endpoint) {
            return isSecondary
                ? new PortalPair(primary, endpoint, colorIndex)
                : new PortalPair(endpoint, secondary, colorIndex);
        }

        public PortalPair withoutPortal(UUID portalId) {
            PortalEndpoint nextPrimary = (primary != null && portalId.equals(primary.portalId())) ? null : primary;
            PortalEndpoint nextSecondary = (secondary != null && portalId.equals(secondary.portalId())) ? null : secondary;
            return new PortalPair(nextPrimary, nextSecondary, colorIndex);
        }

        public PortalEndpoint partnerEndpointOf(UUID portalId) {
            if (primary != null && portalId.equals(primary.portalId())) {
                return secondary;
            }
            if (secondary != null && portalId.equals(secondary.portalId())) {
                return primary;
            }
            return null;
        }

        public UUID partnerOf(UUID portalId) {
            PortalEndpoint ep = partnerEndpointOf(portalId);
            return ep != null ? ep.portalId() : null;
        }

        public PortalColorScheme colorScheme(int channel) {
            return PortalColorScheme.forChannelAndPlayer(channel, colorIndex);
        }
    }

    public record PortalOwner(UUID ownerId, int channel, boolean secondary) {}

    public record PlacementResult(
        UUID replacedPortalId,
        PortalEndpoint partnerEndpoint,
        PortalColorScheme colorScheme
    ) {
        public UUID partnerPortalId() {
            return partnerEndpoint != null ? partnerEndpoint.portalId() : null;
        }
    }

    private final Map<PairKey, PortalPair> pairsByKey = new HashMap<>();
    private final Map<UUID, PortalOwner> ownerByPortal = new HashMap<>();
    private final Map<UUID, Integer> playerColorIndices = new HashMap<>();
    private int nextColorIndex = 0;

    public int getOrAssignPlayerColorIndex(UUID ownerId) {
        return playerColorIndices.computeIfAbsent(ownerId, id -> nextColorIndex++);
    }

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId, int channel) {
        int colorIdx = getOrAssignPlayerColorIndex(ownerId);
        return PortalColorScheme.forChannelAndPlayer(channel, colorIdx);
    }

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId) {
        return getOrAssignColorScheme(ownerId, 0);
    }

    public PlacementResult registerPortal(UUID ownerId, int channel, boolean secondary, PortalEndpoint endpoint) {
        int colorIdx = getOrAssignPlayerColorIndex(ownerId);
        PairKey key = new PairKey(ownerId, channel);
        PortalPair current = pairsByKey.computeIfAbsent(key, k -> new PortalPair(null, null, colorIdx));
        UUID replaced = secondary ? current.secondaryId() : current.primaryId();
        if (replaced != null && !replaced.equals(endpoint.portalId())) {
            ownerByPortal.remove(replaced);
        }
        PortalPair updated = current.withPortal(secondary, endpoint);
        pairsByKey.put(key, updated);
        ownerByPortal.put(endpoint.portalId(), new PortalOwner(ownerId, channel, secondary));
        return new PlacementResult(replaced, updated.partnerEndpointOf(endpoint.portalId()), updated.colorScheme(channel));
    }

    /**
     * Convenience overload for unit tests (channel 0, dummy endpoint at origin).
     */
    public PlacementResult registerPortal(UUID ownerId, boolean secondary, UUID newPortalId) {
        PortalColorScheme scheme = getOrAssignColorScheme(ownerId, 0);
        PortalEndpoint ep = PortalEndpoint.of(
            newPortalId,
            Vec3.ZERO,
            PortalBasis.fromFaceAndShooterYaw(Direction.NORTH, 0f),
            scheme.colorFor(secondary)
        );
        return registerPortal(ownerId, 0, secondary, ep);
    }

    public Optional<UUID> unregisterPortal(UUID portalId) {
        PortalOwner owner = ownerByPortal.remove(portalId);
        if (owner == null) {
            return Optional.empty();
        }
        PairKey key = new PairKey(owner.ownerId(), owner.channel());
        PortalPair pair = pairsByKey.get(key);
        if (pair == null) {
            return Optional.empty();
        }
        UUID partner = pair.partnerOf(portalId);
        pairsByKey.put(key, pair.withoutPortal(portalId));
        return Optional.ofNullable(partner);
    }

    public Optional<PortalEndpoint> getPartnerEndpoint(UUID portalId) {
        PortalOwner owner = ownerByPortal.get(portalId);
        if (owner == null) {
            return Optional.empty();
        }
        PortalPair pair = pairsByKey.get(new PairKey(owner.ownerId(), owner.channel()));
        if (pair == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pair.partnerEndpointOf(portalId));
    }

    public Optional<UUID> getPartnerPortal(UUID portalId) {
        return getPartnerEndpoint(portalId).map(PortalEndpoint::portalId);
    }

    public Optional<PortalOwner> getOwner(UUID portalId) {
        return Optional.ofNullable(ownerByPortal.get(portalId));
    }

    public Optional<PortalPair> getPair(UUID ownerId, int channel) {
        return Optional.ofNullable(pairsByKey.get(new PairKey(ownerId, channel)));
    }

    public Optional<PortalPair> getPair(UUID ownerId) {
        return getPair(ownerId, 0);
    }

    public Map<PairKey, PortalPair> snapshotPairs() {
        return Collections.unmodifiableMap(pairsByKey);
    }

    public int getNextColorIndex() {
        return nextColorIndex;
    }

    public void loadFromSnapshot(Map<PairKey, PortalPair> pairs, int savedNextColorIndex) {
        pairsByKey.clear();
        ownerByPortal.clear();
        playerColorIndices.clear();
        int maxIdx = -1;
        for (Map.Entry<PairKey, PortalPair> entry : pairs.entrySet()) {
            PairKey key = entry.getKey();
            PortalPair pair = entry.getValue();
            pairsByKey.put(key, pair);
            playerColorIndices.putIfAbsent(key.ownerId(), pair.colorIndex());
            if (pair.primary() != null) {
                ownerByPortal.put(pair.primary().portalId(), new PortalOwner(key.ownerId(), key.channel(), false));
            }
            if (pair.secondary() != null) {
                ownerByPortal.put(pair.secondary().portalId(), new PortalOwner(key.ownerId(), key.channel(), true));
            }
            maxIdx = Math.max(maxIdx, pair.colorIndex());
        }
        this.nextColorIndex = Math.max(savedNextColorIndex, maxIdx + 1);
    }
}
