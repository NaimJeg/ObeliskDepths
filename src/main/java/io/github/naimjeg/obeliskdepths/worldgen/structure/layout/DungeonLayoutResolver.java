package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.layout.AuthoredDungeonConnection;
import io.github.naimjeg.obeliskdepths.dungeon.layout.AuthoredDungeonRoom;
import io.github.naimjeg.obeliskdepths.dungeon.layout.DungeonLayoutDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonCorridorRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DungeonLayoutResolver {
    private DungeonLayoutResolver() {
    }

    public static ResolvedDungeonLayout resolveProcedural(
            BlockPos layoutOrigin,
            DungeonLayoutPlan layout,
            String primaryEntryRoomId,
            DungeonGenerationCatalog catalog,
            DungeonWorldgenTrace.Context traceContext
    ) {
        Map<String, List<IncidentEdge>> incidents = incidentEdges(layout);
        Map<String, Map<String, String>> assignedPortsByRoom =
                new LinkedHashMap<>();
        List<ResolvedDungeonRoom> rooms = new ArrayList<>();

        for (DungeonLayoutNode node : layout.nodes()) {
            Identifier definitionId = node.definitionId();
            DungeonRoomDefinition definition = catalog.requireRoom(
                    definitionId,
                    "procedural layout room="
                            + node.roomId()
                            + " type="
                            + node.type().getSerializedName()
                            + " theme="
                            + catalog.themeId()
            );
            DungeonTemplateGeometry geometry =
                    catalog.geometry().resolve(definition.template());
            TransformAssignment assignment = chooseProceduralTransform(
                    node,
                    definitionId,
                    definition,
                    geometry,
                    incidents.getOrDefault(node.roomId(), List.of())
            );
            assignedPortsByRoom.put(node.roomId(), assignment.edgePortIds());
            rooms.add(resolveRoom(
                    node.roomId(),
                    definitionId,
                    definition,
                    node.cellOrigin(),
                    assignment.rotation(),
                    assignment.mirror(),
                    geometry,
                    layoutOrigin
            ));
        }

        validateRoomBounds(rooms);
        List<ResolvedDungeonConnection> connections = new ArrayList<>();
        for (DungeonLayoutEdge edge : layout.edges()) {
            String fromPortId = assignedPortsByRoom
                    .getOrDefault(edge.fromRoomId(), Map.of())
                    .get(edge.id());
            String toPortId = assignedPortsByRoom
                    .getOrDefault(edge.toRoomId(), Map.of())
                    .get(edge.id());
            if (fromPortId == null || toPortId == null) {
                throw new DungeonLayoutGenerationException(
                        "Procedural edge did not receive exact named ports: "
                                + edge.id()
                                + " from="
                                + edge.fromRoomId()
                                + " to="
                                + edge.toRoomId()
                );
            }
            DungeonPortReference fromReference =
                    new DungeonPortReference(edge.fromRoomId(), fromPortId);
            DungeonPortReference toReference =
                    new DungeonPortReference(edge.toRoomId(), toPortId);
            connections.add(new ResolvedDungeonConnection(
                    edge.id(),
                    fromReference,
                    toReference,
                    edge.kind(),
                    edge.plannedPath()
            ));
        }

        ResolvedDungeonLayout unrouted = new ResolvedDungeonLayout(
                layoutOrigin,
                primaryEntryRoomId,
                rooms,
                connections
        );
        validatePortConnections(unrouted);
        return DungeonCorridorRouter.route(unrouted, catalog, traceContext);
    }

    public static ResolvedDungeonLayout resolveAuthored(
            BlockPos layoutOrigin,
            DungeonLayoutDefinition definition,
            DungeonGenerationCatalog catalog,
            DungeonWorldgenTrace.Context traceContext
    ) {
        Map<String, ResolvedDungeonRoom> roomsById = new LinkedHashMap<>();

        for (AuthoredDungeonRoom room : definition.rooms()) {
            DungeonRoomDefinition roomDefinition = catalog.requireRoom(
                    room.definitionId(),
                    "authored layout room="
                            + room.id()
                            + " layoutPrimaryEntry="
                            + definition.primaryEntryRoomId()
                            + " theme="
                            + catalog.themeId()
            );
            if (!roomDefinition.allowedRotations().contains(room.rotation())) {
                throw new IllegalArgumentException(
                        "Authored room uses unsupported rotation: room="
                                + room.id()
                                + " definition="
                                + room.definitionId()
                                + " rotation="
                                + room.rotation().getSerializedName()
                );
            }
            if (room.mirror() && !roomDefinition.allowMirror()) {
                throw new IllegalArgumentException(
                        "Authored room uses mirror=true but definition does not allow mirror: room="
                                + room.id()
                                + " definition="
                                + room.definitionId()
                );
            }
            DungeonTemplateGeometry geometry =
                    catalog.geometry().resolve(roomDefinition.template());
            ResolvedDungeonRoom resolved = resolveRoom(
                    room.id(),
                    room.definitionId(),
                    roomDefinition,
                    room.cellOrigin(),
                    room.rotation(),
                    room.mirror(),
                    geometry,
                    layoutOrigin
            );
            roomsById.put(room.id(), resolved);
        }

        List<ResolvedDungeonRoom> rooms = List.copyOf(roomsById.values());
        validateRoomBounds(rooms);

        List<ResolvedDungeonConnection> connections = new ArrayList<>();
        Set<DungeonPortReference> usedPorts = new HashSet<>();
        for (AuthoredDungeonConnection connection : definition.connections()) {
            requireAuthoredPort(roomsById, connection.from(), connection.id());
            requireAuthoredPort(roomsById, connection.to(), connection.id());
            if (!usedPorts.add(connection.from())) {
                throw new IllegalArgumentException(
                        "Authored layout reuses from port without support: "
                                + connection.id()
                                + " port="
                                + connection.from()
                );
            }
            if (!usedPorts.add(connection.to())) {
                throw new IllegalArgumentException(
                        "Authored layout reuses to port without support: "
                                + connection.id()
                                + " port="
                                + connection.to()
                );
            }
            connections.add(new ResolvedDungeonConnection(
                    connection.id(),
                    connection.from(),
                    connection.to(),
                    connection.kind(),
                    connection.path()
            ));
        }

        ResolvedDungeonLayout unrouted = new ResolvedDungeonLayout(
                layoutOrigin,
                definition.primaryEntryRoomId(),
                rooms,
                connections
        );
        validatePortConnections(unrouted);
        return DungeonCorridorRouter.route(unrouted, catalog, traceContext);
    }

    public static ResolvedDungeonRoom resolveRoomForTests(
            String roomId,
            Identifier definitionId,
            DungeonRoomDefinition definition,
            DungeonCellPos cellOrigin,
            DungeonRoomRotation rotation,
            boolean mirror,
            DungeonTemplateGeometry geometry,
            BlockPos layoutOrigin
    ) {
        return resolveRoom(
                roomId,
                definitionId,
                definition,
                cellOrigin,
                rotation,
                mirror,
                geometry,
                layoutOrigin
        );
    }

    private static ResolvedDungeonRoom resolveRoom(
            String roomId,
            Identifier definitionId,
            DungeonRoomDefinition definition,
            DungeonCellPos cellOrigin,
            DungeonRoomRotation rotation,
            boolean mirror,
            DungeonTemplateGeometry geometry,
            BlockPos layoutOrigin
    ) {
        DungeonRoomFootprint footprint =
                DungeonLayoutTransforms.resolveFootprint(
                        definition.footprint(),
                        geometry,
                        rotation,
                        mirror
                );
        DungeonTemplateGeometry transformedGeometry =
                geometry.transformed(rotation);
        BlockPos cellBlockOrigin = layoutOrigin.offset(
                DungeonLayoutConstants.cellToBlockX(cellOrigin.x()),
                DungeonLayoutConstants.cellToBlockY(cellOrigin.y()),
                DungeonLayoutConstants.cellToBlockZ(cellOrigin.z())
        );
        BlockPos templateOrigin = cellBlockOrigin.offset(
                definition.templateOffset()
        );
        BlockPos transformedAnchor =
                DungeonLayoutTransforms.transformBlockPoint(
                        definition.anchor(),
                        geometry,
                        rotation,
                        mirror
                );
        BlockPos anchor = templateOrigin.offset(transformedAnchor);
        DungeonBlockBox exactBounds = transformedGeometry.boxAt(templateOrigin);
        List<ResolvedDungeonPort> ports = transformPorts(
                definition,
                geometry,
                rotation,
                mirror
        );

        return new ResolvedDungeonRoom(
                roomId,
                definitionId,
                definition,
                cellOrigin,
                templateOrigin,
                anchor,
                rotation,
                mirror,
                geometry,
                transformedGeometry,
                footprint,
                exactBounds,
                ports
        );
    }

    private static List<ResolvedDungeonPort> transformPorts(
            DungeonRoomDefinition definition,
            DungeonTemplateGeometry geometry,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonRoomFootprint baseFootprint =
                definition.footprint().isAuto()
                        ? DungeonRoomFootprint.rectangular(
                                geometry.routingCellsX(),
                                geometry.routingCellsY(),
                                geometry.routingCellsZ()
                        )
                        : definition.footprint();
        List<ResolvedDungeonPort> ports = new ArrayList<>();
        for (RoomConnectorDefinition port : definition.ports()) {
            ports.add(new ResolvedDungeonPort(
                    port.id(),
                    DungeonLayoutTransforms.transformSide(
                            port.facing(),
                            rotation,
                            mirror
                    ),
                    DungeonLayoutTransforms.transformCell(
                            port.boundaryCell(),
                            baseFootprint.widthCells(),
                            baseFootprint.depthCells(),
                            rotation,
                            mirror
                    ),
                    DungeonLayoutTransforms.transformOpeningMin(
                            port,
                            geometry,
                            rotation,
                            mirror
                    ),
                    port.connectorType(),
                    port.widthBlocks(),
                    port.heightBlocks(),
                    port.required()
            ));
        }
        return List.copyOf(ports);
    }

    private static TransformAssignment chooseProceduralTransform(
            DungeonLayoutNode node,
            Identifier definitionId,
            DungeonRoomDefinition definition,
            DungeonTemplateGeometry geometry,
            List<IncidentEdge> incidents
    ) {
        List<String> diagnostics = new ArrayList<>();
        for (DungeonRoomRotation rotation : definition.allowedRotations()) {
            List<Boolean> mirrorChoices = definition.allowMirror()
                    ? List.of(false, true)
                    : List.of(false);
            for (boolean mirror : mirrorChoices) {
                List<ResolvedDungeonPort> ports = transformPorts(
                        definition,
                        geometry,
                        rotation,
                        mirror
                );
                Optional<Map<String, String>> assigned = assignPorts(
                        node,
                        definition,
                        incidents,
                        ports
                );
                if (assigned.isPresent()) {
                    return new TransformAssignment(
                            rotation,
                            mirror,
                            assigned.get()
                    );
                }
                diagnostics.add("rotation="
                        + rotation.getSerializedName()
                        + " mirror="
                        + mirror
                        + " available="
                        + describePorts(ports));
            }
        }

        throw new DungeonLayoutGenerationException(
                "Cannot resolve exact-facing ports for procedural room "
                        + node.roomId()
                        + " definition="
                        + definitionId
                        + " type="
                        + node.type()
                        + " requestedEdges="
                        + incidents.stream()
                                .map(IncidentEdge::describe)
                                .toList()
                        + " attempts="
                        + diagnostics
        );
    }

    private static Optional<Map<String, String>> assignPorts(
            DungeonLayoutNode node,
            DungeonRoomDefinition definition,
            List<IncidentEdge> incidents,
            List<ResolvedDungeonPort> ports
    ) {
        Map<String, String> assigned = new LinkedHashMap<>();
        Set<String> usedPorts = new HashSet<>();
        List<ResolvedDungeonPort> orderedPorts = ports.stream()
                .filter(port -> !port.facing().vertical())
                .sorted(Comparator.comparing(ResolvedDungeonPort::id))
                .toList();

        for (IncidentEdge incident : incidents) {
            if (!supportsEdgeKind(definition, incident.kind())) {
                return Optional.empty();
            }
            ResolvedDungeonPort selected = null;
            List<ResolvedDungeonPort> preferredPorts = orderedPorts.stream()
                    .filter(port -> !usedPorts.contains(port.id()))
                    .filter(port -> port.facing() == incident.side())
                    .sorted(portAssignmentOrder(node, incident))
                    .toList();
            for (ResolvedDungeonPort port : preferredPorts) {
                selected = port;
                break;
            }
            if (selected == null) {
                return Optional.empty();
            }
            usedPorts.add(selected.id());
            assigned.put(incident.edgeId(), selected.id());
        }

        if (incidents.size() > ports.size()) {
            return Optional.empty();
        }

        return Optional.of(Map.copyOf(assigned));
    }

    private static Comparator<ResolvedDungeonPort> portAssignmentOrder(
            DungeonLayoutNode node,
            IncidentEdge incident
    ) {
        return Comparator
                .comparingInt((ResolvedDungeonPort port) ->
                        portEndpointDistance(node, incident, port))
                .thenComparing(ResolvedDungeonPort::id);
    }

    private static int portEndpointDistance(
            DungeonLayoutNode node,
            IncidentEdge incident,
            ResolvedDungeonPort port
    ) {
        if (incident.plannedEndpoint() == null) {
            return 0;
        }
        DungeonCellPos outside = port.outsideCell(node.cellOrigin());
        return Math.abs(outside.x() - incident.plannedEndpoint().x())
                + Math.abs(outside.y() - incident.plannedEndpoint().y())
                + Math.abs(outside.z() - incident.plannedEndpoint().z());
    }

    private static boolean supportsEdgeKind(
            DungeonRoomDefinition definition,
            DungeonGraphEdgeKind kind
    ) {
        return switch (kind) {
            case TREE -> definition.supportsTreeEdges();
            case LOOP -> definition.supportsLoopEdges();
            case SECRET -> definition.supportsSecretEdges();
        };
    }

    private static Map<String, List<IncidentEdge>> incidentEdges(
            DungeonLayoutPlan layout
    ) {
        Map<String, List<IncidentEdge>> incidents = new LinkedHashMap<>();
        for (DungeonLayoutNode node : layout.nodes()) {
            incidents.put(node.roomId(), new ArrayList<>());
        }
        for (DungeonLayoutEdge edge : layout.edges()) {
            incidents.get(edge.fromRoomId()).add(new IncidentEdge(
                    edge.id(),
                    edge.kind(),
                    edge.fromSide(),
                    edge.plannedPath().isEmpty()
                            ? null
                            : edge.plannedPath().getFirst()
            ));
            incidents.get(edge.toRoomId()).add(new IncidentEdge(
                    edge.id(),
                    edge.kind(),
                    edge.toSide(),
                    edge.plannedPath().isEmpty()
                            ? null
                            : edge.plannedPath().getLast()
            ));
        }
        return Map.copyOf(incidents);
    }

    private static void requireAuthoredPort(
            Map<String, ResolvedDungeonRoom> roomsById,
            DungeonPortReference reference,
            String connectionId
    ) {
        ResolvedDungeonRoom room = roomsById.get(reference.roomId());
        if (room == null) {
            throw new IllegalArgumentException(
                    "Authored connection references unknown room: connection="
                            + connectionId
                            + " room="
                            + reference.roomId()
            );
        }
        if (room.port(reference.portId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "Authored connection references unknown port: connection="
                            + connectionId
                            + " room="
                            + reference.roomId()
                            + " port="
                            + reference.portId()
                            + " definition="
                            + room.definitionId()
            );
        }
    }

    private static void validateRoomBounds(List<ResolvedDungeonRoom> rooms) {
        for (int i = 0; i < rooms.size(); i++) {
            ResolvedDungeonRoom first = rooms.get(i);
            for (int j = i + 1; j < rooms.size(); j++) {
                ResolvedDungeonRoom second = rooms.get(j);
                if (first.exactBounds().intersects(second.exactBounds())) {
                    throw new IllegalArgumentException(
                            "Resolved room physical bounds overlap: first="
                                    + first.roomId()
                                    + " firstDefinition="
                                    + first.definitionId()
                                    + " firstBounds="
                                    + first.exactBounds()
                                    + " second="
                                    + second.roomId()
                                    + " secondDefinition="
                                    + second.definitionId()
                                    + " secondBounds="
                                    + second.exactBounds()
                    );
                }
            }
        }
    }

    private static void validatePortConnections(
            ResolvedDungeonLayout layout
    ) {
        for (ResolvedDungeonConnection connection : layout.connections()) {
            ResolvedDungeonPort from = layout.requirePort(connection.from());
            ResolvedDungeonPort to = layout.requirePort(connection.to());
            if (!from.connectorType().equals(to.connectorType())) {
                throw new IllegalArgumentException(
                        "Resolved connection has incompatible connector types: "
                                + connection.id()
                                + " from="
                                + connection.from()
                                + " type="
                                + from.connectorType()
                                + " to="
                                + connection.to()
                                + " type="
                                + to.connectorType()
                );
            }
            if (from.widthBlocks() != to.widthBlocks()
                    || from.heightBlocks() != to.heightBlocks()) {
                throw new IllegalArgumentException(
                        "Resolved connection has incompatible opening sizes: "
                                + connection.id()
                                + " from="
                                + from.widthBlocks()
                                + "x"
                                + from.heightBlocks()
                                + " to="
                                + to.widthBlocks()
                                + "x"
                                + to.heightBlocks()
                );
            }
        }
    }

    private static List<String> describePorts(List<ResolvedDungeonPort> ports) {
        return ports.stream()
                .map(port -> port.id()
                        + ":"
                        + port.facing().getSerializedName()
                        + "@"
                        + port.boundaryCell())
                .toList();
    }

    private record IncidentEdge(
            String edgeId,
            DungeonGraphEdgeKind kind,
            DungeonConnectorSide side,
            DungeonCellPos plannedEndpoint
    ) {
        private String describe() {
            return this.edgeId
                    + ":"
                    + this.kind
                    + ":"
                    + this.side.getSerializedName()
                    + "@"
                    + this.plannedEndpoint;
        }
    }

    private record TransformAssignment(
            DungeonRoomRotation rotation,
            boolean mirror,
            Map<String, String> edgePortIds
    ) {
        private TransformAssignment {
            edgePortIds = Map.copyOf(edgePortIds);
        }
    }
}
