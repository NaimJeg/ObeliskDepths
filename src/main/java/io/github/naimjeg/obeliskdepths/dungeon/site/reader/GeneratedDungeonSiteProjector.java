package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.Optional;

public final class GeneratedDungeonSiteProjector {
    private GeneratedDungeonSiteProjector() {
    }

    public static DungeonSite project(
            DungeonSiteKey key,
            StructureStart start
    ) {
        BoundingBox box = start.getBoundingBox();

        DungeonBounds bounds = new DungeonBounds(
                box.minX(),
                box.minY(),
                box.minZ(),
                box.maxX(),
                box.maxY(),
                box.maxZ()
        );

        List<DungeonGeneratedRoom> rooms =
                GeneratedDungeonRoomProjector.projectRooms(start);

        if (rooms.isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "Projected dungeon site {} has generated StructureStart bounds but no dungeon room metadata pieces: bounds={}",
                    key,
                    bounds
            );
        }

        BlockPos fallbackStartPos = new BlockPos(
                box.getCenter().getX(),
                box.minY(),
                box.getCenter().getZ()
        );

        Optional<DungeonRoomId> primaryEntryRoomId = primaryEntryRoomId(start);

        if (primaryEntryRoomId.isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "Projected dungeon site {} is missing authoritative primary entry metadata; old development chunks must be regenerated or migrated.",
                    key
            );
            return new DungeonSite(
                    key,
                    bounds,
                    DungeonRoomId.of("missing_primary_entry"),
                    fallbackStartPos,
                    rooms
            );
        }

        BlockPos startPos = rooms.stream()
                .filter(room -> room.id().equals(primaryEntryRoomId.get()))
                .filter(room -> room.type() == DungeonRoomType.START)
                .findFirst()
                .map(DungeonGeneratedRoom::spawnPos)
                .orElseThrow(() -> new IllegalStateException(
                        "Primary entry metadata does not reference a generated START room: "
                                + primaryEntryRoomId.get()
                ));

        if (!rooms.isEmpty()
                && rooms.stream().noneMatch(room -> room.type() == DungeonRoomType.START)) {
            ObeliskDepths.LOGGER.warn(
                    "Projected dungeon site {} has rooms but no start room metadata: bounds={}, rooms={}",
                    key,
                    bounds,
                    rooms.size()
            );
        }

        if (rooms.stream().noneMatch(room -> room.contains(startPos))) {
            ObeliskDepths.LOGGER.warn(
                    "Projected dungeon site {} has spawn outside all rooms: spawn={}, bounds={}, rooms={}",
                    key,
                    startPos,
                    bounds,
                    rooms.size()
            );
        }

        return new DungeonSite(
                key,
                bounds,
                primaryEntryRoomId.get(),
                startPos,
                rooms
        );
    }

    private static Optional<DungeonRoomId> primaryEntryRoomId(StructureStart start) {
        return start.getPieces()
                .stream()
                .filter(piece -> piece instanceof ObeliskDungeonPiece)
                .map(piece -> (ObeliskDungeonPiece) piece)
                .filter(ObeliskDungeonPiece::primaryEntry)
                .map(piece -> DungeonRoomId.of(piece.roomId()))
                .findFirst();
    }
}
