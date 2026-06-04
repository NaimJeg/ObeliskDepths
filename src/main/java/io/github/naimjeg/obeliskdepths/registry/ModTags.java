package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    private ModTags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> GREAT_SWAMP_TAXODIUM_LOGS =
                create("great_swamp_taxodium_logs");

        private Blocks() {
        }

        private static TagKey<Block> create(String path) {
            return TagKey.create(
                    Registries.BLOCK,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            path
                    )
            );
        }
    }

    public static final class Items {
        public static final TagKey<Item> GREAT_SWAMP_TAXODIUM_LOGS =
                create("great_swamp_taxodium_logs");

        public static final TagKey<Item> TEMPERABLE_WEAPONS =
                create("temperable_weapons");

        public static final TagKey<Item> REWARD_WEAPONS_TIER_1 =
                create("rewards/weapons/tier_1");
        public static final TagKey<Item> REWARD_WEAPONS_TIER_2 =
                create("rewards/weapons/tier_2");
        public static final TagKey<Item> REWARD_WEAPONS_TIER_3 =
                create("rewards/weapons/tier_3");
        public static final TagKey<Item> REWARD_WEAPONS_TIER_4 =
                create("rewards/weapons/tier_4");
        public static final TagKey<Item> REWARD_ARMOR_TIER_1 =
                create("rewards/armor/tier_1");
        public static final TagKey<Item> REWARD_ARMOR_TIER_2 =
                create("rewards/armor/tier_2");
        public static final TagKey<Item> REWARD_ARMOR_TIER_3 =
                create("rewards/armor/tier_3");
        public static final TagKey<Item> REWARD_ARMOR_TIER_4 =
                create("rewards/armor/tier_4");

        private Items() {
        }

        public static TagKey<Item> rewardWeapons(int tier) {
            return switch (tier) {
                case 1 -> REWARD_WEAPONS_TIER_1;
                case 2 -> REWARD_WEAPONS_TIER_2;
                case 3 -> REWARD_WEAPONS_TIER_3;
                default -> REWARD_WEAPONS_TIER_4;
            };
        }

        public static TagKey<Item> rewardArmor(int tier) {
            return switch (tier) {
                case 1 -> REWARD_ARMOR_TIER_1;
                case 2 -> REWARD_ARMOR_TIER_2;
                case 3 -> REWARD_ARMOR_TIER_3;
                default -> REWARD_ARMOR_TIER_4;
            };
        }

        private static TagKey<Item> create(String path) {
            return TagKey.create(
                    Registries.ITEM,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            path
                    )
            );
        }
    }
}
