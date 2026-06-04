package io.github.naimjeg.obeliskdepths.dungeon.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.layout.DungeonLayoutDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.template.DungeonTemplateResourceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionValidator;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads authored room, corridor, and theme definitions in one pass so
 * cross-reference validation is atomic. Broken datapacks leave the previous
 * valid snapshot in place.
 */
public final class DungeonContentReloadListener
        implements PreparableReloadListener {
    private static final FileToIdConverter ROOM_LISTER =
            FileToIdConverter.json("dungeon_room");
    private static final FileToIdConverter CORRIDOR_LISTER =
            FileToIdConverter.json("dungeon_corridor");
    private static final FileToIdConverter THEME_LISTER =
            FileToIdConverter.json("dungeon_theme");
    private static final FileToIdConverter LAYOUT_LISTER =
            FileToIdConverter.json("dungeon_layout");

    @Override
    public CompletableFuture<Void> reload(
            SharedState sharedState,
            Executor backgroundExecutor,
            PreparationBarrier barrier,
            Executor gameExecutor
    ) {
        return CompletableFuture
                .supplyAsync(
                        () -> load(sharedState.resourceManager()),
                        backgroundExecutor
                )
                .thenCompose(barrier::wait)
                .thenAcceptAsync(DungeonContentReloadListener::install,
                        gameExecutor);
    }

    public static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes
    ) {
        return validateAndInstall(rooms, corridors, themes, List.of());
    }

    public static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            Map<Identifier, DungeonLayoutDefinition> layouts
    ) {
        return validateAndInstall(rooms, corridors, themes, layouts, List.of());
    }

    static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            List<String> candidateErrors
    ) {
        return validateAndInstall(
                rooms,
                corridors,
                themes,
                Map.of(),
                candidateErrors
        );
    }

    static boolean validateAndInstall(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            Map<Identifier, DungeonLayoutDefinition> layouts,
            List<String> candidateErrors
    ) {
        LoadedContent content = validate(
                rooms,
                corridors,
                themes,
                layouts,
                candidateErrors
        );

        if (!content.errors().isEmpty()) {
            return false;
        }

        install(content);
        return true;
    }

    private static LoadedContent load(ResourceManager manager) {
        ParseResult<DungeonRoomDefinition> rooms = parseDefinitions(
                manager,
                ROOM_LISTER,
                DungeonRoomDefinition.CODEC,
                "dungeon room"
        );
        ParseResult<DungeonCorridorDefinition> corridors = parseDefinitions(
                manager,
                CORRIDOR_LISTER,
                DungeonCorridorDefinition.CODEC,
                "dungeon corridor"
        );
        ParseResult<DungeonThemeDefinition> themes = parseDefinitions(
                manager,
                THEME_LISTER,
                DungeonThemeDefinition.CODEC,
                "dungeon theme"
        );
        ParseResult<DungeonLayoutDefinition> layouts = parseDefinitions(
                manager,
                LAYOUT_LISTER,
                DungeonLayoutDefinition.CODEC,
                "dungeon layout"
        );

        List<String> errors = new ArrayList<>();
        errors.addAll(rooms.errors());
        errors.addAll(corridors.errors());
        errors.addAll(themes.errors());
        errors.addAll(layouts.errors());

        LoadedContent validated = validate(
                rooms.values(),
                corridors.values(),
                themes.values(),
                layouts.values(),
                errors
        );

        if (!validated.errors().isEmpty()) {
            return validated;
        }

        List<String> templateErrors = new ArrayList<>();
        templateErrors.addAll(DungeonTemplateResourceValidator.validateRooms(
                validated.snapshot().rooms(),
                manager
        ));
        templateErrors.addAll(DungeonTemplateResourceValidator.validateCorridors(
                validated.snapshot().corridors(),
                manager
        ));
        templateErrors.addAll(
                DungeonTemplateResourceValidator
                        .validateAllSuppliedTemplatesReferenced(
                                validated.snapshot().rooms(),
                                validated.snapshot().corridors()
                        )
        );

        if (templateErrors.isEmpty()) {
            return validated;
        }

        errors = new ArrayList<>(validated.errors());
        errors.addAll(templateErrors);
        return new LoadedContent(
                validated.snapshot(),
                List.copyOf(errors)
        );
    }

    private static <T> ParseResult<T> parseDefinitions(
            ResourceManager manager,
            FileToIdConverter converter,
            Codec<T> codec,
            String typeName
    ) {
        Map<Identifier, T> result = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, Resource> entry
                : converter.listMatchingResources(manager).entrySet()) {
            Identifier fileId = entry.getKey();
            Identifier definitionId = converter.fileToId(fileId);

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                List<String> parseErrors = new ArrayList<>();
                T parsed = codec.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(parseErrors::add)
                        .orElse(null);

                if (parsed == null) {
                    if (parseErrors.isEmpty()) {
                        parseErrors.add("unknown codec error");
                    }

                    for (String error : parseErrors) {
                        errors.add("failed to parse "
                                + typeName
                                + " "
                                + definitionId
                                + ": "
                                + error);
                    }
                    continue;
                }

                T previous = result.put(definitionId, parsed);
                if (previous != null) {
                    errors.add("duplicate "
                            + typeName
                            + " definition id "
                            + definitionId);
                }
            } catch (Exception exception) {
                errors.add("failed to load "
                        + typeName
                        + " "
                        + definitionId
                        + ": "
                        + exception.getMessage());
            }
        }

        return new ParseResult<>(orderedCopy(result), List.copyOf(errors));
    }

    private static LoadedContent validate(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonThemeDefinition> themes,
            Map<Identifier, DungeonLayoutDefinition> layouts,
            List<String> existingErrors
    ) {
        Map<Identifier, DungeonRoomDefinition> roomCopy = orderedCopy(rooms);
        Map<Identifier, DungeonCorridorDefinition> corridorCopy =
                orderedCopy(corridors);
        Map<Identifier, DungeonThemeDefinition> themeCopy = orderedCopy(themes);
        Map<Identifier, DungeonLayoutDefinition> layoutCopy =
                orderedCopy(layouts);
        List<String> errors = new ArrayList<>(existingErrors);

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : roomCopy.entrySet()) {
            for (String error : DungeonRoomDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue()
            )) {
                errors.add("room " + entry.getKey() + ": " + error);
            }
        }

        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridorCopy.entrySet()) {
            for (String error : DungeonCorridorDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue()
            )) {
                errors.add("corridor " + entry.getKey() + ": " + error);
            }
        }

        for (Map.Entry<Identifier, DungeonThemeDefinition> entry
                : themeCopy.entrySet()) {
            for (String error : DungeonThemeDefinitionValidator.validate(
                    entry.getKey(),
                    entry.getValue(),
                    roomCopy,
                    corridorCopy
            )) {
                errors.add("theme " + entry.getKey() + ": " + error);
            }
        }

        for (Map.Entry<Identifier, DungeonLayoutDefinition> entry
                : layoutCopy.entrySet()) {
            validateLayout(entry.getKey(), entry.getValue(), roomCopy, errors);
        }

        return new LoadedContent(
                new DungeonContentSnapshot(
                        roomCopy,
                        corridorCopy,
                        themeCopy,
                        layoutCopy
                ),
                List.copyOf(errors)
        );
    }

    private static void validateLayout(
            Identifier layoutId,
            DungeonLayoutDefinition layout,
            Map<Identifier, DungeonRoomDefinition> rooms,
            List<String> errors
    ) {
        Map<String, DungeonRoomDefinition> authoredRooms =
                new LinkedHashMap<>();
        for (var room : layout.rooms()) {
            DungeonRoomDefinition definition = rooms.get(room.definitionId());
            if (definition == null) {
                errors.add("layout "
                        + layoutId
                        + ": unknown room definition "
                        + room.definitionId()
                        + " for room "
                        + room.id());
                continue;
            }
            if (!definition.allowedRotations().contains(room.rotation())) {
                errors.add("layout "
                        + layoutId
                        + ": room "
                        + room.id()
                        + " uses unsupported rotation "
                        + room.rotation().getSerializedName());
            }
            if (room.mirror() && !definition.allowMirror()) {
                errors.add("layout "
                        + layoutId
                        + ": room "
                        + room.id()
                        + " uses mirror=true but definition disallows mirroring");
            }
            authoredRooms.put(room.id(), definition);
        }

        for (var connection : layout.connections()) {
            validateLayoutPort(layoutId, connection.id(), connection.from(),
                    authoredRooms, errors);
            validateLayoutPort(layoutId, connection.id(), connection.to(),
                    authoredRooms, errors);
        }
    }

    private static void validateLayoutPort(
            Identifier layoutId,
            String connectionId,
            io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonPortReference reference,
            Map<String, DungeonRoomDefinition> rooms,
            List<String> errors
    ) {
        DungeonRoomDefinition room = rooms.get(reference.roomId());
        if (room == null) {
            errors.add("layout "
                    + layoutId
                    + ": connection "
                    + connectionId
                    + " references unknown room "
                    + reference.roomId());
            return;
        }
        boolean found = room.ports().stream()
                .anyMatch(port -> port.id().equals(reference.portId()));
        if (!found) {
            errors.add("layout "
                    + layoutId
                    + ": connection "
                    + connectionId
                    + " references unknown port "
                    + reference.roomId()
                    + "."
                    + reference.portId());
        }
    }

    private static void install(LoadedContent content) {
        if (!content.errors().isEmpty()) {
            for (String error : content.errors()) {
                ObeliskDepths.LOGGER.error(
                        "Skipping dungeon content reload: {}",
                        error
                );
            }
            throw new IllegalStateException(
                    "Dungeon content reload failed with "
                            + content.errors().size()
                            + " error(s)."
            );
        }

        DungeonContent.install(content.snapshot());
        ObeliskDepths.LOGGER.info(
                "Loaded {} dungeon rooms, {} dungeon corridors, {} dungeon themes, and {} dungeon layouts",
                content.snapshot().rooms().size(),
                content.snapshot().corridors().size(),
                content.snapshot().themes().size(),
                content.snapshot().layouts().size()
        );
    }

    private static <T> Map<Identifier, T> orderedCopy(Map<Identifier, T> input) {
        Objects.requireNonNull(input, "input");

        if (input.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, T> result = new LinkedHashMap<>();
        input.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> result.put(
                        Objects.requireNonNull(entry.getKey(), "content id"),
                        Objects.requireNonNull(entry.getValue(), "content value")
                ));
        return Collections.unmodifiableMap(result);
    }

    private record ParseResult<T>(
            Map<Identifier, T> values,
            List<String> errors
    ) {
    }

    private record LoadedContent(
            DungeonContentSnapshot snapshot,
            List<String> errors
    ) {
    }
}
