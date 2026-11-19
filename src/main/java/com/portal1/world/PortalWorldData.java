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
 * Persistent server-side storage for per-player portal pairs and color assignments.
 * Singleplayer naturally assigns color index 0 (classic Portal 1 Blue/Orange).
 * Multiplayer assigns each player their own independent pair and unique color scheme.
 */
public final class PortalWorldData extends SavedData {

    private record EntryRecord(UUID owner, Optional<UUID> primary, Optional<UUID> secondary, int colorIndex) {
        static final Codec<EntryRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("owner").forGetter(EntryRecord::owner),
            UUIDUtil.CODEC.optionalFieldOf("primary").forGetter(EntryRecord::primary),
            UUIDUtil.CODEC.optionalFieldOf("secondary").forGetter(EntryRecord::secondary),
            Codec.INT.fieldOf("color_index").forGetter(EntryRecord::colorIndex)
        ).apply(instance, EntryRecord::new));
    }

    private static final Codec<PortalWorldData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        EntryRecord.CODEC.listOf().fieldOf("pairs").forGetter(PortalWorldData::toEntryList),
        Codec.INT.optionalFieldOf("next_color_index", 0).forGetter(d -> d.registry.getNextColorIndex())
    ).apply(instance, PortalWorldData::fromSerialized));

    public static final SavedDataType<PortalWorldData> TYPE = new SavedDataType<>(
        Portal1Mod.id("portal_pairs"),
        PortalWorldData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    private final PlayerPortalRegistry registry = new PlayerPortalRegistry();

    public PortalWorldData() {}

    private static PortalWorldData fromSerialized(List<EntryRecord> entries, int nextColorIndex) {
        PortalWorldData data = new PortalWorldData();
        Map<UUID, PlayerPortalRegistry.PortalPair> map = new HashMap<>();
        for (EntryRecord rec : entries) {
            map.put(rec.owner(), new PlayerPortalRegistry.PortalPair(
                rec.primary().orElse(null),
                rec.secondary().orElse(null),
                rec.colorIndex()
            ));
        }
        data.registry.loadFromSnapshot(map, nextColorIndex);
        return data;
    }

    private List<EntryRecord> toEntryList() {
        List<EntryRecord> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerPortalRegistry.PortalPair> e : registry.snapshotPairs().entrySet()) {
            PlayerPortalRegistry.PortalPair pair = e.getValue();
            list.add(new EntryRecord(
                e.getKey(),
                Optional.ofNullable(pair.primaryId()),
                Optional.ofNullable(pair.secondaryId()),
                pair.colorIndex()
            ));
        }
        return list;
    }

    public static PortalWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public PortalColorScheme getOrAssignColorScheme(UUID ownerId) {
        PortalColorScheme scheme = registry.getOrAssignColorScheme(ownerId);
        setDirty();
        return scheme;
    }

    public PlayerPortalRegistry.PlacementResult registerPortal(UUID ownerId, boolean secondary, UUID newPortalId) {
        PlayerPortalRegistry.PlacementResult result = registry.registerPortal(ownerId, secondary, newPortalId);
        setDirty();
        return result;
    }

    public Optional<UUID> unregisterPortal(UUID portalId) {
        Optional<UUID> partner = registry.unregisterPortal(portalId);
        setDirty();
        return partner;
    }

    public Optional<UUID> getPartnerPortal(UUID portalId) {
        return registry.getPartnerPortal(portalId);
    }

    public Optional<PlayerPortalRegistry.PortalPair> getPair(UUID ownerId) {
        return registry.getPair(ownerId);
    }
}
