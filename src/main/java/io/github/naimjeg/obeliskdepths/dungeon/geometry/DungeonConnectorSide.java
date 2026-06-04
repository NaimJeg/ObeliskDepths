package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonConnectorSide implements StringRepresentable {
    NORTH("north", 0, 0, -1),
    SOUTH("south", 0, 0, 1),
    WEST("west", -1, 0, 0),
    EAST("east", 1, 0, 0),
    UP("up", 0, 1, 0),
    DOWN("down", 0, -1, 0);

    public static final Codec<DungeonConnectorSide> CODEC =
            StringRepresentable.fromEnum(DungeonConnectorSide::values);

    private final String serializedName;
    private final int dx;
    private final int dy;
    private final int dz;

    DungeonConnectorSide(
            String serializedName,
            int dx,
            int dy,
            int dz
    ) {
        this.serializedName = serializedName;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public DungeonConnectorSide opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }

    public int dx() {
        return this.dx;
    }

    public int dy() {
        return this.dy;
    }

    public int dz() {
        return this.dz;
    }

    public boolean vertical() {
        return this == UP || this == DOWN;
    }
}
