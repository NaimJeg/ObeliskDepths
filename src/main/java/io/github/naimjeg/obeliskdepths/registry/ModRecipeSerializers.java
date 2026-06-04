package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(
                    Registries.RECIPE_SERIALIZER,
                    ObeliskDepths.MOD_ID
            );

    public static final Supplier<RecipeSerializer<ObeliskTemperingRecipe>> OBELISK_TEMPERING =
            RECIPE_SERIALIZERS.register("obelisk_tempering", () ->
                    new RecipeSerializer<>(
                            ObeliskTemperingRecipe.CODEC,
                            ObeliskTemperingRecipe.STREAM_CODEC
                    )
            );

    private ModRecipeSerializers() {
    }

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
