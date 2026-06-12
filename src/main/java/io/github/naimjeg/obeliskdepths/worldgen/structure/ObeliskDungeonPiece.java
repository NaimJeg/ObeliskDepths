package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

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
 *   boundingBox - inherited physical bounds used for chunk placement
 *
 * Runtime systems must project room metadata from this piece instead of
 * reconstructing room layout independently.
 */
public final class ObeliskDungeonPiece extends StructurePiece {
    private static final String TAG_ROLE = "Role";
    private static final String TAG_ROOM_ID = "RoomId";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";

    private final ObeliskDungeonPieceRole role;
    private final String roomId;
    private final BlockPos anchorPos;

    public ObeliskDungeonPiece(
            ObeliskDungeonPieceRole role,
            String roomId,
            BlockPos anchorPos,
            BoundingBox boundingBox
    ) {
        super(
                ModWorldgen.OBELISK_DUNGEON_PIECE.get(),
                0,
                boundingBox
        );

        this.role = role;
        this.roomId = roomId;
        this.anchorPos = anchorPos;
    }

    public ObeliskDungeonPiece(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        super(ModWorldgen.OBELISK_DUNGEON_PIECE.get(), tag);

        this.role = ObeliskDungeonPieceRole.byName(
                tag.getStringOr(TAG_ROLE, ObeliskDungeonPieceRole.START_ROOM.serializedName())
        );

        this.roomId = tag.getStringOr(TAG_ROOM_ID, this.role.serializedName());

        this.anchorPos = new BlockPos(
                tag.getIntOr(TAG_ANCHOR_X, this.boundingBox.getCenter().getX()),
                tag.getIntOr(TAG_ANCHOR_Y, this.boundingBox.minY() + 1),
                tag.getIntOr(TAG_ANCHOR_Z, this.boundingBox.getCenter().getZ())
        );
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

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        tag.putString(TAG_ROLE, this.role.serializedName());
        tag.putString(TAG_ROOM_ID, this.roomId);
        tag.putInt(TAG_ANCHOR_X, this.anchorPos.getX());
        tag.putInt(TAG_ANCHOR_Y, this.anchorPos.getY());
        tag.putInt(TAG_ANCHOR_Z, this.anchorPos.getZ());
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

        switch (this.role) {
            case SITE -> {
                // Site piece intentionally carries authoritative bounds only.
            }
            case CORRIDOR -> placeFloorPlane(
                    level,
                    chunkBB,
                    pieceBB,
                    ModBlocks.DUNGEON_BRICKS.get().defaultBlockState()
            );
            case START_ROOM, COMBAT_ROOM, TREASURE_ROOM, BOSS_ROOM, EXIT_ROOM ->
                    placeRoom(level, chunkBB, pieceBB);
        }
    }

    /*
     * Current graph/debug renderer: generated pieces expose authoritative
     * site/room/corridor metadata and place only floor/path markers. Authored
     * room architecture and NBT templates are intentionally deferred.
     */
    private void placeRoom(
            WorldGenLevel level,
            BoundingBox chunkBB,
            BoundingBox pieceBB
    ) {
        placeFloorPlane(level, chunkBB, pieceBB, floorStateForRole());
        placeRoomMarker(level, chunkBB, pieceBB);
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
            case START_ROOM, EXIT_ROOM -> ModBlocks.DUNGEON_LAMP.get().defaultBlockState();
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

    private BlockState floorStateForRole() {
        return switch (this.role) {
            case SITE, START_ROOM, EXIT_ROOM, CORRIDOR -> ModBlocks.DUNGEON_BRICKS.get().defaultBlockState();
            case COMBAT_ROOM -> ModBlocks.DUNGEON_STONE.get().defaultBlockState();
            case TREASURE_ROOM, BOSS_ROOM -> ModBlocks.DUNGEON_TILES.get().defaultBlockState();
        };
    }
}
