package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonPort;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
        if (offset == 0) {
            return this;
        }
        return new PlacedDungeonRoom(
                this.id,
                this.type,
                this.definitionId,
                this.templateId,
                this.cellOrigin,
                translate(this.templateOrigin, offset),
                translate(this.anchor, offset),
                this.rotation,
                this.mirror,
                translate(this.bounds, offset),
                this.ports,
                this.primaryEntry
        );
    }

    private static BlockPos translate(
            BlockPos pos,
            int offset
    ) {
        return pos.offset(0, offset, 0);
    }

    private static BoundingBox translate(
            BoundingBox bounds,
            int offset
    ) {
        return new BoundingBox(
                bounds.minX(),
                Math.addExact(bounds.minY(), offset),
                bounds.minZ(),
                bounds.maxX(),
                Math.addExact(bounds.maxY(), offset),
                bounds.maxZ()
        );
    }
}
