package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;

import java.util.Locale;
import java.util.Objects;

public record DungeonRoomId(String value) {
    public static final Codec<DungeonRoomId> CODEC =
            Codec.STRING.xmap(DungeonRoomId::of, DungeonRoomId::value);

    public DungeonRoomId {
        Objects.requireNonNull(value, "value must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("Dungeon room id must not be blank");
        }

        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Dungeon room id must be lowercase: " + value);
        }

        if (!value.matches("[a-z0-9_./:-]+")) {
            throw new IllegalArgumentException("Invalid dungeon room id: " + value);
        }
    }

    public static DungeonRoomId of(String value) {
        return new DungeonRoomId(value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}