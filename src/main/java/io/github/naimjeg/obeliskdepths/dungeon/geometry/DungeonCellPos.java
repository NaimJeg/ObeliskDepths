package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DungeonCellPos(
        int x,
        int y,
        int z
) {
    public static final Codec<DungeonCellPos> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .fieldOf("x")
                            .forGetter(DungeonCellPos::x),
                    Codec.INT
                            .fieldOf("y")
                            .forGetter(DungeonCellPos::y),
                    Codec.INT
                            .fieldOf("z")
                            .forGetter(DungeonCellPos::z)
            ).apply(instance, DungeonCellPos::new));
}
