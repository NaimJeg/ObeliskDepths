package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.GreatSwampVineBlock;
import io.github.naimjeg.obeliskdepths.block.GreatSwampVinePlantBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskSmithingTableBlock;
import io.github.naimjeg.obeliskdepths.block.StrippableRotatedPillarBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ModBlocks {
    private ModBlocks() {
    }

    private static final float OBSIDIAN_HARDNESS = 50.0F;
    private static final float OBSIDIAN_RESISTANCE = 1200.0F;

    private static final float OBSIDIAN_PLUS_HARDNESS = 60.0F;
    private static final float OBSIDIAN_PLUS_RESISTANCE = 1500.0F;

    private static final float UNBREAKABLE_HARDNESS = -1.0F;
    private static final float UNBREAKABLE_RESISTANCE = 3600000.0F;
    private static final float WOOD_HARDNESS = 2.0F;
    private static final float WOOD_RESISTANCE = 3.0F;
    private static final float LEAVES_HARDNESS = 0.2F;
    private static final float ROOT_TANGLE_HARDNESS = 1.5F;
    private static final float ROOT_TANGLE_RESISTANCE = 3.0F;

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ObeliskDepths.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ObeliskDepths.MOD_ID);

    // ---------------------------------------------------------------------
    // Functional blocks
    // ---------------------------------------------------------------------

    public static final DeferredBlock<ObeliskBlock> OBELISK = BLOCKS.register(
            "obelisk",
            registryName -> new ObeliskBlock(
                    baseProperties(
                            registryName,
                            3.0F,
                            6.0F,
                            SoundType.STONE
                    ).noOcclusion()
            )
    );

    public static final DeferredItem<BlockItem> OBELISK_ITEM =
            registerBlockItem(OBELISK);

    public static final DeferredBlock<ObeliskChestBlock> OBELISK_CHEST = BLOCKS.register(
            "obelisk_chest",
            registryName -> new ObeliskChestBlock(baseProperties(
                    registryName,
                    UNBREAKABLE_HARDNESS,
                    UNBREAKABLE_RESISTANCE,
                    SoundType.WOOD
            ).noLootTable().pushReaction(PushReaction.BLOCK).noOcclusion())
    );

    public static final DeferredItem<BlockItem> OBELISK_CHEST_ITEM =
            registerBlockItem(OBELISK_CHEST);

    public static final DeferredBlock<ObeliskSmithingTableBlock> OBELISK_SMITHING_TABLE = registerBlock(
            "obelisk_smithing_table",
            2.5F,
            6.0F,
            SoundType.WOOD,
            ObeliskSmithingTableBlock::new
    );

    public static final DeferredItem<BlockItem> OBELISK_SMITHING_TABLE_ITEM =
            registerBlockItem(OBELISK_SMITHING_TABLE);

    // ---------------------------------------------------------------------
    // Dungeon stone set
    // ---------------------------------------------------------------------

    public static final StoneBlockSet DUNGEON_STONE_SET =
            registerStoneSet("dungeon_stone", OBSIDIAN_HARDNESS, OBSIDIAN_RESISTANCE);

    public static final StoneBlockSet DUNGEON_BRICKS_SET =
            registerStoneSet("dungeon_bricks", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final StoneBlockSet DUNGEON_TILES_SET =
            registerStoneSet("dungeon_tiles", OBSIDIAN_HARDNESS, OBSIDIAN_RESISTANCE);

    public static final DeferredBlock<Block> DUNGEON_STONE = DUNGEON_STONE_SET.base();
    //public static final DeferredItem<BlockItem> DUNGEON_STONE_ITEM = DUNGEON_STONE_SET.baseItem();
    public static final DeferredBlock<Block> DUNGEON_BRICKS = DUNGEON_BRICKS_SET.base();
    //public static final DeferredItem<BlockItem> DUNGEON_BRICKS_ITEM = DUNGEON_BRICKS_SET.baseItem();

    public static final DeferredBlock<Block> DUNGEON_TILES = DUNGEON_TILES_SET.base();
    public static final DeferredItem<BlockItem> DUNGEON_TILES_ITEM = DUNGEON_TILES_SET.baseItem();
//    public static final DeferredBlock<Block> DUNGEON_TILES =
//            registerStoneBlock("dungeon_tiles", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);
//
//    public static final DeferredItem<BlockItem> DUNGEON_TILES_ITEM =
//            registerBlockItem(DUNGEON_TILES);

    public static final List<StoneBlockSet> STONE_BLOCK_SETS =
            List.of(
                    DUNGEON_STONE_SET,
                    DUNGEON_BRICKS_SET,
                    DUNGEON_TILES_SET
            );

    public static final List<DeferredBlock<? extends Block>> STONE_SET_BLOCKS =
            STONE_BLOCK_SETS.stream()
                    .flatMap(set -> set.blocks().stream())
                    .toList();

    public static final List<DeferredItem<? extends BlockItem>> STONE_SET_ITEMS =
            STONE_BLOCK_SETS.stream()
                    .flatMap(set -> set.items().stream())
                    .toList();

    public static final DeferredBlock<Block> REINFORCED_DUNGEON_STONE =
            registerStoneBlock("reinforced_dungeon_stone", UNBREAKABLE_HARDNESS, UNBREAKABLE_RESISTANCE);

    public static final DeferredItem<BlockItem> REINFORCED_DUNGEON_STONE_ITEM =
            registerBlockItem(REINFORCED_DUNGEON_STONE);



    public static final DeferredBlock<Block> DUNGEON_CRACKED_TILES =
            registerStoneBlock("dungeon_cracked_tiles", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_CRACKED_TILES_ITEM =
            registerBlockItem(DUNGEON_CRACKED_TILES);

    public static final DeferredBlock<Block> DUNGEON_CRACKED_BRICKS =
            registerStoneBlock("dungeon_cracked_bricks", OBSIDIAN_PLUS_HARDNESS, OBSIDIAN_PLUS_RESISTANCE);

    public static final DeferredItem<BlockItem> DUNGEON_CRACKED_BRICKS_ITEM =
            registerBlockItem(DUNGEON_CRACKED_BRICKS);

    // ---------------------------------------------------------------------
    // Theme Style Blocks / Biome-Based Natural Blocks
    // ---------------------------------------------------------------------

    public static final DeferredBlock<Block> GREAT_SWAMP_GRASS_BLOCK = registerBlock(
            "great_swamp_grass_block",
            0.5F,
            0.5F,
            SoundType.GRASS,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_GRASS_BLOCK_ITEM =
            registerBlockItem(GREAT_SWAMP_GRASS_BLOCK);

    public static final DeferredBlock<Block> GREAT_SWAMP_COARSE_DIRT = registerBlock(
            "great_swamp_coarse_dirt",
            0.5F,
            0.5F,
            SoundType.GRAVEL,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_COARSE_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_COARSE_DIRT);

    public static final DeferredBlock<Block> GREAT_SWAMP_MUD = registerBlock(
            "great_swamp_mud",
            0.5F,
            0.5F,
            SoundType.MUD,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_MUD_ITEM =
            registerBlockItem(GREAT_SWAMP_MUD);

    public static final DeferredBlock<Block> GREAT_SWAMP_DIRT = registerBlock(
            "great_swamp_dirt",
            0.5F,
            0.5F,
            SoundType.GRAVEL,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_DIRT);

    public static final DeferredBlock<Block> GREAT_SWAMP_ROOTED_DIRT = registerBlock(
            "great_swamp_root_dirt",
            0.5F,
            0.5F,
            SoundType.ROOTED_DIRT,
            Block::new
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_ROOTED_DIRT_ITEM =
            registerBlockItem(GREAT_SWAMP_ROOTED_DIRT);

    public static final DeferredBlock<GreatSwampVineBlock> GREAT_SWAMP_VINES = BLOCKS.register(
            "great_swamp_vines",
            registryName -> new GreatSwampVineBlock(vineHeadProperties(registryName).lightLevel(state -> 6))
    );

    public static final DeferredBlock<GreatSwampVinePlantBlock> GREAT_SWAMP_VINES_PLANT = BLOCKS.register(
            "great_swamp_vines_plant",
            registryName -> new GreatSwampVinePlantBlock(vineBodyProperties(registryName).lightLevel(state -> 6))
    );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_VINE_ITEM =
            registerBlockItem(GREAT_SWAMP_VINES);

    public static final WoodBlockSet GREAT_SWAMP_TAXODIUM =
            registerWoodSet(
                    "great_swamp_taxodium",
                    ModWoodTypes.GREAT_SWAMP_TAXODIUM,
                    ModWoodTypes.GREAT_SWAMP_TAXODIUM_SET
            );

    public static final List<WoodBlockSet> WOOD_BLOCK_SETS =
            List.of(GREAT_SWAMP_TAXODIUM);

    public static final List<DeferredBlock<? extends Block>> WOOD_SET_BLOCKS =
            WOOD_BLOCK_SETS.stream()
                    .flatMap(set -> set.blocks().stream())
                    .toList();

    public static final List<DeferredItem<? extends Item>> WOOD_SET_ITEMS =
            WOOD_BLOCK_SETS.stream()
                    .flatMap(set -> set.items().stream())
                    .toList();

    public static final DeferredBlock<Block> GREAT_SWAMP_TAXODIUM_ROOT_TANGLE =
            registerBlock(
                    "great_swamp_taxodium_root_tangle",
                    ROOT_TANGLE_HARDNESS,
                    ROOT_TANGLE_RESISTANCE,
                    SoundType.ROOTED_DIRT,
                    Block::new
            );

    public static final DeferredItem<BlockItem> GREAT_SWAMP_TAXODIUM_ROOT_TANGLE_ITEM =
            registerBlockItem(GREAT_SWAMP_TAXODIUM_ROOT_TANGLE);

    // ---------------------------------------------------------------------
    // Dungeon decoration
    // ---------------------------------------------------------------------

    private static BlockBehaviour.Properties vineHeadProperties(
            Identifier registryName
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.VINE)
                .pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties vineBodyProperties(
            Identifier registryName
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .noCollision()
                .instabreak()
                .sound(SoundType.VINE)
                .pushReaction(PushReaction.DESTROY);
    }

    public static final DeferredBlock<Block> DUNGEON_LAMP = BLOCKS.register(
            "dungeon_lamp",
            registryName -> new Block(baseProperties(
                    registryName,
                    1.5F,
                    6.0F,
                    SoundType.COPPER_BULB
            ))
    );

    public static final DeferredItem<BlockItem> DUNGEON_LAMP_ITEM =
            registerBlockItem(DUNGEON_LAMP);

    public static final DeferredBlock<Block> LIT_DUNGEON_LAMP = BLOCKS.register(
            "lit_dungeon_lamp",
            registryName -> new Block(baseProperties(
                    registryName,
                    1.5F,
                    6.0F,
                    SoundType.COPPER_BULB
            ).lightLevel(state -> 12))
    );

    public static final DeferredItem<BlockItem> LIT_DUNGEON_LAMP_ITEM =
            registerBlockItem(LIT_DUNGEON_LAMP);

    // ---------------------------------------------------------------------
    // Shared lists
    // ---------------------------------------------------------------------

    /**
     * Blocks that can safely use simple cube_all model generation.
     *
     * Do not include DUNGEON_VINE here.
     */
    public static final List<DeferredBlock<? extends Block>> TRIVIAL_CUBE_BLOCKS =
            List.of(
                    //OBELISK,
                    DUNGEON_STONE,
                    DUNGEON_BRICKS,
                    DUNGEON_TILES,
                    DUNGEON_CRACKED_TILES,
                    DUNGEON_CRACKED_BRICKS,
                    GREAT_SWAMP_COARSE_DIRT,
                    GREAT_SWAMP_MUD,
                    GREAT_SWAMP_DIRT,
                    GREAT_SWAMP_ROOTED_DIRT,
                    DUNGEON_LAMP,
                    LIT_DUNGEON_LAMP
            );

    /**
     * Blocks that should drop themselves by default.
     *
     * DUNGEON_VINE is intentionally excluded for now because vine-like blocks
     * usually need special drop behavior.
     */
    public static final List<DeferredBlock<? extends Block>> SELF_DROPPING_BLOCKS =
            Stream.concat(
                    STONE_SET_BLOCKS.stream(),
                    List.<DeferredBlock<? extends Block>>of(
                            OBELISK,
                            OBELISK_SMITHING_TABLE,
                            REINFORCED_DUNGEON_STONE,
                            DUNGEON_CRACKED_TILES,
                            DUNGEON_CRACKED_BRICKS,
                            GREAT_SWAMP_GRASS_BLOCK,
                            GREAT_SWAMP_COARSE_DIRT,
                            GREAT_SWAMP_MUD,
                            GREAT_SWAMP_DIRT,
                            GREAT_SWAMP_ROOTED_DIRT,
                            GREAT_SWAMP_TAXODIUM_ROOT_TANGLE,
                            DUNGEON_LAMP,
                            LIT_DUNGEON_LAMP
                    ).stream()
            ).toList();

    public static final List<DeferredItem<? extends Item>> BUILDING_BLOCK_ITEMS =
            Stream.concat(
                    Stream.concat(
                            STONE_SET_ITEMS.stream(),
                            WOOD_SET_ITEMS.stream()
                    ),
                    List.<DeferredItem<? extends Item>>of(
                            OBELISK_ITEM,
                            OBELISK_CHEST_ITEM,
                            OBELISK_SMITHING_TABLE_ITEM,
                            REINFORCED_DUNGEON_STONE_ITEM,
                            DUNGEON_CRACKED_TILES_ITEM,
                            DUNGEON_CRACKED_BRICKS_ITEM,
                            GREAT_SWAMP_COARSE_DIRT_ITEM,
                            GREAT_SWAMP_GRASS_BLOCK_ITEM,
                            GREAT_SWAMP_MUD_ITEM,
                            GREAT_SWAMP_DIRT_ITEM,
                            GREAT_SWAMP_ROOTED_DIRT_ITEM,
                            GREAT_SWAMP_VINE_ITEM,
                            GREAT_SWAMP_TAXODIUM_ROOT_TANGLE_ITEM,
                            DUNGEON_LAMP_ITEM,
                            LIT_DUNGEON_LAMP_ITEM
                    ).stream()
            ).toList();

    private static DeferredBlock<Block> registerStoneBlock(
            String name,
            float destroyTime,
            float explosionResistance
    ) {
        return BLOCKS.register(
                name,
                blockKey -> new Block(stoneProperties(
                        blockKey,
                        destroyTime,
                        explosionResistance
                ))
        );
    }

    private static BlockBehaviour.Properties stoneProperties(
            Identifier registryName,
            float destroyTime,
            float explosionResistance
    ) {
        return baseProperties(
                registryName,
                destroyTime,
                explosionResistance,
                SoundType.STONE
        ).requiresCorrectToolForDrops();
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(
            String name,
            float destroyTime,
            float explosionResistance,
            SoundType soundType,
            Function<BlockBehaviour.Properties, T> factory
    ) {
        return BLOCKS.register(
                name,
                blockKey -> factory.apply(baseProperties(
                        blockKey,
                        destroyTime,
                        explosionResistance,
                        soundType
                ))
        );
    }

    public record StoneBlockSet(
            DeferredBlock<Block> base,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<StairBlock> stairs,
            DeferredBlock<WallBlock> wall,
            DeferredItem<BlockItem> baseItem,
            DeferredItem<BlockItem> slabItem,
            DeferredItem<BlockItem> stairsItem,
            DeferredItem<BlockItem> wallItem
    ) {
        public List<DeferredBlock<? extends Block>> blocks() {
            return List.of(base, slab, stairs, wall);
        }

        public List<DeferredItem<? extends BlockItem>> items() {
            return List.of(baseItem, slabItem, stairsItem, wallItem);
        }
    }

    private static StoneBlockSet registerStoneSet(
            String name,
            float destroyTime,
            float explosionResistance
    ) {
        DeferredBlock<Block> base = registerStoneBlock(
                name,
                destroyTime,
                explosionResistance
        );

        DeferredBlock<SlabBlock> slab = BLOCKS.register(
                name + "_slab",
                registryName -> new SlabBlock(stoneProperties(
                        registryName,
                        destroyTime,
                        explosionResistance
                ))
        );

        DeferredBlock<StairBlock> stairs = BLOCKS.register(
                name + "_stairs",
                registryName -> new StairBlock(
                        base.get().defaultBlockState(),
                        stoneProperties(registryName, destroyTime, explosionResistance)
                )
        );

        DeferredBlock<WallBlock> wall = BLOCKS.register(
                name + "_wall",
                registryName -> new WallBlock(stoneProperties(
                        registryName,
                        destroyTime,
                        explosionResistance
                ))
        );

        return new StoneBlockSet(
                base,
                slab,
                stairs,
                wall,
                registerBlockItem(base),
                registerBlockItem(slab),
                registerBlockItem(stairs),
                registerBlockItem(wall)
        );
    }

    public record WoodBlockSet(
            DeferredBlock<StrippableRotatedPillarBlock> log,
            DeferredBlock<StrippableRotatedPillarBlock> wood,
            DeferredBlock<RotatedPillarBlock> strippedLog,
            DeferredBlock<RotatedPillarBlock> strippedWood,
            DeferredBlock<Block> planks,
            DeferredBlock<StairBlock> stairs,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<FenceBlock> fence,
            DeferredBlock<FenceGateBlock> fenceGate,
            DeferredBlock<DoorBlock> door,
            DeferredBlock<TrapDoorBlock> trapdoor,
//            DeferredBlock<PressurePlateBlock> pressurePlate,
//            DeferredBlock<ButtonBlock> button,
//            DeferredBlock<StandingSignBlock> sign,
//            DeferredBlock<WallSignBlock> wallSign,
//            DeferredBlock<CeilingHangingSignBlock> hangingSign,
//            DeferredBlock<WallHangingSignBlock> wallHangingSign,
            DeferredBlock<? extends LeavesBlock> leaves,
            DeferredItem<BlockItem> logItem,
            DeferredItem<BlockItem> woodItem,
            DeferredItem<BlockItem> strippedLogItem,
            DeferredItem<BlockItem> strippedWoodItem,
            DeferredItem<BlockItem> planksItem,
            DeferredItem<BlockItem> stairsItem,
            DeferredItem<BlockItem> slabItem,
            DeferredItem<BlockItem> fenceItem,
            DeferredItem<BlockItem> fenceGateItem,
            DeferredItem<BlockItem> doorItem,
            DeferredItem<BlockItem> trapdoorItem,
//            DeferredItem<BlockItem> pressurePlateItem,
//            DeferredItem<BlockItem> buttonItem,
//            DeferredItem<SignItem> signItem,
//            DeferredItem<HangingSignItem> hangingSignItem,
            DeferredItem<BlockItem> leavesItem
    ) {
        public List<DeferredBlock<? extends Block>> blocks() {
            return List.of(
                    log,
                    wood,
                    strippedLog,
                    strippedWood,
                    planks,
                    stairs,
                    slab,
                    fence,
                    fenceGate,
                    door,
                    trapdoor,
//                    pressurePlate,
//                    button,
//                    sign,
//                    wallSign,
//                    hangingSign,
//                    wallHangingSign,
                    leaves
            );
        }

        public List<DeferredItem<? extends Item>> items() {
            return List.of(
                    logItem,
                    woodItem,
                    strippedLogItem,
                    strippedWoodItem,
                    planksItem,
                    stairsItem,
                    slabItem,
                    fenceItem,
                    fenceGateItem,
                    doorItem,
                    trapdoorItem,
//                    pressurePlateItem,
//                    buttonItem,
//                    signItem,
//                    hangingSignItem,
                    leavesItem
            );
        }

        public List<DeferredBlock<? extends Block>> logBlocks() {
            return List.of(log, wood, strippedLog, strippedWood);
        }
    }

    private static WoodBlockSet registerWoodSet(
            String name,
            WoodType woodType,
            BlockSetType blockSetType
    ) {
        DeferredBlock<RotatedPillarBlock> strippedLog = BLOCKS.register(
                "stripped_" + name + "_log",
                registryName -> new RotatedPillarBlock(logProperties(registryName))
        );
        DeferredBlock<RotatedPillarBlock> strippedWood = BLOCKS.register(
                "stripped_" + name + "_wood",
                registryName -> new RotatedPillarBlock(logProperties(registryName))
        );
        DeferredBlock<StrippableRotatedPillarBlock> log = BLOCKS.register(
                name + "_log",
                registryName -> new StrippableRotatedPillarBlock(
                        strippedLog,
                        logProperties(registryName)
                )
        );
        DeferredBlock<StrippableRotatedPillarBlock> wood = BLOCKS.register(
                name + "_wood",
                registryName -> new StrippableRotatedPillarBlock(
                        strippedWood,
                        logProperties(registryName)
                )
        );
        DeferredBlock<Block> planks = BLOCKS.register(
                name + "_planks",
                registryName -> new Block(woodProperties(registryName))
        );
        DeferredBlock<StairBlock> stairs = BLOCKS.register(
                name + "_stairs",
                registryName -> new StairBlock(
                        planks.get().defaultBlockState(),
                        woodProperties(registryName)
                )
        );
        DeferredBlock<SlabBlock> slab = BLOCKS.register(
                name + "_slab",
                registryName -> new SlabBlock(woodProperties(registryName))
        );
        DeferredBlock<FenceBlock> fence = BLOCKS.register(
                name + "_fence",
                registryName -> new FenceBlock(woodProperties(registryName))
        );
        DeferredBlock<FenceGateBlock> fenceGate = BLOCKS.register(
                name + "_fence_gate",
                registryName -> new FenceGateBlock(
                        woodType,
                        woodProperties(registryName)
                )
        );
        DeferredBlock<DoorBlock> door = BLOCKS.register(
                name + "_door",
                registryName -> new DoorBlock(
                        blockSetType,
                        woodProperties(registryName).noOcclusion()
                )
        );
        DeferredBlock<TrapDoorBlock> trapdoor = BLOCKS.register(
                name + "_trapdoor",
                registryName -> new TrapDoorBlock(
                        blockSetType,
                        woodProperties(registryName).noOcclusion()
                )
        );
//        DeferredBlock<PressurePlateBlock> pressurePlate = BLOCKS.register(
//                name + "_pressure_plate",
//                registryName -> new PressurePlateBlock(
//                        blockSetType,
//                        woodProperties(registryName)
//                )
//        );
//        DeferredBlock<ButtonBlock> button = BLOCKS.register(
//                name + "_button",
//                registryName -> new ButtonBlock(
//                        blockSetType,
//                        30,
//                        woodProperties(registryName).noCollision()
//                )
//        );
//        DeferredBlock<StandingSignBlock> sign = BLOCKS.register(
//                name + "_sign",
//                registryName -> new StandingSignBlock(
//                        woodType,
//                        signProperties(registryName)
//                )
//        );
//        DeferredBlock<WallSignBlock> wallSign = BLOCKS.register(
//                name + "_wall_sign",
//                registryName -> new WallSignBlock(
//                        woodType,
//                        signProperties(registryName)
//                )
//        );
//        DeferredBlock<CeilingHangingSignBlock> hangingSign = BLOCKS.register(
//                name + "_hanging_sign",
//                registryName -> new CeilingHangingSignBlock(
//                        woodType,
//                        signProperties(registryName)
//                )
//        );
//        DeferredBlock<WallHangingSignBlock> wallHangingSign = BLOCKS.register(
//                name + "_wall_hanging_sign",
//                registryName -> new WallHangingSignBlock(
//                        woodType,
//                        signProperties(registryName)
//                )
//        );
        DeferredBlock<TintedParticleLeavesBlock> leaves = BLOCKS.register(
                name + "_leaves",
                registryName -> new TintedParticleLeavesBlock(
                        0.01F,
                        leavesProperties(registryName)
                )
        );

        return new WoodBlockSet(
                log,
                wood,
                strippedLog,
                strippedWood,
                planks,
                stairs,
                slab,
                fence,
                fenceGate,
                door,
                trapdoor,
//                pressurePlate,
//                button,
//                sign,
//                wallSign,
//                hangingSign,
//                wallHangingSign,
                leaves,
                registerBlockItem(log),
                registerBlockItem(wood),
                registerBlockItem(strippedLog),
                registerBlockItem(strippedWood),
                registerBlockItem(planks),
                registerBlockItem(stairs),
                registerBlockItem(slab),
                registerBlockItem(fence),
                registerBlockItem(fenceGate),
                registerBlockItem(door),
                registerBlockItem(trapdoor),
//                registerBlockItem(pressurePlate),
//                registerBlockItem(button),
//                registerSignItem(name + "_sign", sign, wallSign),
//                registerHangingSignItem(name + "_hanging_sign", hangingSign, wallHangingSign),
                registerBlockItem(leaves)
        );
    }

    private static BlockBehaviour.Properties logProperties(Identifier registryName) {
        return baseProperties(
                registryName,
                WOOD_HARDNESS,
                WOOD_RESISTANCE,
                SoundType.WOOD
        ).ignitedByLava();
    }

    private static BlockBehaviour.Properties woodProperties(Identifier registryName) {
        return baseProperties(
                registryName,
                WOOD_HARDNESS,
                WOOD_RESISTANCE,
                SoundType.WOOD
        ).ignitedByLava();
    }

    private static BlockBehaviour.Properties signProperties(Identifier registryName) {
        return baseProperties(
                registryName,
                1.0F,
                1.0F,
                SoundType.WOOD
        )
                .noCollision()
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties leavesProperties(Identifier registryName) {
        return baseProperties(
                registryName,
                LEAVES_HARDNESS,
                LEAVES_HARDNESS,
                SoundType.GRASS
        )
                .randomTicks()
                .noOcclusion()
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY);
    }

    private static <T extends Block> DeferredItem<BlockItem> registerBlockItem(
            DeferredBlock<T> block
    ) {
        return ITEMS.registerSimpleBlockItem(block);
    }

    private static DeferredItem<SignItem> registerSignItem(
            String name,
            DeferredBlock<? extends Block> sign,
            DeferredBlock<? extends Block> wallSign
    ) {
        return ITEMS.register(
                name,
                registryName -> new SignItem(
                        sign.get(),
                        wallSign.get(),
                        new Item.Properties()
                                .setId(itemKey(registryName))
                                .stacksTo(16)
                )
        );
    }

    private static DeferredItem<HangingSignItem> registerHangingSignItem(
            String name,
            DeferredBlock<? extends Block> hangingSign,
            DeferredBlock<? extends Block> wallHangingSign
    ) {
        return ITEMS.register(
                name,
                registryName -> new HangingSignItem(
                        hangingSign.get(),
                        wallHangingSign.get(),
                        new Item.Properties()
                                .setId(itemKey(registryName))
                                .stacksTo(16)
                )
        );
    }

    private static BlockBehaviour.Properties baseProperties(
            Identifier registryName,
            float destroyTime,
            float explosionResistance,
            SoundType soundType
    ) {
        return BlockBehaviour.Properties.of()
                .setId(blockKey(registryName))
                .destroyTime(destroyTime)
                .explosionResistance(explosionResistance)
                .sound(soundType);
    }

    private static ResourceKey<Block> blockKey(Identifier registryName) {
        return ResourceKey.create(Registries.BLOCK, registryName);
    }

    private static ResourceKey<Item> itemKey(Identifier registryName) {
        return ResourceKey.create(Registries.ITEM, registryName);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
    }
}
