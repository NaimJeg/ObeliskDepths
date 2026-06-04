package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

public record RewardDeliveryPlan(
        List<ItemStack> stacks,
        int nextOrdinal
) {
    public static final Codec<RewardDeliveryPlan> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ItemStack.CODEC.listOf()
                            .fieldOf("stacks")
                            .forGetter(RewardDeliveryPlan::stacks),
                    Codec.INT
                            .fieldOf("next_ordinal")
                            .forGetter(RewardDeliveryPlan::nextOrdinal)
            ).apply(instance, RewardDeliveryPlan::new));

    public RewardDeliveryPlan {
        Objects.requireNonNull(stacks, "stacks");
        List<ItemStack> copiedStacks = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copiedStacks.add(Objects.requireNonNull(stack, "stack").copy());
        }
        stacks = List.copyOf(copiedStacks);
        if (nextOrdinal < 0 || nextOrdinal > stacks.size()) {
            throw new IllegalArgumentException(
                    "Invalid reward delivery ordinal: "
                            + nextOrdinal
                            + " size="
                            + stacks.size()
            );
        }
    }

    @Override
    public List<ItemStack> stacks() {
        return this.stacks.stream()
                .map(ItemStack::copy)
                .toList();
    }

    public static RewardDeliveryPlan start(List<ItemStack> stacks) {
        Objects.requireNonNull(stacks, "stacks");
        List<ItemStack> copiedStacks = stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        if (copiedStacks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Reward delivery plan must contain at least one stack."
            );
        }

        return new RewardDeliveryPlan(copiedStacks, 0);
    }

    public boolean complete() {
        return this.nextOrdinal >= this.stacks.size();
    }

    public ItemStack currentStack() {
        if (this.complete()) {
            return ItemStack.EMPTY;
        }

        return this.stacks.get(this.nextOrdinal).copy();
    }

    public RewardDeliveryPlan advance() {
        if (this.complete()) {
            return this;
        }

        return new RewardDeliveryPlan(this.stacks, this.nextOrdinal + 1);
    }
}
