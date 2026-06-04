package io.github.naimjeg.obeliskdepths.client.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import io.github.naimjeg.damagenexus.api.item.template.DamageItemTemplateReferences;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.client.tooltip.DamageNexusClientTooltips;
import io.github.naimjeg.damagenexus.client.tooltip.DamageTooltipRenderer;
import io.github.naimjeg.damagenexus.client.tooltip.RulePhraseRenderer;
import io.github.naimjeg.damagenexus.client.tooltip.TooltipDetailLevel;
import io.github.naimjeg.damagenexus.client.tooltip.TooltipPresentationPolicy;
import io.github.naimjeg.damagenexus.client.tooltip.document.DamageTooltipDocument;
import io.github.naimjeg.damagenexus.client.tooltip.document.DamageTooltipDocumentPlanner;
import io.github.naimjeg.damagenexus.client.tooltip.narrative.RuleNarrativePlanner;
import io.github.naimjeg.damagenexus.config.TooltipDebugLevel;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(
        modid = ObeliskDepths.MOD_ID,
        value = Dist.CLIENT
)
public final class ObeliskUniqueEquipmentTooltipHandler {
    private ObeliskUniqueEquipmentTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        DamageEntryDefinition presentation =
                ObeliskUniqueEquipmentTooltipLogic.resolveForPresentation(
                        event.getItemStack()
                ).orElse(null);
        if (presentation == null) {
            return;
        }

        var registry = DamageNexusClientTooltips.registry();
        RuleNarrativePlanner narratives = new RuleNarrativePlanner(registry);
        DamageTooltipDocument document = new DamageTooltipDocumentPlanner(narratives)
                .plan(
                        List.of(presentation),
                        List.of(),
                        List.of(),
                        DamageItemTemplateReferences.EMPTY,
                        TooltipDebugLevel.OFF
                );
        if (document.isEmpty()) {
            return;
        }

        TooltipDetailLevel detailLevel =
                event.getFlags().hasShiftDown() || isShiftDown()
                        ? TooltipDetailLevel.EXPANDED
                        : TooltipDetailLevel.COMPACT;
        DamageTooltipRenderer renderer = new DamageTooltipRenderer(
                narratives,
                new RulePhraseRenderer(registry)
        );
        renderer.render(
                event.getToolTip(),
                document,
                new TooltipPresentationPolicy(detailLevel, TooltipDebugLevel.OFF)
        );
    }

    private static boolean isShiftDown() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return false;
        }
        Window window = minecraft.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
}
