package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometry;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonConnection;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonLayout;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonPort;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonPiecePlanCompiler {
    private static final int SITE_BOUNDS_BUFFER_BLOCKS = 2;
    private static final int PROCEDURAL_CORRIDOR_WIDTH_BLOCKS = 4;
    private static final int PROCEDURAL_CORRIDOR_CLEAR_HEIGHT_BLOCKS = 4;

    private DungeonPiecePlanCompiler() {
    }

    public static DungeonPiecePlan compile(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout,
            String primaryEntryRoomId,
            DungeonGenerationCatalog catalog
    ) {
        return compile(
                layoutOrigin,
                layout,
                primaryEntryRoomId,
                catalog,
                null,
                DungeonWorldgenTrace.Context.disabled(null)
        );
    }

    public static DungeonPiecePlan compile(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout,
            String primaryEntryRoomId,
            DungeonGenerationCatalog catalog,
            StructureTemplateManager templateManager,
            DungeonWorldgenTrace.Context traceContext
    ) {
        if (layout != null) {
            ResolvedDungeonLayout resolved =
                    DungeonLayoutResolver.resolveProcedural(
                            layoutOrigin,
                            layout,
                            primaryEntryRoomId,
                            catalog,
                            traceContext
                    );
            return compile(
                    resolved,
                    templateManager,
                    traceContext
            );
        }

        throw new IllegalArgumentException("Cannot compile a null dungeon layout");
    }

    public static DungeonPiecePlan compile(
            ResolvedDungeonLayout layout,
            StructureTemplateManager templateManager,
            DungeonWorldgenTrace.Context traceContext
    ) {
        validateResolvedRooms(layout);

        List<DungeonPieceMetadata> roomPieces = layout.rooms()
                .stream()
                .map(room -> resolvedRoomPiece(
                        room,
                        layout.primaryEntryRoomId(),
                        templateManager,
                        traceContext
                ))
                .toList();
        List<CompiledCorridorPiece> compiledCorridorPieces = new ArrayList<>();
        List<DungeonRoutedCorridor> routedCorridors = new ArrayList<>();

        for (ResolvedDungeonConnection connection : layout.connections()) {
            ResolvedDungeonRoom fromRoom =
                    layout.requireRoom(connection.from().roomId());
            ResolvedDungeonRoom toRoom =
                    layout.requireRoom(connection.to().roomId());
            ResolvedDungeonPort fromPort =
                    fromRoom.requirePort(connection.from().portId());
            ResolvedDungeonPort toPort =
                    toRoom.requirePort(connection.to().portId());
            DungeonRoutedCorridor routed = new DungeonRoutedCorridor(
                    connection.id(),
                    connection.from().roomId(),
                    connection.to().roomId(),
                    fromPort.facing(),
                    toPort.facing(),
                    connection.kind(),
                    connection.routeCells()
            );
            routedCorridors.add(routed);
            compiledCorridorPieces.addAll(resolvedCorridorPieces(
                    layout.layoutOrigin(),
                    connection,
                    fromRoom,
                    fromPort,
                    toRoom,
                    toPort,
                    routed,
                    templateManager,
                    traceContext
            ));
        }

        BoundingBox union = null;
        for (DungeonPieceMetadata room : roomPieces) {
            union = include(union, room.bounds());
        }
        for (CompiledCorridorPiece corridor : compiledCorridorPieces) {
            union = include(union, corridor.metadata().bounds());
        }
        if (union == null) {
            throw new IllegalArgumentException(
                    "Cannot compile piece plan for empty resolved layout"
            );
        }

        List<DungeonPieceMetadata> pieces = new ArrayList<>();
        List<DungeonPieceMetadata> corridorPieces = compiledCorridorPieces
                .stream()
                .map(CompiledCorridorPiece::metadata)
                .toList();
        pieces.addAll(corridorPieces);
        pieces.addAll(roomPieces);
        validateResolvedCorridorIntersections(
                roomPieces,
                compiledCorridorPieces
        );

        DungeonPieceMetadata primaryEntry = roomPieces.stream()
                .filter(piece -> piece.id().equals(layout.primaryEntryRoomId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Resolved piece plan missing primary START room"
                ));
        if (primaryEntry.role() != ObeliskDungeonPieceRole.START_ROOM) {
            throw new IllegalArgumentException(
                    "Resolved primary entry room is not a START room: "
                            + layout.primaryEntryRoomId()
            );
        }

        BoundingBox siteBounds = inflate(union, SITE_BOUNDS_BUFFER_BLOCKS);
        return new DungeonPiecePlan(
                layout.layoutOrigin(),
                siteBounds,
                layout.primaryEntryRoomId(),
                primaryEntry.anchor(),
                routedCorridors,
                pieces
        );
    }

    private static DungeonPieceMetadata resolvedRoomPiece(
            ResolvedDungeonRoom room,
            String primaryEntryRoomId,
            StructureTemplateManager templateManager,
            DungeonWorldgenTrace.Context traceContext
    ) {
        BoundingBox bounds = resolveCompiledBounds(
                templateManager,
                room.definition().template(),
                room.rotation(),
                room.mirrored(),
                room.templateGeometry(),
                room.templateOrigin(),
                false,
                room.roomId(),
                traceContext
        );
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=resolved-room-piece room="
                        + room.roomId()
                        + " definition="
                        + room.definitionId()
                        + " template="
                        + room.definition().template()
                        + " origin="
                        + room.templateOrigin()
                        + " rotation="
                        + room.rotation().getSerializedName()
                        + " mirror="
                        + room.mirrored()
                        + " bounds="
                        + bounds
                        + " ports="
                        + room.ports().stream()
                                .map(port -> port.id()
                                        + ":"
                                        + port.facing().getSerializedName()
                                        + "@"
                                        + port.boundaryCell())
                                .toList()
        );
        return new DungeonPieceMetadata(
                roleFor(room.definition().type()),
                room.roomId(),
                room.anchor(),
                bounds,
                room.roomId().equals(primaryEntryRoomId),
                Optional.of(room.definitionId()),
                Optional.of(room.definition().template()),
                room.rotation(),
                room.mirrored(),
                room.templateOrigin(),
                true
        );
    }

    private static List<CompiledCorridorPiece> resolvedCorridorPieces(
            BlockPos layoutOrigin,
            ResolvedDungeonConnection connection,
            ResolvedDungeonRoom fromRoom,
            ResolvedDungeonPort fromPort,
            ResolvedDungeonRoom toRoom,
            ResolvedDungeonPort toPort,
            DungeonRoutedCorridor corridor,
            StructureTemplateManager templateManager,
            DungeonWorldgenTrace.Context traceContext
    ) {
        List<CompiledCorridorPiece> pieces = new ArrayList<>();
        List<DungeonCellPos> path = corridor.path();

        if (path.isEmpty()) {
            throw new DungeonLayoutGenerationException(
                    "Cannot compile corridor with empty route: edge="
                            + corridor.edgeId()
            );
        }

        int sectionIndex = 0;
        for (CorridorSection section : corridorSections(path)) {
            BoundingBox bounds = corridorSectionBounds(
                    layoutOrigin,
                    path,
                    section
            );
            String pieceId = corridor.edgeId()
                    + "_section_"
                    + sectionIndex++;
            pieces.add(new CompiledCorridorPiece(
                    proceduralCorridorMetadata(pieceId, bounds),
                    corridor.edgeId(),
                    connection.from().roomId(),
                    connection.to().roomId(),
                    section.startIndex(),
                    endpointBounds(
                            fromRoom,
                            fromPort,
                            toRoom,
                            toPort,
                            path.size(),
                            section.startIndex(),
                            section.endIndex(),
                            bounds
                    )
            ));
            logCompiledCorridor(
                    corridor,
                    pieceId,
                    section.startIndex(),
                    section.endIndex(),
                    bounds,
                    "section",
                    traceContext
            );
        }

        int apronIndex = 0;
        for (int routeIndex : corridorApronIndices(path)) {
            BoundingBox bounds = corridorApronBounds(
                    layoutOrigin,
                    path.get(routeIndex),
                    routeIndex,
                    path.size(),
                    fromRoom,
                    fromPort,
                    toRoom,
                    toPort
            );
            String pieceId = corridor.edgeId()
                    + "_apron_"
                    + apronIndex++;
            pieces.add(new CompiledCorridorPiece(
                    proceduralCorridorMetadata(pieceId, bounds),
                    corridor.edgeId(),
                    connection.from().roomId(),
                    connection.to().roomId(),
                    routeIndex,
                    endpointBounds(
                            fromRoom,
                            fromPort,
                            toRoom,
                            toPort,
                            path.size(),
                            routeIndex,
                            routeIndex,
                            bounds
                    )
            ));
            logCompiledCorridor(
                    corridor,
                    pieceId,
                    routeIndex,
                    routeIndex,
                    bounds,
                    "apron",
                    traceContext
            );
        }

        return pieces;
    }

    private static DungeonPieceMetadata proceduralCorridorMetadata(
            String pieceId,
            BoundingBox bounds
    ) {
        return new DungeonPieceMetadata(
                ObeliskDungeonPieceRole.CORRIDOR,
                pieceId,
                bounds.getCenter(),
                bounds,
                false,
                Optional.empty(),
                Optional.empty(),
                DungeonRoomRotation.NONE,
                false,
                new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                false
        );
    }

    private static List<CorridorSection> corridorSections(
            List<DungeonCellPos> path
    ) {
        if (path.size() == 1) {
            return List.of();
        }

        List<CorridorSection> sections = new ArrayList<>();
        int start = 0;
        DungeonConnectorSide currentDirection = directionBetween(
                path.get(0),
                path.get(1)
        );

        for (int index = 2; index < path.size(); index++) {
            DungeonConnectorSide nextDirection = directionBetween(
                    path.get(index - 1),
                    path.get(index)
            );
            if (nextDirection != currentDirection) {
                sections.add(new CorridorSection(
                        start,
                        index - 1,
                        currentDirection
                ));
                start = index - 1;
                currentDirection = nextDirection;
            }
        }

        sections.add(new CorridorSection(
                start,
                path.size() - 1,
                currentDirection
        ));
        return List.copyOf(sections);
    }

    private static Set<Integer> corridorApronIndices(
            List<DungeonCellPos> path
    ) {
        Set<Integer> indices = new LinkedHashSet<>();
        indices.add(0);
        indices.add(path.size() - 1);
        for (int index = 1; index < path.size() - 1; index++) {
            DungeonConnectorSide previous = directionBetween(
                    path.get(index - 1),
                    path.get(index)
            );
            DungeonConnectorSide next = directionBetween(
                    path.get(index),
                    path.get(index + 1)
            );
            if (previous != next) {
                indices.add(index);
            }
        }
        return indices;
    }

    private static BoundingBox corridorSectionBounds(
            BlockPos layoutOrigin,
            List<DungeonCellPos> path,
            CorridorSection section
    ) {
        DungeonCellPos start = path.get(section.startIndex());
        DungeonCellPos end = path.get(section.endIndex());
        int minCellX = Math.min(start.x(), end.x());
        int maxCellX = Math.max(start.x(), end.x());
        int minCellZ = Math.min(start.z(), end.z());
        int maxCellZ = Math.max(start.z(), end.z());
        int minY = layoutOrigin.getY()
                + DungeonLayoutConstants.cellToBlockY(start.y());
        int maxY = minY + PROCEDURAL_CORRIDOR_CLEAR_HEIGHT_BLOCKS;

        return switch (section.direction()) {
            case EAST, WEST -> {
                int minX = layoutOrigin.getX()
                        + DungeonLayoutConstants.cellToBlockX(minCellX);
                int maxX = layoutOrigin.getX()
                        + DungeonLayoutConstants.cellToBlockX(maxCellX)
                        + DungeonLayoutConstants.CELL_SIZE_X
                        - 1;
                int minZ = layoutOrigin.getZ()
                        + DungeonLayoutConstants.cellToBlockZ(start.z())
                        + centeredCorridorOffset(DungeonLayoutConstants.CELL_SIZE_Z);
                yield new BoundingBox(
                        minX,
                        minY,
                        minZ,
                        maxX,
                        maxY,
                        minZ + PROCEDURAL_CORRIDOR_WIDTH_BLOCKS - 1
                );
            }
            case NORTH, SOUTH -> {
                int minX = layoutOrigin.getX()
                        + DungeonLayoutConstants.cellToBlockX(start.x())
                        + centeredCorridorOffset(DungeonLayoutConstants.CELL_SIZE_X);
                int minZ = layoutOrigin.getZ()
                        + DungeonLayoutConstants.cellToBlockZ(minCellZ);
                int maxZ = layoutOrigin.getZ()
                        + DungeonLayoutConstants.cellToBlockZ(maxCellZ)
                        + DungeonLayoutConstants.CELL_SIZE_Z
                        - 1;
                yield new BoundingBox(
                        minX,
                        minY,
                        minZ,
                        minX + PROCEDURAL_CORRIDOR_WIDTH_BLOCKS - 1,
                        maxY,
                        maxZ
                );
            }
            case UP, DOWN -> throw new DungeonLayoutGenerationException(
                    "Vertical corridor sections are not supported by current procedural corridors: "
                            + start
                            + " -> "
                            + end
            );
        };
    }

    private static BoundingBox corridorApronBounds(
            BlockPos layoutOrigin,
            DungeonCellPos cell,
            int routeIndex,
            int routeLength,
            ResolvedDungeonRoom fromRoom,
            ResolvedDungeonPort fromPort,
            ResolvedDungeonRoom toRoom,
            ResolvedDungeonPort toPort
    ) {
        BoundingBox bounds = fullCellCorridorBounds(layoutOrigin, cell);
        if (routeIndex == 0) {
            bounds = include(bounds, endpointProjectionBounds(fromRoom, fromPort));
        }
        if (routeIndex == routeLength - 1) {
            bounds = include(bounds, endpointProjectionBounds(toRoom, toPort));
        }
        return bounds;
    }

    private static BoundingBox fullCellCorridorBounds(
            BlockPos layoutOrigin,
            DungeonCellPos cell
    ) {
        int minX = layoutOrigin.getX()
                + DungeonLayoutConstants.cellToBlockX(cell.x());
        int minY = layoutOrigin.getY()
                + DungeonLayoutConstants.cellToBlockY(cell.y());
        int minZ = layoutOrigin.getZ()
                + DungeonLayoutConstants.cellToBlockZ(cell.z());
        return new BoundingBox(
                minX,
                minY,
                minZ,
                minX + DungeonLayoutConstants.CELL_SIZE_X - 1,
                minY + PROCEDURAL_CORRIDOR_CLEAR_HEIGHT_BLOCKS,
                minZ + DungeonLayoutConstants.CELL_SIZE_Z - 1
        );
    }

    private static BoundingBox endpointProjectionBounds(
            ResolvedDungeonRoom room,
            ResolvedDungeonPort port
    ) {
        BlockPos opening = room.templateOrigin().offset(port.openingMin());
        int minY = Math.min(room.templateOrigin().getY(), opening.getY());
        int maxY = Math.max(
                room.templateOrigin().getY()
                        + PROCEDURAL_CORRIDOR_CLEAR_HEIGHT_BLOCKS,
                opening.getY() + port.heightBlocks() - 1
        );
        return switch (port.facing()) {
            case NORTH -> new BoundingBox(
                    opening.getX(),
                    minY,
                    opening.getZ() - DungeonLayoutConstants.CELL_SIZE_Z,
                    opening.getX() + port.widthBlocks() - 1,
                    maxY,
                    opening.getZ() - 1
            );
            case SOUTH -> new BoundingBox(
                    opening.getX(),
                    minY,
                    opening.getZ() + 1,
                    opening.getX() + port.widthBlocks() - 1,
                    maxY,
                    opening.getZ() + DungeonLayoutConstants.CELL_SIZE_Z
            );
            case WEST -> new BoundingBox(
                    opening.getX() - DungeonLayoutConstants.CELL_SIZE_X,
                    minY,
                    opening.getZ(),
                    opening.getX() - 1,
                    maxY,
                    opening.getZ() + port.widthBlocks() - 1
            );
            case EAST -> new BoundingBox(
                    opening.getX() + 1,
                    minY,
                    opening.getZ(),
                    opening.getX() + DungeonLayoutConstants.CELL_SIZE_X,
                    maxY,
                    opening.getZ() + port.widthBlocks() - 1
            );
            case UP, DOWN -> throw new DungeonLayoutGenerationException(
                    "Vertical dungeon connector projection is not supported: "
                            + room.roomId()
                            + "."
                            + port.id()
            );
        };
    }

    private static int centeredCorridorOffset(int cellSize) {
        return Math.floorDiv(
                cellSize - PROCEDURAL_CORRIDOR_WIDTH_BLOCKS,
                2
        );
    }

    private static Map<String, BoundingBox> endpointBounds(
            ResolvedDungeonRoom fromRoom,
            ResolvedDungeonPort fromPort,
            ResolvedDungeonRoom toRoom,
            ResolvedDungeonPort toPort,
            int routeLength,
            int startIndex,
            int endIndex,
            BoundingBox pieceBounds
    ) {
        Map<String, BoundingBox> bounds = new LinkedHashMap<>();
        if (startIndex <= 1) {
            bounds.put(fromRoom.roomId(), endpointAllowance(
                    fromRoom,
                    fromPort,
                    pieceBounds
            ));
        }
        if (endIndex >= routeLength - 2) {
            bounds.put(toRoom.roomId(), endpointAllowance(
                    toRoom,
                    toPort,
                    pieceBounds
            ));
        }
        return Map.copyOf(bounds);
    }

    private static BoundingBox endpointAllowance(
            ResolvedDungeonRoom room,
            ResolvedDungeonPort port,
            BoundingBox pieceBounds
    ) {
        BlockPos opening = room.templateOrigin().offset(port.openingMin());
        int minY = Math.min(room.templateOrigin().getY(), opening.getY());
        int maxY = Math.max(
                room.templateOrigin().getY(),
                opening.getY() + port.heightBlocks() - 1
        );
        return switch (port.facing()) {
            case NORTH -> new BoundingBox(
                    Math.min(opening.getX(), pieceBounds.minX()),
                    minY,
                    opening.getZ() - DungeonLayoutConstants.CELL_SIZE_Z,
                    Math.max(
                            opening.getX() + port.widthBlocks() - 1,
                            pieceBounds.maxX()
                    ),
                    maxY,
                    opening.getZ() + DungeonLayoutConstants.CELL_SIZE_Z - 1
            );
            case SOUTH -> new BoundingBox(
                    Math.min(opening.getX(), pieceBounds.minX()),
                    minY,
                    opening.getZ() - DungeonLayoutConstants.CELL_SIZE_Z + 1,
                    Math.max(
                            opening.getX() + port.widthBlocks() - 1,
                            pieceBounds.maxX()
                    ),
                    maxY,
                    opening.getZ() + DungeonLayoutConstants.CELL_SIZE_Z
            );
            case WEST -> new BoundingBox(
                    opening.getX() - DungeonLayoutConstants.CELL_SIZE_X,
                    minY,
                    Math.min(opening.getZ(), pieceBounds.minZ()),
                    opening.getX() + DungeonLayoutConstants.CELL_SIZE_X - 1,
                    maxY,
                    Math.max(
                            opening.getZ() + port.widthBlocks() - 1,
                            pieceBounds.maxZ()
                    )
            );
            case EAST -> new BoundingBox(
                    opening.getX() - DungeonLayoutConstants.CELL_SIZE_X + 1,
                    minY,
                    Math.min(opening.getZ(), pieceBounds.minZ()),
                    opening.getX() + DungeonLayoutConstants.CELL_SIZE_X,
                    maxY,
                    Math.max(
                            opening.getZ() + port.widthBlocks() - 1,
                            pieceBounds.maxZ()
                    )
            );
            case UP, DOWN -> throw new DungeonLayoutGenerationException(
                    "Vertical dungeon connector overlap is not supported: "
                            + room.roomId()
                            + "."
                            + port.id()
            );
        };
    }

    private static void validateResolvedRooms(ResolvedDungeonLayout layout) {
        for (int i = 0; i < layout.rooms().size(); i++) {
            ResolvedDungeonRoom first = layout.rooms().get(i);
            for (int j = i + 1; j < layout.rooms().size(); j++) {
                ResolvedDungeonRoom second = layout.rooms().get(j);
                if (first.exactBounds().intersects(second.exactBounds())) {
                    throw new DungeonLayoutGenerationException(
                            "Resolved room-room collision before piece compile: first="
                                    + first.roomId()
                                    + " firstBounds="
                                    + first.exactBounds()
                                    + " second="
                                    + second.roomId()
                                    + " secondBounds="
                                    + second.exactBounds()
                    );
                }
            }
        }
    }

    private static void validateResolvedCorridorIntersections(
            List<DungeonPieceMetadata> roomPieces,
            List<CompiledCorridorPiece> corridorPieces
    ) {
        for (int i = 0; i < corridorPieces.size(); i++) {
            CompiledCorridorPiece first = corridorPieces.get(i);
            for (int j = i + 1; j < corridorPieces.size(); j++) {
                CompiledCorridorPiece second = corridorPieces.get(j);
                BoundingBox intersection = intersection(
                        first.metadata().bounds(),
                        second.metadata().bounds()
                );
                if (intersection == null) {
                    continue;
                }
                if (first.corridorId().equals(second.corridorId())) {
                    continue;
                }

                Optional<String> legalSharedRoom = sharedEndpointRooms(first, second)
                        .stream()
                        .filter(roomId -> legalEndpointIntersection(
                                first,
                                second,
                                roomId,
                                intersection
                        ))
                        .findFirst();
                if (legalSharedRoom.isPresent()) {
                    continue;
                }

                String sharedRoom = sharedEndpointRooms(first, second)
                        .stream()
                        .findFirst()
                        .orElse("<none>");
                throw new DungeonLayoutGenerationException(
                        "Resolved corridor-corridor collision: firstCorridor="
                                + first.corridorId()
                                + " firstPiece="
                                + first.metadata().id()
                                + " firstBounds="
                                + first.metadata().bounds()
                                + " firstRouteIndex="
                                + first.routeIndex()
                                + " secondCorridor="
                                + second.corridorId()
                                + " secondPiece="
                                + second.metadata().id()
                                + " secondBounds="
                                + second.metadata().bounds()
                                + " secondRouteIndex="
                                + second.routeIndex()
                                + " sharedRoom="
                                + sharedRoom
                                + " intersection="
                                + intersection
                                + " firstAllowed="
                                + first.endpointBoundsByRoom().get(sharedRoom)
                                + " secondAllowed="
                                + second.endpointBoundsByRoom().get(sharedRoom)
                );
            }

            for (DungeonPieceMetadata room : roomPieces) {
                BoundingBox intersection = intersection(
                        first.metadata().bounds(),
                        room.bounds()
                );
                if (intersection == null) {
                    continue;
                }
                BoundingBox allowed =
                        first.endpointBoundsByRoom().get(room.id());
                if (allowed != null && contains(allowed, intersection)) {
                    continue;
                }
                throw new DungeonLayoutGenerationException(
                        "Resolved corridor-room collision: corridor="
                                + first.corridorId()
                                + " piece="
                                + first.metadata().id()
                                + " corridorBounds="
                                + first.metadata().bounds()
                                + " routeIndex="
                                + first.routeIndex()
                                + " room="
                                + room.id()
                                + " roomBounds="
                                + room.bounds()
                                + " intersection="
                                + intersection
                                + " allowedEndpointBounds="
                                + allowed
                );
            }
        }
    }

    private static List<String> sharedEndpointRooms(
            CompiledCorridorPiece first,
            CompiledCorridorPiece second
    ) {
        List<String> shared = new ArrayList<>();
        if (first.fromRoomId().equals(second.fromRoomId())
                || first.fromRoomId().equals(second.toRoomId())) {
            shared.add(first.fromRoomId());
        }
        if (!first.toRoomId().equals(first.fromRoomId())
                && (first.toRoomId().equals(second.fromRoomId())
                || first.toRoomId().equals(second.toRoomId()))) {
            shared.add(first.toRoomId());
        }
        return shared;
    }

    private static boolean legalEndpointIntersection(
            CompiledCorridorPiece first,
            CompiledCorridorPiece second,
            String roomId,
            BoundingBox intersection
    ) {
        BoundingBox firstAllowed = first.endpointBoundsByRoom().get(roomId);
        BoundingBox secondAllowed = second.endpointBoundsByRoom().get(roomId);
        return firstAllowed != null
                && secondAllowed != null
                && contains(firstAllowed, intersection)
                && contains(secondAllowed, intersection);
    }

    private static boolean contains(
            BoundingBox outer,
            BoundingBox inner
    ) {
        return outer.minX() <= inner.minX()
                && outer.minY() <= inner.minY()
                && outer.minZ() <= inner.minZ()
                && outer.maxX() >= inner.maxX()
                && outer.maxY() >= inner.maxY()
                && outer.maxZ() >= inner.maxZ();
    }

    private static BoundingBox resolveCompiledBounds(
            StructureTemplateManager templateManager,
            Identifier templateId,
            DungeonRoomRotation rotation,
            boolean mirror,
            DungeonTemplateGeometry geometry,
            BlockPos targetMinimum,
            boolean corridor,
            String pieceId,
            DungeonWorldgenTrace.Context traceContext
    ) {
        if (templateManager == null) {
            return geometry.transformed(rotation)
                    .boxAt(targetMinimum)
                    .toInclusiveBoundingBox();
        }

        StructureTemplate template = templateManager.get(templateId)
                .orElseThrow(() -> new DungeonContentException(
                        templateId,
                        "missing structure template"
                ));
        if (template.getSize().getX() <= 0
                || template.getSize().getY() <= 0
                || template.getSize().getZ() <= 0) {
            throw new DungeonContentException(
                    templateId,
                    "template dimensions must be positive: "
                            + template.getSize().getX()
                            + "x"
                            + template.getSize().getY()
                            + "x"
                            + template.getSize().getZ()
            );
        }
        ResolvedTemplatePlacement placement = ResolvedTemplatePlacement.resolve(
                template,
                targetMinimum,
                rotation,
                mirror
        );
        BoundingBox transformed = placement.transformedBounds();

        return transformed;
    }

    private static void logCompiledCorridor(
            DungeonRoutedCorridor corridor,
            String pieceId,
            int startIndex,
            int endIndex,
            BoundingBox bounds,
            String kind,
            DungeonWorldgenTrace.Context traceContext
    ) {
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=compiled-corridor edge="
                        + corridor.edgeId()
                        + " blockBounds="
                        + bounds
                        + " piece="
                        + pieceId
                        + " kind="
                        + kind
                        + " routeStart="
                        + startIndex
                        + " routeEnd="
                        + endIndex
                        + " templateBacked=false"
        );
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

    private static boolean intersects(
            BoundingBox first,
            BoundingBox second
    ) {
        return first.minX() <= second.maxX()
                && first.maxX() >= second.minX()
                && first.minY() <= second.maxY()
                && first.maxY() >= second.minY()
                && first.minZ() <= second.maxZ()
                && first.maxZ() >= second.minZ();
    }

    private static BoundingBox intersection(
            BoundingBox first,
            BoundingBox second
    ) {
        if (!intersects(first, second)) {
            return null;
        }
        return new BoundingBox(
                Math.max(first.minX(), second.minX()),
                Math.max(first.minY(), second.minY()),
                Math.max(first.minZ(), second.minZ()),
                Math.min(first.maxX(), second.maxX()),
                Math.min(first.maxY(), second.maxY()),
                Math.min(first.maxZ(), second.maxZ())
        );
    }

    private static DungeonConnectorSide directionBetween(
            DungeonCellPos from,
            DungeonCellPos to
    ) {
        int dx = Integer.compare(to.x() - from.x(), 0);
        int dz = Integer.compare(to.z() - from.z(), 0);

        if (Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) != 1
                || from.y() != to.y()) {
            throw new DungeonLayoutGenerationException(
                    "Routed corridor cells must be horizontally adjacent: "
                            + from
                            + " -> "
                            + to
            );
        }

        if (dx > 0) {
            return DungeonConnectorSide.EAST;
        }
        if (dx < 0) {
            return DungeonConnectorSide.WEST;
        }
        if (dz > 0) {
            return DungeonConnectorSide.SOUTH;
        }

        return DungeonConnectorSide.NORTH;
    }

    private static ObeliskDungeonPieceRole roleFor(DungeonRoomType type) {
        return switch (type) {
            case START -> ObeliskDungeonPieceRole.START_ROOM;
            case COMBAT -> ObeliskDungeonPieceRole.COMBAT_ROOM;
            case TREASURE -> ObeliskDungeonPieceRole.TREASURE_ROOM;
            case BOSS -> ObeliskDungeonPieceRole.BOSS_ROOM;
        };
    }

    private record CompiledCorridorPiece(
            DungeonPieceMetadata metadata,
            String corridorId,
            String fromRoomId,
            String toRoomId,
            int routeIndex,
            Map<String, BoundingBox> endpointBoundsByRoom
    ) {
        private CompiledCorridorPiece {
            endpointBoundsByRoom = endpointBoundsByRoom == null
                    ? Map.of()
                    : Map.copyOf(endpointBoundsByRoom);
        }
    }

    private record CorridorSection(
            int startIndex,
            int endIndex,
            DungeonConnectorSide direction
    ) {
    }

}
