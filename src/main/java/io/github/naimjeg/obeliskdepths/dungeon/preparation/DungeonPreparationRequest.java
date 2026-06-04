package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record DungeonPreparationRequest(
        UUID playerId,
        ResourceKey<Level> sourceDimension,
        BlockPos obeliskPos,
        PortalAdmissionMode requestedMode
) {
    public DungeonPreparationRequest {
        if (playerId == null) {
            throw new IllegalArgumentException("Preparation player id must be present.");
        }
        if (sourceDimension == null) {
            throw new IllegalArgumentException("Preparation source dimension must be present.");
        }
        if (obeliskPos == null) {
            throw new IllegalArgumentException("Preparation obelisk position must be present.");
        }
        if (requestedMode == null) {
            throw new IllegalArgumentException("Preparation admission mode must be present.");
        }
        obeliskPos = obeliskPos.immutable();
    }
}
