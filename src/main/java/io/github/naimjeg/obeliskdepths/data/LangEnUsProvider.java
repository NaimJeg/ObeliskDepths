package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.LanguageProvider;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Locale;
import java.util.Set;

public final class LangEnUsProvider extends LanguageProvider {
    private static final String LOCALE = "en_us";

    /*
     * IDs excluded from automatic block name generation.
     *
     * Use this when:
     * - the generated name is awkward,
     * - the block should be hidden,
     * - the block needs a lore-specific display name,
     * - or the block should be translated manually.
     */
    private static final Set<Identifier> EXCLUDED_BLOCK_IDS = Set.of(
            // Example:
            // ModBlocks.DUNGEON_VINE.getId()
    );

    /*
     * IDs excluded from automatic item name generation.
     *
     * BlockItem translations are usually covered by addBlock(...).
     * This list is mostly for standalone items from ModItems.
     */
    private static final Set<Identifier> EXCLUDED_ITEM_IDS = Set.of(
            // Example:
            // ModItems.TEMPERING_SMITHING_TEMPLATE.getId()
    );

    public LangEnUsProvider(PackOutput output) {
        super(output, ObeliskDepths.MOD_ID, LOCALE);
    }

    @Override
    protected void addTranslations() {
        addCreativeTabs();

        addGeneratedBlockNames();

        addGeneratedItemNames();

        addManualOverrides();
    }

    private void addCreativeTabs() {
        add(
                "itemGroup." + ObeliskDepths.MOD_ID + ".building_blocks",
                "Obelisk Depths Building Blocks"
        );

        add(
                "itemGroup." + ObeliskDepths.MOD_ID + ".obelisk_items",
                "Obelisk Depths Items"
        );
    }

    private void addGeneratedBlockNames() {
        addGeneratedBlock(ModBlocks.OBELISK);
        addGeneratedBlock(ModBlocks.OBELISK_SMITHING_TABLE);

        ModBlocks.STONE_BLOCK_SETS.forEach(set ->
                set.blocks().forEach(this::addGeneratedBlock)
        );

        addGeneratedBlock(ModBlocks.REINFORCED_DUNGEON_STONE);
        addGeneratedBlock(ModBlocks.DUNGEON_CRACKED_TILES);
        addGeneratedBlock(ModBlocks.DUNGEON_CRACKED_BRICKS);

        addGeneratedBlock(ModBlocks.GREAT_SWAMP_GRASS_BLOCK);
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_COARSE_DIRT);
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_MUD);
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_DIRT);
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_ROOTED_DIRT);
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_VINES);
        ModBlocks.WOOD_BLOCK_SETS.forEach(set -> {
            set.blocks().forEach(this::addGeneratedBlock);
//            addGeneratedItem(set.signItem());
//            addGeneratedItem(set.hangingSignItem());
        });
        addGeneratedBlock(ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE);

        addGeneratedBlock(ModBlocks.DUNGEON_LAMP);
    }

    private void addGeneratedItemNames() {
        //addGeneratedItem(ModItems.TEMPERING_SMITHING_TEMPLATE);
    }

    private void addManualOverrides() {

        add(ModItems.TEMPERING_SMITHING_TEMPLATE.get(), "Tempering Upgrade");
        add(
                "container.obeliskdepths.obelisk_tempering",
                "Obelisk Tempering"
        );
        add(
                "gui.obeliskdepths.tempering.invalid_recipe",
                "No matching tempering recipe"
        );
        add(
                "gui.obeliskdepths.tempering.directions",
                "Directions"
        );
        add(
                "gui.obeliskdepths.tempering.possible_affixes",
                "Possible Affixes"
        );
        add(
                "gui.obeliskdepths.tempering.preview_unavailable",
                "Pool preview unavailable"
        );
        add(
                "gui.obeliskdepths.tempering.no_possible_affixes",
                "No possible affixes"
        );
        add(
                "tempering_direction.obeliskdepths.balance",
                "Balance"
        );
        add(
                "tempering_direction.obeliskdepths.balance.description",
                "Balanced tempering draws from all matching balanced pools."
        );
        add(
                "tempering_direction.obeliskdepths.edge",
                "Edge"
        );
        add(
                "tempering_direction.obeliskdepths.edge.description",
                "Edge tempering favors direct offensive affixes."
        );
        add(
                "tempering_direction.obeliskdepths.guard",
                "Guard"
        );
        add(
                "tempering_direction.obeliskdepths.guard.description",
                "Guard tempering favors defensive affixes."
        );
        add(
                "tempering_direction.obeliskdepths.echo",
                "Echo"
        );
        add(
                "tempering_direction.obeliskdepths.echo.description",
                "Echo tempering favors unusual resonance affixes."
        );
        add(
                "tooltip.obeliskdepths.tempering_template.tier",
                "Tempering Tier: %s"
        );
        add(
                "tooltip.obeliskdepths.tempering_template.weight",
                "Tempering Weight: %s"
        );
        add(
                "entry.obeliskdepths.fire_edge.name",
                "Fire Edge"
        );
        add(
                "entry.obeliskdepths.fire_edge.flavor",
                "A tempering mark that burns through the weapon edge."
        );
        add(
                "entry.obeliskdepths.critical_edge.name",
                "Critical Edge"
        );
        add(
                "entry.obeliskdepths.critical_edge.flavor",
                "A tempering mark that rewards clean decisive strikes."
        );

        add(
                "container.obeliskdepths.obelisk_portal",
                "Obelisk Portal"
        );
        add(
                "gui.obeliskdepths.portal.mode.solo",
                "Solo"
        );
        add(
                "gui.obeliskdepths.portal.mode.party_open",
                "Party"
        );
        add(
                "gui.obeliskdepths.portal.start",
                "Start"
        );
        add(
                "gui.obeliskdepths.portal.tribute",
                "Tribute"
        );
        add(
                "gui.obeliskdepths.portal.selected.solo",
                "Selected: Solo portal"
        );
        add(
                "gui.obeliskdepths.portal.selected.party_open",
                "Selected: Party portal"
        );
        add(
                "gui.obeliskdepths.portal.note",
                "Mode controls portal entry only."
        );
        add(
                "gui.obeliskdepths.portal.loading",
                "Preparing dungeon..."
        );
        add(
                "gui.obeliskdepths.portal.failed",
                "Activation failed."
        );
        add(
                "event.obeliskdepths.dungeon_raid",
                "Dungeon Raid — Kills %1$s/%2$s"
        );
        add(
                "message.obeliskdepths.obelisk.no_dimension",
                "ObeliskDepths dimension was not found."
        );
        add(
                "message.obeliskdepths.obelisk.inside_dungeon_denied",
                "Obelisks cannot be used inside the dungeon."
        );
        add(
                "message.obeliskdepths.obelisk.invalid_tribute",
                "Invalid tribute."
        );
        add(
                "message.obeliskdepths.obelisk.invalid_obelisk",
                "The obelisk is no longer valid."
        );
        add(
                "message.obeliskdepths.obelisk.activation_failed",
                "Failed to open dungeon."
        );
        /*
         * Manual translations go here.
         *
         * Do not add a key here if it was already generated above,
         * because LanguageProvider throws on duplicate translation keys.
         */

        // Example:
        // add(ModBlocks.OBELISK.get(), "Depth Obelisk");

        // Example:
        // add(ModItems.TEMPERING_SMITHING_TEMPLATE.get(), "Obelisk Tempering Smithing Template");
    }

    private void addGeneratedBlock(DeferredBlock<? extends Block> block) {
        Identifier id = block.getId();

        if (EXCLUDED_BLOCK_IDS.contains(id)) {
            return;
        }

        addBlock(block, nameFromId(id));
    }

    private void addGeneratedItem(DeferredItem<? extends Item> item) {
        Identifier id = item.getId();

        if (EXCLUDED_ITEM_IDS.contains(id)) {
            return;
        }

        addItem(item, nameFromId(id));
    }

    private static String nameFromId(Identifier id) {
        return nameFromPath(id.getPath());
    }

    private static String nameFromPath(String path) {
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(capitalize(word));
        }

        return result.toString();
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }

        if (word.length() == 1) {
            return word.toUpperCase(Locale.ROOT);
        }

        return word.substring(0, 1).toUpperCase(Locale.ROOT)
                + word.substring(1).toLowerCase(Locale.ROOT);
    }
}
