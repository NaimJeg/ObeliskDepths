package io.github.naimjeg.obeliskdepths.dungeon.site;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;

/*
 * Authoritative room metadata projected from generated ObeliskDungeonPiece data.
 *
 * Runtime systems should consume these ids/types/bounds/anchors for rooms,
 * raids, rewards, and debug commands. Runtime must not reconstruct room bounds
 * from a planner when creating real dungeon instances. The current flat planes
 * are temporary geometry; this metadata is the stable contract that future .nbt
 * template placement must preserve.
 */
public record DungeonGeneratedRoom(
        DungeonRoomId id,
        DungeonRoomType type,
        DungeonBounds bounds,
        BlockPos anchorPos
) {
    public static final Codec<DungeonGeneratedRoom> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRoomId.CODEC.fieldOf("id")
                            .forGetter(DungeonGeneratedRoom::id),
                    DungeonRoomType.CODEC.fieldOf("type")
                            .forGetter(DungeonGeneratedRoom::type),
                    DungeonBounds.CODEC.fieldOf("bounds")
                            .forGetter(DungeonGeneratedRoom::bounds),
                    BlockPos.CODEC.fieldOf("anchor_pos")
                            .forGetter(DungeonGeneratedRoom::anchorPos)
            ).apply(instance, DungeonGeneratedRoom::new));

    public boolean contains(BlockPos pos) {
        return this.bounds.contains(pos);
    }

    public BlockPos spawnPos() {
        return this.anchorPos;
    }
}
