package com.portal1.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure multiplayer portal ownership & linking state.
 *
 * Rules:
 * - Each player owns an independent pair: (primaryPortalId, secondaryPortalId).
 * - Firing primary/secondary only replaces that player's own portal of that slot;
 *   it NEVER removes, moves, or closes another player's portals.
 * - Each player receives a unique color scheme index (index 0 = classic Portal 1 Blue/Orange,
 *   so singleplayer gracefully degrades to vanilla Portal 1 colors).
 * - Any entity/player entering ANY portal resolves its partner via getPartnerPortal(portalId),
 *   which always points to that portal's owner's linked partner portal.
 */
public final class PlayerPortalRegistry {

    public record PortalPair(UUID primaryId, UUID secondaryId, int colorIndex) {
        public PortalPair withPortal(boolean secondary, UUID newPortalId) {
            return secondary
                ? new PortalPair(primaryId, newPortalId, colorIndex)
                : new PortalPair(newPortalId, secondaryId, colorIndex);
        }

        public PortalPair withoutPortal(UUID portalId) {
            UUID nextPrimary = portalId.equals(primaryId) ? null : primaryId;
            UUID nextSecondary = portalId.equals(secondaryId) ? null : secondaryId;
            return new PortalPair(nextPrimary, nextSecondary, colorIndex);
        }

        public UUID partnerOf(UUID portalId) {
            if (portalId.equals(primaryId)) {
                return secondaryId;
            }
            if (portalId.equals(secondaryId)) {
                return primaryId;
            }
            return null;
        }

        public PortalColorScheme colorScheme() {
            return PortalColorScheme.forIndex(colorIndex);
        }
    }

    public record PortalOwner(UUID ownerId, boolean secondary) {}

    public record PlacementResult(UUID replacedPortalId, UUID partnerPortalId, PortalColorScheme colorScheme) {}

    private final Map<UUID, PortalPair> pairsByOwner = new HashMap<>();
    private final Map<UUID, PortalOwner> ownerByPortal = new HashMap<>();
    private int nextColorIndex = 0;

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId) {
        PortalPair pair = pairsByOwner.computeIfAbsent(ownerId, id -> new PortalPair(null, null, nextColorIndex++));
        return pair.colorScheme();
    }

    public int getOrAssignColorIndex(UUID ownerId) {
        PortalPair pair = pairsByOwner.computeIfAbsent(ownerId, id -> new PortalPair(null, null, nextColorIndex++));
        return pair.colorIndex();
    }

    /**
     * Registers a newly placed portal for ownerId in the primary (false) or secondary (true) slot.
     * Returns any previously open portal of the SAME owner & slot that must be closed, plus the
     * owner's linked partner portal (if open) and the owner's color scheme.
     */
    public PlacementResult registerPortal(UUID ownerId, boolean secondary, UUID newPortalId) {
        PortalPair current = pairsByOwner.computeIfAbsent(ownerId, id -> new PortalPair(null, null, nextColorIndex++));
        UUID replaced = secondary ? current.secondaryId() : current.primaryId();
        if (replaced != null && !replaced.equals(newPortalId)) {
            ownerByPortal.remove(replaced);
        }
        PortalPair updated = current.withPortal(secondary, newPortalId);
        pairsByOwner.put(ownerId, updated);
        ownerByPortal.put(newPortalId, new PortalOwner(ownerId, secondary));
        return new PlacementResult(replaced, updated.partnerOf(newPortalId), updated.colorScheme());
    }

    /**
     * Removes a portal (e.g. when its backing block is broken) without affecting any other player's portals.
     */
    public Optional<UUID> unregisterPortal(UUID portalId) {
        PortalOwner owner = ownerByPortal.remove(portalId);
        if (owner == null) {
            return Optional.empty();
        }
        PortalPair pair = pairsByOwner.get(owner.ownerId());
        if (pair == null) {
            return Optional.empty();
        }
        UUID partner = pair.partnerOf(portalId);
        pairsByOwner.put(owner.ownerId(), pair.withoutPortal(portalId));
        return Optional.ofNullable(partner);
    }

    public Optional<UUID> getPartnerPortal(UUID portalId) {
        PortalOwner owner = ownerByPortal.get(portalId);
        if (owner == null) {
            return Optional.empty();
        }
        PortalPair pair = pairsByOwner.get(owner.ownerId());
        if (pair == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pair.partnerOf(portalId));
    }

    public Optional<PortalOwner> getOwner(UUID portalId) {
        return Optional.ofNullable(ownerByPortal.get(portalId));
    }

    public Optional<PortalPair> getPair(UUID ownerId) {
        return Optional.ofNullable(pairsByOwner.get(ownerId));
    }

    public Map<UUID, PortalPair> snapshotPairs() {
        return Collections.unmodifiableMap(pairsByOwner);
    }

    public int getNextColorIndex() {
        return nextColorIndex;
    }

    public void loadFromSnapshot(Map<UUID, PortalPair> pairs, int savedNextColorIndex) {
        pairsByOwner.clear();
        ownerByPortal.clear();
        int maxIdx = -1;
        for (Map.Entry<UUID, PortalPair> entry : pairs.entrySet()) {
            UUID ownerId = entry.getKey();
            PortalPair pair = entry.getValue();
            pairsByOwner.put(ownerId, pair);
            if (pair.primaryId() != null) {
                ownerByPortal.put(pair.primaryId(), new PortalOwner(ownerId, false));
            }
            if (pair.secondaryId() != null) {
                ownerByPortal.put(pair.secondaryId(), new PortalOwner(ownerId, true));
            }
            maxIdx = Math.max(maxIdx, pair.colorIndex());
        }
        this.nextColorIndex = Math.max(savedNextColorIndex, maxIdx + 1);
    }
}
