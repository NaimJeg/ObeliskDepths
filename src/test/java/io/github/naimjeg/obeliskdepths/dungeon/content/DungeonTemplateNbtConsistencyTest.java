package io.github.naimjeg.obeliskdepths.dungeon.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.template.DungeonTemplateResourceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemeDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

public final class DungeonTemplateNbtConsistencyTest {
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path GENERATED_DATA =
            Path.of("src/generated/resources/data/obeliskdepths");

    private DungeonTemplateNbtConsistencyTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<Identifier, DungeonRoomDefinition> javaRooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> javaCorridors =
                BuiltinDungeonCorridorDefinitions.all();
        Map<Identifier, DungeonThemeDefinition> javaThemes =
                BuiltinDungeonThemeDefinitions.all();

        Map<Identifier, DungeonRoomDefinition> jsonRooms = readDefinitions(
                GENERATED_DATA.resolve("dungeon_room"),
                DungeonRoomDefinition.CODEC
        );
        Map<Identifier, DungeonCorridorDefinition> jsonCorridors = readDefinitions(
                GENERATED_DATA.resolve("dungeon_corridor"),
                DungeonCorridorDefinition.CODEC
        );
        Map<Identifier, DungeonThemeDefinition> jsonThemes = readDefinitions(
                GENERATED_DATA.resolve("dungeon_theme"),
                DungeonThemeDefinition.CODEC
        );

        List<String> errors = new ArrayList<>();
        assertMapEquals(javaRooms, jsonRooms, "rooms", errors);
        assertMapEquals(javaCorridors, jsonCorridors, "corridors", errors);
        assertMapEquals(javaThemes, jsonThemes, "themes", errors);

        Map<Identifier, List<String>> references = references(
                javaRooms,
                javaCorridors,
                jsonRooms,
                jsonCorridors
        );
        BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES.forEach(template ->
                references.computeIfAbsent(template, ignored -> new ArrayList<>())
                        .add("java:ALL_SUPPLIED_TEMPLATES"));

        Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit> audits =
                new TreeMap<>(Comparator.comparing(Identifier::toString));
        for (Map.Entry<Identifier, List<String>> entry : references.entrySet()) {
            DungeonTemplateResourceValidator.TemplateAudit audit =
                    DungeonTemplateResourceValidator.readTemplateAudit(
                            MAIN_RESOURCES,
                            entry.getKey(),
                            entry.getValue()
                    );
            audits.put(entry.getKey(), audit);
            if (!audit.exists()) {
                errors.add("template=" + entry.getKey()
                        + " resource=" + audit.resourcePath()
                        + " references=" + audit.sourceReferences()
                        + " reason=missing nbt");
                continue;
            }
            if (audit.size() == null) {
                errors.add("template=" + entry.getKey()
                        + " resource=" + audit.resourcePath()
                        + " reason=missing size");
            }
        }

        validateDefinitions("java room", javaRooms, audits, errors);
        validateDefinitions("json room", jsonRooms, audits, errors);
        validateCorridorDefinitions("java corridor", javaCorridors, audits, errors);
        validateCorridorDefinitions("json corridor", jsonCorridors, audits, errors);
        validateAllSuppliedTemplatesReferenced(javaRooms, javaCorridors, errors);
        warnUnusedDungeonNbts(audits);
        printRoomAudit(audits, javaRooms);
        printCorridorAudit(audits, javaCorridors);

        if (!errors.isEmpty()) {
            throw new AssertionError(
                    "Dungeon template NBT/source consistency failed:\n"
                            + String.join("\n", errors)
            );
        }
    }

    private static Map<Identifier, List<String>> references(
            Map<Identifier, DungeonRoomDefinition> javaRooms,
            Map<Identifier, DungeonCorridorDefinition> javaCorridors,
            Map<Identifier, DungeonRoomDefinition> jsonRooms,
            Map<Identifier, DungeonCorridorDefinition> jsonCorridors
    ) {
        Map<Identifier, List<String>> references = new LinkedHashMap<>();
        javaRooms.forEach((id, room) -> addReference(
                references,
                room.template(),
                "java-room:" + id
        ));
        javaCorridors.forEach((id, corridor) -> addReference(
                references,
                corridor.template(),
                "java-corridor:" + id
        ));
        jsonRooms.forEach((id, room) -> addReference(
                references,
                room.template(),
                "json-room:" + id
        ));
        jsonCorridors.forEach((id, corridor) -> addReference(
                references,
                corridor.template(),
                "json-corridor:" + id
        ));
        return references;
    }

    private static void addReference(
            Map<Identifier, List<String>> references,
            Identifier template,
            String source
    ) {
        references.computeIfAbsent(template, ignored -> new ArrayList<>())
                .add(source);
    }

    private static void validateDefinitions(
            String label,
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit> audits,
            List<String> errors
    ) {
        rooms.forEach((id, room) -> {
            DungeonTemplateResourceValidator.TemplateAudit audit =
                    audits.get(room.template());
            if (audit == null || audit.size() == null) {
                return;
            }
            errors.addAll(DungeonTemplateResourceValidator
                    .validateRoom(id, room, audit.size())
                    .stream()
                    .map(error -> label + " " + error)
                    .toList());
            validateBoundaryCells(label, id, room.ports(), errors);
        });
    }

    private static void validateCorridorDefinitions(
            String label,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit> audits,
            List<String> errors
    ) {
        corridors.forEach((id, corridor) -> {
            DungeonTemplateResourceValidator.TemplateAudit audit =
                    audits.get(corridor.template());
            if (audit == null || audit.size() == null) {
                return;
            }
            errors.addAll(DungeonTemplateResourceValidator
                    .validateCorridor(id, corridor, audit.size())
                    .stream()
                    .map(error -> label + " " + error)
                    .toList());
            validateBoundaryCells(label, id, corridor.ports(), errors);
        });
    }

    private static void validateBoundaryCells(
            String label,
            Identifier id,
            List<RoomConnectorDefinition> ports,
            List<String> errors
    ) {
        for (RoomConnectorDefinition port : ports) {
            var expected = RoomConnectorDefinition.boundaryCellForOpening(
                    port.openingMin()
            );
            if (!expected.equals(port.boundaryCell())) {
                errors.add(label
                        + " "
                        + id
                        + " port="
                        + port.id()
                        + " boundaryCell="
                        + port.boundaryCell()
                        + " expected="
                        + expected
                        + " reason=boundary cell must be derived from opening_min and the 8x8x8 routing grid");
            }
        }
    }

    private static void validateAllSuppliedTemplatesReferenced(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            List<String> errors
    ) {
        errors.addAll(DungeonTemplateResourceValidator
                .validateAllSuppliedTemplatesReferenced(rooms, corridors));
    }

    private static void warnUnusedDungeonNbts(
            Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit>
                    referencedAudits
    ) throws IOException {
        Path structureRoot = MAIN_RESOURCES
                .resolve("data")
                .resolve("obeliskdepths")
                .resolve("structure");
        if (!Files.exists(structureRoot)) {
            return;
        }
        List<Identifier> referenced = referencedAudits.keySet().stream().toList();
        try (var paths = Files.walk(structureRoot)) {
            paths.filter(path -> path.toString().endsWith(".nbt"))
                    .map(path -> structureRoot.relativize(path)
                            .toString()
                            .replace('\\', '/')
                            .replaceFirst("\\.nbt$", ""))
                    .map(path -> Identifier.fromNamespaceAndPath(
                            "obeliskdepths",
                            path
                    ))
                    .filter(id -> !referenced.contains(id))
                    .sorted(Comparator.comparing(Identifier::toString))
                    .forEach(id -> System.err.println(
                            "[DungeonTemplateNbtConsistencyTest] warning unused dungeon NBT: "
                                    + id
                    ));
        }
    }

    private static void printCorridorAudit(
            Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit> audits,
            Map<Identifier, DungeonCorridorDefinition> javaCorridors
    ) {
        System.out.println("template audit: corridors");
        javaCorridors.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    DungeonTemplateResourceValidator.TemplateAudit audit =
                            audits.get(entry.getValue().template());
                    String size = audit == null || audit.size() == null
                            ? "<missing>"
                            : audit.size().x()
                            + "x"
                            + audit.size().y()
                            + "x"
                            + audit.size().z();
                    System.out.println(entry.getValue().template()
                            + " exists="
                            + (audit != null && audit.exists())
                            + " size="
                            + size
                            + " ports="
                            + entry.getValue().ports().stream()
                                    .map(port -> port.id()
                                            + ":"
                                            + port.facing().getSerializedName()
                                            + "@"
                                            + posText(port.openingMin())
                                            + " "
                                            + port.widthBlocks()
                                            + "x"
                                            + port.heightBlocks())
                                    .toList()
                            + " markers="
                            + (audit == null ? List.of() : audit.markers())
                            + " boundary="
                            + (audit == null ? Map.of() : audit.boundaryBlocks())
                            + " openings="
                            + (audit == null
                            ? List.of()
                            : audit.candidateOpenings().stream()
                                    .filter(opening -> opening.face() == DungeonConnectorSide.NORTH
                                            || opening.face() == DungeonConnectorSide.SOUTH
                                            || opening.face() == DungeonConnectorSide.EAST
                                            || opening.face() == DungeonConnectorSide.WEST)
                                    .map(opening -> opening.face().getSerializedName()
                                            + "@"
                                            + posText(opening.openingMin())
                                            + " "
                                            + opening.widthBlocks()
                                            + "x"
                                            + opening.heightBlocks())
                                    .toList()));
                });
    }

    private static void printRoomAudit(
            Map<Identifier, DungeonTemplateResourceValidator.TemplateAudit> audits,
            Map<Identifier, DungeonRoomDefinition> javaRooms
    ) {
        System.out.println("template audit: rooms");
        javaRooms.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> {
                    DungeonTemplateResourceValidator.TemplateAudit audit =
                            audits.get(entry.getValue().template());
                    String size = audit == null || audit.size() == null
                            ? "<missing>"
                            : audit.size().x()
                            + "x"
                            + audit.size().y()
                            + "x"
                            + audit.size().z();
                    System.out.println(entry.getValue().template()
                            + " exists="
                            + (audit != null && audit.exists())
                            + " size="
                            + size
                            + " ports="
                            + entry.getValue().ports().stream()
                                    .map(port -> port.id()
                                            + ":"
                                            + port.facing().getSerializedName()
                                            + "@"
                                            + posText(port.openingMin())
                                            + " cell="
                                            + port.boundaryCell())
                                    .toList());
                });
    }

    private static String posText(net.minecraft.core.BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static <T> Map<Identifier, T> readDefinitions(
            Path root,
            Codec<T> codec
    ) throws IOException {
        Map<Identifier, T> result = new TreeMap<>(
                Comparator.comparing(Identifier::toString)
        );
        if (!Files.exists(root)) {
            return result;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths
                    .filter(candidate -> candidate.toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                Identifier id = Identifier.fromNamespaceAndPath(
                        "obeliskdepths",
                        root.relativize(path)
                                .toString()
                                .replace('\\', '/')
                                .replaceFirst("\\.json$", "")
                );
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    result.put(id, codec.parse(JsonOps.INSTANCE, json)
                            .getOrThrow());
                }
            }
        }
        return result;
    }

    private static <T> void assertMapEquals(
            Map<Identifier, T> expected,
            Map<Identifier, T> actual,
            String label,
            List<String> errors
    ) {
        if (!expected.keySet().equals(actual.keySet())) {
            errors.add(label
                    + " json key mismatch expected="
                    + expected.keySet()
                    + " actual="
                    + actual.keySet());
            return;
        }
        expected.forEach((id, expectedValue) -> {
            T actualValue = actual.get(id);
            if (!expectedValue.equals(actualValue)) {
                errors.add(label
                        + " json mismatch id="
                        + id
                        + " expected="
                        + expectedValue
                        + " actual="
                        + actualValue);
            }
        });
    }
}
