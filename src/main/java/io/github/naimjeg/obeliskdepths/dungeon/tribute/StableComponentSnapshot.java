package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable value snapshot of an ItemStack's non-default data-component patch.
 */
public record StableComponentSnapshot(DataComponentPatch patch) {
    public static final StableComponentSnapshot EMPTY =
            new StableComponentSnapshot(DataComponentPatch.EMPTY);

    public StableComponentSnapshot {
        Objects.requireNonNull(patch, "patch");
    }

    public static StableComponentSnapshot from(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        DataComponentPatch source = stack.getComponentsPatch();
        if (source.isEmpty()) {
            return EMPTY;
        }

        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : source.entrySet()) {
            copyPatchEntry(builder, entry.getKey(), entry.getValue());
        }
        return new StableComponentSnapshot(builder.build());
    }

    public boolean matches(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return this.patch.equals(stack.getComponentsPatch());
    }

    @SuppressWarnings("unchecked")
    private static <T> void copyPatchEntry(
            DataComponentPatch.Builder builder,
            DataComponentType<?> type,
            Optional<?> value
    ) {
        DataComponentType<T> typed = (DataComponentType<T>) type;
        if (value.isPresent()) {
            builder.set(typed, (T) value.get());
        } else {
            builder.remove(typed);
        }
    }
}
