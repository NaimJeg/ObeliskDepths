package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.registry.ModDataComponents;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.world.item.ItemStack;

public final class TemperingTemplateItems {
    private TemperingTemplateItems() {
    }

    public static boolean isTemperingTemplate(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && stack.is(ModItems.TEMPERING_SMITHING_TEMPLATE.get());
    }

    /**
     * Checks whether explicit tier/weight data is stored.
     */
    public static boolean hasTemplateData(ItemStack stack) {
        return isTemperingTemplate(stack)
                && stack.has(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get()
        );
    }

    /**
     * A component-less tempering template is a valid tier-1 template.
     */
    public static TemperingTemplateData getOrDefault(
            ItemStack stack
    ) {
        TemperingTemplateData data = stack.get(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get()
        );

        return data != null
                ? data
                : new TemperingTemplateData(1, 0.0F);
    }

    public static ItemStack createTemplate(
            int tier,
            float weight
    ) {
        ItemStack stack = new ItemStack(
                ModItems.TEMPERING_SMITHING_TEMPLATE.get()
        );

        stack.set(
                ModDataComponents.TEMPERING_TEMPLATE_DATA.get(),
                new TemperingTemplateData(tier, weight)
        );

        return stack;
    }
}
