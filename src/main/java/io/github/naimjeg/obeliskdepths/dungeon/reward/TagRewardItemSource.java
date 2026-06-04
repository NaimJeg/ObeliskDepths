package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Reward item source that selects from a tag, falling back to a hardcoded
 * list when the tag is missing, empty, or unavailable.
 *
 * <p>This replaces the inline tag + fallback logic in
 * {@link DefaultDungeonRewardGenerator} with a reusable component.</p>
 *
 * <p><b>Empty-tag behaviour (intentional change):</b>
 * The old generator fell back only when a tag was absent.
 * This implementation also falls back when a tag exists but contains
 * no entries ({@code HolderSet.size() == 0}).  This prevents
 * silently-empty rewards when a data pack removes all entries from a
 * tag without removing the tag key itself, and is the documented
 * preferred behaviour for this system.</p>
 */
public final class TagRewardItemSource implements DungeonRewardItemSource {
    private final TagKey<Item> tag;
    private final List<Item> fallback;

    public TagRewardItemSource(TagKey<Item> tag, List<Item> fallback) {
        this.tag = Objects.requireNonNull(tag, "tag");
        this.fallback = List.copyOf(Objects.requireNonNull(fallback, "fallback"));
    }

    @Override
    public Stream<ItemStack> items(DungeonRewardContext context, Random random) {
        Stream<Holder<Item>> fallbackStream = this.fallback.stream()
                .map(BuiltInRegistries.ITEM::wrapAsHolder);

        List<Item> candidates = context.level()
                .registryAccess()
                .lookupOrThrow(Registries.ITEM)
                .get(this.tag)
                .filter(set -> set.size() > 0)
                .map(HolderSet::stream)
                .orElseGet(() -> fallbackStream)
                .map(Holder::value)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();

        if (candidates.isEmpty()) {
            return Stream.empty();
        }

        Item chosen = candidates.get(random.nextInt(candidates.size()));
        return Stream.of(new ItemStack(chosen));
    }
}