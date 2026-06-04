package io.github.naimjeg.obeliskdepths.dungeon.content;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.layout.DungeonLayoutDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;

public record DungeonContentSnapshot(
        Map<Identifier, DungeonRoomDefinition> rooms,
        Map<Identifier, DungeonCorridorDefinition> corridors,
        Map<Identifier, DungeonThemeDefinition> themes,
        Map<Identifier, DungeonLayoutDefinition> layouts
) {
    private static final DungeonContentSnapshot EMPTY =
            new DungeonContentSnapshot(Map.of(), Map.of(), Map.of(), Map.of());

    public DungeonContentSnapshot {
        rooms = immutableCheckedCopy(rooms, "rooms");
        corridors = immutableCheckedCopy(corridors, "corridors");
        themes = immutableCheckedCopy(themes, "themes");
        layouts = immutableCheckedCopy(layouts, "layouts");
    }

    public static DungeonContentSnapshot empty() {
        return EMPTY;
    }

    private static <T> Map<Identifier, T> immutableCheckedCopy(
            Map<Identifier, T> values,
            String label
    ) {
        Objects.requireNonNull(values, label);

        if (values.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, T> copy = new LinkedHashMap<>();
        values.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> {
                    Identifier id = Objects.requireNonNull(
                            entry.getKey(),
                            label + " contains a null id"
                    );
                    T value = Objects.requireNonNull(
                            entry.getValue(),
                            label + " contains a null value for " + id
                    );
                    copy.put(id, value);
                });

        return Collections.unmodifiableMap(copy);
    }
}
