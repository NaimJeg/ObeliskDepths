package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonSpatialLayoutValidator {
    private DungeonSpatialLayoutValidator() {
    }

    public static void validate(DungeonLayoutPlan plan) {
        plan.validateSpatial();
    }

    public static void validatePieceBounds(
            DungeonLayoutPlan plan,
            BoundingBox siteBounds,
            List<BoundingBox> pieceBounds,
            BlockPos primaryEntryAnchor
    ) {
        for (BoundingBox bounds : pieceBounds) {
            requireContains(siteBounds, bounds, "piece " + bounds);
        }

        BoundingBox entryBounds = pieceBounds.stream()
                .filter(bounds -> bounds.isInside(primaryEntryAnchor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Primary entry anchor is outside generated piece bounds: " + primaryEntryAnchor
                ));

        if (!entryBounds.isInside(primaryEntryAnchor)) {
            throw new IllegalArgumentException(
                    "Primary entry anchor is outside entry-room bounds: anchor="
                            + primaryEntryAnchor
                            + " bounds="
                            + entryBounds
            );
        }
    }

    private static void requireContains(
            BoundingBox outer,
            BoundingBox inner,
            String label
    ) {
        if (inner.minX() < outer.minX()
                || inner.maxX() > outer.maxX()
                || inner.minY() < outer.minY()
                || inner.maxY() > outer.maxY()
                || inner.minZ() < outer.minZ()
                || inner.maxZ() > outer.maxZ()) {
            throw new IllegalArgumentException(
                    "Dungeon site bounds do not contain " + label + ": site=" + outer + ", inner=" + inner
            );
        }
    }
}
