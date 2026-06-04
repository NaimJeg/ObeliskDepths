package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.EnumSet;

public enum DungeonConnectorShapeType implements StringRepresentable {
    CAP("cap"),
    STRAIGHT("straight"),
    CORNER("corner"),
    T("tee"),
    CROSS("cross"),
    VERTICAL_CAP("vertical_cap"),
    VERTICAL_THROUGH("vertical_through"),
    MIXED_3D("mixed_3d");

    public static final Codec<DungeonConnectorShapeType> CODEC =
            StringRepresentable.fromEnum(DungeonConnectorShapeType::values);

    private final String serializedName;

    DungeonConnectorShapeType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public static DungeonConnectorShapeType fromSides(EnumSet<DungeonConnectorSide> sides) {
        boolean hasVertical = sides.stream().anyMatch(DungeonConnectorSide::vertical);

        if (hasVertical) {
            if (sides.size() == 1) {
                return VERTICAL_CAP;
            }

            if (sides.size() == 2
                    && sides.contains(DungeonConnectorSide.UP)
                    && sides.contains(DungeonConnectorSide.DOWN)) {
                return VERTICAL_THROUGH;
            }

            return MIXED_3D;
        }

        return switch (sides.size()) {
            case 1 -> CAP;
            case 2 -> sides.stream().anyMatch(side -> sides.contains(side.opposite()))
                    ? STRAIGHT
                    : CORNER;
            case 3 -> T;
            case 4 -> CROSS;
            default -> throw new IllegalArgumentException(
                    "Connector shape requires 1-4 sides: " + sides
            );
        };
    }
}
