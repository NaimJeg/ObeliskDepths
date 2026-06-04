package io.github.naimjeg.obeliskdepths.dungeon.debug;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class DungeonDebugChunkWarmupService {
    public static final int MAX_RADIUS_CHUNKS = 12;

    private DungeonDebugChunkWarmupService() {
    }

    public static int warmupChunks(
            ServerLevel level,
            BlockPos origin,
            int requestedRadiusChunks
    ) {
        /*
         * Debug-only chunk generation/load helper.
         *
         * Runtime reservation intentionally does not call this. If a real
         * DungeonInstance is created, its site metadata must come from a
         * generated vanilla StructureStart. This helper merely gives developers
         * an in-game way to force nearby chunks to exist so the authoritative
         * reader has structure starts to inspect.
         */
        int radius = Math.max(
                0,
                Math.min(MAX_RADIUS_CHUNKS, requestedRadiusChunks)
        );
        ChunkPos center = new ChunkPos(
                SectionPos.blockToSectionCoord(origin.getX()),
                SectionPos.blockToSectionCoord(origin.getZ())
        );
        int loaded = 0;

        for (int chunkX = center.x() - radius; chunkX <= center.x() + radius; chunkX++) {
            for (int chunkZ = center.z() - radius; chunkZ <= center.z() + radius; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
                loaded++;
            }
        }

        return loaded;
    }
}
