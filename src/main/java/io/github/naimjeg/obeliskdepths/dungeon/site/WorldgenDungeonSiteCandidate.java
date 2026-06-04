package io.github.naimjeg.obeliskdepths.dungeon.site;

import net.minecraft.core.BlockPos;

public record WorldgenDungeonSiteCandidate(
        DungeonSiteKey key,
        BlockPos startPos,
        int searchDistanceSqr
) {
    public static WorldgenDungeonSiteCandidate fromKey(DungeonSiteKey key) {
        return new WorldgenDungeonSiteCandidate(
                key,
                new BlockPos(
                        key.toChunkPos().getMiddleBlockX(),
                        DungeonSitePlacement.PREVIEW_Y,
                        key.toChunkPos().getMiddleBlockZ()
                ),
                0
        );
    }
}
