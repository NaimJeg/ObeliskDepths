package io.github.naimjeg.obeliskdepths.dungeon.territory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import net.minecraft.core.BlockPos;

import java.util.Objects;

public record DungeonTerritory(
        DungeonTerritoryId id,
        DungeonInstanceId instanceId,
        DungeonBounds bounds,
        BlockPos startPos
) {
    public static final Codec<DungeonTerritory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DungeonTerritoryId.CODEC.fieldOf("id").forGetter(DungeonTerritory::id),
            DungeonInstanceId.CODEC.fieldOf("instance_id").forGetter(DungeonTerritory::instanceId),
            DungeonBounds.CODEC.fieldOf("bounds").forGetter(DungeonTerritory::bounds),
            BlockPos.CODEC.fieldOf("start_pos").forGetter(DungeonTerritory::startPos)
    ).apply(instance, DungeonTerritory::new));

    public DungeonTerritory {
        Objects.requireNonNull(id, "territory id");
        Objects.requireNonNull(instanceId, "instance id");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(startPos, "start pos");
    }
}
