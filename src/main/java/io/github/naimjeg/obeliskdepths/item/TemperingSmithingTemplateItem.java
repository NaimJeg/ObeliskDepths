package io.github.naimjeg.obeliskdepths.item;

import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

public class TemperingSmithingTemplateItem extends Item {
    public TemperingSmithingTemplateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack itemStack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Consumer<Component> builder,
            TooltipFlag tooltipFlag
    ) {
        super.appendHoverText(itemStack, context, display, builder, tooltipFlag);

        if (!TemperingTemplateItems.isTemperingTemplate(itemStack)) {
            return;
        }

        TemperingTemplateData data =
                TemperingTemplateItems.getOrDefault(itemStack);

        builder.accept(Component.translatable(
                "tooltip.obeliskdepths.tempering_template.tier",
                data.tier()
        ));
        builder.accept(Component.translatable(
                "tooltip.obeliskdepths.tempering_template.weight",
                String.format(Locale.ROOT, "%.2f", data.weight())
        ));
    }
}
