package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonSpatialLayoutValidator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonPiecePlanCompiler {
    private static final int SITE_BOUNDS_BUFFER_BLOCKS = 2;
    private static final int MIN_CORRIDOR_WIDTH_BLOCKS = 3;
    private static final int CORRIDOR_HEIGHT_BLOCKS = 4;

    private DungeonPiecePlanCompiler() {
    }

    public static DungeonPiecePlan compile(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout
    ) {
        DungeonSpatialLayoutValidator.validate(layout);

        List<DungeonPieceMetadata> roomPieces = layout.nodes()
                .stream()
                .map(node -> roomPiece(layoutOrigin, node))
                .toList();
        List<DungeonPieceMetadata> corridorPieces = new ArrayList<>();

        for (DungeonLayoutEdge edge : layout.edges()) {
            corridorPieces.addAll(corridorPieces(roomPieces, edge));
        }

        BoundingBox union = null;

        for (DungeonPieceMetadata room : roomPieces) {
            union = include(union, room.bounds());
        }

        for (DungeonPieceMetadata corridor : corridorPieces) {
            union = include(union, corridor.bounds());
        }

        if (union == null) {
            throw new IllegalArgumentException("Cannot compile piece plan for empty layout");
        }

        List<DungeonPieceMetadata> pieces = new ArrayList<>();
        pieces.addAll(corridorPieces);
        pieces.addAll(roomPieces);

        BoundingBox siteBounds = inflate(union, SITE_BOUNDS_BUFFER_BLOCKS);
        BlockPos startRoomAnchor = roomPieces.stream()
                .filter(piece -> piece.role() == ObeliskDungeonPieceRole.START_ROOM)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Piece plan missing START room"))
                .anchor();

        DungeonSpatialLayoutValidator.validatePieceBounds(
                layout,
                siteBounds,
                pieces.stream().map(DungeonPieceMetadata::bounds).toList(),
                startRoomAnchor
        );

        return new DungeonPiecePlan(layoutOrigin, siteBounds, pieces);
    }

    private static DungeonPieceMetadata roomPiece(
            BlockPos layoutOrigin,
            DungeonLayoutNode node
    ) {
        BoundingBox bounds = node.cellBox().toBlockBounds(layoutOrigin);

        return new DungeonPieceMetadata(
                roleFor(node.type()),
                node.roomId(),
                node.blockAnchor(layoutOrigin),
                bounds
        );
    }

    private static List<DungeonPieceMetadata> corridorPieces(
            List<DungeonPieceMetadata> rooms,
            DungeonLayoutEdge edge
    ) {
        if (edge.fromSide().vertical() || edge.toSide().vertical()) {
            throw new UnsupportedOperationException(
                    "Vertical dungeon corridors are not implemented yet: " + edge.id()
            );
        }

        DungeonPieceMetadata from = requirePiece(rooms, edge.fromRoomId());
        DungeonPieceMetadata to = requirePiece(rooms, edge.toRoomId());
        BlockPos fromCenter = connectorCenter(from.bounds(), edge.fromSide());
        BlockPos toCenter = connectorCenter(to.bounds(), edge.toSide());
        int width = Math.max(
                MIN_CORRIDOR_WIDTH_BLOCKS,
                edge.widthCells() * DungeonLayoutConstants.CELL_SIZE_X
        );

        if (fromCenter.getX() == toCenter.getX() || fromCenter.getZ() == toCenter.getZ()) {
            BoundingBox bounds = corridorBounds(fromCenter, toCenter, width);
            return List.of(new DungeonPieceMetadata(
                    ObeliskDungeonPieceRole.CORRIDOR,
                    edge.id(),
                    bounds.getCenter(),
                    bounds
            ));
        }

        BlockPos bend = new BlockPos(toCenter.getX(), fromCenter.getY(), fromCenter.getZ());
        BoundingBox first = corridorBounds(fromCenter, bend, width);
        BoundingBox second = corridorBounds(bend, toCenter, width);

        return List.of(
                new DungeonPieceMetadata(
                        ObeliskDungeonPieceRole.CORRIDOR,
                        edge.id() + "_a",
                        first.getCenter(),
                        first
                ),
                new DungeonPieceMetadata(
                        ObeliskDungeonPieceRole.CORRIDOR,
                        edge.id() + "_b",
                        second.getCenter(),
                        second
                )
        );
    }

    private static DungeonPieceMetadata requirePiece(
            List<DungeonPieceMetadata> rooms,
            String roomId
    ) {
        return rooms.stream()
                .filter(room -> room.id().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown embedded room piece: " + roomId));
    }

    private static BlockPos connectorCenter(
            BoundingBox bounds,
            DungeonConnectorSide side
    ) {
        int centerX = bounds.minX() + bounds.getXSpan() / 2;
        int centerY = bounds.minY() + 1;
        int centerZ = bounds.minZ() + bounds.getZSpan() / 2;

        return switch (side) {
            case NORTH -> new BlockPos(centerX, centerY, bounds.minZ());
            case SOUTH -> new BlockPos(centerX, centerY, bounds.maxZ());
            case WEST -> new BlockPos(bounds.minX(), centerY, centerZ);
            case EAST -> new BlockPos(bounds.maxX(), centerY, centerZ);
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical connector center is not supported yet: " + side
            );
        };
    }

    private static BoundingBox corridorBounds(
            BlockPos first,
            BlockPos second,
            int width
    ) {
        int halfLow = width / 2;
        int halfHigh = width - halfLow - 1;
        int minY = Math.min(first.getY(), second.getY()) - 1;
        int maxY = minY + CORRIDOR_HEIGHT_BLOCKS;

        if (first.getX() == second.getX()) {
            int x = first.getX();
            return new BoundingBox(
                    x - halfLow,
                    minY,
                    Math.min(first.getZ(), second.getZ()),
                    x + halfHigh,
                    maxY,
                    Math.max(first.getZ(), second.getZ())
            );
        }

        if (first.getZ() == second.getZ()) {
            int z = first.getZ();
            return new BoundingBox(
                    Math.min(first.getX(), second.getX()),
                    minY,
                    z - halfLow,
                    Math.max(first.getX(), second.getX()),
                    maxY,
                    z + halfHigh
            );
        }

        throw new IllegalArgumentException("Corridor segment must be axis-aligned: " + first + " -> " + second);
    }

    private static BoundingBox include(
            BoundingBox current,
            BoundingBox next
    ) {
        if (current == null) {
            return next;
        }

        return new BoundingBox(
                Math.min(current.minX(), next.minX()),
                Math.min(current.minY(), next.minY()),
                Math.min(current.minZ(), next.minZ()),
                Math.max(current.maxX(), next.maxX()),
                Math.max(current.maxY(), next.maxY()),
                Math.max(current.maxZ(), next.maxZ())
        );
    }

    private static BoundingBox inflate(
            BoundingBox bounds,
            int amount
    ) {
        return new BoundingBox(
                bounds.minX() - amount,
                bounds.minY() - amount,
                bounds.minZ() - amount,
                bounds.maxX() + amount,
                bounds.maxY() + amount,
                bounds.maxZ() + amount
        );
    }

    private static ObeliskDungeonPieceRole roleFor(DungeonRoomType type) {
        return switch (type) {
            case START -> ObeliskDungeonPieceRole.START_ROOM;
            case COMBAT -> ObeliskDungeonPieceRole.COMBAT_ROOM;
            case TREASURE -> ObeliskDungeonPieceRole.TREASURE_ROOM;
            case BOSS -> ObeliskDungeonPieceRole.BOSS_ROOM;
            case EXIT -> ObeliskDungeonPieceRole.EXIT_ROOM;
        };
    }
}
