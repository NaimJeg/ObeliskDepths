package io.github.naimjeg.obeliskdepths.dungeon.site;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record WorldgenDungeonSiteDiagnostic(
        BlockPos origin,
        boolean warmedChunks,
        int warmedChunkCount,
        Optional<DungeonSiteKey> prototypePreviewKey
) {
    public String notFoundMessage(ServerLevel dungeonLevel) {
        return "No generated authoritative dungeon site found near origin "
                + this.origin
                + " in "
                + dungeonLevel.dimension().identifier()
                + ". Move/search farther, generate chunks in the ObeliskDepths dimension, "
                + "or verify structure placement/biome tags.";
    }

    public String detailMessage() {
        return "Diagnostics: warmedChunks="
                + this.warmedChunks
                + ", warmedChunkCount="
                + this.warmedChunkCount
                + ", prototypePreviewNearby="
                + this.prototypePreviewKey.map(DungeonSiteKey::toString).orElse("<none>")
                + ". Prototype preview metadata is debug-only and was not used.";
    }
}
