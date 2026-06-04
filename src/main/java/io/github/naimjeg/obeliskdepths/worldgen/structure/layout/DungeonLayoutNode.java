package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public record DungeonLayoutNode(
        String roomId,
        DungeonRoomType type,
        Identifier definitionId,
        DungeonCellPos cellOrigin,
        DungeonRoomFootprint footprint,
        EnumSet<DungeonConnectorSide> connectorSides
) {
    public DungeonLayoutNode {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Layout node room id must be non-empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Layout node type must be present: " + roomId);
        }

        if (definitionId == null) {
            throw new IllegalArgumentException("Layout node definition id must be present: " + roomId);
        }

        if (footprint == null) {
            throw new IllegalArgumentException("Layout node footprint must be present: " + roomId);
        }

        if (cellOrigin == null) {
            throw new IllegalArgumentException("Layout node cell origin must be present: " + roomId);
        }

        connectorSides = connectorSides == null
                ? EnumSet.noneOf(DungeonConnectorSide.class)
                : EnumSet.copyOf(connectorSides);

        if (connectorSides.isEmpty()) {
            throw new IllegalArgumentException(
                    "Layout node connectors must be non-empty for " + roomId
            );
        }

    }

    public DungeonConnectorShapeType connectorShapeType() {
        return DungeonConnectorShapeType.fromSides(this.connectorSides);
    }

    public int minGridX() {
        return this.cellOrigin.x();
    }

    public int minGridY() {
        return this.cellOrigin.y();
    }

    public int minGridZ() {
        return this.cellOrigin.z();
    }

    public int maxGridXExclusive() {
        return this.cellOrigin.x() + this.footprint.widthCells();
    }

    public int maxGridYExclusive() {
        return this.cellOrigin.y() + this.footprint.heightCells();
    }

    public int maxGridZExclusive() {
        return this.cellOrigin.z() + this.footprint.depthCells();
    }

    public boolean intersects(DungeonLayoutNode other) {
        return this.cellBox().intersects(other.cellBox());
    }

    public DungeonCellBox cellBox() {
        return this.footprint.toCellBox(this.cellOrigin);
    }

    public BlockPos blockOrigin(BlockPos layoutOrigin) {
        return layoutOrigin.offset(
                DungeonLayoutConstants.cellToBlockX(this.cellOrigin.x()),
                DungeonLayoutConstants.cellToBlockY(this.cellOrigin.y()),
                DungeonLayoutConstants.cellToBlockZ(this.cellOrigin.z())
        );
    }

    public BlockPos blockAnchor(BlockPos layoutOrigin) {
        BlockPos origin = this.blockOrigin(layoutOrigin);

        return origin.offset(
                this.footprint.widthBlocks() / 2,
                1,
                this.footprint.depthBlocks() / 2
        );
    }
}
