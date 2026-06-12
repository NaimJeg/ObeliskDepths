package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonSpatialLayoutValidator {
    private DungeonSpatialLayoutValidator() {
    }

    public static void validate(DungeonLayoutPlan plan) {
        plan.validateSpatial();
        validateConnectorDirections(plan);
    }

    public static void validatePieceBounds(
            DungeonLayoutPlan plan,
            BoundingBox siteBounds,
            List<BoundingBox> pieceBounds,
            BlockPos startRoomAnchor
    ) {
        for (BoundingBox bounds : pieceBounds) {
            requireContains(siteBounds, bounds, "piece " + bounds);
        }

        DungeonLayoutNode start = plan.nodes()
                .stream()
                .filter(node -> node.type() == io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedded layout missing START room"));

        BoundingBox startBounds = pieceBounds.stream()
                .filter(bounds -> bounds.isInside(startRoomAnchor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Start-room anchor is outside generated piece bounds: " + startRoomAnchor
                ));

        if (!startBounds.isInside(startRoomAnchor)) {
            throw new IllegalArgumentException(
                    "Start-room anchor is outside start-room bounds: "
                            + start.roomId()
                            + " anchor="
                            + startRoomAnchor
                            + " bounds="
                            + startBounds
            );
        }
    }

    private static void validateConnectorDirections(DungeonLayoutPlan plan) {
        for (DungeonLayoutEdge edge : plan.edges()) {
            DungeonLayoutNode source = plan.requireNode(edge.fromRoomId());
            DungeonLayoutNode target = plan.requireNode(edge.toRoomId());
            DungeonConnectorSide expected = connectorSide(source, target);

            if (edge.fromSide() != expected || edge.toSide() != expected.opposite()) {
                throw new IllegalArgumentException(
                        "Embedded corridor connector directions are invalid: "
                                + edge.id()
                                + " expected="
                                + expected
                                + "/"
                                + expected.opposite()
                                + " actual="
                                + edge.fromSide()
                                + "/"
                                + edge.toSide()
                );
            }
        }
    }

    private static DungeonConnectorSide connectorSide(
            DungeonLayoutNode source,
            DungeonLayoutNode target
    ) {
        int sourceCenterX = source.cellOrigin().x() + source.footprint().widthCells() / 2;
        int sourceCenterZ = source.cellOrigin().z() + source.footprint().depthCells() / 2;
        int targetCenterX = target.cellOrigin().x() + target.footprint().widthCells() / 2;
        int targetCenterZ = target.cellOrigin().z() + target.footprint().depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? DungeonConnectorSide.EAST : DungeonConnectorSide.WEST;
        }

        return dz >= 0 ? DungeonConnectorSide.SOUTH : DungeonConnectorSide.NORTH;
    }

    private static void requireContains(
            BoundingBox outer,
            BoundingBox inner,
            String label
    ) {
        if (inner.minX() < outer.minX()
                || inner.maxX() > outer.maxX()
                || inner.minY() < outer.minY()
                || inner.maxY() > outer.maxY()
                || inner.minZ() < outer.minZ()
                || inner.maxZ() > outer.maxZ()) {
            throw new IllegalArgumentException(
                    "Dungeon site bounds do not contain " + label + ": site=" + outer + ", inner=" + inner
            );
        }
    }
}
