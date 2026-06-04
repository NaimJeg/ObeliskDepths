package io.github.naimjeg.obeliskdepths.dungeon.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutCodecs;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonPortReference;

import java.util.List;

public record AuthoredDungeonConnection(
        String id,
        DungeonPortReference from,
        DungeonPortReference to,
        DungeonGraphEdgeKind kind,
        List<DungeonCellPos> path
) {
    public static final Codec<AuthoredDungeonConnection> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .fieldOf("id")
                            .forGetter(AuthoredDungeonConnection::id),
                    DungeonPortReference.CODEC
                            .fieldOf("from")
                            .forGetter(AuthoredDungeonConnection::from),
                    DungeonPortReference.CODEC
                            .fieldOf("to")
                            .forGetter(AuthoredDungeonConnection::to),
                    DungeonLayoutCodecs.EDGE_KIND
                            .optionalFieldOf("kind", DungeonGraphEdgeKind.TREE)
                            .forGetter(AuthoredDungeonConnection::kind),
                    DungeonLayoutCodecs.CELL_POS
                            .listOf()
                            .optionalFieldOf("path", List.of())
                            .forGetter(AuthoredDungeonConnection::path)
            ).apply(instance, AuthoredDungeonConnection::new));

    public AuthoredDungeonConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Authored dungeon connection id must be non-empty"
            );
        }
        if (from == null || to == null || kind == null) {
            throw new IllegalArgumentException(
                    "Authored dungeon connection metadata is incomplete: " + id
            );
        }
        path = path == null ? List.of() : List.copyOf(path);
        for (int index = 1; index < path.size(); index++) {
            DungeonCellPos previous = path.get(index - 1);
            DungeonCellPos current = path.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                throw new IllegalArgumentException(
                        "Authored dungeon path is not contiguous: "
                                + id
                                + " previous="
                                + previous
                                + " current="
                                + current
                );
            }
        }
    }
}
