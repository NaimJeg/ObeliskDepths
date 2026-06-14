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
                "Steady weapon tempering with reliable general damage."
        );
        add(
                "tempering_direction.obeliskdepths.edge",
                "Edge"
        );
        add(
                "tempering_direction.obeliskdepths.edge.description",
                "Physical offense, armor pressure, and finishing power."
        );
        add(
                "tempering_direction.obeliskdepths.flame",
                "Flame"
        );
        add(
                "tempering_direction.obeliskdepths.flame.description",
                "Fire damage and burning-target pressure."
        );
        add(
                "tempering_direction.obeliskdepths.frost",
                "Frost"
        );
        add(
                "tempering_direction.obeliskdepths.frost.description",
                "Cold damage and physical-to-cold conversion."
        );
        add(
                "tempering_direction.obeliskdepths.storm",
                "Storm"
        );
        add(
                "tempering_direction.obeliskdepths.storm.description",
                "Lightning and kinetic damage for forceful strikes."
        );
        add(
                "tempering_direction.obeliskdepths.arcane",
                "Arcane"
        );
        add(
                "tempering_direction.obeliskdepths.arcane.description",
                "Magic damage and spellblade-style damage gains."
        );
        add(
                "tempering_direction.obeliskdepths.venom",
                "Venom"
        );
        add(
                "tempering_direction.obeliskdepths.venom.description",
                "Poison, wither, and toxic weapon conversion."
        );
        add(
                "tempering_direction.obeliskdepths.precision",
                "Precision"
        );
        add(
                "tempering_direction.obeliskdepths.precision.description",
                "Critical hits and opening-strike pressure."
        );
        add(
                "tempering_direction.obeliskdepths.hunt",
                "Hunt"
        );
        add(
                "tempering_direction.obeliskdepths.hunt.description",
                "Boss hunting and weakened-target execution."
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
        addTemperingEntry(
                "tempered",
                "Tempered",
                "+3 physical damage",
                "A steady strike mark with no fragile condition."
        );
        addTemperingEntry(
                "brutal",
                "Brutal",
                "+10% global damage",
                "A blunt force mark that pushes every hit harder."
        );
        addTemperingEntry(
                "razor_edged",
                "Razor Edged",
                "+12% physical damage",
                "A clean edge mark for weapons that solve problems directly."
        );
        addTemperingEntry(
                "piercing",
                "Piercing",
                "+1.5 physical true damage",
                "A narrow point of force that slips past mitigation."
        );
        addTemperingEntry(
                "sundering",
                "Sundering",
                "-12% target armor effectiveness",
                "A breaker mark that makes armor answer less loudly."
        );
        addTemperingEntry(
                "executioners",
                "Executioner's",
                "+20% physical damage below 35% target health",
                "A finishing mark that leans into a weakened enemy."
        );
        addTemperingEntry(
                "flaming",
                "Flaming",
                "+3 fire damage",
                "A direct ember mark that adds reliable fire damage."
        );
        addTemperingEntry(
                "flameforged",
                "Flameforged",
                "Converts 20% physical damage to fire",
                "A furnace-born mark that changes part of the blade's bite."
        );
        addTemperingEntry(
                "smoldering",
                "Smoldering",
                "+15% global damage against burning targets",
                "A patient heat mark that rewards keeping enemies burning."
        );
        addTemperingEntry(
                "frostbound",
                "Frostbound",
                "+3 cold damage",
                "A cold mark that lays winter into the weapon's edge."
        );
        addTemperingEntry(
                "frostforged",
                "Frostforged",
                "Converts 20% physical damage to cold",
                "A pale forge mark that turns impact into chill."
        );
        addTemperingEntry(
                "stormcharged",
                "Stormcharged",
                "+3 lightning damage",
                "A charged mark that snaps through the strike."
        );
        addTemperingEntry(
                "stormforged",
                "Stormforged",
                "Converts 18% physical damage to lightning",
                "A storm mark that turns force into a hard flash."
        );
        addTemperingEntry(
                "impacting",
                "Impacting",
                "+2.5 kinetic damage",
                "A concussive mark that adds blunt momentum."
        );
        addTemperingEntry(
                "arcane",
                "Arcane",
                "+3 magic damage",
                "A focused sigil that threads magic through the hit."
        );
        addTemperingEntry(
                "spellblade",
                "Spellblade",
                "Gain 15% physical damage as magic",
                "A blade-and-sigil mark that echoes force as magic."
        );
        addTemperingEntry(
                "venomous",
                "Venomous",
                "+3 poison damage",
                "A toxin mark that leaves a bitter cut."
        );
        addTemperingEntry(
                "toxic_edge",
                "Toxic Edge",
                "Gain 15% physical damage as poison",
                "A coated-edge mark that makes clean cuts turn toxic."
        );
        addTemperingEntry(
                "withering",
                "Withering",
                "+2 wither damage",
                "A fading mark that carries a dry, ruinous bite."
        );
        addTemperingEntry(
                "deadly",
                "Deadly",
                "+20% physical damage on critical hits",
                "A precise mark that rewards decisive timing."
        );
        addTemperingEntry(
                "ambushers",
                "Ambusher's",
                "+18% global damage above 80% target health",
                "An opening-strike mark made for the first clean hit."
        );
        addTemperingEntry(
                "giant_slayers",
                "Giant Slayer's",
                "+20% global damage against bosses",
                "A hunter's mark built for targets that should not stand."
        );
        addTemperingEntry(
                "fire_edge",
                "Fire Edge",
                "+4 fire damage",
                "A tempering mark that burns through the weapon edge."
        );
        add(
                "entry.obeliskdepths.fire_edge.tooltip.1",
                "+15% fire damage"
        );
        addTemperingEntry(
                "critical_edge",
                "Critical Edge",
                "+20% physical damage on critical hits",
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

    private void addTemperingEntry(
            String key,
            String name,
            String tooltip,
            String flavor
    ) {
        String prefix = "entry." + ObeliskDepths.MOD_ID + "." + key;
        add(prefix + ".name", name);
        add(prefix + ".tooltip.0", tooltip);
        add(prefix + ".flavor", flavor);
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
