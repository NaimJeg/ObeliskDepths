package io.github.naimjeg.obeliskdepths.dungeon.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public record DungeonBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public static final Codec<DungeonBounds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_x").forGetter(DungeonBounds::minX),
            Codec.INT.fieldOf("min_y").forGetter(DungeonBounds::minY),
            Codec.INT.fieldOf("min_z").forGetter(DungeonBounds::minZ),
            Codec.INT.fieldOf("max_x").forGetter(DungeonBounds::maxX),
            Codec.INT.fieldOf("max_y").forGetter(DungeonBounds::maxY),
            Codec.INT.fieldOf("max_z").forGetter(DungeonBounds::maxZ)
    ).apply(instance, DungeonBounds::new));

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.minX
                && pos.getX() <= this.maxX
                && pos.getY() >= this.minY
                && pos.getY() <= this.maxY
                && pos.getZ() >= this.minZ
                && pos.getZ() <= this.maxZ;
    }

    public boolean intersects(DungeonBounds other) {
        return this.minX <= other.maxX
                && this.maxX >= other.minX
                && this.minY <= other.maxY
                && this.maxY >= other.minY
                && this.minZ <= other.maxZ
                && this.maxZ >= other.minZ;
    }

    public boolean containsWithBuffer(BlockPos pos, int buffer) {
        return pos.getX() >= this.minX - buffer
                && pos.getX() <= this.maxX + buffer
                && pos.getY() >= this.minY - buffer
                && pos.getY() <= this.maxY + buffer
                && pos.getZ() >= this.minZ - buffer
                && pos.getZ() <= this.maxZ + buffer;
    }
}