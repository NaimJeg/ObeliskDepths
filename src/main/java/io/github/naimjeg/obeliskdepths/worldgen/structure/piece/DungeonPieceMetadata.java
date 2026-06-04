package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.Optional;

public record DungeonPieceMetadata(
        ObeliskDungeonPieceRole role,
        String id,
        BlockPos anchor,
        BoundingBox bounds,
        boolean primaryEntry,
        Optional<Identifier> definitionId,
        Optional<Identifier> templateId,
        DungeonRoomRotation rotation,
        boolean mirror,
        BlockPos templateOrigin,
        boolean templateBacked
) {
    public DungeonPieceMetadata(
            ObeliskDungeonPieceRole role,
            String id,
            BlockPos anchor,
            BoundingBox bounds,
            boolean primaryEntry
    ) {
        this(
                role,
                id,
                anchor,
                bounds,
                primaryEntry,
                Optional.empty(),
                Optional.empty(),
                DungeonRoomRotation.NONE,
                false,
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                false
        );
    }

    public DungeonPieceMetadata {
        if (role == null) {
            throw new IllegalArgumentException("Dungeon piece role must be present: " + id);
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dungeon piece id must be non-empty");
        }

        if (anchor == null || bounds == null) {
            throw new IllegalArgumentException("Dungeon piece anchor and bounds must be present: " + id);
        }

        definitionId = definitionId == null ? Optional.empty() : definitionId;
        templateId = templateId == null ? Optional.empty() : templateId;
        rotation = rotation == null ? DungeonRoomRotation.NONE : rotation;

        if (templateOrigin == null) {
            templateOrigin = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        }

        if (primaryEntry && role != ObeliskDungeonPieceRole.START_ROOM) {
            throw new IllegalArgumentException("Only START_ROOM pieces may be primary entry metadata: " + id);
        }

        if (templateBacked && templateId.isEmpty()) {
            throw new IllegalArgumentException("Template-backed piece requires template id: " + id);
        }
    }
}
