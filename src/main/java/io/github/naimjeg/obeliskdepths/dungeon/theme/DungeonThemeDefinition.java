package io.github.naimjeg.obeliskdepths.dungeon.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A weighted grouping of semantic room and corridor definitions. Themes
 * reference definition IDs, never exact physical template IDs.
 */
public record DungeonThemeDefinition(
        Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools,
        Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>> corridorPools,
        Optional<Identifier> defaultProcessorList,
        boolean enabled
) {
    public static final Codec<DungeonThemeDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.unboundedMap(
                                    DungeonRoomType.CODEC,
                                    WeightedDungeonRoom.CODEC.listOf()
                            )
                            .optionalFieldOf("room_pools", Map.of())
                            .forGetter(DungeonThemeDefinition::roomPools),
                    Codec.unboundedMap(
                                    DungeonConnectorShapeType.CODEC,
                                    WeightedDungeonCorridor.CODEC.listOf()
                            )
                            .optionalFieldOf("corridor_pools", Map.of())
                            .forGetter(DungeonThemeDefinition::corridorPools),
                    Identifier.CODEC
                            .optionalFieldOf("default_processor_list")
                            .forGetter(DungeonThemeDefinition::defaultProcessorList),
                    Codec.BOOL
                            .optionalFieldOf("enabled", true)
                            .forGetter(DungeonThemeDefinition::enabled)
            ).apply(instance, DungeonThemeDefinition::new));

    public DungeonThemeDefinition {
        roomPools = copyRoomPools(roomPools);
        corridorPools = copyCorridorPools(corridorPools);
        defaultProcessorList = defaultProcessorList == null
                ? Optional.empty()
                : defaultProcessorList;
    }

    public List<WeightedDungeonRoom> roomsFor(DungeonRoomType type) {
        if (type == null) {
            return List.of();
        }

        return this.roomPools.getOrDefault(type, List.of());
    }

    public List<WeightedDungeonCorridor> corridorsFor(
            DungeonConnectorShapeType shape
    ) {
        if (shape == null) {
            return List.of();
        }

        return this.corridorPools.getOrDefault(shape, List.of());
    }

    private static Map<DungeonRoomType, List<WeightedDungeonRoom>> copyRoomPools(
            Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools
    ) {
        if (roomPools == null || roomPools.isEmpty()) {
            return Map.of();
        }

        EnumMap<DungeonRoomType, List<WeightedDungeonRoom>> copy =
                new EnumMap<>(DungeonRoomType.class);

        for (Map.Entry<DungeonRoomType, List<WeightedDungeonRoom>> entry
                : roomPools.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Dungeon theme room pools must not contain null"
                );
            }

            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Collections.unmodifiableMap(copy);
    }

    private static Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
    copyCorridorPools(
            Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                    corridorPools
    ) {
        if (corridorPools == null || corridorPools.isEmpty()) {
            return Map.of();
        }

        EnumMap<DungeonConnectorShapeType, List<WeightedDungeonCorridor>> copy =
                new EnumMap<>(DungeonConnectorShapeType.class);

        for (Map.Entry<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                entry : corridorPools.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException(
                        "Dungeon theme corridor pools must not contain null"
                );
            }

            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Collections.unmodifiableMap(copy);
    }
}
