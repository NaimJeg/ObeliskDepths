package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public final class DungeonTerritoryStore {
    private final Map<DungeonTerritoryId, DungeonTerritory> territories = new HashMap<>();
    private final Map<Long, Set<DungeonTerritoryId>> territoriesByChunk = new HashMap<>();
    private final Runnable dirty;

    public DungeonTerritoryStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonTerritory> territories) {
        for (DungeonTerritory territory : territories) {
            this.putLoaded(territory);
        }
    }

    public Collection<DungeonTerritory> all() {
        return List.copyOf(this.territories.values());
    }

    public Optional<DungeonTerritory> get(DungeonTerritoryId id) {
        return Optional.ofNullable(this.territories.get(id));
    }

    public DungeonTerritory put(DungeonTerritory territory) {
        DungeonTerritory previous = this.territories.put(territory.id(), territory);

        if (previous != null) {
            this.unindex(previous);
        }

        this.index(territory);

        if (previous != territory) {
            this.dirty.run();
        }

        return territory;
    }

    public Optional<DungeonTerritory> remove(DungeonTerritoryId id) {
        DungeonTerritory removed = this.territories.remove(id);

        if (removed == null) {
            return Optional.empty();
        }

        this.unindex(removed);
        this.dirty.run();
        return Optional.of(removed);
    }

    public Optional<DungeonTerritory> findContaining(BlockPos pos) {
        long chunkKey = packChunk(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );

        Set<DungeonTerritoryId> candidates = this.territoriesByChunk.get(chunkKey);

        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonTerritoryId territoryId : candidates) {
            DungeonTerritory territory = this.territories.get(territoryId);

            if (territory != null && territory.bounds().contains(pos)) {
                return Optional.of(territory);
            }
        }

        return Optional.empty();
    }

    public Optional<DungeonInstanceId> findOwner(BlockPos pos) {
        return this.findContaining(pos).map(DungeonTerritory::instanceId);
    }

    public int size() {
        return this.territories.size();
    }

    private void putLoaded(DungeonTerritory territory) {
        DungeonTerritory previous = this.territories.put(territory.id(), territory);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate dungeon territory id in saved data: " + territory.id()
            );
        }

        this.index(territory);
    }

    private void index(DungeonTerritory territory) {
        for (long chunkKey : chunkKeysFor(territory.bounds())) {
            this.territoriesByChunk
                    .computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                    .add(territory.id());
        }
    }

    private void unindex(DungeonTerritory territory) {
        for (long chunkKey : chunkKeysFor(territory.bounds())) {
            Set<DungeonTerritoryId> ids = this.territoriesByChunk.get(chunkKey);

            if (ids == null) {
                continue;
            }

            ids.remove(territory.id());

            if (ids.isEmpty()) {
                this.territoriesByChunk.remove(chunkKey);
            }
        }
    }

    private static List<Long> chunkKeysFor(DungeonBounds bounds) {
        int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());

        List<Long> result = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                result.add(packChunk(chunkX, chunkZ));
            }
        }

        return result;
    }

    private static long packChunk(
            int chunkX,
            int chunkZ
    ) {
        return ((long) chunkX & 0xFFFFFFFFL)
                | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }
}
