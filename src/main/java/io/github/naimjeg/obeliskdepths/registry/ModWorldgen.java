package io.github.naimjeg.obeliskdepths.registry;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonStructure;
import io.github.naimjeg.obeliskdepths.worldgen.structure.tree.AmphixylonPiece;
import io.github.naimjeg.obeliskdepths.worldgen.structure.tree.AmphixylonStructure;
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
            StructureType<AmphixylonStructure>
            > AMPHIXYLON =
            STRUCTURE_TYPES.register(
                    "amphixylon",
                    () -> new StructureType<>() {
                        @Override
                        public MapCodec<AmphixylonStructure> codec() {
                            return AmphixylonStructure.CODEC;
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
            > AMPHIXYLON_PIECE =
            STRUCTURE_PIECE_TYPES.register(
                    "amphixylon_piece",
                    () -> AmphixylonPiece::new
            );

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECE_TYPES.register(eventBus);
    }
}
