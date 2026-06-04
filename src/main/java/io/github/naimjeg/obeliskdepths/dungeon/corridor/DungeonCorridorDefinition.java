package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DungeonCorridorDefinition(
        Identifier template,
        DungeonConnectorShapeType shape,
        DungeonRoomFootprint footprint,
        List<RoomConnectorDefinition> ports,
        List<DungeonRoomRotation> allowedRotations,
        boolean allowMirror,
        Optional<Identifier> processorList
) {
    public static final Codec<DungeonCorridorDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("template")
                            .forGetter(DungeonCorridorDefinition::template),
                    DungeonConnectorShapeType.CODEC
                            .fieldOf("shape")
                            .forGetter(DungeonCorridorDefinition::shape),
                    DungeonRoomFootprint.CODEC
                            .optionalFieldOf(
                                    "footprint",
                                    DungeonRoomFootprint.auto()
                            )
                            .forGetter(DungeonCorridorDefinition::footprint),
                    RoomConnectorDefinition.CODEC
                            .listOf()
                            .fieldOf("ports")
                            .forGetter(DungeonCorridorDefinition::ports),
                    DungeonRoomRotation.CODEC
                            .listOf()
                            .optionalFieldOf(
                                    "allowed_rotations",
                                    List.of(DungeonRoomRotation.NONE)
                            )
                            .forGetter(DungeonCorridorDefinition::allowedRotations),
                    Codec.BOOL
                            .optionalFieldOf("allow_mirror", false)
                            .forGetter(DungeonCorridorDefinition::allowMirror),
                    Identifier.CODEC
                            .optionalFieldOf("processor_list")
                            .forGetter(DungeonCorridorDefinition::processorList)
            ).apply(instance, DungeonCorridorDefinition::new));

    public DungeonCorridorDefinition {
        if (template == null) {
            throw new IllegalArgumentException(
                    "Corridor template must not be null"
            );
        }

        if (shape == null) {
            throw new IllegalArgumentException(
                    "Corridor shape must not be null: " + template
            );
        }

        if (footprint == null) {
            footprint = DungeonRoomFootprint.auto();
        }

        ports = ports == null ? List.of() : List.copyOf(ports);
        allowedRotations = allowedRotations == null
                ? List.of()
                : List.copyOf(allowedRotations);
        processorList = processorList == null
                ? Optional.empty()
                : processorList;

        if (ports.isEmpty()) {
            throw new IllegalArgumentException(
                    "Corridor must declare at least one port: " + template
            );
        }

        if (allowedRotations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Corridor must allow at least one rotation: " + template
            );
        }

        Set<String> portIds = new HashSet<>();

        for (RoomConnectorDefinition port : ports) {
            if (port == null) {
                throw new IllegalArgumentException(
                        "Corridor ports must not contain null: " + template
                );
            }

            if (!portIds.add(port.id())) {
                throw new IllegalArgumentException(
                        "Duplicate corridor port id "
                                + port.id()
                                + " in "
                                + template
                );
            }
        }
    }
}
