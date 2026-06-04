//package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;
//
//import io.github.naimjeg.obeliskdepths.registry.ModStructures;
//import java.lang.reflect.Field;
//import net.minecraft.core.Registry;
//import net.minecraft.core.RegistryAccess;
//import net.minecraft.core.registries.Registries;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.world.level.ChunkPos;
//import net.minecraft.world.level.levelgen.structure.StructureSet;
//import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
//import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
//
//public final class ObeliskDungeonPlacementResolver {
//    private static final Field STRUCTURE_PLACEMENT_SALT = saltField();
//
//    private ObeliskDungeonPlacementResolver() {
//    }
//
//    public static RegisteredRandomSpreadPlacement resolveRandomSpread(
//            ServerLevel level
//    ) {
//        return resolveRandomSpread(level.registryAccess());
//    }
//
//    public static RegisteredRandomSpreadPlacement resolveRandomSpread(
//            RegistryAccess registryAccess
//    ) {
//        Registry<StructureSet> registry = registryAccess
//                .lookupOrThrow(Registries.STRUCTURE_SET);
//        StructureSet structureSet = registry.getValueOrThrow(
//                ModStructures.OBELISK_DUNGEONS
//        );
//        if (!(structureSet.placement() instanceof RandomSpreadStructurePlacement placement)) {
//            throw new IllegalStateException(
//                    "Dungeon structure set must use minecraft:random_spread placement: "
//                            + ModStructures.OBELISK_DUNGEONS.identifier()
//                            + " actual="
//                            + structureSet.placement().type()
//            );
//        }
//        return fromPlacement(placement);
//    }
//
//    public static RegisteredRandomSpreadPlacement fromPlacement(
//            RandomSpreadStructurePlacement placement
//    ) {
//        if (placement == null) {
//            throw new IllegalArgumentException("Random spread placement is required");
//        }
//        return new RegisteredRandomSpreadPlacement(
//                placement,
//                placement.spacing(),
//                placement.separation(),
//                salt(placement),
//                placement.spreadType().getSerializedName()
//        );
//    }
//
//    private static int salt(StructurePlacement placement) {
//        try {
//            return STRUCTURE_PLACEMENT_SALT.getInt(placement);
//        } catch (IllegalAccessException exception) {
//            throw new IllegalStateException(
//                    "Unable to read registered structure placement salt",
//                    exception
//            );
//        }
//    }
//
//    private static Field saltField() {
//        try {
//            Field field = StructurePlacement.class.getDeclaredField("salt");
//            field.setAccessible(true);
//            return field;
//        } catch (NoSuchFieldException exception) {
//            throw new IllegalStateException(
//                    "Current mappings do not expose StructurePlacement salt field",
//                    exception
//            );
//        }
//    }
//
//    public record RegisteredRandomSpreadPlacement(
//            RandomSpreadStructurePlacement placement,
//            int spacing,
//            int separation,
//            int salt,
//            String spreadType
//    ) {
//        public RegisteredRandomSpreadPlacement {
//            if (spacing <= separation) {
//                throw new IllegalArgumentException(
//                        "Random spread spacing must exceed separation: spacing="
//                                + spacing
//                                + " separation="
//                                + separation
//                );
//            }
//        }
//
//        public int minimumAxisCenterSeparationBlocks() {
//            return (this.separation + 1) * 16;
//        }
//
//        public ChunkPos potentialChunk(
//                long seed,
//                int regionX,
//                int regionZ
//        ) {
//            return this.placement.getPotentialStructureChunk(
//                    seed,
//                    regionX * this.spacing,
//                    regionZ * this.spacing
//            );
//        }
//    }
//}