package io.github.naimjeg.obeliskdepths.dungeon.serialization;

import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

public final class DungeonCodecs {
    public static final Codec<UUID> UUID_CODEC = UUIDUtil.CODEC;

    private DungeonCodecs() {
    }
}