package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RewardDeliveryPlan(
        List<ItemStack> stacks,
        int firstOrdinal,
        int nextOrdinal
) {
    public static final Codec<RewardDeliveryPlan> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ItemStack.CODEC.listOf()
                            .fieldOf("stacks")
                            .forGetter(RewardDeliveryPlan::stacks),
                    Codec.INT
                            .optionalFieldOf("first_ordinal", 0)
                            .forGetter(RewardDeliveryPlan::firstOrdinal),
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
        long finalOrdinal = (long) firstOrdinal + stacks.size();
        if (firstOrdinal < 0
                || nextOrdinal < firstOrdinal
                || nextOrdinal > finalOrdinal
                || finalOrdinal > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Invalid reward delivery ordinal range: first="
                            + firstOrdinal
                            + " next="
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

    public static RewardDeliveryPlan startAt(
            List<ItemStack> stacks,
            int firstOrdinal
    ) {
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

        return new RewardDeliveryPlan(copiedStacks, firstOrdinal, firstOrdinal);
    }

    public boolean complete() {
        return this.nextOrdinal >= this.finalOrdinal();
    }

    public int stackCount() {
        return this.stacks.size();
    }

    public int finalOrdinal() {
        return this.firstOrdinal + this.stacks.size();
    }

    public ItemStack currentStack() {
        if (this.complete()) {
            return ItemStack.EMPTY;
        }

        return this.stacks.get(this.nextOrdinal - this.firstOrdinal).copy();
    }

    public RewardDeliveryPlan advance() {
        if (this.complete()) {
            return this;
        }

        return new RewardDeliveryPlan(
                this.stacks,
                this.firstOrdinal,
                this.nextOrdinal + 1
        );
    }
}
