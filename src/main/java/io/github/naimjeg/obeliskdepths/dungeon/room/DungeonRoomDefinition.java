package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Authored room asset contract loaded from JSON. The footprint is optional
 * routing-cell metadata only; exact physical dimensions are resolved from the
 * referenced NBT template during generation and validation.
 */
public record DungeonRoomDefinition(
        Identifier template,
        DungeonRoomType type,
        DungeonRoomFootprint footprint,
        BlockPos templateOffset,
        BlockPos anchor,
        List<RoomConnectorDefinition> ports,
        List<DungeonRoomRotation> allowedRotations,
        boolean allowMirror,
        Optional<Identifier> encounter,
        Optional<Identifier> lootTable,
        int minGraphDepth,
        int maxGraphDepth,
        boolean supportsTreeEdges,
        boolean supportsLoopEdges,
        boolean supportsSecretEdges
) {
    public static final Codec<DungeonRoomDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("template")
                            .forGetter(DungeonRoomDefinition::template),
                    DungeonRoomType.CODEC
                            .fieldOf("type")
                            .forGetter(DungeonRoomDefinition::type),
                    DungeonRoomFootprint.CODEC
                            .optionalFieldOf(
                                    "footprint",
                                    DungeonRoomFootprint.auto()
                            )
                            .forGetter(DungeonRoomDefinition::footprint),
                    BlockPos.CODEC
                            .optionalFieldOf("template_offset", BlockPos.ZERO)
                            .forGetter(DungeonRoomDefinition::templateOffset),
                    BlockPos.CODEC
                            .fieldOf("anchor")
                            .forGetter(DungeonRoomDefinition::anchor),
                    RoomConnectorDefinition.CODEC
                            .listOf()
                            .optionalFieldOf("ports", List.of())
                            .forGetter(DungeonRoomDefinition::ports),
                    DungeonRoomRotation.CODEC
                            .listOf()
                            .optionalFieldOf(
                                    "allowed_rotations",
                                    List.of(DungeonRoomRotation.NONE)
                            )
                            .forGetter(DungeonRoomDefinition::allowedRotations),
                    Codec.BOOL
                            .optionalFieldOf("allow_mirror", false)
                            .forGetter(DungeonRoomDefinition::allowMirror),
                    Identifier.CODEC
                            .optionalFieldOf("encounter")
                            .forGetter(DungeonRoomDefinition::encounter),
                    Identifier.CODEC
                            .optionalFieldOf("loot_table")
                            .forGetter(DungeonRoomDefinition::lootTable),
                    Codec.INT
                            .optionalFieldOf("min_graph_depth", 0)
                            .forGetter(DungeonRoomDefinition::minGraphDepth),
                    Codec.INT
                            .optionalFieldOf("max_graph_depth", Integer.MAX_VALUE)
                            .forGetter(DungeonRoomDefinition::maxGraphDepth),
                    Codec.BOOL
                            .optionalFieldOf("supports_tree_edges", true)
                            .forGetter(DungeonRoomDefinition::supportsTreeEdges),
                    Codec.BOOL
                            .optionalFieldOf("supports_loop_edges", true)
                            .forGetter(DungeonRoomDefinition::supportsLoopEdges),
                    Codec.BOOL
                            .optionalFieldOf("supports_secret_edges", false)
                            .forGetter(DungeonRoomDefinition::supportsSecretEdges)
            ).apply(instance, DungeonRoomDefinition::new));

    public DungeonRoomDefinition {
        if (template == null) {
            throw new IllegalArgumentException("Room template must not be null");
        }

        if (type == null) {
            throw new IllegalArgumentException("Room type must not be null");
        }

        if (footprint == null) {
            footprint = DungeonRoomFootprint.auto();
        }

        if (templateOffset == null) {
            throw new IllegalArgumentException(
                    "Room templateOffset must not be null"
            );
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Room anchor must not be null");
        }

        ports = ports == null ? List.of() : List.copyOf(ports);
        allowedRotations = allowedRotations == null
                ? List.of()
                : List.copyOf(allowedRotations);
        encounter = encounter == null ? Optional.empty() : encounter;
        lootTable = lootTable == null ? Optional.empty() : lootTable;

        if (allowedRotations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Room must allow at least one rotation: " + template
            );
        }

        Set<String> portIds = new HashSet<>();

        for (RoomConnectorDefinition port : ports) {
            if (port == null) {
                throw new IllegalArgumentException(
                        "Room ports must not contain null: " + template
                );
            }

            if (!portIds.add(port.id())) {
                throw new IllegalArgumentException(
                        "Duplicate room port id " + port.id() + " in " + template
                );
            }
        }

        if (minGraphDepth < 0) {
            throw new IllegalArgumentException(
                    "Room minGraphDepth must be non-negative: " + template
            );
        }

        if (maxGraphDepth < minGraphDepth) {
            throw new IllegalArgumentException(
                    "Room maxGraphDepth must be >= minGraphDepth: " + template
            );
        }
    }
}
