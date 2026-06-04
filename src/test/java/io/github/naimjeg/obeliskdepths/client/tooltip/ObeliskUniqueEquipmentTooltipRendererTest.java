package io.github.naimjeg.obeliskdepths.client.tooltip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.naimjeg.damagenexus.api.client.phrase.RulePhraseRegistry;
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
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentIds;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentCatalog;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentDefinition;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ObeliskUniqueEquipmentTooltipRendererTest {
    private static final List<String> RESISTANCE_CHANNELS = List.of(
            "Physical", "Fire", "Cold", "Lightning", "Magic",
            "Poison", "Wither", "Kinetic"
    );

    @Test
    void tyraelsMightCompactShowsOnlyAuthoredSummaryAndShiftHint()
            throws Exception {
        try (DamageNexusTestLanguage ignored = DamageNexusTestLanguage.install()) {
            DamageEntryDefinition presentation = tyraelsMightPresentation();
            List<String> lines = render(presentation, TooltipDetailLevel.COMPACT);
            String text = String.join("\n", lines);

            assertTrue(text.contains(
                    "+10 resistance rating to all supported damage channels"));
            assertTrue(text.contains("+4 magic damage while above 99% health"));
            assertEquals(1, occurrences(text,
                    "+10 resistance rating to all supported damage channels"));
            assertEquals(1, occurrences(text,
                    "+4 magic damage while above 99% health"));
            assertEquals(1, occurrences(text, "Hold Shift for details"));
            assertTrue(text.contains("Justice is heaviest when carried without fear."));
            assertFalse(text.contains("Conditions"));
            assertFalse(text.contains("Effects"));
            assertFalse(text.contains("Rules"));
            assertFalse(text.contains("Physical resistance"));
            assertFalse(text.contains("Attacker health"));
            assertFalse(text.contains("Tyrael's Might"));
        }
    }

    @Test
    void tyraelsMightExpandedReplacesAuthoredSummaryWithModularRules()
            throws Exception {
        try (DamageNexusTestLanguage ignored = DamageNexusTestLanguage.install()) {
            DamageEntryDefinition presentation = tyraelsMightPresentation();
            List<String> lines = render(presentation, TooltipDetailLevel.EXPANDED);
            String text = String.join("\n", lines);

            assertFalse(text.contains(
                    "+10 resistance rating to all supported damage channels"));
            assertFalse(text.contains("+4 magic damage while above 99% health"));
            assertFalse(text.contains("Hold Shift for details"));
            assertFalse(text.contains("Tyrael's Might"));
            assertTrue(text.contains("Rules"));
            assertTrue(text.contains("Conditions"));
            assertTrue(text.contains("Effects"));
            assertTrue(text.contains("attacker health is above 99%"));
            assertTrue(text.contains("Add 4 Magic damage"));
            assertEquals(8, occurrences(text, "Add 10 temporary"));
            for (String channel : RESISTANCE_CHANNELS) {
                assertEquals(1, occurrences(text, channel + " resistance"),
                        channel);
            }
            assertEquals(1, occurrences(text,
                    "Justice is heaviest when carried without fear."));
        }
    }

    private static DamageEntryDefinition tyraelsMightPresentation() {
        ObeliskUniqueEquipmentDefinition unique =
                ObeliskUniqueEquipmentCatalog.all().stream()
                        .filter(value -> value.templateId()
                                .equals(ObeliskEquipmentIds.TYRAELS_MIGHT))
                        .findFirst()
                        .orElseThrow();
        return ObeliskUniqueEquipmentTooltipLogic.withoutDisplayName(
                unique.content().definition()
        );
    }

    private static List<String> render(
            DamageEntryDefinition presentation,
            TooltipDetailLevel detail
    ) {
        RulePhraseRegistry registry = DamageNexusClientTooltips.registry();
        RuleNarrativePlanner narratives = new RuleNarrativePlanner(registry);
        DamageTooltipDocument document = new DamageTooltipDocumentPlanner(narratives)
                .plan(
                        List.of(presentation),
                        List.of(),
                        List.of(),
                        DamageItemTemplateReferences.EMPTY,
                        TooltipDebugLevel.OFF
                );
        List<Component> tooltip = new ArrayList<>();
        new DamageTooltipRenderer(
                narratives,
                new RulePhraseRenderer(registry, Locale.US)
        ).render(
                tooltip,
                document,
                new TooltipPresentationPolicy(detail, TooltipDebugLevel.OFF)
        );
        return tooltip.stream().map(Component::getString).toList();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static final class DamageNexusTestLanguage
            extends Language implements AutoCloseable {
        private final Language previous;
        private final Map<String, String> values;

        private DamageNexusTestLanguage(Map<String, String> values) {
            this.previous = Language.getInstance();
            this.values = Map.copyOf(values);
            Language.inject(this);
        }

        static DamageNexusTestLanguage install() throws IOException {
            Path path = Path.of(
                    "libs/DamageNexus/src/main/resources/assets/damagenexus/lang/"
                            + "en_us.json"
            );
            JsonObject json = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)
            ).getAsJsonObject();
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return new DamageNexusTestLanguage(values);
        }

        @Override
        public String getOrDefault(String elementId, String defaultValue) {
            return values.getOrDefault(elementId, defaultValue);
        }

        @Override
        public boolean has(String elementId) {
            return values.containsKey(elementId);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return false;
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText logicalOrderText) {
            return previous.getVisualOrder(logicalOrderText);
        }

        @Override
        public Map<String, String> getLanguageData() {
            return values;
        }

        @Override
        public void close() {
            Language.inject(previous);
        }
    }
}
