package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

public final class DungeonLayoutCodecs {
    public static final Codec<DungeonCellPos> CELL_POS =
            Codec.INT.listOf().comapFlatMap(
                    values -> {
                        if (values.size() != 3) {
                            return DataResult.error(() ->
                                    "Cell position must contain exactly 3 integers"
                            );
                        }
                        return DataResult.success(new DungeonCellPos(
                                values.get(0),
                                values.get(1),
                                values.get(2)
                        ));
                    },
                    pos -> List.of(pos.x(), pos.y(), pos.z())
            );

    public static final Codec<DungeonGraphEdgeKind> EDGE_KIND =
            Codec.STRING.comapFlatMap(
                    raw -> {
                        for (DungeonGraphEdgeKind kind
                                : DungeonGraphEdgeKind.values()) {
                            if (kind.name().equalsIgnoreCase(raw)) {
                                return DataResult.success(kind);
                            }
                        }
                        return DataResult.error(() ->
                                "Unknown dungeon graph edge kind: " + raw
                        );
                    },
                    kind -> kind.name().toLowerCase(java.util.Locale.ROOT)
            );

    private DungeonLayoutCodecs() {
    }
}
