package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Exact resolved room placement. Block-space bounds and named ports are
 * authoritative; routing cells are an acceleration mask only.
 */
public record ResolvedDungeonRoom(
        String roomId,
        Identifier definitionId,
        DungeonRoomDefinition definition,
        DungeonCellPos cellOrigin,
        BlockPos templateOrigin,
        BlockPos anchor,
        DungeonRoomRotation rotation,
        boolean mirrored,
        DungeonTemplateGeometry templateGeometry,
        DungeonTemplateGeometry transformedGeometry,
        DungeonRoomFootprint transformedFootprint,
        DungeonBlockBox exactBounds,
        List<ResolvedDungeonPort> ports
) {
    public ResolvedDungeonRoom {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolved room id must be non-empty"
            );
        }
        if (definitionId == null || definition == null || cellOrigin == null
                || templateOrigin == null || anchor == null
                || templateGeometry == null || transformedGeometry == null
                || transformedFootprint == null || exactBounds == null) {
            throw new IllegalArgumentException(
                    "Resolved room metadata is incomplete: " + roomId
            );
        }
        rotation = rotation == null ? DungeonRoomRotation.NONE : rotation;
        if (transformedFootprint.isAuto()) {
            throw new IllegalArgumentException(
                    "Resolved room footprint must not be AUTO: " + roomId
            );
        }
        ports = ports == null ? List.of() : List.copyOf(ports);
    }

    public Optional<ResolvedDungeonPort> port(String id) {
        return this.ports.stream()
                .filter(port -> port.id().equals(id))
                .findFirst();
    }

    public ResolvedDungeonPort requirePort(String id) {
        return port(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resolved room "
                                + this.roomId
                                + " does not contain port "
                                + id
                                + " in definition "
                                + this.definitionId
                ));
    }

    public Set<DungeonCellPos> occupiedWorldCells() {
        LinkedHashSet<DungeonCellPos> cells = new LinkedHashSet<>();
        for (DungeonCellPos cell : this.transformedFootprint.occupiedCells()) {
            cells.add(new DungeonCellPos(
                    this.cellOrigin.x() + cell.x(),
                    this.cellOrigin.y() + cell.y(),
                    this.cellOrigin.z() + cell.z()
            ));
        }
        return Set.copyOf(cells);
    }

    public DungeonCellBox cellBox() {
        return this.transformedFootprint.toCellBox(this.cellOrigin);
    }
}
