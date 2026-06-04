package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DungeonLayoutTransforms {
    private DungeonLayoutTransforms() {
    }

    public static DungeonConnectorSide transformSide(
            DungeonConnectorSide side,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonConnectorSide mirrored = mirrorSide(side, mirror);
        return rotateSide(mirrored, rotation);
    }

    public static DungeonCellPos transformCell(
            DungeonCellPos cell,
            int widthCells,
            int depthCells,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonCellPos mirrored = mirror
                ? new DungeonCellPos(
                        cell.x(),
                        cell.y(),
                        depthCells - 1 - cell.z()
                )
                : cell;
        return DungeonRoomFootprint.rotateCell(
                mirrored,
                rotation == null ? DungeonRoomRotation.NONE : rotation,
                widthCells,
                depthCells
        );
    }

    public static BlockPos transformBlockPoint(
            BlockPos point,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        BlockPos mirrored = mirror
                ? new BlockPos(
                        point.getX(),
                        point.getY(),
                        geometry.sizeZ() - 1 - point.getZ()
                )
                : point;
        return rotateBlockPoint(mirrored, geometry, rotation);
    }

    public static BlockPos transformOpeningMin(
            RoomConnectorDefinition port,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonBlockBox localBox = openingBox(port);
        DungeonBlockBox transformed = transformBlockBox(
                localBox,
                geometry,
                rotation,
                mirror
        );
        return transformed.minPos();
    }

    public static DungeonRoomFootprint resolveFootprint(
            DungeonRoomFootprint authored,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonRoomFootprint base = authored == null || authored.isAuto()
                ? DungeonRoomFootprint.rectangular(
                        geometry.routingCellsX(),
                        geometry.routingCellsY(),
                        geometry.routingCellsZ()
                )
                : authored;
        if ((rotation == null || rotation == DungeonRoomRotation.NONE)
                && !mirror) {
            return base;
        }

        LinkedHashSet<DungeonCellPos> transformed = new LinkedHashSet<>();
        for (DungeonCellPos cell : base.occupiedCells()) {
            transformed.add(transformCell(
                    cell,
                    base.widthCells(),
                    base.depthCells(),
                    rotation,
                    mirror
            ));
        }
        return new DungeonRoomFootprint(transformed);
    }

    public static DungeonBlockBox transformBlockBox(
            DungeonBlockBox box,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        List<BlockPos> corners = List.of(
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxXExclusive() - 1, box.minY(), box.minZ()),
                new BlockPos(box.minX(), box.maxYExclusive() - 1, box.minZ()),
                new BlockPos(box.minX(), box.minY(), box.maxZExclusive() - 1),
                new BlockPos(
                        box.maxXExclusive() - 1,
                        box.maxYExclusive() - 1,
                        box.minZ()
                ),
                new BlockPos(
                        box.maxXExclusive() - 1,
                        box.minY(),
                        box.maxZExclusive() - 1
                ),
                new BlockPos(
                        box.minX(),
                        box.maxYExclusive() - 1,
                        box.maxZExclusive() - 1
                ),
                new BlockPos(
                        box.maxXExclusive() - 1,
                        box.maxYExclusive() - 1,
                        box.maxZExclusive() - 1
                )
        );
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos corner : corners) {
            BlockPos transformed = transformBlockPoint(
                    corner,
                    geometry,
                    rotation,
                    mirror
            );
            minX = Math.min(minX, transformed.getX());
            minY = Math.min(minY, transformed.getY());
            minZ = Math.min(minZ, transformed.getZ());
            maxX = Math.max(maxX, transformed.getX());
            maxY = Math.max(maxY, transformed.getY());
            maxZ = Math.max(maxZ, transformed.getZ());
        }

        return new DungeonBlockBox(
                minX,
                minY,
                minZ,
                maxX + 1,
                maxY + 1,
                maxZ + 1
        );
    }

    private static DungeonConnectorSide mirrorSide(
            DungeonConnectorSide side,
            boolean mirror
    ) {
        if (!mirror || side.vertical()) {
            return side;
        }
        return switch (side) {
            case NORTH -> DungeonConnectorSide.SOUTH;
            case SOUTH -> DungeonConnectorSide.NORTH;
            case EAST, WEST, UP, DOWN -> side;
        };
    }

    private static DungeonConnectorSide rotateSide(
            DungeonConnectorSide side,
            DungeonRoomRotation rotation
    ) {
        if (side.vertical()) {
            return side;
        }
        DungeonRoomRotation resolved = rotation == null
                ? DungeonRoomRotation.NONE
                : rotation;
        return switch (resolved) {
            case NONE -> side;
            case CLOCKWISE_90 -> switch (side) {
                case NORTH -> DungeonConnectorSide.EAST;
                case EAST -> DungeonConnectorSide.SOUTH;
                case SOUTH -> DungeonConnectorSide.WEST;
                case WEST -> DungeonConnectorSide.NORTH;
                case UP, DOWN -> side;
            };
            case CLOCKWISE_180 -> side.opposite();
            case COUNTERCLOCKWISE_90 -> switch (side) {
                case NORTH -> DungeonConnectorSide.WEST;
                case WEST -> DungeonConnectorSide.SOUTH;
                case SOUTH -> DungeonConnectorSide.EAST;
                case EAST -> DungeonConnectorSide.NORTH;
                case UP, DOWN -> side;
            };
        };
    }

    private static BlockPos rotateBlockPoint(
            BlockPos point,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation
    ) {
        DungeonRoomRotation resolved = rotation == null
                ? DungeonRoomRotation.NONE
                : rotation;
        return switch (resolved) {
            case NONE -> point;
            case CLOCKWISE_90 -> new BlockPos(
                    geometry.sizeZ() - 1 - point.getZ(),
                    point.getY(),
                    point.getX()
            );
            case CLOCKWISE_180 -> new BlockPos(
                    geometry.sizeX() - 1 - point.getX(),
                    point.getY(),
                    geometry.sizeZ() - 1 - point.getZ()
            );
            case COUNTERCLOCKWISE_90 -> new BlockPos(
                    point.getZ(),
                    point.getY(),
                    geometry.sizeX() - 1 - point.getX()
            );
        };
    }

    private static DungeonBlockBox openingBox(RoomConnectorDefinition port) {
        int sizeX = port.facing() == DungeonConnectorSide.NORTH
                || port.facing() == DungeonConnectorSide.SOUTH
                ? port.widthBlocks()
                : 1;
        int sizeZ = port.facing() == DungeonConnectorSide.EAST
                || port.facing() == DungeonConnectorSide.WEST
                ? port.widthBlocks()
                : 1;
        if (port.facing().vertical()) {
            sizeX = port.widthBlocks();
            sizeZ = port.widthBlocks();
        }
        return DungeonBlockBox.fromMinAndSize(
                port.openingMin(),
                sizeX,
                port.heightBlocks(),
                sizeZ
        );
    }
}
