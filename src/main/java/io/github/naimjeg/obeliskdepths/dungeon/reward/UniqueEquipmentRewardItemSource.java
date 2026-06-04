package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentCatalog;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentDefinition;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentStacks;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

/** Deterministic tier-gated source for the formal unique equipment catalog. */
public final class UniqueEquipmentRewardItemSource
        implements DungeonRewardItemSource {
    private final TierProvider tierProvider;

    public UniqueEquipmentRewardItemSource(TierProvider tierProvider) {
        this.tierProvider = Objects.requireNonNull(tierProvider, "tierProvider");
    }

    @Override
    public Stream<ItemStack> items(
            DungeonRewardContext context,
            Random random
    ) {
        Objects.requireNonNull(random, "random");
        int rewardTier = DungeonRewardTiers.clampTier(
                tierProvider.tier(context, random)
        );
        return select(rewardTier, random)
                .map(ObeliskUniqueEquipmentStacks::create)
                .stream();
    }

    Optional<ObeliskUniqueEquipmentDefinition> select(
            int rewardTier,
            Random random
    ) {
        Objects.requireNonNull(random, "random");
        List<ObeliskUniqueEquipmentDefinition> candidates =
                ObeliskUniqueEquipmentCatalog.availableAtTier(rewardTier);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<ObeliskUniqueEquipmentDefinition> weighted =
                new ArrayList<>(candidates.size());
        long totalWeight = 0L;
        for (ObeliskUniqueEquipmentDefinition candidate : candidates) {
            totalWeight = Math.addExact(totalWeight, candidate.rewardWeight());
            weighted.add(candidate);
        }

        long draw = DungeonRewardPool.boundLong(random, totalWeight);
        for (ObeliskUniqueEquipmentDefinition candidate : weighted) {
            draw -= candidate.rewardWeight();
            if (draw < 0L) {
                return Optional.of(candidate);
            }
        }
        throw new IllegalStateException("Unique equipment selection fell through");
    }
}
