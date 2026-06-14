package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import io.github.naimjeg.obeliskdepths.tempering.BuiltinTemperingPools;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class ModRecipeProvider extends RecipeProvider {

    private final HolderLookup.Provider registries;

    private ModRecipeProvider(
            HolderLookup.Provider registries,
            RecipeOutput output
    ) {
        super(registries, output);
        this.registries = registries;
    }

    @Override
    protected void buildRecipes() {
        ModBlocks.STONE_BLOCK_SETS.forEach(this::addStoneBlockSetRecipes);
        ModBlocks.WOOD_BLOCK_SETS.forEach(this::addWoodBlockSetRecipes);

        this.addObeliskTemperingRecipes();
        this.addTemperingProgressionRecipes();
    }

    private void addObeliskTemperingRecipes() {
        Ingredient weapon = Ingredient.of(
                this.registries
                        .lookupOrThrow(Registries.ITEM)
                        .getOrThrow(
                                ModTags.Items.TEMPERABLE_WEAPONS
                        )
        );

        Ingredient template = Ingredient.of(
                ModItems.TEMPERING_SMITHING_TEMPLATE.get()
        );

        this.saveTemperingRecipe(
                "tempering/balance_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.BALANCE_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.BALANCE
                )
        );

        /*
         * Keep the original generated recipe id to avoid breaking datapacks that
         * referenced the first tier-1 edge contribution. Do not also emit
         * tempering/edge_tier_1, because both recipes would base-match and double
         * all edge pool weights.
         */
        this.saveTemperingRecipe(
                "tempering/sword_edge_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.EDGE_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.EDGE
                )
        );

        this.saveTemperingRecipe(
                "tempering/flame_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.FLAME_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.FLAME
                )
        );

        this.saveTemperingRecipe(
                "tempering/frost_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.FROST_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.FROST
                )
        );

        this.saveTemperingRecipe(
                "tempering/storm_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.STORM_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.STORM
                )
        );

        this.saveTemperingRecipe(
                "tempering/arcane_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.ARCANE_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.ARCANE
                )
        );

        this.saveTemperingRecipe(
                "tempering/venom_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.VENOM_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.VENOM
                )
        );

        this.saveTemperingRecipe(
                "tempering/precision_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.PRECISION_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.PRECISION
                )
        );

        this.saveTemperingRecipe(
                "tempering/hunt_tier_1",
                weapon,
                template,
                Optional.of(
                        Ingredient.of(Items.ECHO_SHARD)
                ),
                BuiltinTemperingPools.HUNT_TIER_1,
                1,
                1,
                1,
                1,
                1.0F,
                false,
                List.of(
                        ObeliskTemperingDirectionRegistry.HUNT
                )
        );
    }

    private void saveTemperingRecipe(
            String path,
            Ingredient weapon,
            Ingredient template,
            Optional<Ingredient> ingredient,
            Identifier pool,
            int minTier,
            int maxTier,
            int minRolls,
            int maxRolls,
            float weightMultiplier,
            boolean replaceExisting,
            List<Identifier> directions
    ) {
        ObeliskTemperingRecipe recipe = new ObeliskTemperingRecipe(
                weapon,
                template,
                ingredient,
                pool,
                minTier,
                maxTier,
                minRolls,
                maxRolls,
                weightMultiplier,
                replaceExisting,
                directions
        );

        this.output.accept(
                recipeKey(path),
                recipe,
                null
        );
    }

    private void addStoneBlockSetRecipes(
            ModBlocks.StoneBlockSet set
    ) {
        Block base = set.base().get();
        Block slab = set.slab().get();
        Block stairs = set.stairs().get();
        Block wall = set.wall().get();

        this.slab(
                RecipeCategory.BUILDING_BLOCKS,
                slab,
                base
        );

        this.stairBuilder(stairs, Ingredient.of(base))
                .unlockedBy(getHasName(base), this.has(base))
                .save(this.output);

        this.wall(
                RecipeCategory.DECORATIONS,
                wall,
                base
        );

        this.stonecutterResultFromBase(
                RecipeCategory.BUILDING_BLOCKS,
                slab,
                base,
                2
        );

        this.stonecutterResultFromBase(
                RecipeCategory.BUILDING_BLOCKS,
                stairs,
                base
        );

        this.stonecutterResultFromBase(
                RecipeCategory.DECORATIONS,
                wall,
                base
        );
    }

    private void addWoodBlockSetRecipes(
            ModBlocks.WoodBlockSet set
    ) {
        Block planks = set.planks().get();

        this.planksFromLogs(
                planks,
                ModTags.Items.GREAT_SWAMP_TAXODIUM_LOGS,
                4
        );
        this.woodFromLogs(
                set.wood().get(),
                set.log().get()
        );
        this.woodFromLogs(
                set.strippedWood().get(),
                set.strippedLog().get()
        );

        this.stairBuilder(set.stairs().get(), Ingredient.of(planks))
                .unlockedBy(getHasName(planks), this.has(planks))
                .save(this.output);
        this.slab(
                RecipeCategory.BUILDING_BLOCKS,
                set.slab().get(),
                planks
        );
        this.fenceBuilder(set.fence().get(), Ingredient.of(planks))
                .unlockedBy(getHasName(planks), this.has(planks))
                .save(this.output);
        this.fenceGateBuilder(set.fenceGate().get(), Ingredient.of(planks))
                .unlockedBy(getHasName(planks), this.has(planks))
                .save(this.output);
        this.doorBuilder(set.door().get(), Ingredient.of(planks))
                .unlockedBy(getHasName(planks), this.has(planks))
                .save(this.output);
        this.trapdoorBuilder(set.trapdoor().get(), Ingredient.of(planks))
                .unlockedBy(getHasName(planks), this.has(planks))
                .save(this.output);
//        this.pressurePlate(
//                set.pressurePlate().get(),
//                planks
//        );
//        this.buttonBuilder(set.button().get(), Ingredient.of(planks))
//                .unlockedBy(getHasName(planks), this.has(planks))
//                .save(this.output);
//        this.signBuilder(set.signItem().get(), Ingredient.of(planks))
//                .unlockedBy(getHasName(planks), this.has(planks))
//                .save(this.output);
//        this.hangingSign(
//                set.hangingSignItem().get(),
//                set.strippedLog().get()
//        );
    }

    private static Identifier modId(String path) {
        return Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                path
        );
    }

    private static ResourceKey<Recipe<?>> recipeKey(String path) {
        return ResourceKey.create(
                Registries.RECIPE,
                modId(path)
        );
    }

    public static final class Runner extends RecipeProvider.Runner {
        public Runner(
                PackOutput output,
                CompletableFuture<HolderLookup.Provider> registries
        ) {
            super(output, registries);
        }

        @Override
        public String getName() {
            return "Obelisk Depths Recipes";
        }

        @Override
        protected RecipeProvider createRecipeProvider(
                HolderLookup.Provider registries,
                RecipeOutput output
        ) {
            return new ModRecipeProvider(registries, output);
        }
    }

    private void addTemperingProgressionRecipes() {
        ShapedRecipeBuilder.shaped(
                        this.registries.lookupOrThrow(Registries.ITEM),
                        RecipeCategory.DECORATIONS,
                        ModBlocks.OBELISK_SMITHING_TABLE_ITEM.get()
                )
                .pattern("OEO")
                .pattern("STS")
                .pattern("OEO")
                .define('O', Items.OBSIDIAN)
                .define('E', Items.ECHO_SHARD)
                .define('S', Items.SMOOTH_STONE)
                .define('T', Items.SMITHING_TABLE)
                .unlockedBy(
                        "has_echo_shard",
                        this.has(Items.ECHO_SHARD)
                )
                .save(this.output);

        ShapedRecipeBuilder.shaped(
                        this.registries.lookupOrThrow(Registries.ITEM),
                        RecipeCategory.MISC,
                        ModItems.TEMPERING_SMITHING_TEMPLATE.get()
                )
                .pattern("DED")
                .pattern("ESP")
                .pattern("DED")
                .define('D', Items.DIAMOND)
                .define('E', Items.ECHO_SHARD)
                .define('S', Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)
                .define('P', Items.PAPER)
                .unlockedBy(
                        "has_echo_shard",
                        this.has(Items.ECHO_SHARD)
                )
                .save(this.output);
    }
}
