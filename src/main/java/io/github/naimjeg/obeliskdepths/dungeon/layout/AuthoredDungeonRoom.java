package io.github.naimjeg.obeliskdepths.dungeon.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutCodecs;
import net.minecraft.resources.Identifier;

public record AuthoredDungeonRoom(
        String id,
        Identifier definitionId,
        DungeonCellPos cellOrigin,
        DungeonRoomRotation rotation,
        boolean mirror
) {
    public static final Codec<AuthoredDungeonRoom> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .fieldOf("id")
                            .forGetter(AuthoredDungeonRoom::id),
                    Identifier.CODEC
                            .fieldOf("definition")
                            .forGetter(AuthoredDungeonRoom::definitionId),
                    DungeonLayoutCodecs.CELL_POS
                            .fieldOf("cell")
                            .forGetter(AuthoredDungeonRoom::cellOrigin),
                    DungeonRoomRotation.CODEC
                            .optionalFieldOf(
                                    "rotation",
                                    DungeonRoomRotation.NONE
                            )
                            .forGetter(AuthoredDungeonRoom::rotation),
                    Codec.BOOL
                            .optionalFieldOf("mirror", false)
                            .forGetter(AuthoredDungeonRoom::mirror)
            ).apply(instance, AuthoredDungeonRoom::new));

    public AuthoredDungeonRoom {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Authored dungeon room id must be non-empty"
            );
        }
        if (definitionId == null || cellOrigin == null) {
            throw new IllegalArgumentException(
                    "Authored dungeon room metadata is incomplete: " + id
            );
        }
        rotation = rotation == null ? DungeonRoomRotation.NONE : rotation;
    }
}
