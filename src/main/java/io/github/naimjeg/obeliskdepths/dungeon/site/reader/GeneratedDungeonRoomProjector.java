package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/*
 * Projects room metadata from generated ObeliskDungeonPiece instances.
 *
 * Runtime room ids, types, bounds, and anchors come from serialized piece
 * fields after vanilla worldgen has produced a StructureStart.
 */
public final class GeneratedDungeonRoomProjector {
    private GeneratedDungeonRoomProjector() {
    }

    public static List<DungeonGeneratedRoom> projectRooms(StructureStart start) {
        List<DungeonGeneratedRoom> result = new ArrayList<>();

        for (var piece : start.getPieces()) {
            if (!(piece instanceof ObeliskDungeonPiece dungeonPiece)) {
                continue;
            }

            if (!dungeonPiece.role().isRoom()) {
                continue;
            }

            BoundingBox box = piece.getBoundingBox();

            DungeonBounds bounds = new DungeonBounds(
                    box.minX(),
                    box.minY(),
                    box.minZ(),
                    box.maxX(),
                    box.maxY(),
                    box.maxZ()
            );

            result.add(new DungeonGeneratedRoom(
                    DungeonRoomId.of(dungeonPiece.roomId()),
                    dungeonPiece.role().roomType(),
                    bounds,
                    dungeonPiece.anchorPos()
            ));
        }

        return result;
    }
}
