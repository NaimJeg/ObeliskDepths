package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonCorridor;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometryResolver;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

public record DungeonGenerationCatalog(
        Identifier themeId,
        DungeonThemeDefinition theme,
        Map<Identifier, DungeonRoomDefinition> rooms,
        Map<Identifier, DungeonCorridorDefinition> corridors,
        DungeonTemplateGeometryCatalog geometry
) {
    private static final Map<DungeonContentSnapshot,
            Map<Identifier, Map<Object, DungeonGenerationCatalog>>> CACHE =
            new IdentityHashMap<>();

    public DungeonGenerationCatalog {
        themeId = Objects.requireNonNull(themeId, "themeId");
        theme = Objects.requireNonNull(theme, "theme");
        rooms = immutableCheckedCopy(rooms, "rooms");
        corridors = immutableCheckedCopy(corridors, "corridors");
        geometry = Objects.requireNonNull(geometry, "geometry");
    }

    public static DungeonGenerationCatalog fromSnapshot(
            Identifier themeId,
            DungeonContentSnapshot snapshot,
            DungeonTemplateGeometryResolver geometryResolver
    ) {
        Objects.requireNonNull(geometryResolver, "geometryResolver");
        return cachedCatalog(
                themeId,
                snapshot,
                geometryResolver,
                () -> buildFromResolver(themeId, snapshot, geometryResolver)
        );
    }

    public static DungeonGenerationCatalog fromSnapshot(
            Identifier themeId,
            DungeonContentSnapshot snapshot,
            DungeonTemplateGeometryCatalog geometry
    ) {
        Objects.requireNonNull(geometry, "geometry");
        return cachedCatalog(
                themeId,
                snapshot,
                geometry,
                () -> buildFromGeometryCatalog(themeId, snapshot, geometry)
        );
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static DungeonGenerationCatalog cachedCatalog(
            Identifier themeId,
            DungeonContentSnapshot snapshot,
            Object geometrySource,
            Supplier<DungeonGenerationCatalog> builder
    ) {
        Objects.requireNonNull(themeId, "themeId");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(geometrySource, "geometrySource");
        Objects.requireNonNull(builder, "builder");

        synchronized (CACHE) {
            Map<Identifier, Map<Object, DungeonGenerationCatalog>> byTheme =
                    CACHE.computeIfAbsent(
                            snapshot,
                            ignored -> new LinkedHashMap<>()
                    );
            Map<Object, DungeonGenerationCatalog> byGeometry =
                    byTheme.computeIfAbsent(
                            themeId,
                            ignored -> new IdentityHashMap<>()
                    );
            return byGeometry.computeIfAbsent(
                    geometrySource,
                    ignored -> builder.get()
            );
        }
    }

    private static DungeonGenerationCatalog buildFromResolver(
            Identifier themeId,
            DungeonContentSnapshot snapshot,
            DungeonTemplateGeometryResolver geometryResolver
    ) {
        SelectedContent selected = selectContent(themeId, snapshot);
        DungeonTemplateGeometryCatalog geometry =
                DungeonTemplateGeometryCatalog.fromResolver(
                        selected.templateIds(),
                        geometryResolver,
                        "theme=" + themeId
                );
        return new DungeonGenerationCatalog(
                themeId,
                selected.theme(),
                selected.rooms(),
                selected.corridors(),
                geometry
        );
    }

    private static DungeonGenerationCatalog buildFromGeometryCatalog(
            Identifier themeId,
            DungeonContentSnapshot snapshot,
            DungeonTemplateGeometryCatalog geometry
    ) {
        SelectedContent selected = selectContent(themeId, snapshot);
        for (Identifier templateId : selected.templateIds()) {
            try {
                geometry.resolve(templateId);
            } catch (DungeonContentException exception) {
                throw selectedTemplateGeometryException(
                        templateId,
                        themeId,
                        exception
                );
            }
        }
        return new DungeonGenerationCatalog(
                themeId,
                selected.theme(),
                selected.rooms(),
                selected.corridors(),
                geometry
        );
    }

    private static SelectedContent selectContent(
            Identifier themeId,
            DungeonContentSnapshot snapshot
    ) {
        Objects.requireNonNull(themeId, "themeId");
        Objects.requireNonNull(snapshot, "snapshot");
        DungeonThemeDefinition theme = snapshot.themes().get(themeId);
        if (theme == null) {
            throw missing(themeId, "theme", "building generation catalog");
        }
        if (!theme.enabled()) {
            throw new DungeonContentException(
                    themeId,
                    "disabled theme category=theme context=building generation catalog"
            );
        }

        Map<Identifier, DungeonRoomDefinition> selectedRooms =
                selectRooms(themeId, theme, snapshot.rooms());
        Map<Identifier, DungeonCorridorDefinition> selectedCorridors =
                selectCorridors(themeId, theme, snapshot.corridors());

        Set<Identifier> templateIds = new LinkedHashSet<>();
        selectedRooms.values()
                .stream()
                .map(DungeonRoomDefinition::template)
                .forEach(templateIds::add);
        selectedCorridors.values()
                .stream()
                .map(DungeonCorridorDefinition::template)
                .forEach(templateIds::add);
        return new SelectedContent(
                theme,
                selectedRooms,
                selectedCorridors,
                Collections.unmodifiableSet(templateIds)
        );
    }

    public DungeonRoomDefinition requireRoom(
            Identifier roomId,
            String context
    ) {
        DungeonRoomDefinition definition = this.rooms.get(roomId);
        if (definition == null) {
            throw missing(roomId, "room", contextFor(context));
        }
        return definition;
    }

    public DungeonCorridorDefinition requireCorridor(
            Identifier corridorId,
            String context
    ) {
        DungeonCorridorDefinition definition = this.corridors.get(corridorId);
        if (definition == null) {
            throw missing(corridorId, "corridor", contextFor(context));
        }
        return definition;
    }

    public Identifier selectRoom(
            DungeonRoomType type,
            RandomSource random,
            String context
    ) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(random, "random");
        List<WeightedDungeonRoom> candidates = this.theme.roomsFor(type);
        if (candidates.isEmpty()) {
            throw new DungeonContentException(
                    this.themeId,
                    "missing room pool category=theme roomType="
                            + type.getSerializedName()
                            + " context="
                            + contextFor(context)
            );
        }
        int totalWeight = 0;
        for (WeightedDungeonRoom candidate : candidates) {
            requireRoom(
                    candidate.room(),
                    "theme=" + this.themeId
                            + " roomType=" + type.getSerializedName()
                            + " " + contextFor(context)
            );
            totalWeight += candidate.weight();
        }
        int selected = random.nextInt(totalWeight);
        for (WeightedDungeonRoom candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return candidate.room();
            }
        }
        throw new IllegalStateException(
                "Weighted dungeon room selection failed for " + type
        );
    }

    public Identifier selectCorridor(
            DungeonConnectorShapeType shape,
            RandomSource random,
            String context
    ) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(random, "random");
        List<WeightedDungeonCorridor> candidates =
                this.theme.corridorsFor(shape);
        if (candidates.isEmpty()) {
            throw new DungeonContentException(
                    this.themeId,
                    "missing corridor pool category=theme shape="
                            + shape.getSerializedName()
                            + " context="
                            + contextFor(context)
            );
        }
        int totalWeight = 0;
        for (WeightedDungeonCorridor candidate : candidates) {
            requireCorridor(
                    candidate.corridor(),
                    "theme=" + this.themeId
                            + " shape=" + shape.getSerializedName()
                            + " " + contextFor(context)
            );
            totalWeight += candidate.weight();
        }
        int selected = random.nextInt(totalWeight);
        for (WeightedDungeonCorridor candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) {
                return candidate.corridor();
            }
        }
        throw new IllegalStateException(
                "Weighted dungeon corridor selection failed for " + shape
        );
    }

    private static Map<Identifier, DungeonRoomDefinition> selectRooms(
            Identifier themeId,
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonRoomDefinition> rooms
    ) {
        EnumMap<DungeonRoomType, Boolean> required =
                new EnumMap<>(DungeonRoomType.class);
        Map<Identifier, DungeonRoomDefinition> selected =
                new LinkedHashMap<>();
        for (DungeonRoomType type : DungeonRoomType.values()) {
            required.put(type, false);
        }

        for (Map.Entry<DungeonRoomType, List<WeightedDungeonRoom>> entry
                : theme.roomPools().entrySet()) {
            DungeonRoomType type = entry.getKey();
            if (!entry.getValue().isEmpty()) {
                required.put(type, true);
            }
            for (WeightedDungeonRoom weightedRoom : entry.getValue()) {
                DungeonRoomDefinition room = rooms.get(weightedRoom.room());
                if (room == null) {
                    throw missing(
                            weightedRoom.room(),
                            "room",
                            "theme=" + themeId
                                    + " roomType="
                                    + type.getSerializedName()
                    );
                }
                selected.putIfAbsent(weightedRoom.room(), room);
                if (room.type() != type) {
                    throw new DungeonContentException(
                            weightedRoom.room(),
                            "room type mismatch category=room expected="
                                    + type.getSerializedName()
                                    + " actual="
                                    + room.type().getSerializedName()
                                    + " context=theme="
                                    + themeId
                    );
                }
            }
        }

        for (Map.Entry<DungeonRoomType, Boolean> entry : required.entrySet()) {
            if (!entry.getValue()) {
                throw new DungeonContentException(
                        themeId,
                        "missing room pool category=theme roomType="
                                + entry.getKey().getSerializedName()
                );
            }
        }
        return selected;
    }

    private static Map<Identifier, DungeonCorridorDefinition> selectCorridors(
            Identifier themeId,
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        Map<Identifier, DungeonCorridorDefinition> selected =
                new LinkedHashMap<>();
        for (Map.Entry<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                entry : theme.corridorPools().entrySet()) {
            DungeonConnectorShapeType shape = entry.getKey();
            for (WeightedDungeonCorridor weightedCorridor : entry.getValue()) {
                DungeonCorridorDefinition corridor =
                        corridors.get(weightedCorridor.corridor());
                if (corridor == null) {
                    throw missing(
                            weightedCorridor.corridor(),
                            "corridor",
                            "theme=" + themeId
                                    + " shape="
                                    + shape.getSerializedName()
                    );
                }
                selected.putIfAbsent(weightedCorridor.corridor(), corridor);
                if (corridor.shape() != shape) {
                    throw new DungeonContentException(
                            weightedCorridor.corridor(),
                            "corridor shape mismatch category=corridor expected="
                                    + shape.getSerializedName()
                                    + " actual="
                                    + corridor.shape().getSerializedName()
                                    + " context=theme="
                                    + themeId
                    );
                }
            }
        }
        return selected;
    }

    private static DungeonContentException selectedTemplateGeometryException(
            Identifier templateId,
            Identifier themeId,
            DungeonContentException cause
    ) {
        return new DungeonContentException(
                templateId,
                "missing selected template geometry category=template_geometry"
                        + " requestedId="
                        + templateId
                        + " context=theme="
                        + themeId,
                cause
        );
    }

    private static DungeonContentException missing(
            Identifier id,
            String category,
            String context
    ) {
        return new DungeonContentException(
                id,
                "missing dungeon content category="
                        + category
                        + " requestedId="
                        + id
                        + " context="
                        + contextFor(context)
        );
    }

    private static String contextFor(String context) {
        return context == null || context.isBlank() ? "<none>" : context;
    }

    private static <T> Map<Identifier, T> immutableCheckedCopy(
            Map<Identifier, T> values,
            String label
    ) {
        Objects.requireNonNull(values, label);
        Map<Identifier, T> copy = new LinkedHashMap<>();
        values.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> copy.put(
                        Objects.requireNonNull(
                                entry.getKey(),
                                label + " id"
                        ),
                        Objects.requireNonNull(
                                entry.getValue(),
                                label + " value for " + entry.getKey()
                        )
                ));
        return Collections.unmodifiableMap(copy);
    }

    private record SelectedContent(
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Set<Identifier> templateIds
    ) {
    }
}
