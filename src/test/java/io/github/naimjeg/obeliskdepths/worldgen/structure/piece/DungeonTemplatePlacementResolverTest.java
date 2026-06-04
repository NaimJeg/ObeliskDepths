package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class DungeonTemplatePlacementResolverTest {
    private DungeonTemplatePlacementResolverTest() {
    }

    public static void main(String[] args) throws Exception {
        squareTemplateRotationsStayOnTargetMinimum();
        nonSquareTemplateRotationsStayOnTargetMinimum();
        mirrorUsesSameSettingsForBounds();
    }

    private static void squareTemplateRotationsStayOnTargetMinimum() throws Exception {
        StructureTemplate template = template(8, 4, 8);
        BlockPos targetMinimum = new BlockPos(32, 12, -16);

        for (DungeonRoomRotation rotation : DungeonRoomRotation.values()) {
            ResolvedTemplatePlacement placement = ResolvedTemplatePlacement.resolve(
                    template,
                    targetMinimum,
                    rotation,
                    false
            );

            assertMinimum(targetMinimum, placement.transformedBounds(), rotation.getSerializedName());
            assertEquals(8, placement.transformedBounds().getXSpan(), "square x span");
            assertEquals(8, placement.transformedBounds().getZSpan(), "square z span");
            assertSameBounds(
                    placement.transformedBounds(),
                    template.getBoundingBox(placement.settings(), placement.placementOrigin()),
                    "settings used for resolved bounds"
            );
        }
    }

    private static void nonSquareTemplateRotationsStayOnTargetMinimum() throws Exception {
        StructureTemplate template = template(5, 3, 9);
        BlockPos targetMinimum = new BlockPos(-11, 7, 40);

        ResolvedTemplatePlacement none = ResolvedTemplatePlacement.resolve(
                template,
                targetMinimum,
                DungeonRoomRotation.NONE,
                false
        );
        assertMinimum(targetMinimum, none.transformedBounds(), "none");
        assertEquals(5, none.transformedBounds().getXSpan(), "none x span");
        assertEquals(9, none.transformedBounds().getZSpan(), "none z span");

        ResolvedTemplatePlacement clockwise = ResolvedTemplatePlacement.resolve(
                template,
                targetMinimum,
                DungeonRoomRotation.CLOCKWISE_90,
                false
        );
        assertMinimum(targetMinimum, clockwise.transformedBounds(), "clockwise");
        assertEquals(9, clockwise.transformedBounds().getXSpan(), "clockwise x span");
        assertEquals(5, clockwise.transformedBounds().getZSpan(), "clockwise z span");

        ResolvedTemplatePlacement counterclockwise = ResolvedTemplatePlacement.resolve(
                template,
                targetMinimum,
                DungeonRoomRotation.COUNTERCLOCKWISE_90,
                false
        );
        assertMinimum(targetMinimum, counterclockwise.transformedBounds(), "counterclockwise");
        assertEquals(9, counterclockwise.transformedBounds().getXSpan(), "counterclockwise x span");
        assertEquals(5, counterclockwise.transformedBounds().getZSpan(), "counterclockwise z span");
    }

    private static void mirrorUsesSameSettingsForBounds() throws Exception {
        StructureTemplate template = template(5, 3, 9);
        BlockPos targetMinimum = new BlockPos(3, 4, 5);
        ResolvedTemplatePlacement placement = ResolvedTemplatePlacement.resolve(
                template,
                targetMinimum,
                DungeonRoomRotation.CLOCKWISE_180,
                true
        );

        assertMinimum(targetMinimum, placement.transformedBounds(), "mirror");
        assertSameBounds(
                placement.transformedBounds(),
                template.getBoundingBox(placement.settings(), placement.placementOrigin()),
                "mirror settings used for resolved bounds"
        );
    }

    private static StructureTemplate template(
            int x,
            int y,
            int z
    ) throws Exception {
        StructureTemplate template = new StructureTemplate();
        Field size = StructureTemplate.class.getDeclaredField("size");
        size.setAccessible(true);
        size.set(template, new Vec3i(x, y, z));
        return template;
    }

    private static void assertMinimum(
            BlockPos expected,
            BoundingBox actual,
            String message
    ) {
        assertEquals(expected.getX(), actual.minX(), message + " min x");
        assertEquals(expected.getY(), actual.minY(), message + " min y");
        assertEquals(expected.getZ(), actual.minZ(), message + " min z");
    }

    private static void assertSameBounds(
            BoundingBox expected,
            BoundingBox actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
