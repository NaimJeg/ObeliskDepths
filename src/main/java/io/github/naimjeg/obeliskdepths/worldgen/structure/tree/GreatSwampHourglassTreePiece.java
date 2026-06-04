package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public final class GreatSwampHourglassTreePiece extends StructurePiece {
    private static final String TAG_ROLE = "Role";
    private static final String TAG_INDEX = "Index";
    private static final String TAG_CENTER_X = "CenterX";
    private static final String TAG_CENTER_Z = "CenterZ";
    private static final String TAG_MIN_Y = "MinY";
    private static final String TAG_MAX_Y = "MaxY";
    private static final String TAG_MAX_RADIUS = "MaxRadius";
    private static final String TAG_TREE_SEED = "TreeSeed";

    private final Role role;
    private final int descriptorIndex;
    private final GreatSwampHourglassTreeSite site;
    private GreatSwampHourglassTreeField field;

    public GreatSwampHourglassTreePiece(
            Role role,
            int descriptorIndex,
            GreatSwampHourglassTreeSite site,
            BoundingBox boundingBox
    ) {
        super(
                ModWorldgen.GREAT_SWAMP_HOURGLASS_TREE_PIECE.get(),
                0,
                boundingBox
        );
        this.role = role;
        this.descriptorIndex = descriptorIndex;
        this.site = site;
    }

    public GreatSwampHourglassTreePiece(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        super(ModWorldgen.GREAT_SWAMP_HOURGLASS_TREE_PIECE.get(), tag);
        this.role = Role.byName(tag.getStringOr(TAG_ROLE, Role.TRUNK.serializedName()));
        this.descriptorIndex = tag.getIntOr(TAG_INDEX, -1);
        this.site = new GreatSwampHourglassTreeSite(
                tag.getIntOr(TAG_CENTER_X, this.boundingBox.getCenter().getX()),
                tag.getIntOr(TAG_CENTER_Z, this.boundingBox.getCenter().getZ()),
                tag.getIntOr(TAG_MIN_Y, this.boundingBox.minY()),
                tag.getIntOr(TAG_MAX_Y, this.boundingBox.maxY()),
                tag.getIntOr(TAG_MAX_RADIUS, GreatSwampHourglassTreeSite.MIN_RADIUS),
                tag.getLongOr(TAG_TREE_SEED, 0L)
        );
    }

    @Override
    protected void addAdditionalSaveData(
            StructurePieceSerializationContext context,
            CompoundTag tag
    ) {
        tag.putString(TAG_ROLE, this.role.serializedName());
        tag.putInt(TAG_INDEX, this.descriptorIndex);
        tag.putInt(TAG_CENTER_X, this.site.centerX());
        tag.putInt(TAG_CENTER_Z, this.site.centerZ());
        tag.putInt(TAG_MIN_Y, this.site.minY());
        tag.putInt(TAG_MAX_Y, this.site.maxY());
        tag.putInt(TAG_MAX_RADIUS, this.site.maxRadius());
        tag.putLong(TAG_TREE_SEED, this.site.treeSeed());
    }

    @Override
    public void postProcess(
            WorldGenLevel level,
            StructureManager structureManager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBB,
            ChunkPos chunkPos,
            BlockPos referencePos
    ) {
        BoundingBox clipped = intersection(this.getBoundingBox(), chunkBB);
        if (clipped == null) {
            return;
        }

        GreatSwampHourglassTreeField treeField = field();
        switch (this.role) {
            case TRUNK -> placeTrunk(level, clipped, treeField);
            case LOWER_ROOT -> placeLowerRoot(level, clipped, treeField, this.descriptorIndex);
            case UPPER_ROOT -> placeUpperRoot(level, clipped, treeField, this.descriptorIndex);
            case BRANCH_CANOPY -> placeBranchCanopy(level, clipped, chunkBB, treeField, this.descriptorIndex);
        }
    }

    private GreatSwampHourglassTreeField field() {
        if (this.field == null) {
            this.field = new GreatSwampHourglassTreeField(this.site);
        }
        return this.field;
    }

    private void placeTrunk(
            WorldGenLevel level,
            BoundingBox clipped,
            GreatSwampHourglassTreeField treeField
    ) {
        BlockState logY = taxodiumLog(Direction.Axis.Y);
        BlockState wood = ModBlocks.GREAT_SWAMP_TAXODIUM.wood().get().defaultBlockState();
        BlockState air = Blocks.CAVE_AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        GreatSwampHourglassTreeField.TrunkSlice[] slices =
                treeField.trunkSlices(clipped.minY(), clipped.maxY());

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            GreatSwampHourglassTreeField.TrunkSlice slice = slices[y - clipped.minY()];
            double outerSq = slice.outerRadius() * slice.outerRadius();
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                double dx = x + 0.5D - slice.centerX();
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    double dz = z + 0.5D - slice.centerZ();
                    if (dx * dx + dz * dz > outerSq) {
                        continue;
                    }
                    if (!treeField.isTrunk(slice, x, z)) {
                        continue;
                    }

                    pos.set(x, y, z);
                    if (treeField.isInsideHollow(x, y, z)) {
                        carveTreeAir(level, pos, air);
                    } else {
                        BlockState state = treeField.isTrunkSurface(slice, x, z)
                                || unit(this.site.treeSeed(), x, y, z, 1001) < 0.36D
                                ? logY
                                : wood;
                        placeNatural(level, pos, state);
                    }
                }
            }
        }
    }

    private void placeLowerRoot(
            WorldGenLevel level,
            BoundingBox clipped,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        if (index < 0 || index >= treeField.lowerRootCount()) {
            return;
        }

        BlockState wood = ModBlocks.GREAT_SWAMP_TAXODIUM.wood().get().defaultBlockState();
        BlockState rootTangle = ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get().defaultBlockState();
        BlockState rootedDirt = ModBlocks.GREAT_SWAMP_ROOTED_DIRT.get().defaultBlockState();
        BlockState air = Blocks.CAVE_AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (treeField.isLowerRoot(index, x, y, z)) {
                        if (treeField.isLowerRootHollow(index, x, y, z)) {
                            carveTreeAir(level, pos, air);
                        } else {
                            BlockState state = treeField.isLowerRootSurface(index, x, y, z)
                                    && unit(this.site.treeSeed(), x, y, z, 1101) < 0.30D
                                    ? rootTangle
                                    : wood;
                            placeNatural(level, pos, state);
                        }
                    } else if (treeField.isNearLowerRootContact(index, x, y, z)
                            && unit(this.site.treeSeed(), x, y, z, 1102) < 0.18D) {
                        placeGroundBlend(level, pos, rootedDirt);
                    }
                }
            }
        }
    }

    private void placeUpperRoot(
            WorldGenLevel level,
            BoundingBox clipped,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        if (index < 0 || index >= treeField.upperRootCount()) {
            return;
        }

        BlockState wood = ModBlocks.GREAT_SWAMP_TAXODIUM.wood().get().defaultBlockState();
        BlockState rootTangle = ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    if (!treeField.isUpperRoot(index, x, y, z)) {
                        continue;
                    }

                    pos.set(x, y, z);
                    BlockState state = treeField.isUpperRootSurface(index, x, y, z)
                            && unit(this.site.treeSeed(), x, y, z, 1201) < 0.36D
                            ? rootTangle
                            : wood;
                    placeNatural(level, pos, state);
                }
            }
        }
    }

    private void placeBranchCanopy(
            WorldGenLevel level,
            BoundingBox clipped,
            BoundingBox chunkBB,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        if (index < 0 || index >= treeField.branchCount()) {
            return;
        }

        placeBranchWood(level, clipped, treeField, index);
        placeBranchLeaves(level, clipped, treeField, index);
        placeBranchVines(level, clipped, chunkBB, treeField, index);
    }

    private void placeBranchWood(
            WorldGenLevel level,
            BoundingBox clipped,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        BlockState wood = ModBlocks.GREAT_SWAMP_TAXODIUM.wood().get().defaultBlockState();
        BlockState rootTangle = ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    if (!treeField.isUpperBranch(index, x, y, z)) {
                        continue;
                    }

                    pos.set(x, y, z);
                    BlockState state = treeField.isBranchSurface(index, x, y, z)
                            && unit(this.site.treeSeed(), x, y, z, 1301) < 0.08D
                            ? rootTangle
                            : wood;
                    placeNatural(level, pos, state);
                }
            }
        }
    }

    private void placeBranchLeaves(
            WorldGenLevel level,
            BoundingBox clipped,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        BlockState leaves = persistentLeaves();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = clipped.minY(); y <= clipped.maxY(); y++) {
            for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
                for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                    if (!treeField.isLeafForBranch(index, x, y, z)) {
                        continue;
                    }

                    pos.set(x, y, z);
                    placeLeaf(level, pos, leaves);
                }
            }
        }
    }

    private void placeBranchVines(
            WorldGenLevel level,
            BoundingBox clipped,
            BoundingBox chunkBB,
            GreatSwampHourglassTreeField treeField,
            int index
    ) {
        BlockState support = ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get().defaultBlockState();
        BlockState body = ModBlocks.GREAT_SWAMP_VINES_PLANT.get().defaultBlockState();
        BlockState head = vineHead();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = clipped.minX(); x <= clipped.maxX(); x++) {
            for (int z = clipped.minZ(); z <= clipped.maxZ(); z++) {
                GreatSwampHourglassTreeField.VineColumn column =
                        treeField.vineAtForBranch(index, x, z).orElse(null);
                if (column == null || column.topY() < clipped.minY() || column.bottomY() > clipped.maxY()) {
                    continue;
                }
                if (column.bottomY() < level.getMinY() || column.topY() >= level.getMaxY()) {
                    continue;
                }

                int supportY = column.topY() + 1;
                if (!isInside(chunkBB, x, supportY, z) || !isInside(chunkBB, x, column.bottomY(), z)) {
                    continue;
                }

                if (!canPlaceCompleteVine(level, pos, x, z, column)) {
                    continue;
                }

                pos.set(x, supportY, z);
                if (!placeVineSupport(level, pos, support)) {
                    continue;
                }

                for (int y = column.topY(); y >= column.bottomY(); y--) {
                    pos.set(x, y, z);
                    level.setBlock(pos, y == column.bottomY() ? head : body, 2);
                }
            }
        }
    }

    private static BlockState taxodiumLog(Direction.Axis axis) {
        BlockState state = ModBlocks.GREAT_SWAMP_TAXODIUM.log().get().defaultBlockState();
        if (state.hasProperty(RotatedPillarBlock.AXIS)) {
            state = state.setValue(RotatedPillarBlock.AXIS, axis);
        }
        return state;
    }

    private static BlockState persistentLeaves() {
        BlockState state = ModBlocks.GREAT_SWAMP_TAXODIUM.leaves().get().defaultBlockState();
        if (state.hasProperty(LeavesBlock.PERSISTENT)) {
            state = state.setValue(LeavesBlock.PERSISTENT, true);
        }
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            state = state.setValue(LeavesBlock.DISTANCE, 1);
        }
        return state;
    }

    private static BlockState vineHead() {
        BlockState state = ModBlocks.GREAT_SWAMP_VINES.get().defaultBlockState();
        if (state.hasProperty(GrowingPlantHeadBlock.AGE)) {
            state = state.setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE);
        }
        return state;
    }

    private static void placeNatural(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            BlockState state
    ) {
        BlockState current = level.getBlockState(pos);
        if (isProtected(current)) {
            return;
        }

        level.setBlock(pos, state, 2);
    }

    private static void carveTreeAir(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            BlockState air
    ) {
        BlockState current = level.getBlockState(pos);
        if (isProtected(current)) {
            return;
        }

        level.setBlock(pos, air, 2);
    }

    private static void placeGroundBlend(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            BlockState state
    ) {
        BlockState current = level.getBlockState(pos);
        if (isProtected(current) || isTaxodiumTreeBlock(current)) {
            return;
        }

        level.setBlock(pos, state, 2);
    }

    private static void placeLeaf(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            BlockState leaves
    ) {
        BlockState current = level.getBlockState(pos);
        if (isProtected(current) || isTaxodiumTreeBlock(current)) {
            return;
        }
        if (!current.isAir() && !current.canBeReplaced()) {
            return;
        }

        level.setBlock(pos, leaves, 2);
    }

    private static boolean placeVineSupport(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            BlockState support
    ) {
        BlockState current = level.getBlockState(pos);
        if (current.isFaceSturdy(level, pos, Direction.DOWN)) {
            return true;
        }
        if (isProtected(current)) {
            return false;
        }
        if (!current.isAir()
                && !current.canBeReplaced()
                && !current.is(ModBlocks.GREAT_SWAMP_TAXODIUM.leaves().get())) {
            return current.isFaceSturdy(level, pos, Direction.DOWN);
        }

        level.setBlock(pos, support, 2);
        return true;
    }

    private static boolean canPlaceCompleteVine(
            WorldGenLevel level,
            BlockPos.MutableBlockPos pos,
            int x,
            int z,
            GreatSwampHourglassTreeField.VineColumn column
    ) {
        for (int y = column.topY(); y >= column.bottomY(); y--) {
            pos.set(x, y, z);
            BlockState current = level.getBlockState(pos);
            if (isProtected(current)
                    || !current.getFluidState().isEmpty()
                    || (!current.isAir() && !current.canBeReplaced())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isProtected(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.is(ModBlocks.OBELISK.get())
                || state.is(ModBlocks.OBELISK_CHEST.get())
                || state.is(ModBlocks.REINFORCED_DUNGEON_STONE.get())
                || state.hasBlockEntity();
    }

    private static boolean isTaxodiumTreeBlock(BlockState state) {
        return state.is(ModBlocks.GREAT_SWAMP_TAXODIUM.log().get())
                || state.is(ModBlocks.GREAT_SWAMP_TAXODIUM.wood().get())
                || state.is(ModBlocks.GREAT_SWAMP_TAXODIUM.leaves().get())
                || state.is(ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get())
                || state.is(ModBlocks.GREAT_SWAMP_VINES.get())
                || state.is(ModBlocks.GREAT_SWAMP_VINES_PLANT.get());
    }

    private static BoundingBox intersection(
            BoundingBox first,
            BoundingBox second
    ) {
        int minX = Math.max(first.minX(), second.minX());
        int minY = Math.max(first.minY(), second.minY());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxX = Math.min(first.maxX(), second.maxX());
        int maxY = Math.min(first.maxY(), second.maxY());
        int maxZ = Math.min(first.maxZ(), second.maxZ());

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return null;
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isInside(BoundingBox box, int x, int y, int z) {
        return x >= box.minX()
                && x <= box.maxX()
                && y >= box.minY()
                && y <= box.maxY()
                && z >= box.minZ()
                && z <= box.maxZ();
    }

    private static double unit(long seed, int x, int y, int z, int salt) {
        long value = seed;
        value ^= (long) x * 0x632BE59BD9B4E019L;
        value ^= (long) y * 0x85157AF5L;
        value ^= (long) z * 0x94D049BB133111EBL;
        value ^= (long) salt * 0x9E3779B97F4A7C15L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (double) (value >>> 11) * 0x1.0p-53D;
    }

    public enum Role {
        TRUNK("trunk"),
        LOWER_ROOT("lower_root"),
        UPPER_ROOT("upper_root"),
        BRANCH_CANOPY("branch_canopy");

        private final String serializedName;

        Role(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static Role byName(String raw) {
            for (Role role : values()) {
                if (role.serializedName.equals(raw)) {
                    return role;
                }
            }
            return TRUNK;
        }
    }
}
