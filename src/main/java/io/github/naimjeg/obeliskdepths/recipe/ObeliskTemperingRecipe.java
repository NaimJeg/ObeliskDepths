package io.github.naimjeg.obeliskdepths.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.registry.ModRecipeSerializers;
import io.github.naimjeg.obeliskdepths.registry.ModRecipeTypes;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingRoller;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ObeliskTemperingRecipe(
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
) implements Recipe<ObeliskTemperingRecipeInput> {
    private static final RecipeBookCategory RECIPE_BOOK_CATEGORY =
            new RecipeBookCategory();

    public static final MapCodec<ObeliskTemperingRecipe> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Ingredient.CODEC
                            .fieldOf("weapon")
                            .forGetter(ObeliskTemperingRecipe::weapon),
                    Ingredient.CODEC
                            .fieldOf("template")
                            .forGetter(ObeliskTemperingRecipe::template),
                    Ingredient.CODEC
                            .optionalFieldOf("ingredient")
                            .forGetter(ObeliskTemperingRecipe::ingredient),
                    Identifier.CODEC
                            .fieldOf("pool")
                            .forGetter(ObeliskTemperingRecipe::pool),
                    ExtraCodecs.POSITIVE_INT
                            .optionalFieldOf("min_tier", 1)
                            .forGetter(ObeliskTemperingRecipe::minTier),
                    Codec.INT
                            .optionalFieldOf("max_tier", 0)
                            .forGetter(ObeliskTemperingRecipe::maxTier),
                    ExtraCodecs.POSITIVE_INT
                            .optionalFieldOf("min_rolls", 1)
                            .forGetter(ObeliskTemperingRecipe::minRolls),
                    Codec.INT
                            .optionalFieldOf("max_rolls", 0)
                            .forGetter(ObeliskTemperingRecipe::maxRolls),
                    Codec.FLOAT
                            .optionalFieldOf("weight_multiplier", 1.0F)
                            .forGetter(ObeliskTemperingRecipe::weightMultiplier),
                    Codec.BOOL
                            .optionalFieldOf("replace_existing", false)
                            .forGetter(ObeliskTemperingRecipe::replaceExisting),
                    Identifier.CODEC
                            .listOf()
                            .fieldOf("directions")
                            .forGetter(ObeliskTemperingRecipe::directions)
            ).apply(instance, ObeliskTemperingRecipe::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ObeliskTemperingRecipe
            > STREAM_CODEC =
            StreamCodec.of(
                    ObeliskTemperingRecipe::encode,
                    ObeliskTemperingRecipe::decode
            );

    public ObeliskTemperingRecipe {
        Objects.requireNonNull(weapon, "weapon must not be null");
        Objects.requireNonNull(template, "template must not be null");
        ingredient = ingredient == null ? Optional.empty() : ingredient;
        Objects.requireNonNull(pool, "pool must not be null");
        minTier = Math.max(1, minTier);
        maxTier = maxTier <= 0 ? minTier : Math.max(minTier, maxTier);
        minRolls = Math.max(1, minRolls);
        maxRolls = maxRolls <= 0 ? minRolls : Math.max(minRolls, maxRolls);
        weightMultiplier = Math.max(0.0F, weightMultiplier);
        directions = normalizeDirections(directions);
    }

    @Override
    public boolean matches(ObeliskTemperingRecipeInput input, Level level) {
        return this.matchesBase(input, level);
    }

    /**
     * Matches recipe inputs that are independent of direction. Direction is
     * intentionally excluded so all base matches can be reverse-aggregated into
     * virtual direction pools.
     */
    public boolean matchesBase(
            ObeliskTemperingRecipeInput input,
            Level level
    ) {
        if (!this.weapon.test(input.weapon())) {
            return false;
        }

        if (!this.template.test(input.template())) {
            return false;
        }

        if (this.ingredient.isPresent()) {
            if (!this.ingredient.get().test(input.ingredient())) {
                return false;
            }
        } else if (!input.ingredient().isEmpty()) {
            return false;
        }

        if (!TemperingTemplateItems.isTemperingTemplate(
                input.template()
        )) {
            return false;
        }

        TemperingTemplateData templateData =
                TemperingTemplateItems.getOrDefault(
                        input.template()
                );

        if (templateData.tier() < this.minTier
                || templateData.tier() > this.maxTier) {
            return false;
        }

        return ObeliskTemperingRoller.canTemper(
                input.weapon(),
                this.replaceExisting
        );
    }

    public boolean supportsDirection(Identifier directionId) {
        return directionId != null && this.directions.contains(directionId);
    }

    @Override
    public ItemStack assemble(ObeliskTemperingRecipeInput input) {
        return ObeliskTemperingRoller.canTemper(
                input.weapon(),
                this.replaceExisting
        ) ? input.weapon().copyWithCount(1) : ItemStack.EMPTY;
    }

    public int computeRolls(
            TemperingTemplateData templateData,
            RandomSource random
    ) {
        int min = Math.max(1, this.minRolls);
        int max = Math.max(min, this.maxRolls);

        if (min == max) {
            return min;
        }

        float clampedWeight = Mth.clamp(
                templateData.weight() * this.weightMultiplier,
                0.0F,
                1.0F
        );

        int range = max - min;
        int guaranteedBonus = Mth.floor(range * clampedWeight);
        float fractionalChance = range * clampedWeight - guaranteedBonus;
        int rolls = min + guaranteedBonus;

        if (rolls < max && random.nextFloat() < fractionalChance) {
            rolls++;
        }

        return Mth.clamp(rolls, min, max);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.createFromOptionals(List.of(
                Optional.of(this.weapon),
                Optional.of(this.template),
                this.ingredient
        ));
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RECIPE_BOOK_CATEGORY;
    }

    @Override
    public RecipeSerializer<? extends Recipe<ObeliskTemperingRecipeInput>> getSerializer() {
        return ModRecipeSerializers.OBELISK_TEMPERING.get();
    }

    @Override
    public RecipeType<? extends Recipe<ObeliskTemperingRecipeInput>> getType() {
        return ModRecipeTypes.OBELISK_TEMPERING.get();
    }

    private static List<Identifier> normalizeDirections(
            List<Identifier> directions
    ) {
        if (directions == null || directions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Obelisk tempering recipe must declare at least one direction"
            );
        }

        Set<Identifier> normalized = new LinkedHashSet<>();

        for (Identifier direction : directions) {
            if (direction != null) {
                normalized.add(direction);
            }
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Obelisk tempering recipe must declare at least one non-null direction"
            );
        }

        return List.copyOf(normalized);
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            ObeliskTemperingRecipe recipe
    ) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.weapon);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template);
        ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC)
                .encode(buffer, recipe.ingredient);
        Identifier.STREAM_CODEC.encode(buffer, recipe.pool);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.minTier);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.maxTier);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.minRolls);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.maxRolls);
        ByteBufCodecs.FLOAT.encode(buffer, recipe.weightMultiplier);
        ByteBufCodecs.BOOL.encode(buffer, recipe.replaceExisting);
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.directions.size());

        for (Identifier direction : recipe.directions) {
            Identifier.STREAM_CODEC.encode(buffer, direction);
        }
    }

    private static ObeliskTemperingRecipe decode(
            RegistryFriendlyByteBuf buffer
    ) {
        Ingredient weapon =
                Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Ingredient template =
                Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Optional<Ingredient> ingredient =
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC)
                        .decode(buffer);
        Identifier pool = Identifier.STREAM_CODEC.decode(buffer);
        int minTier = ByteBufCodecs.VAR_INT.decode(buffer);
        int maxTier = ByteBufCodecs.VAR_INT.decode(buffer);
        int minRolls = ByteBufCodecs.VAR_INT.decode(buffer);
        int maxRolls = ByteBufCodecs.VAR_INT.decode(buffer);
        float weightMultiplier = ByteBufCodecs.FLOAT.decode(buffer);
        boolean replaceExisting = ByteBufCodecs.BOOL.decode(buffer);
        int directionCount = Math.max(0, ByteBufCodecs.VAR_INT.decode(buffer));
        List<Identifier> directions = new ArrayList<>();

        for (int i = 0; i < directionCount; i++) {
            directions.add(Identifier.STREAM_CODEC.decode(buffer));
        }

        return new ObeliskTemperingRecipe(
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
    }
}
