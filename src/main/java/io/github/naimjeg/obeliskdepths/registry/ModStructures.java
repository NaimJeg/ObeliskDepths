package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class ModStructures {
    private ModStructures() {
    }

    public static final ResourceKey<Structure> DEPTHS_SITE =
            ResourceKey.create(
                    Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            "depths_site"
                    )
            );

    public static final ResourceKey<Structure> GREAT_SWAMP_HOURGLASS_TREE =
            ResourceKey.create(
                    Registries.STRUCTURE,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            "great_swamp_hourglass_tree"
                    )
            );

    public static final ResourceKey<StructureSet> OBELISK_DUNGEONS =
            ResourceKey.create(
                    Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            "obelisk_dungeons"
                    )
            );

    public static final ResourceKey<StructureSet> GREAT_SWAMP_HOURGLASS_TREES =
            ResourceKey.create(
                    Registries.STRUCTURE_SET,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            "great_swamp_hourglass_trees"
                    )
            );
}
