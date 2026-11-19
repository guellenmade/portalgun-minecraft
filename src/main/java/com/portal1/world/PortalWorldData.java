package com.portal1.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.portal1.Portal1Mod;
import com.portal1.core.PlayerPortalRegistry;
import com.portal1.core.PortalColorScheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent server-side storage for per-player, per-channel portal pairs and lightweight
 * PortalEndpoints. Storing endpoints here keeps distant portals linked and teleportable even
 * when the partner portal's chunk is unloaded — WITHOUT force-loading chunks or ticking machines.
 */
public final class PortalWorldData extends SavedData {

    private static final Codec<PlayerPortalRegistry.PortalEndpoint> ENDPOINT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf("id").forGetter(PlayerPortalRegistry.PortalEndpoint::portalId),
        Codec.DOUBLE.fieldOf("x").forGetter(PlayerPortalRegistry.PortalEndpoint::x),
        Codec.DOUBLE.fieldOf("y").forGetter(PlayerPortalRegistry.PortalEndpoint::y),
        Codec.DOUBLE.fieldOf("z").forGetter(PlayerPortalRegistry.PortalEndpoint::z),
        Codec.INT.fieldOf("facing").forGetter(PlayerPortalRegistry.PortalEndpoint::facing3D),
        Codec.DOUBLE.fieldOf("up_x").forGetter(PlayerPortalRegistry.PortalEndpoint::upX),
        Codec.DOUBLE.fieldOf("up_y").forGetter(PlayerPortalRegistry.PortalEndpoint::upY),
        Codec.DOUBLE.fieldOf("up_z").forGetter(PlayerPortalRegistry.PortalEndpoint::upZ),
        Codec.INT.fieldOf("color").forGetter(PlayerPortalRegistry.PortalEndpoint::colorRgb)
    ).apply(instance, PlayerPortalRegistry.PortalEndpoint::new));

    private record EntryRecord(
        UUID owner,
        int channel,
        Optional<PlayerPortalRegistry.PortalEndpoint> primary,
        Optional<PlayerPortalRegistry.PortalEndpoint> secondary,
        int colorIndex
    ) {
        static final Codec<EntryRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(EntryRecord::owner),
            Codec.INT.optionalFieldOf("channel", 0).forGetter(EntryRecord::channel),
            ENDPOINT_CODEC.optionalFieldOf("primary_ep").forGetter(EntryRecord::primary),
            ENDPOINT_CODEC.optionalFieldOf("secondary_ep").forGetter(EntryRecord::secondary),
            Codec.INT.fieldOf("color_index").forGetter(EntryRecord::colorIndex)
        ).apply(instance, EntryRecord::new));
    }

    private static final Codec<PortalWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EntryRecord.CODEC.listOf().fieldOf("pairs").forGetter(PortalWorldData::toEntryList),
        Codec.INT.optionalFieldOf("next_color_index", 0).forGetter(d -> d.registry.getNextColorIndex())
    ).apply(instance, PortalWorldData::fromSerialized));

    public static final SavedDataType<PortalWorldData> TYPE = new SavedDataType<>(
        Portal1Mod.id("portal_pairs_v2"),
        PortalWorldData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    private final PlayerPortalRegistry registry = new PlayerPortalRegistry();

    public PortalWorldData() {}

    private static PortalWorldData fromSerialized(List<EntryRecord> entries, int nextColorIndex) {
        PortalWorldData data = new PortalWorldData();
        Map<PlayerPortalRegistry.PairKey, PlayerPortalRegistry.PortalPair> map = new HashMap<>();
        for (EntryRecord rec : entries) {
            map.put(
                new PlayerPortalRegistry.PairKey(rec.owner(), rec.channel()),
                new PlayerPortalRegistry.PortalPair(
                    rec.primary().orElse(null),
                    rec.secondary().orElse(null),
                    rec.colorIndex()
                )
            );
        }
        data.registry.loadFromSnapshot(map, nextColorIndex);
        return data;
    }

    private List<EntryRecord> toEntryList() {
        List<EntryRecord> list = new ArrayList<>();
        for (Map.Entry<PlayerPortalRegistry.PairKey, PlayerPortalRegistry.PortalPair> e : registry.snapshotPairs().entrySet()) {
            PlayerPortalRegistry.PairKey key = e.getKey();
            PlayerPortalRegistry.PortalPair pair = e.getValue();
            list.add(new EntryRecord(
                key.ownerId(),
                key.channel(),
                Optional.ofNullable(pair.primary()),
                Optional.ofNullable(pair.secondary()),
                pair.colorIndex()
            ));
        }
        return list;
    }

    public static PortalWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId, int channel) {
        PortalColorScheme scheme = registry.getOrAssignColorScheme(ownerId, channel);
        setDirty();
        return scheme;
    }

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId) {
        return getOrAssignColorScheme(ownerId, 0);
    }

    public PlayerPortalRegistry.PlacementResult registerPortal(
        UUID ownerId,
        int channel,
        boolean secondary,
        PlayerPortalRegistry.PortalEndpoint endpoint
    ) {
        PlayerPortalRegistry.PlacementResult result = registry.registerPortal(ownerId, channel, secondary, endpoint);
        setDirty();
        return result;
    }

    public Optional<UUID> unregisterPortal(UUID portalId) {
        Optional<UUID> partner = registry.unregisterPortal(portalId);
        setDirty();
        return partner;
    }

    public Optional<PlayerPortalRegistry.PortalEndpoint> getPartnerEndpoint(UUID portalId) {
        return registry.getPartnerEndpoint(portalId);
    }

    public Optional<UUID> getPartnerPortal(UUID portalId) {
        return registry.getPartnerPortal(portalId);
    }

    public Optional<PlayerPortalRegistry.PortalPair> getPair(UUID ownerId, int channel) {
        return registry.getPair(ownerId, channel);
    }

    public Optional<PlayerPortalRegistry.PortalPair> getPair(UUID ownerId) {
        return getPair(ownerId, 0);
    }
}
