package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Immutable tribute fingerprint for detecting slot-content changes
 * between preparation submission and commit.
 *
 * <p>Stores item identity, required count, a stable data-component patch,
 * and the resolved tribute accepted at submission time. It never stores a
 * mutable {@link ItemStack} reference.</p>
 */
public record TributeFingerprint(
        Identifier itemId,
        int requiredCount,
        StableComponentSnapshot components,
        ResolvedTribute resolvedTribute
) {
    public TributeFingerprint {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(resolvedTribute, "resolvedTribute");
        if (requiredCount <= 0) {
            throw new IllegalArgumentException(
                    "requiredCount must be positive: " + requiredCount
            );
        }
        if (!resolvedTribute.valid()) {
            throw new IllegalArgumentException("resolvedTribute must be valid");
        }
        if (resolvedTribute.amount() != requiredCount) {
            throw new IllegalArgumentException(
                    "requiredCount must equal resolved tribute amount"
            );
        }
    }

    public static TributeFingerprint from(
            ItemStack stack,
            int count,
            ResolvedTribute resolvedTribute
    ) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("stack must not be empty");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        Identifier registryId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new TributeFingerprint(
                registryId,
                count,
                StableComponentSnapshot.from(stack),
                resolvedTribute
        );
    }

    public static TributeFingerprint from(ItemStack stack, int count) {
        ResolvedTribute resolved = TributeResolver.resolve(stack);
        return from(stack, count, resolved);
    }

    public boolean matches(ItemStack currentStack) {
        Objects.requireNonNull(currentStack, "currentStack");
        if (currentStack.isEmpty()) {
            return false;
        }
        Identifier currentId = BuiltInRegistries.ITEM.getKey(currentStack.getItem());
        if (!this.itemId.equals(currentId)) {
            return false;
        }
        if (currentStack.getCount() < this.requiredCount) {
            return false;
        }
        if (!this.components.matches(currentStack)) {
            return false;
        }
        return TributeResolver.resolve(currentStack).equals(this.resolvedTribute);
    }
}
