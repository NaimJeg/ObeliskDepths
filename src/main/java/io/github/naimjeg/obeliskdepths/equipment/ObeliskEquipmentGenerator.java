package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Server-side deterministic reward-equipment generation. */
public final class ObeliskEquipmentGenerator {
    private static final long REWARD_EQUIPMENT_SALT = 0x6F62_656C_6973_6B45L;

    private ObeliskEquipmentGenerator() {
    }

    public static ItemStack generateRewardStack(
            ItemStack baseStack,
            long rewardSeed,
            UUID rewardId,
            UUID instanceId,
            int rollOrdinal
    ) {
        if (baseStack == null || baseStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = baseStack.copy();
        if (ObeliskUniqueEquipmentStacks.isUnique(result)) {
            return result;
        }
        Optional<ObeliskEquipmentSlot> slot = ObeliskEquipmentRules.slot(result);
        if (slot.isEmpty() || rewardId == null || instanceId == null || rollOrdinal < 0) {
            return result;
        }

        if (ObeliskEquipmentRules.hasManagedReference(result)) {
            return result;
        }

        Optional<ObeliskEquipmentRoll> roll = roll(
                slot.get(),
                rewardSeed,
                rewardId,
                instanceId,
                rollOrdinal
        );
        if (roll.isEmpty()) {
            return result;
        }

        try {
            ObeliskEquipmentContent content = ObeliskEquipmentTemplateCatalog
                    .find(roll.get().templateId())
                    .orElseThrow();
            if (!ObeliskEquipmentRules.applyTemplateReference(result, content)) {
                return baseStack.copy();
            }
        } catch (IllegalArgumentException exception) {
            ObeliskDepths.LOGGER.warn(
                    "Unable to attach Obelisk reward equipment template: template={}, ordinal={}",
                    roll.get().templateId(),
                    rollOrdinal,
                    exception
            );
            return baseStack.copy();
        }
        return result;
    }

    public static Optional<ObeliskEquipmentRoll> roll(
            ObeliskEquipmentSlot slot,
            long rewardSeed,
            UUID rewardId,
            UUID instanceId,
            int rollOrdinal
    ) {
        if (slot == null || rewardId == null || instanceId == null || rollOrdinal < 0) {
            return Optional.empty();
        }

        List<ObeliskEquipmentContent> candidates = ObeliskEquipmentTemplateCatalog
                .all()
                .stream()
                .filter(content -> content.temperingEligible() && content.supports(slot))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        long seed = rewardEquipmentSeed(
                rewardSeed,
                rewardId,
                instanceId,
                rollOrdinal
        );
        Random random = new Random(seed);
        long totalWeight = 0L;
        List<ObeliskEquipmentContent> weighted = new ArrayList<>(candidates.size());
        for (ObeliskEquipmentContent candidate : candidates) {
            totalWeight = Math.addExact(totalWeight, candidate.defaultWeight());
            weighted.add(candidate);
        }

        long draw = boundedLong(random, totalWeight);
        for (ObeliskEquipmentContent candidate : weighted) {
            draw -= candidate.defaultWeight();
            if (draw < 0L) {
                return Optional.of(new ObeliskEquipmentRoll(
                        candidate.templateId(),
                        seed,
                        rollOrdinal
                ));
            }
        }
        throw new IllegalStateException("Reward equipment selection fell through");
    }

    public static long rewardEquipmentSeed(
            long rewardSeed,
            UUID rewardId,
            UUID instanceId,
            int rollOrdinal
    ) {
        if (rewardId == null || instanceId == null || rollOrdinal < 0) {
            throw new IllegalArgumentException("Invalid reward equipment seed inputs");
        }
        long mixed = mix(rewardSeed, REWARD_EQUIPMENT_SALT);
        mixed = mix(mixed, rewardId.getMostSignificantBits());
        mixed = mix(mixed, rewardId.getLeastSignificantBits());
        mixed = mix(mixed, instanceId.getMostSignificantBits());
        mixed = mix(mixed, instanceId.getLeastSignificantBits());
        return mix(mixed, 0x9E37_79B9_7F4A_7C15L * (rollOrdinal + 1L));
    }

    private static long mix(long seed, long salt) {
        long value = seed ^ salt;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static long boundedLong(Random random, long bound) {
        long bits;
        long value;
        do {
            bits = random.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1L) < 0L);
        return value;
    }
}
