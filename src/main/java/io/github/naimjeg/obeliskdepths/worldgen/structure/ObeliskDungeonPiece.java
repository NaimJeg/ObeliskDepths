package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonCorridorCell;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlanner;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.ResolvedTemplatePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * Worldgen-owned dungeon piece.
 *
 * This is the authoritative source for room/corridor metadata after a
 * StructureStart has been generated.
 *
 * Stored metadata:
 *   role      - semantic role of this piece, such as start/combat/corridor
 *   roomId    - stable id used by runtime room state
 *   anchorPos - semantic anchor for spawn/reward/debug logic
 *   template  - optional authored NBT template identity and placement metadata
 *   boundingBox - inherited physical bounds used for chunk placement
 *
 * Runtime systems must project room metadata from this piece instead of
 * reconstructing room layout independently.
 */
public final class ObeliskDungeonPiece extends StructurePiece {
    private static final String TAG_FORMAT_VERSION = "FormatVersion";
    private static final int FORMAT_VERSION = 2;
    private static final String TAG_ROLE = "Role";
    private static final String TAG_ROOM_ID = "RoomId";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";
    private static final String TAG_PRIMARY_ENTRY = "PrimaryEntry";
    private static final String TAG_DEFINITION_ID = "DefinitionId";
    private static final String TAG_TEMPLATE_ID = "TemplateId";
    private static final String TAG_ROTATION = "Rotation";
    private static final String TAG_MIRROR = "Mirror";
    private static final String TAG_TEMPLATE_X = "TemplateX";
    private static final String TAG_TEMPLATE_Y = "TemplateY";
    private static final String TAG_TEMPLATE_Z = "TemplateZ";
    private static final String TAG_TEMPLATE_BACKED = "TemplateBacked";
    private static final String TAG_CORRIDOR_CELLS = "CorridorCells";
    private static final String TAG_CELL_X = "X";
    private static final String TAG_CELL_Y = "Y";
    private static final String TAG_CELL_Z = "Z";
    private static final String TAG_CELL_MASK = "Mask";

    private final ObeliskDungeonPieceRole role;
    private final String roomId;
    private final BlockPos anchorPos;
    private final boolean primaryEntry;
    private final Optional<Identifier> definitionId;
    private final Optional<Identifier> templateId;
    private final DungeonRoomRotation rotation;
    private final boolean mirror;
    private final BlockPos templateOrigin;
    private final boolean templateBacked;
    private final List<DungeonCorridorCell> corridorCells;

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox
    ) {
        this(role, roomId, anchorPos, boundingBox, false);
    }

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox,
            boolean primaryEntry
    ) {
        this(
                role,
                roomId,
                anchorPos,
                boundingBox,
                primaryEntry,
                Optional.empty(),
                Optional.empty(),
                DungeonRoomRotation.NONE,
                false,
                new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()),
                false,
                List.of()
        );
    }

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox,
            boolean primaryEntry,
            Optional<Identifier> definitionId,
            Optional<Identifier> templateId,
            DungeonRoomRotation rotation,
            boolean mirror,
            BlockPos templateOrigin,
            boolean templateBacked
    ) {
        this(
                role,
                roomId,
                anchorPos,
                boundingBox,
                primaryEntry,
                definitionId,
                templateId,
                rotation,
                mirror,
                templateOrigin,
                templateBacked,
                List.of()
        );
    }

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox,
            boolean primaryEntry,
            Optional<Identifier> definitionId,
            Optional<Identifier> templateId,
            DungeonRoomRotation rotation,
            boolean mirror,
            BlockPos templateOrigin,
            boolean templateBacked,
            List<DungeonCorridorCell> corridorCells
    ) {
        super(
                ModWorldgen.OBELISK_DUNGEON_PIECE.get(),
                0,
                boundingBox
        );

        this.role = role;
        this.roomId = roomId;
        this.anchorPos = anchorPos;
        this.primaryEntry = primaryEntry && role == ObeliskDungeonPieceRole.START_ROOM;
        this.definitionId = definitionId == null ? Optional.empty() : definitionId;
        this.templateId = templateId == null ? Optional.empty() : templateId;
        this.rotation = rotation == null ? DungeonRoomRotation.NONE : rotation;
        this.mirror = mirror;
        this.templateOrigin = templateOrigin == null
                ? new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ())
                : templateOrigin;
        this.templateBacked = templateBacked && this.templateId.isPresent();
        this.corridorCells = corridorCells == null
                ? List.of()
                : List.copyOf(corridorCells);
    }

    public ObeliskDungeonPiece(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        super(ModWorldgen.OBELISK_DUNGEON_PIECE.get(), tag);

        int formatVersion = tag.getIntOr(TAG_FORMAT_VERSION, 1);
        if (formatVersion > FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported obelisk dungeon piece format version: "
                            + formatVersion
            );
        }

        this.role = ObeliskDungeonPieceRole.byName(
                tag.getStringOr(TAG_ROLE, ObeliskDungeonPieceRole.START_ROOM.serializedName())
        );

        this.roomId = tag.getStringOr(TAG_ROOM_ID, this.role.serializedName());

        this.anchorPos = new BlockPos(
                tag.getIntOr(TAG_ANCHOR_X, this.boundingBox.getCenter().getX()),
                tag.getIntOr(TAG_ANCHOR_Y, this.boundingBox.minY() + 1),
                tag.getIntOr(TAG_ANCHOR_Z, this.boundingBox.getCenter().getZ())
        );

        this.primaryEntry = tag.getBooleanOr(TAG_PRIMARY_ENTRY, false)
                && this.role == ObeliskDungeonPieceRole.START_ROOM;
        this.definitionId = parseIdentifier(tag.getStringOr(TAG_DEFINITION_ID, ""));
        this.templateId = parseIdentifier(tag.getStringOr(TAG_TEMPLATE_ID, ""));
        this.rotation = rotationByName(tag.getStringOr(
                TAG_ROTATION,
                DungeonRoomRotation.NONE.getSerializedName()
        ));
        this.mirror = tag.getBooleanOr(TAG_MIRROR, false);
        this.templateOrigin = new BlockPos(
                tag.getIntOr(TAG_TEMPLATE_X, this.boundingBox.minX()),
                tag.getIntOr(TAG_TEMPLATE_Y, this.boundingBox.minY()),
                tag.getIntOr(TAG_TEMPLATE_Z, this.boundingBox.minZ())
        );
        this.templateBacked = tag.getBooleanOr(TAG_TEMPLATE_BACKED, false)
                && this.templateId.isPresent();
        this.corridorCells = readCorridorCells(tag);
    }

    public ObeliskDungeonPieceRole role() {
        return this.role;
    }

    public String roomId() {
        return this.roomId;
    }

    public BlockPos anchorPos() {
        return this.anchorPos;
    }

    public boolean primaryEntry() {
        return this.primaryEntry;
    }

    public Optional<Identifier> definitionId() {
        return this.definitionId;
    }

    public Optional<Identifier> templateId() {
        return this.templateId;
    }

    public DungeonRoomRotation rotation() {
        return this.rotation;
    }

    public boolean mirror() {
        return this.mirror;
    }

    public BlockPos templateOrigin() {
        return this.templateOrigin;
    }

    public boolean templateBacked() {
        return this.templateBacked;
    }

    public List<DungeonCorridorCell> corridorCells() {
        return this.corridorCells;
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        tag.putInt(TAG_FORMAT_VERSION, FORMAT_VERSION);
        tag.putString(TAG_ROLE, this.role.serializedName());
        tag.putString(TAG_ROOM_ID, this.roomId);
        tag.putInt(TAG_ANCHOR_X, this.anchorPos.getX());
        tag.putInt(TAG_ANCHOR_Y, this.anchorPos.getY());
        tag.putInt(TAG_ANCHOR_Z, this.anchorPos.getZ());
        tag.putBoolean(TAG_PRIMARY_ENTRY, this.primaryEntry);
        this.definitionId.ifPresent(id -> tag.putString(TAG_DEFINITION_ID, id.toString()));
        this.templateId.ifPresent(id -> tag.putString(TAG_TEMPLATE_ID, id.toString()));
        tag.putString(TAG_ROTATION, this.rotation.getSerializedName());
        tag.putBoolean(TAG_MIRROR, this.mirror);
        tag.putInt(TAG_TEMPLATE_X, this.templateOrigin.getX());
        tag.putInt(TAG_TEMPLATE_Y, this.templateOrigin.getY());
        tag.putInt(TAG_TEMPLATE_Z, this.templateOrigin.getZ());
        tag.putBoolean(TAG_TEMPLATE_BACKED, this.templateBacked);
        if (!this.corridorCells.isEmpty()) {
            ListTag cells = new ListTag();
            for (DungeonCorridorCell cell : this.corridorCells) {
                CompoundTag cellTag = new CompoundTag();
                cellTag.putInt(TAG_CELL_X, cell.cell().x());
                cellTag.putInt(TAG_CELL_Y, cell.cell().y());
                cellTag.putInt(TAG_CELL_Z, cell.cell().z());
                cellTag.putInt(TAG_CELL_MASK, cell.openingMask());
                cells.add(cellTag);
            }
            tag.put(TAG_CORRIDOR_CELLS, cells);
        }
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BlockPos referencePos
    ) {
        BoundingBox pieceBB = this.getBoundingBox();
        if (this.role != ObeliskDungeonPieceRole.SITE
                && intersection(pieceBB, chunkBB) == null) {
            return;
        }

        switch (this.role) {
            case SITE -> {
                // Site piece intentionally carries authoritative bounds only.
            }

            case CORRIDOR -> placeCorridor(
                    level,
                    random,
                    chunkBB,
                    chunkPos,
                    pieceBB
            );

            case START_ROOM,
                 COMBAT_ROOM,
                 TREASURE_ROOM,
                 BOSS_ROOM -> placeRoom(
                    level,
                    random,
                    chunkBB,
                    chunkPos,
                    pieceBB
            );
        }
    }

    private void placeRoom(
            WorldGenLevel level,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BoundingBox pieceBB
    ) {
        if (placeTemplate(level, random, chunkBB, chunkPos)) {
            DungeonFoundationPlacer.placeForBounds(
                    level,
                    chunkBB,
                    pieceBB,
                    foundationState()
            );
            return;
        }

        /*
         * The dungeon is generated inside solid terrain. Clear the piece volume
         * above its floor so the room is physically traversable.
         *
         * Walls and ceilings are intentionally not placed during the graph/debug
         * generation phase.
         */
        carveInterior(level, chunkBB, pieceBB);

        placeFloorPlane(
                level,
                chunkBB,
                pieceBB,
                floorStateForRole()
        );

        placeRoomMarker(level, chunkBB, pieceBB);
        DungeonFoundationPlacer.placeForBounds(
                level,
                chunkBB,
                pieceBB,
                foundationState()
        );
    }

    private void placeCorridor(
            WorldGenLevel level,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BoundingBox pieceBB
    ) {
        if (!this.corridorCells.isEmpty()) {
            placeCorridorCells(level, chunkBB, chunkPos);
            return;
        }

        if (placeTemplate(level, random, chunkBB, chunkPos)) {
            DungeonFoundationPlacer.placeForBounds(
                    level,
                    chunkBB,
                    pieceBB,
                    foundationState()
            );
            return;
        }

        carveInterior(level, chunkBB, pieceBB);

        placeFloorPlane(
                level,
                chunkBB,
                pieceBB,
                corridorFloorState()
        );
        DungeonFoundationPlacer.placeForBounds(
                level,
                chunkBB,
                pieceBB,
                foundationState()
        );
    }

    private void placeCorridorCells(
            WorldGenLevel level,
            BoundingBox chunkBB,
            ChunkPos chunkPos
    ) {
        long start = System.nanoTime();
        int processedCells = 0;
        int setBlocks = 0;
        int foundationBlocks = 0;

        for (DungeonCorridorCell cell : this.corridorCells) {
            BoundingBox cellBounds = DungeonGenerationPlanner.corridorCellBounds(
                    this.templateOrigin,
                    cell.cell()
            );
            BoundingBox clipped = intersection(cellBounds, chunkBB);
            if (clipped == null) {
                continue;
            }
            processedCells++;
            setBlocks += placeCorridorCell(level, clipped, cellBounds);
            foundationBlocks += DungeonFoundationPlacer.placeForBounds(
                    level,
                    chunkBB,
                    cellBounds,
                    foundationState()
            );
        }

        if (DungeonWorldgenTrace.placementEnabled()) {
            long elapsedMicros = (System.nanoTime() - start) / 1_000L;
            int finalProcessedCells = processedCells;
            int finalSetBlocks = setBlocks;
            int finalFoundationBlocks = foundationBlocks;
            DungeonWorldgenTrace.Context context = new DungeonWorldgenTrace.Context(
                    true,
                    chunkPos,
                    "placement:" + chunkPos.x() + "," + chunkPos.z()
            );
            DungeonWorldgenTrace.debug(
                    context,
                    () -> "stage=post-process-corridor-grid piece="
                            + this.roomId
                            + " chunk="
                            + chunkPos.x()
                            + ","
                            + chunkPos.z()
                            + " pieceBounds="
                            + this.getBoundingBox()
                            + " processedCells="
                            + finalProcessedCells
                            + " totalCells="
                            + this.corridorCells.size()
                            + " setBlocks="
                            + finalSetBlocks
                            + " foundationBlocks="
                            + finalFoundationBlocks
                            + " elapsedMicros="
                            + elapsedMicros
            );
        }
    }

    private int placeCorridorCell(
            WorldGenLevel level,
            BoundingBox clipped,
            BoundingBox cellBounds
    ) {
        BlockState air = Blocks.CAVE_AIR.defaultBlockState();
        BlockState floor = corridorFloorState();
        int floorY = cellBounds.minY();
        int clearMaxY = Math.min(
                cellBounds.maxY(),
                floorY + 4
        );
        int written = 0;

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            if (y > clearMaxY) {
                continue;
            }
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    level.setBlock(
                            new BlockPos(x, y, z),
                            y == floorY ? floor : air,
                            2
                    );
                    written++;
                }
            }
        }

        return written;
    }

    private boolean placeTemplate(
            WorldGenLevel level,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos
    ) {
        if (!this.templateBacked || this.templateId.isEmpty()) {
            return false;
        }

        StructureTemplate template = level.getLevel()
                .getStructureManager()
                .get(this.templateId.get())
                .orElseThrow(() -> new DungeonContentException(
                        this.templateId.get(),
                        "missing structure template during dungeon piece placement"
                ));
        ResolvedTemplatePlacement placement = ResolvedTemplatePlacement.resolve(
                template,
                this.templateOrigin,
                this.rotation,
                this.mirror,
                chunkBB
        );
        BoundingBox pieceBB = this.getBoundingBox();
        BoundingBox transformedBounds = placement.transformedBounds();

        if (DungeonWorldgenTrace.placementEnabled()
                && !contains(pieceBB, transformedBounds)) {
            DungeonWorldgenTrace.warn(
                    new DungeonWorldgenTrace.Context(
                            true,
                            chunkPos,
                            "placement:" + chunkPos.x() + "," + chunkPos.z()
                    ),
                    () -> "stage=placement-invariant piece="
                            + this.roomId
                            + " role="
                            + this.role.serializedName()
                            + " template="
                            + this.templateId.map(Identifier::toString).orElse("<none>")
                            + " pieceBounds="
                            + pieceBB
                            + " transformedBounds="
                            + transformedBounds
                            + " configuredTemplateOrigin="
                            + this.templateOrigin
                            + " resolvedPlacementOrigin="
                            + placement.placementOrigin()
            );
        }

        boolean result = template.placeInWorld(
                level,
                placement.placementOrigin(),
                placement.placementOrigin(),
                placement.settings(),
                random,
                2
        );

        if (DungeonWorldgenTrace.placementEnabled()) {
            DungeonWorldgenTrace.Context context = new DungeonWorldgenTrace.Context(
                    true,
                    chunkPos,
                    "placement:" + chunkPos.x() + "," + chunkPos.z()
            );
            DungeonWorldgenTrace.debug(
                    context,
                    () -> "stage=place-piece piece="
                            + this.roomId
                            + " role="
                            + this.role.serializedName()
                            + " template="
                            + this.templateId.map(Identifier::toString).orElse("<none>")
                            + " chunk="
                            + chunkPos.x()
                            + ","
                            + chunkPos.z()
                            + " clip="
                            + chunkBB
                            + " pieceBounds="
                            + pieceBB
                            + " configuredTemplateOrigin="
                            + this.templateOrigin
                            + " resolvedPlacementOrigin="
                            + placement.placementOrigin()
                            + " rotation="
                            + this.rotation.getSerializedName()
                            + " mirror="
                            + this.mirror
                            + " templateSize="
                            + template.getSize()
                            + " transformedBounds="
                            + transformedBounds
                            + " clipIntersection="
                            + intersection(transformedBounds, chunkBB)
                            + " result="
                            + result
            );
        }

        return result;
    }

    private static void carveInterior(
            WorldGenLevel level,
            BoundingBox chunkBB,
            BoundingBox pieceBB
    ) {
        int interiorMinY = pieceBB.minY() + 1;

        if (interiorMinY > pieceBB.maxY()) {
            return;
        }

        BoundingBox clipped = intersection(
                chunkBB,
                new BoundingBox(
                        pieceBB.minX(),
                        interiorMinY,
                        pieceBB.minZ(),
                        pieceBB.maxX(),
                        pieceBB.maxY(),
                        pieceBB.maxZ()
                )
        );

        if (clipped == null) {
            return;
        }

        BlockState air = Blocks.CAVE_AIR.defaultBlockState();

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    level.setBlock(
                            new BlockPos(x, y, z),
                            air,
                            2
                    );
                }
            }
        }
    }

    private void placeRoomMarker(
            WorldGenLevel level,
            BoundingBox chunkBB,
            BoundingBox pieceBB
    ) {
        if (this.anchorPos.getY() != pieceBB.minY() + 1) {
            return;
        }

        BlockPos markerPos = this.anchorPos.below();

        if (!chunkBB.isInside(markerPos)) {
            return;
        }

        BlockState marker = switch (this.role) {
            case START_ROOM -> ModBlocks.DUNGEON_LAMP.get().defaultBlockState();
            case COMBAT_ROOM -> ModBlocks.REINFORCED_DUNGEON_STONE.get().defaultBlockState();
            case TREASURE_ROOM -> ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get().defaultBlockState();
            case BOSS_ROOM -> ModBlocks.OBELISK.get().defaultBlockState();
            case SITE, CORRIDOR -> ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
        };

        level.setBlock(markerPos, marker, 2);
    }

    private static void placeFloorPlane(
            WorldGenLevel level,
            BoundingBox chunkBB,
            BoundingBox pieceBB,
            BlockState state
    ) {
        int floorY = pieceBB.minY();

        if (floorY < chunkBB.minY() || floorY > chunkBB.maxY()) {
            return;
        }

        BoundingBox clipped = intersection(
                chunkBB,
                new BoundingBox(
                        pieceBB.minX(),
                        floorY,
                        pieceBB.minZ(),
                        pieceBB.maxX(),
                        floorY,
                        pieceBB.maxZ()
                )
        );

        if (clipped == null) {
            return;
        }

        for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
            for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                level.setBlock(new BlockPos(x, floorY, z), state, 2);
            }
        }
    }

    private static BoundingBox intersection(
            BoundingBox first,
            BoundingBox second
    ) {
        int minX = Math.max(first.minX(), second.minX());
        int minY = Math.max(first.minY(), second.minY());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxX = Math.min(first.maxX(), second.maxX());
        int maxY = Math.min(first.maxY(), second.maxY());
        int maxZ = Math.min(first.maxZ(), second.maxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean contains(
            BoundingBox outer,
            BoundingBox inner
    ) {
        return inner.minX() >= outer.minX()
                && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY()
                && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ()
                && inner.maxZ() <= outer.maxZ();
    }

    private BlockState floorStateForRole() {
        return switch (this.role) {
            case SITE, START_ROOM, CORRIDOR -> ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
            case COMBAT_ROOM -> ModBlocks.DUNGEON_STONE.get().defaultBlockState();
            case TREASURE_ROOM, BOSS_ROOM -> ModBlocks.DUNGEON_TILES.get().defaultBlockState();
        };
    }

    private BlockState corridorFloorState() {
        return ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
    }

    private BlockState foundationState() {
        return ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
    }

    private static List<DungeonCorridorCell> readCorridorCells(
            CompoundTag tag
    ) {
        Tag raw = tag.get(TAG_CORRIDOR_CELLS);
        if (!(raw instanceof ListTag list) || list.isEmpty()) {
            return List.of();
        }

        List<DungeonCorridorCell> cells = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Tag rawCell = list.get(index);
            if (!(rawCell instanceof CompoundTag cellTag)) {
                throw new IllegalArgumentException(
                        "Malformed obelisk dungeon corridor cell at index "
                                + index
                );
            }
            DungeonCellPos cell = new DungeonCellPos(
                    cellTag.getIntOr(TAG_CELL_X, 0),
                    cellTag.getIntOr(TAG_CELL_Y, 0),
                    cellTag.getIntOr(TAG_CELL_Z, 0)
            );
            int mask = cellTag.getIntOr(TAG_CELL_MASK, 0);
            cells.add(new DungeonCorridorCell(cell, mask));
        }
        return List.copyOf(cells);
    }

    private static Optional<Identifier> parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Identifier.parse(raw));
    }

    private static DungeonRoomRotation rotationByName(String raw) {
        for (DungeonRoomRotation rotation : DungeonRoomRotation.values()) {
            if (rotation.getSerializedName().equals(raw)) {
                return rotation;
            }
        }

        return DungeonRoomRotation.NONE;
    }
}
