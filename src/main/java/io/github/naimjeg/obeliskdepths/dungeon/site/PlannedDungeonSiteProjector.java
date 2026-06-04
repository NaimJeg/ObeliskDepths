package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;

import java.util.List;

/*
 * Prototype/debug projection only.
 *
 * This does not read a vanilla StructureStart and must not be used for runtime
 * reservation or real DungeonInstance creation.
 */
public final class PlannedDungeonSiteProjector {
    private PlannedDungeonSiteProjector() {
    }

    public static DungeonSite project(
            WorldgenDungeonSiteCandidate candidate
    ) {
        BlockPos start = candidate.startPos();

        DungeonBounds fullBounds = new DungeonBounds(
                start.getX() - 48,
                24,
                start.getZ() - 48,
                start.getX() + 48,
                80,
                start.getZ() + 48
        );

        DungeonGeneratedRoom startRoom = new DungeonGeneratedRoom(
                DungeonRoomId.of("start"),
                DungeonRoomType.START,
                new DungeonBounds(
                        start.getX() - 7,
                        start.getY(),
                        start.getZ() - 7,
                        start.getX() + 7,
                        start.getY() + 8,
                        start.getZ() + 7
                ),
                start
        );

        DungeonGeneratedRoom combatRoom = new DungeonGeneratedRoom(
                DungeonRoomId.of("combat_01"),
                DungeonRoomType.COMBAT,
                new DungeonBounds(
                        start.getX() + 18,
                        start.getY(),
                        start.getZ() - 7,
                        start.getX() + 34,
                        start.getY() + 8,
                        start.getZ() + 7
                ),
                start.offset(26, 0, 0)
        );

        return new DungeonSite(
                candidate.key(),
                fullBounds,
                startRoom.id(),
                start,
                List.of(startRoom, combatRoom)
        );
    }
}
