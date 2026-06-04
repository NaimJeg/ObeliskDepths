package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Occupied routing-cell mask for authored rooms and corridors.
 *
 * <p>This is not physical template geometry. AUTO footprints are derived from
 * exact transformed NBT template bounds during generation. EXPLICIT_MASK
 * footprints are optional routing occupancy overrides for unusual assets.</p>
 *
 * JSON layers use Y as the outer list index, bottom to top. Each layer string
 * list is Z rows north to south, and each character is X west to east. '#'
 * reserves a cell; '.' leaves it unoccupied.
 */
public final class DungeonRoomFootprint {
    public static final int MAX_CELLS = 64;
    public static final DungeonRoomFootprint AUTO =
            new DungeonRoomFootprint();

    private static final Codec<DungeonRoomFootprint> AUTO_CODEC =
            Codec.STRING.comapFlatMap(
                    value -> "auto".equals(value)
                            ? DataResult.success(AUTO)
                            : DataResult.error(() -> "Expected footprint mode auto"),
                    footprint -> "auto"
            );

    private static final Codec<DungeonRoomFootprint> LEGACY_RECTANGLE_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .fieldOf("width_cells")
                            .forGetter(DungeonRoomFootprint::widthCells),
                    Codec.INT
                            .fieldOf("height_cells")
                            .forGetter(DungeonRoomFootprint::heightCells),
                    Codec.INT
                            .fieldOf("depth_cells")
                            .forGetter(DungeonRoomFootprint::depthCells)
            ).apply(instance, DungeonRoomFootprint::rectangular));

    private static final Codec<DungeonRoomFootprint> LAYERS_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .listOf()
                            .listOf()
                            .fieldOf("layers")
                            .forGetter(DungeonRoomFootprint::layers)
            ).apply(instance, DungeonRoomFootprint::fromLayers));

    public static final Codec<DungeonRoomFootprint> CODEC =
            Codec.either(
                    AUTO_CODEC,
                    Codec.either(LAYERS_CODEC, LEGACY_RECTANGLE_CODEC)
            )
                    .xmap(
                            either -> either.map(
                                    footprint -> footprint,
                                    nested -> nested.map(
                                            footprint -> footprint,
                                            footprint -> footprint
                                    )
                            ),
                            footprint -> footprint.isAuto()
                                    ? Either.left(footprint)
                                    : Either.right(Either.left(footprint))
                    );

    private final Set<DungeonCellPos> occupiedCells;
    private final int widthCells;
    private final int heightCells;
    private final int depthCells;
    private final RoutingFootprintMode mode;

    private DungeonRoomFootprint() {
        this.occupiedCells = Set.of();
        this.widthCells = 0;
        this.heightCells = 0;
        this.depthCells = 0;
        this.mode = RoutingFootprintMode.AUTO;
    }

    public DungeonRoomFootprint(
            int widthCells,
            int heightCells,
            int depthCells
    ) {
        this(rectangularCells(widthCells, heightCells, depthCells));
    }

    public DungeonRoomFootprint(Set<DungeonCellPos> occupiedCells) {
        if (occupiedCells == null || occupiedCells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Room footprint must occupy at least one cell"
            );
        }

        LinkedHashSet<DungeonCellPos> normalized = new LinkedHashSet<>();
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;

        for (DungeonCellPos cell : occupiedCells) {
            if (cell == null) {
                throw new IllegalArgumentException(
                        "Room footprint cells must not contain null"
                );
            }

            if (cell.x() < 0 || cell.y() < 0 || cell.z() < 0) {
                throw new IllegalArgumentException(
                        "Room footprint cell coordinates must be non-negative: "
                                + cell
                );
            }

            normalized.add(cell);
            maxX = Math.max(maxX, cell.x());
            maxY = Math.max(maxY, cell.y());
            maxZ = Math.max(maxZ, cell.z());
        }

        this.widthCells = maxX + 1;
        this.heightCells = maxY + 1;
        this.depthCells = maxZ + 1;
        this.mode = RoutingFootprintMode.EXPLICIT_MASK;

        if (this.widthCells > MAX_CELLS
                || this.heightCells > MAX_CELLS
                || this.depthCells > MAX_CELLS) {
            throw new IllegalArgumentException(
                    "Room footprint exceeds conservative max "
                            + MAX_CELLS
                            + " cells: "
                            + this.widthCells
                            + "x"
                            + this.heightCells
                            + "x"
                            + this.depthCells
            );
        }

        this.occupiedCells = Collections.unmodifiableSet(normalized);
    }

    public static DungeonRoomFootprint auto() {
        return AUTO;
    }

    public static DungeonRoomFootprint rectangular(
            int widthCells,
            int heightCells,
            int depthCells
    ) {
        return new DungeonRoomFootprint(
                rectangularCells(widthCells, heightCells, depthCells)
        );
    }

    public static DungeonRoomFootprint fromLayers(List<List<String>> layers) {
        if (layers == null || layers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Room footprint layers must not be empty"
            );
        }

        int expectedDepth = -1;
        int expectedWidth = -1;
        LinkedHashSet<DungeonCellPos> cells = new LinkedHashSet<>();

        for (int y = 0; y < layers.size(); y++) {
            List<String> layer = layers.get(y);

            if (layer == null || layer.isEmpty()) {
                throw new IllegalArgumentException(
                        "Room footprint layer must contain at least one row: "
                                + y
                );
            }

            if (expectedDepth < 0) {
                expectedDepth = layer.size();
            } else if (layer.size() != expectedDepth) {
                throw new IllegalArgumentException(
                        "Room footprint layers must have equal row counts"
                );
            }

            for (int z = 0; z < layer.size(); z++) {
                String row = layer.get(z);

                if (row == null || row.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Room footprint rows must not be empty"
                    );
                }

                if (expectedWidth < 0) {
                    expectedWidth = row.length();
                } else if (row.length() != expectedWidth) {
                    throw new IllegalArgumentException(
                            "Room footprint rows must have equal widths"
                    );
                }

                for (int x = 0; x < row.length(); x++) {
                    char value = row.charAt(x);

                    if (value == '#') {
                        cells.add(new DungeonCellPos(x, y, z));
                    } else if (value != '.') {
                        throw new IllegalArgumentException(
                                "Room footprint layers may only contain '#' or '.'"
                        );
                    }
                }
            }
        }

        return new DungeonRoomFootprint(cells);
    }

    public Set<DungeonCellPos> occupiedCells() {
        return this.occupiedCells;
    }

    public RoutingFootprintMode mode() {
        return this.mode;
    }

    public boolean isAuto() {
        return this.mode == RoutingFootprintMode.AUTO;
    }

    public int widthCells() {
        return this.widthCells;
    }

    public int heightCells() {
        return this.heightCells;
    }

    public int depthCells() {
        return this.depthCells;
    }

    public int widthBlocks() {
        return DungeonLayoutConstants.cellToBlockX(this.widthCells);
    }

    public int heightBlocks() {
        return DungeonLayoutConstants.cellToBlockY(this.heightCells);
    }

    public int depthBlocks() {
        return DungeonLayoutConstants.cellToBlockZ(this.depthCells);
    }

    public boolean containsCell(int localX, int localZ) {
        return containsCell(localX, 0, localZ);
    }

    public boolean containsCell(int localX, int localY, int localZ) {
        return containsCell(new DungeonCellPos(localX, localY, localZ));
    }

    public boolean containsCell(DungeonCellPos pos) {
        return this.occupiedCells.contains(pos);
    }

    public boolean isRectangular() {
        if (isAuto()) {
            return false;
        }

        return this.occupiedCells.size()
                == this.widthCells * this.heightCells * this.depthCells;
    }

    public DungeonCellBox envelopeAt(DungeonCellPos origin) {
        if (isAuto()) {
            throw new IllegalStateException(
                    "AUTO routing footprint must be resolved from template geometry"
            );
        }

        return new DungeonCellBox(
                origin.x(),
                origin.y(),
                origin.z(),
                this.widthCells,
                this.heightCells,
                this.depthCells
        );
    }

    public DungeonRoomFootprint rotated(DungeonRoomRotation rotation) {
        if (isAuto()) {
            return this;
        }

        if (rotation == null || rotation == DungeonRoomRotation.NONE) {
            return this;
        }

        LinkedHashSet<DungeonCellPos> rotated = new LinkedHashSet<>();

        for (DungeonCellPos cell : this.occupiedCells) {
            rotated.add(rotateCell(
                    cell,
                    rotation,
                    this.widthCells,
                    this.depthCells
            ));
        }

        return new DungeonRoomFootprint(rotated);
    }

    public static DungeonCellPos rotateCell(
            DungeonCellPos cell,
            DungeonRoomRotation rotation,
            int widthCells,
            int depthCells
    ) {
        return switch (rotation) {
            case NONE -> cell;
            case CLOCKWISE_90 -> new DungeonCellPos(
                    depthCells - 1 - cell.z(),
                    cell.y(),
                    cell.x()
            );
            case CLOCKWISE_180 -> new DungeonCellPos(
                    widthCells - 1 - cell.x(),
                    cell.y(),
                    depthCells - 1 - cell.z()
            );
            case COUNTERCLOCKWISE_90 -> new DungeonCellPos(
                    cell.z(),
                    cell.y(),
                    widthCells - 1 - cell.x()
            );
        };
    }

    public DungeonCellBox toCellBox(DungeonCellPos origin) {
        return envelopeAt(origin);
    }

    public BoundingBox toBlockBounds(
            BlockPos layoutOrigin,
            DungeonCellPos origin
    ) {
        return toCellBox(origin).toBlockBounds(layoutOrigin);
    }

    public BoundingBox toBounds(BlockPos origin, int height) {
        return new BoundingBox(
                origin.getX(),
                origin.getY() - 1,
                origin.getZ(),
                origin.getX() + this.widthBlocks() - 1,
                origin.getY() + height - 2,
                origin.getZ() + this.depthBlocks() - 1
        );
    }

    public List<List<String>> layers() {
        if (isAuto()) {
            return List.of();
        }

        return java.util.stream.IntStream.range(0, this.heightCells)
                .mapToObj(y -> java.util.stream.IntStream
                        .range(0, this.depthCells)
                        .mapToObj(z -> row(y, z))
                        .toList())
                .toList();
    }

    private String row(int y, int z) {
        StringBuilder result = new StringBuilder(this.widthCells);

        for (int x = 0; x < this.widthCells; x++) {
            result.append(containsCell(x, y, z) ? '#' : '.');
        }

        return result.toString();
    }

    private static Set<DungeonCellPos> rectangularCells(
            int widthCells,
            int heightCells,
            int depthCells
    ) {
        if (widthCells <= 0 || heightCells <= 0 || depthCells <= 0) {
            throw new IllegalArgumentException(
                    "Room footprint cells must be positive"
            );
        }

        LinkedHashSet<DungeonCellPos> cells = new LinkedHashSet<>();

        for (int y = 0; y < heightCells; y++) {
            for (int z = 0; z < depthCells; z++) {
                for (int x = 0; x < widthCells; x++) {
                    cells.add(new DungeonCellPos(x, y, z));
                }
            }
        }

        return cells;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof DungeonRoomFootprint other)) {
            return false;
        }

        return this.widthCells == other.widthCells
                && this.heightCells == other.heightCells
                && this.depthCells == other.depthCells
                && this.mode == other.mode
                && this.occupiedCells.equals(other.occupiedCells);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                this.occupiedCells,
                this.widthCells,
                this.heightCells,
                this.depthCells,
                this.mode
        );
    }

    @Override
    public String toString() {
        return "DungeonRoomFootprint{"
                + "mode="
                + this.mode
                + ", "
                + "widthCells="
                + this.widthCells
                + ", heightCells="
                + this.heightCells
                + ", depthCells="
                + this.depthCells
                + ", occupiedCells="
                + this.occupiedCells
                        .stream()
                        .sorted(Comparator
                                .comparingInt(DungeonCellPos::y)
                                .thenComparingInt(DungeonCellPos::z)
                                .thenComparingInt(DungeonCellPos::x))
                        .toList()
                + '}';
    }
}
