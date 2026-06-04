package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonBlockBox;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonResolvedTopologyValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonConnection;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonLayout;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonRoom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonGenerationPlanner {
    private static final int SITE_BOUNDS_BUFFER_BLOCKS = 2;

    private DungeonGenerationPlanner() {
    }

    public static DungeonGenerationPlan plan(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            BlockPos origin,
            DungeonGenerationCatalog catalog,
            long attemptSalt,
            int layoutAttempt,
            DungeonWorldgenTrace.Context traceContext
    ) {
        DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(
                graph,
                analysis,
                origin,
                catalog,
                attemptSalt
        );
        ResolvedDungeonLayout resolved =
                DungeonLayoutResolver.resolveProcedural(
                        origin,
                        layout,
                        graph.primaryEntryNodeId(),
                        catalog,
                        traceContext
                );
        DungeonResolvedTopologyValidator.validateProcedural(
                graph,
                layout,
                resolved,
                layoutAttempt
        );
        return fromResolved(resolved);
    }

    public static DungeonGenerationPlan fromResolved(
            ResolvedDungeonLayout layout
    ) {
        List<PlacedDungeonRoom> rooms = new ArrayList<>();
        BoundingBox union = null;
        for (ResolvedDungeonRoom room : layout.rooms()) {
            BoundingBox bounds = room.exactBounds().toInclusiveBoundingBox();
            rooms.add(new PlacedDungeonRoom(
                    room.roomId(),
                    room.definition().type(),
                    room.definitionId(),
                    room.definition().template(),
                    room.cellOrigin(),
                    room.templateOrigin(),
                    room.anchor(),
                    room.rotation(),
                    room.mirrored(),
                    bounds,
                    room.ports(),
                    room.roomId().equals(layout.primaryEntryRoomId())
            ));
            union = include(union, bounds);
        }

        List<RoutedDungeonConnection> connections = new ArrayList<>();
        for (ResolvedDungeonConnection connection : layout.connections()) {
            connections.add(new RoutedDungeonConnection(
                    connection.id(),
                    connection.from(),
                    connection.to(),
                    connection.kind(),
                    connection.routeCells()
            ));
            for (DungeonCellPos cell : connection.routeCells()) {
                union = include(union, corridorCellBounds(layout.layoutOrigin(), cell));
            }
        }

        if (union == null) {
            throw new IllegalArgumentException(
                    "Cannot create dungeon generation plan for empty layout"
            );
        }

        return new DungeonGenerationPlan(
                layout.layoutOrigin(),
                inflate(union, SITE_BOUNDS_BUFFER_BLOCKS),
                layout.primaryEntryRoomId(),
                rooms,
                connections
        );
    }

    public static BoundingBox corridorCellBounds(
            BlockPos origin,
            DungeonCellPos cell
    ) {
        int minX = origin.getX() + DungeonLayoutConstants.cellToBlockX(cell.x());
        int minY = origin.getY() + DungeonLayoutConstants.cellToBlockY(cell.y());
        int minZ = origin.getZ() + DungeonLayoutConstants.cellToBlockZ(cell.z());
        return new BoundingBox(
                minX,
                minY,
                minZ,
                minX + DungeonLayoutConstants.CELL_SIZE_X - 1,
                minY + DungeonLayoutConstants.CELL_SIZE_Y - 1,
                minZ + DungeonLayoutConstants.CELL_SIZE_Z - 1
        );
    }

    private static BoundingBox include(
            BoundingBox current,
            BoundingBox next
    ) {
        if (next == null) {
            return current;
        }
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
            BoundingBox box,
            int blocks
    ) {
        DungeonBlockBox exclusive = DungeonBlockBox.fromInclusive(box)
                .expand(blocks);
        return exclusive.toInclusiveBoundingBox();
    }
}
