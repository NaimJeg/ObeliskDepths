package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Incremental equivalent of sorting the room interior by Manhattan distance,
 * then x ascending, then z ascending. Each call performs a bounded amount of
 * cursor work and may therefore yield no coordinate.
 */
final class DungeonSafeSpawnCandidateCursor {
    private final int feetY;
    private final long minX;
    private final long maxX;
    private final long minZ;
    private final long maxZ;
    private final long anchorX;
    private final long anchorZ;
    private final long maxDistance;
    private final long totalCandidates;

    private long distance;
    private long nextX;
    private long ringMaxX;
    private long pendingSecondZ;
    private long pendingX;
    private boolean hasPendingSecondZ;
    private boolean exhausted;

    DungeonSafeSpawnCandidateCursor(DungeonGeneratedRoom room) {
        DungeonBounds bounds = room.bounds();
        this.feetY = room.anchorPos().getY();
        this.minX = (long)bounds.minX() + 1L;
        this.maxX = (long)bounds.maxX() - 1L;
        this.minZ = (long)bounds.minZ() + 1L;
        this.maxZ = (long)bounds.maxZ() - 1L;
        this.anchorX = room.anchorPos().getX();
        this.anchorZ = room.anchorPos().getZ();

        boolean yInside = this.feetY >= bounds.minY()
                && this.feetY <= bounds.maxY();
        if (!yInside || this.minX > this.maxX || this.minZ > this.maxZ) {
            this.totalCandidates = 0L;
            this.maxDistance = -1L;
            this.exhausted = true;
            return;
        }

        this.totalCandidates = saturatedMultiply(
                this.maxX - this.minX + 1L,
                this.maxZ - this.minZ + 1L
        );
        this.maxDistance = Math.max(
                manhattan(this.minX, this.minZ),
                Math.max(
                        manhattan(this.minX, this.maxZ),
                        Math.max(
                                manhattan(this.maxX, this.minZ),
                                manhattan(this.maxX, this.maxZ)
                        )
                )
        );
        initializeRing();
    }

    long totalCandidates() {
        return this.totalCandidates;
    }

    CursorStep step() {
        if (this.exhausted) {
            return CursorStep.end();
        }
        if (this.hasPendingSecondZ) {
            long z = this.pendingSecondZ;
            this.hasPendingSecondZ = false;
            if (insideZ(z)) {
                return CursorStep.candidate(position(this.pendingX, z));
            }
            return CursorStep.skipped();
        }
        if (this.nextX > this.ringMaxX) {
            this.distance++;
            if (this.distance > this.maxDistance) {
                this.exhausted = true;
                return CursorStep.end();
            }
            initializeRing();
            return CursorStep.skipped();
        }

        long x = this.nextX++;
        long deltaZ = this.distance - absoluteDifference(x, this.anchorX);
        long firstZ = this.anchorZ - deltaZ;
        long secondZ = this.anchorZ + deltaZ;
        if (firstZ != secondZ) {
            this.pendingX = x;
            this.pendingSecondZ = secondZ;
            this.hasPendingSecondZ = true;
        }
        if (insideZ(firstZ)) {
            return CursorStep.candidate(position(x, firstZ));
        }
        return CursorStep.skipped();
    }

    private void initializeRing() {
        this.nextX = Math.max(this.minX, this.anchorX - this.distance);
        this.ringMaxX = Math.min(this.maxX, this.anchorX + this.distance);
    }

    private long manhattan(long x, long z) {
        return absoluteDifference(x, this.anchorX)
                + absoluteDifference(z, this.anchorZ);
    }

    private boolean insideZ(long z) {
        return z >= this.minZ && z <= this.maxZ;
    }

    private BlockPos position(long x, long z) {
        return new BlockPos(Math.toIntExact(x), this.feetY, Math.toIntExact(z));
    }

    private static long absoluteDifference(long first, long second) {
        return first >= second ? first - second : second - first;
    }

    private static long saturatedMultiply(long first, long second) {
        try {
            return Math.multiplyExact(first, second);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    record CursorStep(Optional<BlockPos> candidate, boolean exhausted) {
        static CursorStep candidate(BlockPos pos) {
            return new CursorStep(Optional.of(pos), false);
        }

        static CursorStep skipped() {
            return new CursorStep(Optional.empty(), false);
        }

        static CursorStep end() {
            return new CursorStep(Optional.empty(), true);
        }
    }
}
