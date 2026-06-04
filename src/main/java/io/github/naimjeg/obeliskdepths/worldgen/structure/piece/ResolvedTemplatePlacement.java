package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public record ResolvedTemplatePlacement(
        BlockPos placementOrigin,
        BoundingBox transformedBounds,
        StructurePlaceSettings settings
) {
    public static ResolvedTemplatePlacement resolve(
            StructureTemplate template,
            BlockPos targetMinimum,
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        return resolve(template, targetMinimum, rotation, mirror, null);
    }

    public static ResolvedTemplatePlacement resolve(
            StructureTemplate template,
            BlockPos targetMinimum,
            DungeonRoomRotation rotation,
            boolean mirror,
            BoundingBox clippingBounds
    ) {
        StructurePlaceSettings settings = settings(rotation, mirror);
        BoundingBox unshifted = template.getBoundingBox(settings, targetMinimum);
        BlockPos placementOrigin = targetMinimum.offset(
                targetMinimum.getX() - unshifted.minX(),
                targetMinimum.getY() - unshifted.minY(),
                targetMinimum.getZ() - unshifted.minZ()
        );

        if (clippingBounds != null) {
            settings.setBoundingBox(clippingBounds);
        }

        return new ResolvedTemplatePlacement(
                placementOrigin,
                template.getBoundingBox(settings, placementOrigin),
                settings
        );
    }

    public static StructurePlaceSettings settings(
            DungeonRoomRotation rotation,
            boolean mirror
    ) {
        DungeonRoomRotation resolvedRotation = rotation == null
                ? DungeonRoomRotation.NONE
                : rotation;
        return new StructurePlaceSettings()
                .setRotation(resolvedRotation.toMinecraftRotation())
                .setMirror(mirror ? Mirror.LEFT_RIGHT : Mirror.NONE);
    }
}
