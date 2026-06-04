package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonRewardSystemSourceTest {
    private DungeonRewardSystemSourceTest() {
    }

    public static void main(String[] args) throws IOException {
        testRewardWorkflowSplit();
        testNoPlaceholderSprayRewards();
        testNoAnchorFallbackOrVanillaChestPlacement();
        testReturnScrollGenerationDoesNotReadDifficulty();
        testChestCapacityIsEnforced();
        testAuthoritativeClaimOperation();
        testTimedPhysicalSprayLifecycle();
        testOrdinalVirtualDeliveryLifecycle();
        testBossDeathPlacementSearch();
        testRewardArtifactCleanupRequiresOwnership();
        testObsoleteRewardTemplateAliasRemoved();
    }

    private static void testRewardWorkflowSplit() throws IOException {
        assertFalse(
                Files.exists(Path.of("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardService.java")),
                "obsolete reward service must be deleted"
        );

        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardLifecycle.java");
        String claim = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardClaim.java");
        String placement = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPlacement.java");
        String delivery = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardDelivery.java");
        String reconciliation = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardReconciliation.java");

        assertOrder(lifecycle, "data.rewards().findByInstance(instanceId)", "DungeonRewardRecord.bossDefeated", "duplicate creation is checked before creating a reward");
        assertFalse(lifecycle.contains("tryPlaceReward"), "lifecycle must not place reward blocks");
        assertFalse(lifecycle.contains("DefaultDungeonRewardGenerator"), "lifecycle must not generate reward items");
        assertTrue(claim.contains("DungeonSessionAccess.canAccessSession"), "claim authorization uses session access policy");
        assertTrue(claim.contains("recoverOpenedToAvailable"), "interrupted physical claim can recover");
        assertTrue(claim.contains("markClaiming(reward)"), "direct delivery first records claiming");
        assertTrue(claim.contains("beginDeliveryPlan(reward"), "virtual delivery must persist a delivery plan before spawning");
        assertTrue(placement.contains("recordPlacementFailure"), "placement owns placement failure reporting");
        assertTrue(placement.contains("MAX_PLACEMENT_FAILURES"), "placement failures are bounded");
        assertTrue(placement.contains("claimPlacementFailureFallback"), "terminal placement failure uses claim/delivery fallback");
        assertTrue(delivery.contains("spawnRewardStack"), "delivery owns stack spawning");
        assertTrue(delivery.contains("TAG_DELIVERY_ORDINAL"), "delivery tags spawned stack ordinals");
        assertTrue(reconciliation.contains("reconcileClaimingRewards"), "reconciliation owns interrupted claim repair");
        assertTrue(reconciliation.contains("reconcileDeliveryPlan"), "reconciliation resumes persisted delivery plans");
        assertFalse(reconciliation.contains("hasTaggedRewardItems"), "reconciliation must not claim from any tagged item");
    }

    private static void testNoPlaceholderSprayRewards() throws IOException {
        String delivery = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardDelivery.java");
        assertFalse(delivery.contains("Items.EMERALD"), "reward delivery must not spray placeholder emeralds");
        assertFalse(delivery.contains("Items.DIAMOND"), "reward delivery must not spray placeholder diamonds");
        assertFalse(delivery.contains("Containers.dropItemStack"), "reward opening must spray persisted stacks explicitly");
    }

    private static void testNoAnchorFallbackOrVanillaChestPlacement() throws IOException {
        String placement = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPlacement.java");
        assertFalse(placement.contains("DungeonGeneratedRoom::anchorPos"), "reward marker must not use room anchor");
        assertFalse(placement.contains("Blocks.CHEST.defaultBlockState()"), "reward placement must not create vanilla chests");
        assertTrue(placement.contains("markers.size() != 1"), "marker resolution must reject zero and multiple markers");
    }

    private static void testReturnScrollGenerationDoesNotReadDifficulty() throws IOException {
        String generator = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DefaultDungeonRewardGenerator.java");
        String method = slice(generator, "public static void generateReturnScrolls", "private static DungeonRewardCategory");
        assertFalse(method.contains("DungeonDifficulty"), "return-scroll method must not accept difficulty");
        assertFalse(method.contains("difficulty"), "return-scroll method must not read difficulty values");
        assertTrue(generator.contains("RETURN_SCROLL_SALT"), "return scrolls need a separate deterministic RNG salt");
        assertTrue(generator.contains("SCALABLE_REWARD_SALT"), "scalable rewards need a separate deterministic RNG salt");
    }

    private static void testChestCapacityIsEnforced() throws IOException {
        String generator = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DefaultDungeonRewardGenerator.java");
        String blockEntity = read("src/main/java/io/github/naimjeg/obeliskdepths/block/entity/ObeliskChestBlockEntity.java");
        assertTrue(generator.contains("ObeliskChestBlockEntity.REWARD_CAPACITY"), "generator must cap output to chest capacity");
        assertTrue(blockEntity.contains("contents.size() > REWARD_CAPACITY"), "block entity must reject oversized initialization");
    }

    private static void testAuthoritativeClaimOperation() throws IOException {
        String claim = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardClaim.java");
        String record = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardRecord.java");
        String blockEntity = read("src/main/java/io/github/naimjeg/obeliskdepths/block/entity/ObeliskChestBlockEntity.java");
        assertTrue(claim.contains("tryClaimReward("), "reward claim needs one authoritative claim operation");
        assertTrue(claim.contains("startPhysicalRewardSpray"), "physical claims must start timed spray");
        assertTrue(claim.contains("completePhysicalRewardSpray"), "block entity must complete timed spray explicitly");
        assertTrue(record.contains("markOpened("), "reward record must expose opened lifecycle transition");
        assertTrue(blockEntity.contains("DungeonRoomId"), "block entity must persist optional room identity");
        assertTrue(blockEntity.contains("reward_seed"), "block entity must persist reward seed");
        assertTrue(blockEntity.contains("matchesRewardRecord"), "block entity must validate full reward identity");
    }

    private static void testTimedPhysicalSprayLifecycle() throws IOException {
        String block = read("src/main/java/io/github/naimjeg/obeliskdepths/block/ObeliskChestBlock.java");
        String blockEntity = read("src/main/java/io/github/naimjeg/obeliskdepths/block/entity/ObeliskChestBlockEntity.java");
        String claim = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardClaim.java");
        String delivery = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardDelivery.java");
        String generator = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DefaultDungeonRewardGenerator.java");

        assertTrue(block.contains("getTicker("), "obelisk chest must expose a block-entity ticker");
        assertTrue(block.contains("level.isClientSide()"), "ticker must be server-side only");
        assertTrue(block.contains("ModBlockEntities.OBELISK_CHEST"), "ticker must be restricted to obelisk chest entity type");
        assertTrue(block.contains("ObeliskChestPart.BOTTOM_FRONT_LEFT"), "only main multipart section may tick");
        assertTrue(block.contains("ObeliskChestBlockEntity.serverTick"), "ticker must delegate to block entity server tick");
        assertTrue(claim.contains("setOpened(level, mainPos, true)"), "opened state must be set before interaction returns");
        assertTrue(block.contains("PropertyDispatch.initial(ObeliskChestBlock.PART, ObeliskChestBlock.OPENED)")
                        || read("src/main/java/io/github/naimjeg/obeliskdepths/data/ModModelProvider.java")
                        .contains("PropertyDispatch.initial(ObeliskChestBlock.PART, ObeliskChestBlock.OPENED)"),
                "datagen must retain OPENED variants");

        assertTrue(blockEntity.contains("OPENING_DELAY_TICKS = 10"), "opening delay must be named");
        assertTrue(blockEntity.contains("SPRAY_INTERVAL_TICKS = 8"), "spray interval must be named");
        assertTrue(blockEntity.contains("POST_SPRAY_CLEANUP_TICKS = 20"), "post-spray cleanup delay must be named");
        assertTrue(blockEntity.contains("spraying"), "spray state must persist");
        assertTrue(blockEntity.contains("ticks_until_next_spray"), "next-spray timer must persist");
        assertTrue(blockEntity.contains("post_spray_cleanup_ticks"), "cleanup timer must persist");
        assertTrue(blockEntity.contains("next_spray_ordinal"), "spray ordinal must persist");
        assertTrue(blockEntity.contains("peekNextPendingStack"), "block entity must peek one pending reward");
        assertTrue(blockEntity.contains("removeNextPendingStack"), "block entity must remove only after spawn succeeds");
        assertTrue(blockEntity.contains("spawnRewardStack("), "block entity must spawn one reward unit per tick interval");
        assertTrue(blockEntity.contains("!spawned"), "failed addFreshEntity must leave pending stack in storage");
        assertTrue(blockEntity.contains("completePhysicalRewardSpray"), "empty queue must complete through reward claim");
        assertTrue(blockEntity.contains("isPhysicalRewardSprayInProgress"), "ticker must verify persisted reward is in progress");
        assertTrue(blockEntity.contains("!chest.hasPendingRewards() && (chest.spraying || openedState)"),
                "empty opened queue must finalize even if multipart chunks become unavailable before cleanup");
        assertFalse(blockEntity.contains("takeAllContents()") && claim.contains("takeAllContents()"),
                "physical claim path must not drain all contents at open time");

        assertTrue(claim.contains("data.rewards().markOpened(reward)"), "physical claim must transition AVAILABLE to OPENED");
        assertTrue(claim.contains("chest.beginSpraying()"), "physical claim must start block entity spraying");
        assertTrue(claim.contains("data.rewards().markClaimed(reward)"), "completion must transition OPENED to CLAIMED");
        assertTrue(claim.contains("cleanupClaimedPhysicalRewardArtifact"), "completion must invoke artifact cleanup");
        assertTrue(delivery.contains("TAG_DELIVERY_ORDINAL"), "sprayed entities should carry an ordinal tag");
        assertTrue(delivery.contains("return level.addFreshEntity(entity)"), "single-stack spawn must report failure");
        assertTrue(delivery.contains("DefaultDungeonRewardGenerator.SPRAY_SALT ^"), "spray RNG must vary by ordinal");
        assertTrue(claim.contains("isPhysicalRewardSprayInProgress"), "claim must expose spray status guard");

        assertFalse(generator.contains("mergeCompatible(output)"), "generated scalable rolls must not be merged before timed spray");
        assertTrue(blockEntity.contains("REWARD_CAPACITY = 32"), "capacity must fit max scalable rolls plus return scrolls");
    }

    private static void testOrdinalVirtualDeliveryLifecycle() throws IOException {
        String claim = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardClaim.java");
        String delivery = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardDelivery.java");
        String reconciliation = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardReconciliation.java");
        String record = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardRecord.java");
        String store = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/state/store/DungeonRewardStore.java");
        String plan = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/RewardDeliveryPlan.java");

        assertTrue(plan.contains("public record RewardDeliveryPlan"), "delivery plan data model must be concrete");
        assertTrue(plan.contains("ItemStack.CODEC.listOf()"), "delivery plan must codec persisted stacks");
        assertTrue(plan.contains(".fieldOf(\"next_ordinal\")"), "delivery plan must persist next ordinal");
        assertTrue(plan.contains("map(ItemStack::copy)"), "delivery plan must defensively copy stacks");
        assertTrue(plan.contains("nextOrdinal < 0 || nextOrdinal > stacks.size()"), "delivery plan rejects invalid ordinals");

        assertTrue(record.contains("optionalFieldOf(\"delivery_plan\")"), "reward record persists optional delivery plan");
        assertTrue(record.contains("deliveryPlan()"), "reward record exposes delivery plan");
        assertTrue(record.contains("hasDeliveryPlan()"), "reward record exposes delivery-plan presence");
        assertTrue(record.contains("this.deliveryPlan != null") && record.contains("return false;"),
                "reward record blocks claimed transition while a plan exists");

        assertTrue(store.contains("requireStored(reward)"), "reward store mutations must verify canonical records");
        String beginPlan = slice(store, "public boolean beginDeliveryPlan", "public boolean advanceDeliveryPlan");
        String advancePlan = slice(store, "public boolean advanceDeliveryPlan", "public boolean clearDeliveryPlan");
        String clearPlan = slice(store, "public boolean clearDeliveryPlan", "private DungeonRewardRecord requireStored");
        assertOrder(beginPlan, "DungeonRewardRecord stored = this.requireStored(reward);", "stored.beginDeliveryPlan(stacks)", "begin plan mutates canonical reward");
        assertOrder(beginPlan, "stored.beginDeliveryPlan(stacks)", "this.dirty.run();", "begin plan marks dirty after mutation");
        assertOrder(advancePlan, "stored.advanceDeliveryPlan()", "this.dirty.run();", "advance plan marks dirty after mutation");
        assertOrder(clearPlan, "stored.clearDeliveryPlan()", "this.dirty.run();", "clear plan marks dirty after mutation");

        assertFalse(claim.contains("sprayGeneratedStacks"), "virtual claim path must not use full-list spray");
        assertFalse(claim.contains("sprayStoredStacks"), "virtual claim path must not use stored full-list spray");
        assertOrder(claim, "data.rewards().markClaiming(reward)", "data.rewards().beginDeliveryPlan(reward, stacks)", "claim persists CLAIMING before the plan");
        assertOrder(claim, "data.rewards().beginDeliveryPlan(reward, stacks)", "deliverVirtualRewardPlan(level, data, reward, resolvedDeliveryPos)", "claim persists plan before delivery");
        assertOrder(claim, "DungeonRewardDelivery.hasSpawnedOrdinal", "DungeonRewardDelivery.spawnRewardOrdinalOrThrow", "existing ordinal is checked before spawning");
        assertOrder(claim, "DungeonRewardDelivery.spawnRewardOrdinalOrThrow", "data.rewards().advanceDeliveryPlan(reward)", "spawned ordinal advances only after spawn");
        String finishVirtual = slice(claim, "private static void finishVirtualRewardDelivery", "static void markRoomRewardClaimed");
        assertOrder(finishVirtual, "data.rewards().clearDeliveryPlan(reward)", "data.rewards().markClaimed(reward)", "successful delivery clears plan before CLAIMED");
        assertTrue(claim.contains("if (!deliveryPlanStarted)"), "failed spawn after plan creation must not recover to available");

        assertTrue(delivery.contains("TAG_REWARD_SEED"), "spawned reward entities must carry reward seed");
        assertTrue(delivery.contains("TAG_DELIVERY_ORDINAL"), "spawned reward entities must carry delivery ordinal");
        assertTrue(delivery.contains("hasSpawnedOrdinal"), "delivery must expose ordinal lookup");
        assertTrue(delivery.contains("spawnRewardOrdinalOrThrow"), "delivery must expose single ordinal spawn");
        assertOrder(delivery, "putLong(TAG_REWARD_SEED", "putInt(TAG_DELIVERY_ORDINAL", "entity identity includes seed before ordinal tag");

        String reconcilePlan = slice(reconciliation, "private static void reconcileDeliveryPlan", "private static BlockPos deliveryCenter");
        assertOrder(reconcilePlan, "RewardDeliveryPlan plan = currentPlan.get()", "int ordinal = plan.nextOrdinal()", "reconciliation resumes from persisted next ordinal");
        String missingOrdinal = slice(reconcilePlan, "if (!DungeonRewardDelivery.hasSpawnedOrdinal", "data.rewards().advanceDeliveryPlan");
        assertOrder(missingOrdinal, "!DungeonRewardDelivery.hasSpawnedOrdinal", "return;", "missing later ordinal leaves reward claiming");
        assertTrue(reconcilePlan.contains("while (true)") && reconcilePlan.contains("data.rewards().advanceDeliveryPlan(reward)"),
                "reconciliation loops after advancing existing ordinal");
        assertOrder(reconcilePlan, "data.rewards().clearDeliveryPlan(reward)", "data.rewards().markClaimed(reward)", "reconciliation claims only after all ordinals complete");
        assertFalse(reconciliation.contains("recoverClaimingToAvailable"), "reconciliation must not reopen claiming rewards");
    }

    private static void testBossDeathPlacementSearch() throws IOException {
        String placement = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPlacement.java");
        String chest = read("src/main/java/io/github/naimjeg/obeliskdepths/block/ObeliskChestBlock.java");
        assertTrue(placement.contains("PLACEMENT_SEARCH_NEGATIVE_OFFSET = -8"), "search lower bound must be -8");
        assertTrue(placement.contains("PLACEMENT_SEARCH_POSITIVE_OFFSET_EXCLUSIVE = 8"), "search upper bound must be exclusive +8");
        assertTrue(placement.contains("new ArrayList<>(4096)"), "search should allocate exactly one 4096-candidate list");
        assertTrue(placement.contains("Direction.NORTH")
                && placement.contains("Direction.EAST")
                && placement.contains("Direction.SOUTH")
                && placement.contains("Direction.WEST"), "all horizontal facings must be tested");
        assertTrue(placement.contains("for (BlockPos candidate : candidates)")
                && placement.contains("for (Direction facing : HORIZONTAL_FACINGS)"), "search must test every candidate/facing pair");
        assertTrue(placement.contains("recordPlacementFailure(level, data, reward, stats.summary(), origin)"), "failure increments after the full search");
        assertTrue(placement.contains("resolvePlacementOrigin"), "placement must resolve saved boss/preferred origin before authored fallback");
        assertTrue(chest.contains("level.getBlockEntity(partPos) != null"), "candidate validation must reject block entities before placement");
        assertFalse(chest.contains("current.is(Blocks.CHEST)"), "reward placement must not adopt an existing vanilla chest");
    }

    private static void testRewardArtifactCleanupRequiresOwnership() throws IOException {
        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/artifact/DungeonRuntimeArtifactCleanupService.java");
        assertFalse(cleanup.contains("state.is(Blocks.CHEST)"), "cleanup must not delete vanilla chest markers by type");
        assertTrue(cleanup.contains("recordMismatch"), "cleanup must persist ownership mismatches for retry/diagnostics");
        assertTrue(cleanup.contains("tickPendingArtifacts"), "pending artifact cleanup must be independent of active instances");
        assertTrue(cleanup.contains("isCompleteRewardStructure"), "cleanup must validate multipart reward chest structure");
    }

    private static void testObsoleteRewardTemplateAliasRemoved() throws IOException {
        String tempering = read("src/main/java/io/github/naimjeg/obeliskdepths/tempering/TemperingTemplateItems.java");
        assertFalse(tempering.contains("createRewardTemplate"), "obsolete reward template alias should be removed");
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Unable to slice source between " + start + " and " + end);
        }

        return source.substring(startIndex, endIndex);
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
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
}
