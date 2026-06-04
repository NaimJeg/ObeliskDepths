package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonPort;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

public record PlacedDungeonRoom(
        String id,
        DungeonRoomType type,
        Identifier definitionId,
        Identifier templateId,
        DungeonCellPos cellOrigin,
        BlockPos templateOrigin,
        BlockPos anchor,
        DungeonRoomRotation rotation,
        boolean mirror,
        BoundingBox bounds,
        List<ResolvedDungeonPort> ports,
        boolean primaryEntry
) {
    public PlacedDungeonRoom {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Placed room id must be non-empty");
        }
        if (type == null || definitionId == null || templateId == null
                || cellOrigin == null || templateOrigin == null
                || anchor == null || bounds == null) {
            throw new IllegalArgumentException(
                    "Placed room metadata is incomplete: " + id
            );
        }
        rotation = rotation == null ? DungeonRoomRotation.NONE : rotation;
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    public PlacedDungeonRoom translatedY(int offset) {
        return this.translated(0, offset, 0);
    }

    public PlacedDungeonRoom translated(
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return this;
        }
        return new PlacedDungeonRoom(
                this.id,
                this.type,
                this.definitionId,
                this.templateId,
                this.cellOrigin,
                translate(this.templateOrigin, offsetX, offsetY, offsetZ),
                translate(this.anchor, offsetX, offsetY, offsetZ),
                this.rotation,
                this.mirror,
                translate(this.bounds, offsetX, offsetY, offsetZ),
                this.ports,
                this.primaryEntry
        );
    }

    private static BlockPos translate(
            BlockPos pos,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        return pos.offset(offsetX, offsetY, offsetZ);
    }

    private static BoundingBox translate(
            BoundingBox bounds,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        return new BoundingBox(
                Math.addExact(bounds.minX(), offsetX),
                Math.addExact(bounds.minY(), offsetY),
                Math.addExact(bounds.minZ(), offsetZ),
                Math.addExact(bounds.maxX(), offsetX),
                Math.addExact(bounds.maxY(), offsetY),
                Math.addExact(bounds.maxZ(), offsetZ)
        );
    }
}
