package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonStructureReferenceEnvelope(
        DungeonStructureDistanceReport.ChunkCoordinate startChunk,
        int referenceDistanceChunks
) {
    public DungeonStructureReferenceEnvelope {
        if (startChunk == null) {
            throw new IllegalArgumentException("Start chunk must be present.");
        }
        if (referenceDistanceChunks < 0) {
            throw new IllegalArgumentException(
                    "Reference distance must be non-negative: "
                            + referenceDistanceChunks
            );
        }
    }

    public static DungeonStructureReferenceEnvelope vanilla(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk
    ) {
        return new DungeonStructureReferenceEnvelope(
                startChunk,
                DungeonStructureDistanceValidator.VANILLA_REFERENCE_DISTANCE_CHUNKS
        );
    }

    public DungeonStructureDistanceReport.ChunkBounds chunkBounds() {
        return new DungeonStructureDistanceReport.ChunkBounds(
                this.startChunk.x() - this.referenceDistanceChunks,
                this.startChunk.z() - this.referenceDistanceChunks,
                this.startChunk.x() + this.referenceDistanceChunks,
                this.startChunk.z() + this.referenceDistanceChunks
        );
    }

    public boolean contains(BoundingBox pieceBounds) {
        if (pieceBounds == null) {
            throw new IllegalArgumentException("Piece bounds must be present.");
        }
        return this.contains(DungeonStructureDistanceValidator.chunkBounds(pieceBounds));
    }

    public boolean contains(DungeonStructureDistanceReport.ChunkBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("Chunk bounds must be present.");
        }
        DungeonStructureDistanceReport.ChunkBounds allowed = this.chunkBounds();
        return bounds.minChunkX() >= allowed.minChunkX()
                && bounds.maxChunkX() <= allowed.maxChunkX()
                && bounds.minChunkZ() >= allowed.minChunkZ()
                && bounds.maxChunkZ() <= allowed.maxChunkZ();
    }

    public Optional<ChunkAlignedBlockOffset> chunkAlignedOffsetToContain(
            DungeonStructureDistanceReport.ChunkBounds bounds
    ) {
        if (bounds == null) {
            throw new IllegalArgumentException("Chunk bounds must be present.");
        }
        DungeonStructureDistanceReport.ChunkBounds allowed = this.chunkBounds();
        int allowedWidth = allowed.maxChunkX() - allowed.minChunkX() + 1;
        int allowedDepth = allowed.maxChunkZ() - allowed.minChunkZ() + 1;
        int width = bounds.maxChunkX() - bounds.minChunkX() + 1;
        int depth = bounds.maxChunkZ() - bounds.minChunkZ() + 1;
        if (width > allowedWidth || depth > allowedDepth) {
            return Optional.empty();
        }

        int offsetChunksX = axisOffset(
                bounds.minChunkX(),
                bounds.maxChunkX(),
                allowed.minChunkX(),
                allowed.maxChunkX()
        );
        int offsetChunksZ = axisOffset(
                bounds.minChunkZ(),
                bounds.maxChunkZ(),
                allowed.minChunkZ(),
                allowed.maxChunkZ()
        );
        return Optional.of(new ChunkAlignedBlockOffset(
                Math.multiplyExact(offsetChunksX, 16),
                Math.multiplyExact(offsetChunksZ, 16)
        ));
    }

    public int minBlockX() {
        return Math.multiplyExact(this.chunkBounds().minChunkX(), 16);
    }

    public int minBlockZ() {
        return Math.multiplyExact(this.chunkBounds().minChunkZ(), 16);
    }

    public int maxBlockX() {
        return Math.multiplyExact(this.chunkBounds().maxChunkX(), 16) + 15;
    }

    public int maxBlockZ() {
        return Math.multiplyExact(this.chunkBounds().maxChunkZ(), 16) + 15;
    }

    private static int axisOffset(
            int min,
            int max,
            int allowedMin,
            int allowedMax
    ) {
        if (min < allowedMin) {
            return allowedMin - min;
        }
        if (max > allowedMax) {
            return allowedMax - max;
        }
        return 0;
    }

    public record ChunkAlignedBlockOffset(
            int x,
            int z
    ) {
        public boolean isZero() {
            return this.x == 0 && this.z == 0;
        }
    }
}
