package io.github.naimjeg.obeliskdepths.registry;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonStructure;
import io.github.naimjeg.obeliskdepths.worldgen.structure.tree.GreatSwampHourglassTreePiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.tree.GreatSwampHourglassTreeStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    private ModWorldgen() {
    }

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_TYPE,
                    ObeliskDepths.MOD_ID
            );

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(
                    Registries.STRUCTURE_PIECE,
                    ObeliskDepths.MOD_ID
            );

    public static final DeferredHolder<
            StructureType<?>,
            StructureType<ObeliskDungeonStructure>
            > OBELISK_DUNGEON =
            STRUCTURE_TYPES.register(
                    "obelisk_dungeon",
                    () -> new StructureType<>() {
                        @Override
                        public MapCodec<ObeliskDungeonStructure> codec() {
                            return ObeliskDungeonStructure.CODEC;
                        }
                    }
            );

    public static final DeferredHolder<
            StructureType<?>,
            StructureType<GreatSwampHourglassTreeStructure>
            > GREAT_SWAMP_HOURGLASS_TREE =
            STRUCTURE_TYPES.register(
                    "great_swamp_hourglass_tree",
                    () -> new StructureType<>() {
                        @Override
                        public MapCodec<GreatSwampHourglassTreeStructure> codec() {
                            return GreatSwampHourglassTreeStructure.CODEC;
                        }
                    }
            );

    public static final DeferredHolder<
            StructurePieceType,
            StructurePieceType
            > OBELISK_DUNGEON_PIECE =
            STRUCTURE_PIECE_TYPES.register(
                    "obelisk_dungeon_piece",
                    () -> ObeliskDungeonPiece::new
            );

    public static final DeferredHolder<
            StructurePieceType,
            StructurePieceType
            > GREAT_SWAMP_HOURGLASS_TREE_PIECE =
            STRUCTURE_PIECE_TYPES.register(
                    "great_swamp_hourglass_tree_piece",
                    () -> GreatSwampHourglassTreePiece::new
            );

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECE_TYPES.register(eventBus);
    }
}
