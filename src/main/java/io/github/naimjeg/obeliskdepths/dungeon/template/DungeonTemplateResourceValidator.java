package io.github.naimjeg.obeliskdepths.dungeon.template;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;

public final class DungeonTemplateResourceValidator {
    private DungeonTemplateResourceValidator() {
    }

    public record Size(int x, int y, int z) {
        public Size {
            if (x <= 0 || y <= 0 || z <= 0) {
                throw new IllegalArgumentException(
                        "Template size must be positive: " + x + "x" + y + "x" + z
                );
            }
        }
    }

    public static List<String> validateRooms(
            Map<Identifier, DungeonRoomDefinition> rooms,
            ResourceManager resourceManager
    ) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry : rooms.entrySet()) {
            Size size = readResourceSize(resourceManager, entry.getValue().template(), errors);

            if (size != null) {
                errors.addAll(validateRoom(entry.getKey(), entry.getValue(), size));
            }
        }

        return errors;
    }

    public static List<String> validateCorridors(
            Map<Identifier, DungeonCorridorDefinition> corridors,
            ResourceManager resourceManager
    ) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry : corridors.entrySet()) {
            Size size = readResourceSize(resourceManager, entry.getValue().template(), errors);

            if (size != null) {
                errors.addAll(validateCorridor(entry.getKey(), entry.getValue(), size));
            }
        }

        return errors;
    }

    public static List<String> validateRoom(
            Identifier id,
            DungeonRoomDefinition room,
            Size templateSize
    ) {
        List<String> errors = new ArrayList<>();
        validateAnchor("dungeon room", id, room.anchor(), templateSize, errors);
        validatePortsInsideTemplate(
                "dungeon room",
                id,
                room.ports(),
                templateSize,
                errors
        );
        return errors;
    }

    public static List<String> validateCorridor(
            Identifier id,
            DungeonCorridorDefinition corridor,
            Size templateSize
    ) {
        List<String> errors = new ArrayList<>();
        validatePortsInsideTemplate(
                "dungeon corridor",
                id,
                corridor.ports(),
                templateSize,
                errors
        );
        return errors;
    }

    public static List<String> validateAllSuppliedTemplatesReferenced(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        Set<Identifier> referenced = new HashSet<>();

        rooms.values().forEach(room -> referenced.add(room.template()));
        corridors.values().forEach(corridor -> referenced.add(corridor.template()));

        List<String> errors = new ArrayList<>();

        for (Identifier template : BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES) {
            if (!referenced.contains(template)) {
                errors.add("supplied dungeon template is not referenced: " + template);
            }
        }

        return errors;
    }

    public static Path templatePath(
            Path resourcesRoot,
            Identifier template
    ) {
        return resourcesRoot
                .resolve("data")
                .resolve(template.getNamespace())
                .resolve("structure")
                .resolve(template.getPath() + ".nbt");
    }

    public static Size readTemplateSize(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return readSize(readRoot(input));
        }
    }

    public static TemplateAudit readTemplateAudit(
            Path resourcesRoot,
            Identifier template,
            List<String> sourceReferences
    ) throws IOException {
        return readTemplateAudit(
                template,
                templatePath(resourcesRoot, template),
                sourceReferences
        );
    }

    public static TemplateAudit readTemplateAudit(
            Identifier template,
            Path path,
            List<String> sourceReferences
    ) throws IOException {
        if (!Files.exists(path)) {
            return new TemplateAudit(
                    template,
                    path,
                    false,
                    null,
                    null,
                    null,
                    List.of(),
                    Map.of(),
                    List.of(),
                    sourceReferences
            );
        }

        try (InputStream input = Files.newInputStream(path)) {
            CompoundTag root = readRoot(input);
            Size size = readSize(root);
            List<String> palette = palette(root);
            Map<BlockPos, String> blocks = blocks(root, palette);
            OccupiedBounds occupiedBounds = occupiedBounds(blocks);

            return new TemplateAudit(
                    template,
                    path,
                    true,
                    size,
                    occupiedBounds.min(),
                    occupiedBounds.max(),
                    markers(blocks, root),
                    boundaryBlocks(size, blocks),
                    candidateOpenings(size, blocks),
                    sourceReferences
            );
        }
    }

    private static Size readResourceSize(
            ResourceManager resourceManager,
            Identifier template,
            List<String> errors
    ) {
        Identifier resourceId = Identifier.fromNamespaceAndPath(
                template.getNamespace(),
                "structure/" + template.getPath() + ".nbt"
        );

        return resourceManager.getResource(resourceId)
                .map(resource -> {
                    try (InputStream input = resource.open()) {
                        return readSize(readRoot(input));
                    } catch (IOException exception) {
                        errors.add("failed to read dungeon template "
                                + template + ": " + exception.getMessage());
                        return null;
                    }
                })
                .orElseGet(() -> {
                    errors.add("missing dungeon template resource: " + template);
                    return null;
                });
    }

    private static void validateAnchor(
            String kind,
            Identifier id,
            BlockPos anchor,
            Size size,
            List<String> errors
    ) {
        if (anchor.getX() < 0
                || anchor.getY() < 0
                || anchor.getZ() < 0
                || anchor.getX() >= size.x()
                || anchor.getY() >= size.y()
                || anchor.getZ() >= size.z()) {
            errors.add(kind + " " + id
                    + " anchor "
                    + anchor
                    + " must be inside exact template size "
                    + size);
        }
    }

    public record CandidateOpening(
            DungeonConnectorSide face,
            BlockPos openingMin,
            int widthBlocks,
            int heightBlocks
    ) {
    }

    public record TemplateAudit(
            Identifier templateId,
            Path resourcePath,
            boolean exists,
            Size size,
            BlockPos minOccupied,
            BlockPos maxOccupied,
            List<String> markers,
            Map<DungeonConnectorSide, Map<String, Integer>> boundaryBlocks,
            List<CandidateOpening> candidateOpenings,
            List<String> sourceReferences
    ) {
        public TemplateAudit {
            markers = markers == null ? List.of() : List.copyOf(markers);
            boundaryBlocks = boundaryBlocks == null
                    ? Map.of()
                    : copyBoundaryBlocks(boundaryBlocks);
            candidateOpenings = candidateOpenings == null
                    ? List.of()
                    : List.copyOf(candidateOpenings);
            sourceReferences = sourceReferences == null
                    ? List.of()
                    : List.copyOf(sourceReferences);
        }
    }

    private static void validatePortsInsideTemplate(
            String kind,
            Identifier id,
            List<RoomConnectorDefinition> ports,
            Size templateSize,
            List<String> errors
    ) {
        for (RoomConnectorDefinition port : ports) {
            BlockPos opening = port.openingMin();

            if (!openingFits(port, templateSize)) {
                errors.add(kind + " " + id + " port " + port.id()
                        + " size="
                        + sizeText(templateSize)
                        + " openingMin=("
                        + opening.getX()
                        + ","
                        + opening.getY()
                        + ","
                        + opening.getZ()
                        + ") openingWidth="
                        + port.widthBlocks()
                        + " openingHeight="
                        + port.heightBlocks()
                        + " facing="
                        + port.facing().getSerializedName()
                        + " reason=opening does not fit template or touch declared boundary; expected "
                        + expectedBoundary(port, templateSize));
            }
        }
    }

    private static boolean openingFits(
            RoomConnectorDefinition port,
            Size size
    ) {
        BlockPos opening = port.openingMin();

        if (opening.getX() < 0 || opening.getY() < 0 || opening.getZ() < 0) {
            return false;
        }

        return switch (port.facing()) {
            case NORTH -> opening.getZ() == 0
                    && opening.getX() + port.widthBlocks() <= size.x()
                    && opening.getY() + port.heightBlocks() <= size.y();
            case SOUTH -> opening.getZ() == size.z() - 1
                    && opening.getX() + port.widthBlocks() <= size.x()
                    && opening.getY() + port.heightBlocks() <= size.y();
            case WEST -> opening.getX() == 0
                    && opening.getZ() + port.widthBlocks() <= size.z()
                    && opening.getY() + port.heightBlocks() <= size.y();
            case EAST -> opening.getX() == size.x() - 1
                    && opening.getZ() + port.widthBlocks() <= size.z()
                    && opening.getY() + port.heightBlocks() <= size.y();
            case DOWN -> opening.getY() == 0
                    && opening.getX() + port.widthBlocks() <= size.x()
                    && opening.getZ() + port.widthBlocks() <= size.z();
            case UP -> opening.getY() == size.y() - 1
                    && opening.getX() + port.widthBlocks() <= size.x()
                    && opening.getZ() + port.widthBlocks() <= size.z();
        };
    }

    private static String expectedBoundary(
            RoomConnectorDefinition port,
            Size size
    ) {
        return switch (port.facing()) {
            case NORTH -> "z=0, x+width<=" + size.x()
                    + ", y+height<=" + size.y();
            case SOUTH -> "z=" + (size.z() - 1)
                    + ", x+width<=" + size.x()
                    + ", y+height<=" + size.y();
            case WEST -> "x=0, z+width<=" + size.z()
                    + ", y+height<=" + size.y();
            case EAST -> "x=" + (size.x() - 1)
                    + ", z+width<=" + size.z()
                    + ", y+height<=" + size.y();
            case DOWN -> "y=0, x+width<=" + size.x()
                    + ", z+width<=" + size.z();
            case UP -> "y=" + (size.y() - 1)
                    + ", x+width<=" + size.x()
                    + ", z+width<=" + size.z();
        };
    }

    private static CompoundTag readRoot(InputStream input) throws IOException {
        try (DataInputStream data = new DataInputStream(decompressIfNeeded(input))) {
            CompoundTag root = NbtIo.read(data, NbtAccounter.unlimitedHeap());

            if (root == null) {
                throw new IOException("Structure NBT root is missing");
            }

            return root;
        }
    }

    private static Size readSize(CompoundTag root) throws IOException {
        Tag rawSize = root.get("size");

        if (!(rawSize instanceof ListTag size)
                || size.size() != 3
                || !(size.get(0) instanceof IntTag)
                || !(size.get(1) instanceof IntTag)
                || !(size.get(2) instanceof IntTag)) {
            throw new IOException("Structure NBT requires size:[I;x,y,z]");
        }

        return new Size(
                size.get(0).asInt().orElseThrow(),
                size.get(1).asInt().orElseThrow(),
                size.get(2).asInt().orElseThrow()
        );
    }

    private static InputStream decompressIfNeeded(InputStream input)
            throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(2);

        int first = buffered.read();
        int second = buffered.read();

        buffered.reset();

        return first == 0x1F && second == 0x8B
                ? new GZIPInputStream(buffered)
                : buffered;
    }

    private static List<String> palette(CompoundTag root) {
        Tag rawPalette = root.get("palette");
        if (!(rawPalette instanceof ListTag palette)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Tag rawEntry : palette) {
            if (rawEntry instanceof CompoundTag entry) {
                names.add(entry.getString("Name").orElse("<missing-name>"));
            } else {
                names.add("<invalid-palette-entry>");
            }
        }
        return names;
    }

    private static Map<BlockPos, String> blocks(
            CompoundTag root,
            List<String> palette
    ) throws IOException {
        Tag rawBlocks = root.get("blocks");
        if (!(rawBlocks instanceof ListTag blocks)) {
            throw new IOException("Structure NBT requires blocks list");
        }

        Map<BlockPos, String> result = new LinkedHashMap<>();
        for (Tag rawBlock : blocks) {
            if (!(rawBlock instanceof CompoundTag block)) {
                continue;
            }
            BlockPos pos = readBlockPos(block.get("pos"));
            int state = readInt(block.get("state"), -1);
            String name = state >= 0 && state < palette.size()
                    ? palette.get(state)
                    : "<state:" + state + ">";
            result.put(pos, name);
        }
        return result;
    }

    private static BlockPos readBlockPos(Tag raw) throws IOException {
        if (!(raw instanceof ListTag pos)
                || pos.size() != 3
                || !(pos.get(0) instanceof IntTag)
                || !(pos.get(1) instanceof IntTag)
                || !(pos.get(2) instanceof IntTag)) {
            throw new IOException("Structure block entry requires pos:[I;x,y,z]");
        }
        return new BlockPos(
                pos.get(0).asInt().orElseThrow(),
                pos.get(1).asInt().orElseThrow(),
                pos.get(2).asInt().orElseThrow()
        );
    }

    private static int readInt(Tag raw, int fallback) {
        return raw instanceof IntTag value
                ? value.asInt().orElse(fallback)
                : fallback;
    }

    private static OccupiedBounds occupiedBounds(Map<BlockPos, String> blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean found = false;

        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            if (isAir(entry.getValue())) {
                continue;
            }

            BlockPos pos = entry.getKey();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
            found = true;
        }

        if (!found) {
            return new OccupiedBounds(null, null);
        }
        return new OccupiedBounds(
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ)
        );
    }

    private static List<String> markers(
            Map<BlockPos, String> blocks,
            CompoundTag root
    ) {
        Tag rawBlocks = root.get("blocks");
        if (!(rawBlocks instanceof ListTag blockTags)) {
            return List.of();
        }

        List<String> markers = new ArrayList<>();
        for (Tag rawBlock : blockTags) {
            if (!(rawBlock instanceof CompoundTag block)) {
                continue;
            }
            try {
                BlockPos pos = readBlockPos(block.get("pos"));
                String name = blocks.getOrDefault(pos, "<missing>");
                Tag rawNbt = block.get("nbt");
                if (name.contains("structure_block")
                        || rawNbt instanceof CompoundTag) {
                    markers.add(name + "@" + pos + " nbt="
                            + (rawNbt == null ? "{}" : rawNbt));
                }
            } catch (IOException exception) {
                markers.add("<invalid-marker-pos>");
            }
        }
        return markers;
    }

    private static Map<DungeonConnectorSide, Map<String, Integer>> boundaryBlocks(
            Size size,
            Map<BlockPos, String> blocks
    ) {
        Map<DungeonConnectorSide, Map<String, Integer>> counts =
                new EnumMap<>(DungeonConnectorSide.class);
        for (DungeonConnectorSide side : DungeonConnectorSide.values()) {
            counts.put(side, new LinkedHashMap<>());
        }

        for (Map.Entry<BlockPos, String> entry : blocks.entrySet()) {
            for (DungeonConnectorSide side : DungeonConnectorSide.values()) {
                if (touchesFace(entry.getKey(), size, side)) {
                    counts.get(side).merge(entry.getValue(), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static List<CandidateOpening> candidateOpenings(
            Size size,
            Map<BlockPos, String> blocks
    ) {
        List<CandidateOpening> openings = new ArrayList<>();
        for (DungeonConnectorSide side : DungeonConnectorSide.values()) {
            openings.addAll(candidateOpenings(size, blocks, side));
        }
        return openings;
    }

    private static List<CandidateOpening> candidateOpenings(
            Size size,
            Map<BlockPos, String> blocks,
            DungeonConnectorSide side
    ) {
        Set<FaceCell> open = new HashSet<>();
        int firstMax = faceFirstAxisSize(size, side);
        int secondMax = faceSecondAxisSize(size, side);

        for (int first = 0; first < firstMax; first++) {
            for (int second = 0; second < secondMax; second++) {
                BlockPos pos = facePos(size, side, first, second);
                if (isAir(blocks.getOrDefault(pos, "minecraft:air"))) {
                    open.add(new FaceCell(first, second));
                }
            }
        }

        List<CandidateOpening> openings = new ArrayList<>();
        Set<FaceCell> visited = new HashSet<>();
        for (FaceCell start : open) {
            if (!visited.add(start)) {
                continue;
            }

            int minFirst = start.first();
            int maxFirst = start.first();
            int minSecond = start.second();
            int maxSecond = start.second();
            Queue<FaceCell> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                FaceCell current = queue.remove();
                minFirst = Math.min(minFirst, current.first());
                maxFirst = Math.max(maxFirst, current.first());
                minSecond = Math.min(minSecond, current.second());
                maxSecond = Math.max(maxSecond, current.second());
                for (FaceCell next : faceNeighbors(current)) {
                    if (open.contains(next) && visited.add(next)) {
                        queue.add(next);
                    }
                }
            }

            openings.add(new CandidateOpening(
                    side,
                    facePos(size, side, minFirst, minSecond),
                    maxFirst - minFirst + 1,
                    maxSecond - minSecond + 1
            ));
        }
        return openings;
    }

    private static List<FaceCell> faceNeighbors(FaceCell cell) {
        return List.of(
                new FaceCell(cell.first() + 1, cell.second()),
                new FaceCell(cell.first() - 1, cell.second()),
                new FaceCell(cell.first(), cell.second() + 1),
                new FaceCell(cell.first(), cell.second() - 1)
        );
    }

    private static boolean touchesFace(
            BlockPos pos,
            Size size,
            DungeonConnectorSide side
    ) {
        return switch (side) {
            case NORTH -> pos.getZ() == 0;
            case SOUTH -> pos.getZ() == size.z() - 1;
            case WEST -> pos.getX() == 0;
            case EAST -> pos.getX() == size.x() - 1;
            case DOWN -> pos.getY() == 0;
            case UP -> pos.getY() == size.y() - 1;
        };
    }

    private static int faceFirstAxisSize(Size size, DungeonConnectorSide side) {
        return switch (side) {
            case NORTH, SOUTH, UP, DOWN -> size.x();
            case WEST, EAST -> size.z();
        };
    }

    private static int faceSecondAxisSize(Size size, DungeonConnectorSide side) {
        return switch (side) {
            case NORTH, SOUTH, WEST, EAST -> size.y();
            case UP, DOWN -> size.z();
        };
    }

    private static BlockPos facePos(
            Size size,
            DungeonConnectorSide side,
            int first,
            int second
    ) {
        return switch (side) {
            case NORTH -> new BlockPos(first, second, 0);
            case SOUTH -> new BlockPos(first, second, size.z() - 1);
            case WEST -> new BlockPos(0, second, first);
            case EAST -> new BlockPos(size.x() - 1, second, first);
            case DOWN -> new BlockPos(first, 0, second);
            case UP -> new BlockPos(first, size.y() - 1, second);
        };
    }

    private static boolean isAir(String blockName) {
        return blockName == null
                || blockName.equals("minecraft:air")
                || blockName.equals("minecraft:cave_air")
                || blockName.equals("minecraft:void_air");
    }

    private static Map<DungeonConnectorSide, Map<String, Integer>>
    copyBoundaryBlocks(Map<DungeonConnectorSide, Map<String, Integer>> source) {
        Map<DungeonConnectorSide, Map<String, Integer>> copy =
                new EnumMap<>(DungeonConnectorSide.class);
        source.forEach((side, counts) ->
                copy.put(side, Map.copyOf(counts)));
        return Map.copyOf(copy);
    }

    private static String sizeText(Size size) {
        return size.x() + "x" + size.y() + "x" + size.z();
    }

    private record OccupiedBounds(BlockPos min, BlockPos max) {
    }

    private record FaceCell(int first, int second) {
    }
}
