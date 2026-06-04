
package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Converts the primary-entry room block bounds of a {@link DungeonSite}
 * into an expanded ordered set of {@link ChunkPos} instances.
 *
 * <p>All chunk calculations use {@code Math.floorDiv(blockCoord, 16)}
 * so that negative block coordinates are correctly placed in the
 * chunk that contains them.</p>
 */
public final class DungeonEntryChunkPlanner {
    private static final int CHUNK_SIZE = 16;

    private DungeonEntryChunkPlanner() {
    }

    /**
     * Plans the entry-chunk footprint for the given site.
     *
     * @param site             an authoritative dungeon site
     * @param safetyRingChunks non-negative safety ring in chunks
     * @return the immutable plan
     * @throws NullPointerException     if the site is null
     * @throws IllegalArgumentException if the site has no primary-entry room
     * @throws IllegalArgumentException if safetyRingChunks is negative or unreasonably large
     */
    public static DungeonEntryChunkPlan plan(
            DungeonSite site,
            int safetyRingChunks
    ) {
        Objects.requireNonNull(site, "site");
        if (safetyRingChunks < 0) {
            throw new IllegalArgumentException(
                    "Safety ring must be non-negative, got " + safetyRingChunks
            );
        }
        if (safetyRingChunks > 16) {
            throw new IllegalArgumentException(
                    "Safety ring too large: " + safetyRingChunks + " (max 16)"
            );
        }

        DungeonGeneratedRoom room = site.primaryEntryRoom()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Site " + site.key()
                                + " has no primary-entry room"
                ));
        DungeonBounds bounds = room.bounds();

        // Convert inclusive block bounds to chunk coordinates.
        ChunkPos roomMin = toChunkMin(bounds.minX(), bounds.minZ());
        ChunkPos roomMax = toChunkMax(bounds.maxX(), bounds.maxZ());

        ChunkPos requestedMin = new ChunkPos(
                roomMin.x() - safetyRingChunks,
                roomMin.z() - safetyRingChunks
        );
        ChunkPos requestedMax = new ChunkPos(
                roomMax.x() + safetyRingChunks,
                roomMax.z() + safetyRingChunks
        );

        // Enumerate every intersecting chunk in row-major order.
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = requestedMin.x(); x <= requestedMax.x(); x++) {
            for (int z = requestedMin.z(); z <= requestedMax.z(); z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }

        return new DungeonEntryChunkPlan(
                roomMin,
                roomMax,
                requestedMin,
                requestedMax,
                chunks
        );
    }

    private static ChunkPos toChunkMin(int blockX, int blockZ) {
        return new ChunkPos(
                Math.floorDiv(blockX, CHUNK_SIZE),
                Math.floorDiv(blockZ, CHUNK_SIZE)
        );
    }

    private static ChunkPos toChunkMax(int blockX, int blockZ) {
        return new ChunkPos(
                Math.floorDiv(blockX, CHUNK_SIZE),
                Math.floorDiv(blockZ, CHUNK_SIZE)
        );
    }
}
