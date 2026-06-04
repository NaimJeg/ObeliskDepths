package io.github.naimjeg.obeliskdepths.block.multipart;

import io.github.naimjeg.obeliskdepths.block.ObeliskChestPart;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

public final class MultiPartLayoutTest {
    private MultiPartLayoutTest() {
    }

    public static void main(String[] args) throws IOException {
        testObeliskParts();
        testChestParts();
        testChestFacingTransforms();
        testGeneratedChestResources();
        testMultipartSilentSiblingRemovalSourceInvariant();
        testMultipartRemovalGuardSourceInvariant();
    }

    private static void testObeliskParts() {
        assertEquals(new Vec3i(0, 0, 0), ObeliskPart.BOTTOM.canonicalOffset(), "obelisk bottom offset");
        assertEquals(new Vec3i(0, 1, 0), ObeliskPart.MIDDLE.canonicalOffset(), "obelisk middle offset");
        assertEquals(new Vec3i(0, 2, 0), ObeliskPart.TOP.canonicalOffset(), "obelisk top offset");
        assertSingleMainPart(ObeliskPart.values(), "obelisk");
        assertUniqueOffsets(ObeliskPart.values(), "obelisk");
    }

    private static void testChestParts() {
        Map<ObeliskChestPart, Vec3i> expectedOffsets = Map.ofEntries(
                Map.entry(ObeliskChestPart.BOTTOM_FRONT_LEFT, new Vec3i(0, 0, 0)),
                Map.entry(ObeliskChestPart.BOTTOM_FRONT_CENTER, new Vec3i(1, 0, 0)),
                Map.entry(ObeliskChestPart.BOTTOM_FRONT_RIGHT, new Vec3i(2, 0, 0)),
                Map.entry(ObeliskChestPart.BOTTOM_BACK_LEFT, new Vec3i(0, 0, 1)),
                Map.entry(ObeliskChestPart.BOTTOM_BACK_CENTER, new Vec3i(1, 0, 1)),
                Map.entry(ObeliskChestPart.BOTTOM_BACK_RIGHT, new Vec3i(2, 0, 1)),
                Map.entry(ObeliskChestPart.TOP_FRONT_LEFT, new Vec3i(0, 1, 0)),
                Map.entry(ObeliskChestPart.TOP_FRONT_CENTER, new Vec3i(1, 1, 0)),
                Map.entry(ObeliskChestPart.TOP_FRONT_RIGHT, new Vec3i(2, 1, 0)),
                Map.entry(ObeliskChestPart.TOP_BACK_LEFT, new Vec3i(0, 1, 1)),
                Map.entry(ObeliskChestPart.TOP_BACK_CENTER, new Vec3i(1, 1, 1)),
                Map.entry(ObeliskChestPart.TOP_BACK_RIGHT, new Vec3i(2, 1, 1))
        );

        assertEquals(12, ObeliskChestPart.values().length, "chest part count");
        assertSingleMainPart(ObeliskChestPart.values(), "chest");
        assertUniqueOffsets(ObeliskChestPart.values(), "chest");

        for (ObeliskChestPart part : ObeliskChestPart.values()) {
            assertEquals(expectedOffsets.get(part), part.canonicalOffset(), part + " canonical offset");
            assertFalse(part.getSerializedName().contains("middle"), part + " obsolete serialized middle layer");

            Vec3i offset = part.canonicalOffset();
            assertBetween(0, 2, offset.getX(), part + " x bound");
            assertBetween(0, 1, offset.getY(), part + " y bound");
            assertBetween(0, 1, offset.getZ(), part + " z bound");
        }
    }

    private static void testChestFacingTransforms() {
        Vec3i farRightBackTop = ObeliskChestPart.TOP_BACK_RIGHT.canonicalOffset();

        assertEquals(new Vec3i(2, 1, 1), ObeliskChestPart.transformCanonicalOffset(Direction.NORTH, farRightBackTop), "north transform");
        assertEquals(new Vec3i(-2, 1, -1), ObeliskChestPart.transformCanonicalOffset(Direction.SOUTH, farRightBackTop), "south transform");
        assertEquals(new Vec3i(-1, 1, 2), ObeliskChestPart.transformCanonicalOffset(Direction.EAST, farRightBackTop), "east transform");
        assertEquals(new Vec3i(1, 1, -2), ObeliskChestPart.transformCanonicalOffset(Direction.WEST, farRightBackTop), "west transform");

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Set<BlockPos> positions = new HashSet<>();
            for (ObeliskChestPart part : ObeliskChestPart.values()) {
                Vec3i offset = ObeliskChestPart.transformCanonicalOffset(facing, part.canonicalOffset());
                positions.add(new BlockPos(offset));
            }

            assertEquals(12, positions.size(), facing + " unique transformed chest positions");
        }
    }

    private static void testGeneratedChestResources() throws IOException {
        Path chestLoot = Path.of("src/generated/resources/data/obeliskdepths/loot_table/blocks/obelisk_chest.json");
        assertFalse(Files.exists(chestLoot), "chest loot table must not be generated");

        Path axeTagPath = Path.of("src/generated/resources/data/minecraft/tags/block/mineable/axe.json");
        if (Files.exists(axeTagPath)) {
            String axeTag = Files.readString(axeTagPath);
            assertFalse(axeTag.contains("obelisk_chest"), "unbreakable chest must not be axe-mineable");
        }

        String blockstate = Files.readString(Path.of("src/generated/resources/assets/obeliskdepths/blockstates/obelisk_chest.json"));
        assertFalse(blockstate.contains("middle_"), "chest blockstate must not reference obsolete middle parts");
        assertEquals(48, countOccurrences(blockstate, "opened=false"), "closed chest blockstate variant count");
        assertEquals(48, countOccurrences(blockstate, "opened=true"), "opened chest blockstate variant count");
        for (ObeliskChestPart part : ObeliskChestPart.values()) {
            assertTrue(blockstate.contains("part=" + part.getSerializedName()), "blockstate contains " + part);
        }
    }

    private static void testMultipartSilentSiblingRemovalSourceInvariant() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/block/multipart/AbstractMultiPartBlock.java"
        ));

        assertTrue(
                source.contains("SILENT_PART_REMOVAL_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS"),
                "sibling removal must suppress drops"
        );
        assertTrue(
                source.contains("partPos.equals(alreadyRemovingPos)"),
                "initiating part must be excluded from sibling cleanup"
        );
        assertFalse(
                source.contains("destroyBlock(partPos, true"),
                "sibling cleanup must not run normal loot generation"
        );
    }

    private static void testMultipartRemovalGuardSourceInvariant() throws IOException {
        String source = readNormalized(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/block/multipart/AbstractMultiPartBlock.java"
        ));
        String guards = readNormalized(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/block/multipart/MultipartRemovalGuards.java"
        ));

        assertFalse(source.contains("STRUCTURE_HOOK_DEPTH"), "global hook depth must be removed");
        assertFalse(source.contains("REMOVAL_DEPTH"), "global removal depth must be removed");
        assertFalse(source.contains("PRE_REMOVED_STRUCTURES"), "position-only pre-removal set must be removed");
        assertTrue(guards.contains("record MultipartStructureKey"), "guard helper must define a structure key");
        assertTrue(guards.contains("CURRENTLY_REMOVING"), "guard helper tracks current structures");
        assertTrue(guards.contains("HOOK_EXECUTED"), "guard helper tracks executed hooks by structure");
        assertTrue(guards.contains("PRE_REMOVED"), "guard helper tracks pre-removed structures by key");
        assertTrue(guards.contains("MAX_PRE_REMOVED_MARKERS"), "pre-removal markers must have a hard cap");
        assertTrue(guards.contains("prunePreRemoved"), "pre-removal markers must be pruned when neighbor callbacks do not arrive");

        String player = slice(source, "public BlockState playerWillDestroy", "@Override\n    public void destroy");
        assertTrue(player.contains("getMainPartPos(pos, state)"), "player destruction keys by main part");
        assertTrue(player.contains("structureKey(level, mainPos)"), "player destruction uses structure key");
        assertTrue(player.contains("removeStructureFromInitiatingPart"), "player destruction uses shared initiating-part flow");

        String destroy = slice(source, "public void destroy", "@Override\n    protected void affectNeighborsAfterRemoval");
        assertTrue(destroy.contains("realLevel.isClientSide()"), "client cleanup remains client-only");
        assertTrue(destroy.contains("structureKey(realLevel, mainPos)"), "client cleanup uses structure key");
        assertFalse(destroy.contains("onStructureRemoved"), "client cleanup must not run server removal hook");

        String neighbor = slice(source, "protected void affectNeighborsAfterRemoval", "@Override\n    protected BlockState updateShape");
        assertTrue(neighbor.contains("structureKey(level, mainPos)"), "neighbor removal uses structure key");
        assertTrue(neighbor.contains("isCurrentlyRemoving(key)"), "neighbor removal suppresses only same structure recursion");
        assertTrue(neighbor.contains("consumePreRemoved(key)"), "neighbor removal consumes pre-removal by structure key");
        assertTrue(neighbor.contains("runHookOnce"), "neighbor removal runs hook through keyed guard");

        String shape = slice(source, "protected BlockState updateShape", "@Override\n    protected boolean canSurvive");
        assertTrue(shape.contains("isCurrentlyRemoving"), "support invalidation checks current keyed removal");
        assertTrue(shape.contains("Blocks.AIR.defaultBlockState()"), "support invalidation still removes broken structures");

        String explosion = slice(source, "protected void onExplosionHit", "private void removeOtherParts");
        assertTrue(explosion.contains("structureKey(level, mainPos)"), "explosion removal uses structure key");
        assertTrue(explosion.contains("removeStructureFromInitiatingPart"), "explosion uses shared initiating-part flow");
        assertTrue(explosion.contains("super.onExplosionHit"), "explosion still preserves vanilla hit behavior");

        String removal = slice(source, "private void removeOtherParts", "private boolean isExpectedStructurePart");
        assertTrue(removal.contains("currentState.getFluidState().createLegacyBlock()"),
                "removed secondary parts must restore their fluid state");

        String initiating = slice(source, "private void removeStructureFromInitiatingPart", "private MultipartRemovalGuards.MultipartStructureKey structureKey");
        assertOrder(initiating, "MultipartRemovalGuards.runHookOnce", "MultipartRemovalGuards.markPreRemoved", "hook executes before pre-removal is recorded");
        assertOrder(initiating, "MultipartRemovalGuards.markPreRemoved", "this.removeOtherParts", "pre-removal is recorded before sibling cleanup");
        assertTrue(initiating.contains("unmarkPreRemoved(key)"), "exception path must clear pre-removed state");
        assertTrue(initiating.contains("!level.isClientSide()"), "server hook path must be server-only");
    }

    private static void assertSingleMainPart(MultiPartPart[] parts, String label) {
        int mainCount = 0;
        for (MultiPartPart part : parts) {
            if (part.isMainPart()) {
                mainCount++;
            }
        }

        assertEquals(1, mainCount, label + " main part count");
    }

    private static void assertUniqueOffsets(MultiPartPart[] parts, String label) {
        Set<Vec3i> offsets = new HashSet<>();
        for (MultiPartPart part : parts) {
            if (!offsets.add(part.canonicalOffset())) {
                throw new AssertionError(label + " duplicate offset: " + part.canonicalOffset());
            }
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertBetween(int min, int max, int actual, String message) {
        if (actual < min || actual > max) {
            throw new AssertionError(message + ": expected " + min + ".." + max + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Unable to slice source between " + start + " and " + end);
        }

        return source.substring(startIndex, endIndex);
    }

    private static String readNormalized(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n");
    }

    private static void assertOrder(
            String source,
            String first,
            String second,
            String message
    ) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(message + ": expected '" + first + "' before '" + second + "'");
        }
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }

        return count;
    }
}
