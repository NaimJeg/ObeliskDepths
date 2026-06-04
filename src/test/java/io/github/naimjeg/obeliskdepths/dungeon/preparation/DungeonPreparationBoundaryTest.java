package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class DungeonPreparationBoundaryTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld")
            );

    private DungeonPreparationBoundaryTest() {
    }

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) throws IOException {
        requestCarriesDelayedCommitIdentity();
        interactionHandlerSubmitsAsyncJobOnly();
        menuKeepsJobIdentityAndCancelsOnClose();
        runtimeOwnsBudgetAndSnapshots();
        activationUsesImmutablePreflightAndTransaction();
        compensationIsReverseAndAggregatesFailures();
        preparedEntryReconciliationRunsBeforeSessionPurge();
        recoveryIsSeparateFromActivation();
        safeSpawnWorkIsIncrementalAndShared();
        portalEntryUsesPreparedDataOnly();
        portalEntryDefinesTeleportIrreversibleBoundary();
        lifecycleCancellationHooksAreExplicit();
        clientIsSoloOnly();
        vanillaDimensionTransitionIsNotIntercepted();
        staticRegressionScanBlocksSynchronousActivationFallbacks();
        fullProductionBlockingCallScanUsesExplicitAllowlist();
        hardCutoverTokensAreAbsent();
    }

    private static void requestCarriesDelayedCommitIdentity() {
        ResolvedTribute tribute = validTribute();
        DungeonPreparationRequest request = DungeonPreparationRequest.forTests(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                tribute,
                42
        );

        check(request.expectedTribute().equals(tribute), "request: tribute snapshot");
        check(request.sourceContainerId() == 42, "request: container identity");
        try {
            DungeonPreparationRequest.forTests(
                    UUID.randomUUID(),
                    OVERWORLD,
                    BlockPos.ZERO,
                    ResolvedTribute.invalid(),
                    1
            );
            check(false, "request: invalid tribute rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("tribute"), "request: tribute message");
        }
    }

    private static void interactionHandlerSubmitsAsyncJobOnly() throws IOException {
        String handler = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java");

        assertContains(handler, "DungeonPreparationRuntime.getOrCreate(dungeonLevel).submit(request)",
                "handler should submit to async runtime");
        assertNotContains(handler, "PortalAdmission" + "Mode",
                "handler should not accept portal admission modes");
        assertNotContains(handler, "DungeonActivationPreparationService",
                "handler must not call legacy preparation service");
        assertNotContains(handler, "DungeonActivationCommitService",
                "handler must not synchronously commit");
        assertNotContains(handler, "WorldgenDungeonSiteProvisioner",
                "handler must not call synchronous worldgen provisioner");
        assertNotContains(handler, ".join(",
                "handler must not block on futures");
        assertNotContains(handler, "CompletableFuture.get",
                "handler must not block on futures");
    }

    private static void menuKeepsJobIdentityAndCancelsOnClose() throws IOException {
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");

        assertContains(menu, "private DungeonPreparationJobId activeJobId",
                "menu should remember accepted job id");
        assertContains(menu, "runtime.cancelJobsForPlayer",
                "menu close should cancel active preparation");
        assertContains(menu, "return !ObeliskPortalMenu.this.isSubmitting()",
                "tribute slot should lock while active");
        assertContains(menu, "matchesActivePreparation",
                "commit should revalidate active menu identity");
        assertNotContains(menu, "serverPlayer.closeContainer();",
                "menu must not close immediately after submission");
    }

    private static void runtimeOwnsBudgetAndSnapshots() throws IOException {
        String runtime = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java");
        String budget = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationTickBudget.java");
        String limits = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationLimits.java");
        String executor = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationJobExecutor.java");

        assertContains(runtime, "DungeonPreparationTickBudget.perLevelTick()",
                "runtime should create one budget per level tick");
        assertContains(runtime, "roundRobinCursor",
                "runtime should process jobs in stable round-robin order");
        assertContains(runtime, "activeSnapshotForPlayer",
                "runtime should expose active player snapshots");
        assertContains(runtime, "snapshot(",
                "runtime should expose job snapshots");
        assertContains(budget, "DungeonPreparationLimits.MAX_ACTIVE_PERSISTED_SCANNERS_PER_LEVEL", "active scanner limit should be global per level");
        assertContains(limits, "MAX_GENERATION_ATTEMPTS = 4",
                "generation attempts should be capped at four");
        assertContains(executor, "tryConsumeStartChunkRequest",
                "executor should consume global start-chunk budget");
        assertContains(executor, "tryConsumeEntryChunkRequest",
                "executor should consume global entry-chunk budget");
        assertNotContains(executor, "MAX_NEW_ENTRY_CHUNK_REQUESTS_PER_JOB_TICK",
                "entry chunk budget must not be per job");
    }

    private static void activationUsesImmutablePreflightAndTransaction()
            throws IOException {
        String commit = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitService.java");
        String plan = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitPlan.java");
        String transaction = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationTransaction.java");
        String backend = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/ServerDungeonActivationTransactionBackend.java");
        String executor = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationJobExecutor.java");

        assertContains(commit, "new DungeonActivationTransaction(",
                "commit service should delegate the only mutating path");
        assertNotContains(commit, "purgeExpired(",
                "commit must not run expiry maintenance");
        assertContains(plan, "public record DungeonActivationCommitPlan",
                "preflight plan should be immutable data");
        assertNotContains(plan, "ItemStack",
                "preflight plan must not retain a mutable stack");
        assertNotContains(plan, "ServerPlayer",
                "preflight plan must not retain a player");
        assertNotContains(plan, "ObeliskPortalMenu",
                "preflight plan must not retain a menu");
        assertNotContains(plan, "DungeonPreparationLeaseBundle",
                "preflight plan must not retain leases");
        assertOrder(transaction, "registerPreparedEntry(", "consumeTribute(",
                "tribute must be consumed after prepared-entry registration");
        assertOrder(transaction, "releaseSiteClaim(", "consumeTribute(",
                "claim release must happen before tribute consumption");
        assertOrder(transaction, "consumeTribute(", "return success",
                "success should only return after tribute consumption");
        assertContains(transaction, "catch (Error originalError)",
                "transaction Error paths must compensate and rethrow");
        assertNotContains(transaction, "DURING_CLAIM_RELEASE",
                "claim injection boundaries must be exact");
        assertNotContains(transaction, "DURING_TRIBUTE_CONSUMPTION",
                "tribute injection boundary must precede consumption");
        assertContains(backend, "findValidBySourceObelisk",
                "commit revalidation must reject a conflicting active source portal");
        assertContains(backend, "DungeonPreparationLeaseTransfer localTransfer",
                "lease detachment should establish one guarded temporary owner");
        assertContains(backend, "this.leaseTransfer = null",
                "consuming a transfer should clear backend ownership");
        assertContains(backend, "removeAndCloseExact(",
                "rollback should remove only its exact prepared entry");
        assertContains(executor, "case READY_TO_COMMIT -> commitReadyJob",
                "executor should own ready-to-commit transition");
        assertContains(executor, "context.commitPlan().isEmpty()",
                "preflight should occupy a separate ready-to-commit tick");
        assertContains(executor, "DungeonPreparationStage.COMMITTING",
                "executor should enter committing stage");
        assertContains(executor, "this.registry.publishCommitted",
                "executor should publish committed job after commit success");
        assertContains(executor, "this.committer.afterCommitReady",
                "executor should publish menu notification after job completion");
    }

    private static void compensationIsReverseAndAggregatesFailures()
            throws IOException {
        String stack = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCompensationStack.java");
        String transaction = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationTransaction.java");
        assertContains(stack, "steps.removeLast()",
                "compensation stack should execute in reverse registration order");
        assertContains(stack, "addSuppressedIfDistinct",
                "rollback cleanup aggregation should preserve suppressed failures");
        assertContains(stack, "catch (Error rollbackError)",
                "rollback Error must be distinguished and rethrown");
        assertContains(transaction, "compensations.register",
                "each transaction mutation should register an inverse");
        assertNotContains(transaction, "CompletableFuture",
                "activation transaction must remain synchronous");
    }

    private static void preparedEntryReconciliationRunsBeforeSessionPurge()
            throws IOException {
        String runtime = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java");
        String tick = read("io/github/naimjeg/obeliskdepths/dungeon/runtime/DungeonTickHandler.java");
        String lifecycle = read("io/github/naimjeg/obeliskdepths/dungeon/portal/DungeonPortalSessionLifecycle.java");
        String registry = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparedEntryRegistry.java");

        assertContains(runtime, "reconcilePreparedEntries",
                "runtime should expose prepared-entry reconciliation");
        assertContains(runtime, "metricsSnapshot",
                "runtime should expose prepared-entry metrics");
        assertContains(registry, "removeAndCloseIf",
                "registry should support predicate cleanup");
        assertContains(registry, "registeredSessionIds",
                "registry should expose registered session ids");
        assertContains(registry, "preparedEntryChunkCount",
                "registry should expose prepared chunk count");
        assertContains(lifecycle, "DungeonPortalSessionLifecycle",
                "portal session cleanup should be coordinated");
        assertOrder(tick, "runtime.reconcilePreparedEntries", "DungeonPortalSessionLifecycle.removeForInactiveInstances",
                "prepared entries must reconcile before expired sessions are purged");
        assertContains(tick, "purgeExpiredBounded",
                "expired sessions should use bounded periodic maintenance");
    }

    private static void portalEntryUsesPreparedDataOnly() throws IOException {
        String entry = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java");

        assertContains(entry, "runtime.preparedPortalEntry",
                "portal collision should look up prepared entry");
        assertContains(entry, "DESTINATION_NOT_PREPARED",
                "missing prepared entry should have explicit result");
        assertContains(entry, "DESTINATION_STABILIZING",
                "missing transient entry should trigger restart-safe recovery");
        assertContains(entry, "submitOrReusePreparedEntryRecovery",
                "portal collision should submit or reuse a recovery job");
        assertContains(entry, "getChunkNow",
                "portal collision should verify chunks nonblocking");
        assertContains(entry, "DungeonPreparedEntryValidator.validate",
                "portal collision should validate immutable prepared state");
        assertContains(entry, "dungeonLevel.getServer().isSameThread()",
                "portal entry should assert owner-thread execution");
        assertContains(entry, "runtime.removeAndClosePreparedEntry",
                "successful entry should close prepared bundle");
        assertContains(entry, "DungeonPortalSessionLifecycle.remove",
                "session-invalid branches should close prepared entries through lifecycle cleanup");
        assertNotContains(entry, "resolveInstanceStart",
                "portal collision must not resolve structures synchronously");
        assertNotContains(entry, "readGeneratedSite",
                "portal collision must not read generated site metadata");
        assertNotContains(entry, "lookupExistingChunk",
                "portal collision must not load existing chunks");
        assertNotContains(entry, "DungeonSafeSpawnResolver.resolvePrimaryEntrySpawn",
                "portal collision must not resolve safe spawn");
        assertNotContains(entry, "DungeonSafeSpawnScan",
                "portal collision must not create an incremental safe-spawn scan");
        assertNotContains(entry, "scanChunk(",
                "portal collision must not scan persistent chunk data");
        assertNotContains(entry, "addTicketAndLoadWithRadius",
                "portal collision must not request chunk tickets");
    }

    private static void recoveryIsSeparateFromActivation() throws IOException {
        String runtime = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java");
        String entry = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java");

        assertContains(runtime, "Map<PortalSessionId, PreparedEntryRecoveryJob>",
                "recovery jobs should be keyed by portal session");
        assertContains(runtime, "DungeonEntryChunkPlanner.plan(site, 0)",
                "recovery should plan entry chunks with radius zero");
        assertContains(runtime, "submitOrReusePreparedEntryRecovery",
                "runtime should expose recovery submission");
        assertContains(runtime, "MAX_ACTIVE_RECOVERY_JOBS_PER_LEVEL",
                "recovery should enforce the per-level cap");
        assertOrder(runtime, "this.recoveryJobs.containsKey(session.id())",
                "this.recoveryJobs.size()",
                "same-session recovery should be reused before the cap is checked");
        assertContains(runtime, "recoveryRoundRobinCursor",
                "recovery jobs should have a separate round-robin cursor");
        assertContains(runtime, "DungeonSafeSpawnScanPurpose.RECOVERY",
                "recovery should use the incremental safe-spawn scan");
        assertContains(runtime, "nextLeaseStateValidationIndex",
                "recovery lease validation should resume from a cursor");
        assertContains(runtime, "nextLoadedChunkValidationIndex",
                "recovery loaded-chunk validation should resume from a cursor");
        assertContains(runtime, "this.safeSpawnScan.cancel()",
                "runtime clear should cancel recovery scan state");
        assertContains(runtime, "new DungeonPreparationLeaseBundle(owned).close()",
                "runtime clear should close every recovery lease through one owner");
        assertContains(runtime, "failRecoveryJob(job, failure)",
                "recovery failure should remove and close the admitted job");
        assertContains(runtime, "retryPendingReleases(",
                "failed physical ticket cleanup should be retried on owner ticks");
        assertNotContains(runtime, "resolvePrimaryEntrySpawn(",
                "recovery must not synchronously resolve a complete room");
        assertContains(entry, "runtime.submitOrReusePreparedEntryRecovery",
                "portal collision should reuse recovery workflow");
        assertNotContains(runtime, "reserveResolvedWorldgenSite",
                "recovery must not reserve a new site");
        assertNotContains(runtime, "PortalSessionId.create()",
                "recovery must not create a new portal session");
        assertNotContains(runtime, "TributeResolver",
                "recovery must not resolve or consume tribute");
        assertContains(runtime, "runBoundedRoundRobin(",
                "prepared-entry reconciliation should use a bounded batch");
        assertContains(runtime, "removeAndCloseExact(",
                "reconciliation should not remove a replacement entry");
    }

    private static void safeSpawnWorkIsIncrementalAndShared() throws IOException {
        String resolver = read("io/github/naimjeg/obeliskdepths/dungeon/site/DungeonSafeSpawnResolver.java");
        String cursor = read("io/github/naimjeg/obeliskdepths/dungeon/site/DungeonSafeSpawnCandidateCursor.java");
        String context = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationExecutionContext.java");
        String executor = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationJobExecutor.java");
        String budget = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationTickBudget.java");
        String runtime = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java");
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");

        assertNotContains(resolver, "ArrayList<BlockPos>",
                "production safe-spawn scanning must not allocate a candidate list");
        assertNotContains(resolver, ".sorted(",
                "production safe-spawn scanning must not sort all candidates");
        assertNotContains(resolver, "resolvePrimaryEntrySpawn(",
                "synchronous full-room resolution must not return");
        assertContains(resolver, "level.getBlockState(floor)",
                "candidate validation should read the floor state");
        assertContains(resolver, "level.getBlockState(feet)",
                "candidate validation should read the feet state");
        assertContains(resolver, "level.getBlockState(head)",
                "candidate validation should read the head state");
        assertContains(resolver, "!floorState.is(Blocks.LAVA)",
                "lava floors should remain invalid");
        assertContains(resolver, "state.isAir() || state.canBeReplaced()",
                "air and replaceable occupancy should remain valid");
        assertContains(cursor, "CursorStep skipped()",
                "cursor traversal should be pausable even between candidates");
        assertContains(context, "DungeonSafeSpawnScan safeSpawnScan",
                "preparation context should own scan cursor state");
        assertContains(context, "clearSafeSpawnScan",
                "preparation cleanup should discard scan state");
        assertContains(context, "nextEntryLeaseValidationIndex",
                "lease-state validation should retain its cursor");
        assertContains(context, "nextLoadedChunkValidationIndex",
                "loaded-chunk validation should retain its cursor");
        assertContains(executor, "DungeonSafeSpawnScanPurpose.PREPARATION",
                "preparation should advance the incremental scan");
        assertContains(budget, "tryConsumeSafeSpawnCandidate",
                "safe-spawn budget should charge exact candidate checks");
        assertContains(runtime, "budget.hasTimeRemaining()",
                "runtime schedulers should observe the wall-clock guard");
        assertContains(menu, "menuDataValue(",
                "menu synchronization should clamp large safe-spawn totals");
    }

    private static void portalEntryDefinesTeleportIrreversibleBoundary()
            throws IOException {
        String entry = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java");

        assertContains(entry, "DungeonPlayerTeleporter",
                "teleport should be behind a testable abstraction");
        assertContains(entry, "PortalEntryMutation.begin",
                "pre-teleport registration should be explicit");
        assertContains(entry, "rollbackBeforeTeleport",
                "pre-teleport rollback should be explicit");
        assertContains(entry, "teleporter.teleport",
                "teleport should be the irreversible boundary");
        assertContains(entry, "finalizeAfterTeleport",
                "post-teleport finalization should be separated");
        assertContains(entry, "beginPreparedEntryHandoffAfterTeleport",
                "prepared leases should transfer to an explicit tracking handoff");
        assertOrder(entry, "teleporter.teleport", "finalizeAfterTeleport",
                "finalization must happen only after teleport");
        assertOrder(entry, "teleporter.teleport", "beginPreparedEntryHandoffAfterTeleport",
                "handoff must start only after teleport succeeds");
        assertOrder(entry, "beginPreparedEntryHandoffAfterTeleport", "finalizeAfterTeleport",
                "lease ownership must be secured before observational finalization");
        assertNotContains(entry, "closePreparedEntryAfterTeleport",
                "successful teleport return must not immediately close leases");
        assertNotContains(
                entry.substring(entry.indexOf("finalizeAfterTeleport(")),
                "REGISTRATION_FAILED",
                "post-teleport path must not report registration failure"
        );
    }

    private static void lifecycleCancellationHooksAreExplicit()
            throws IOException {
        String playerEvents = read("io/github/naimjeg/obeliskdepths/event/DungeonPlayerEvents.java");
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");
        String runtime = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java");

        assertContains(playerEvents, "cancelPreparationJobOnLogout",
                "logout should cancel active preparation");
        assertContains(playerEvents, "cancelPreparationJobOnDimensionChange",
                "dimension change should cancel active preparation");
        assertContains(playerEvents, "cancelPreparationJobOnDeath",
                "death should cancel active preparation");
        assertContains(menu, "cancelActivePreparation",
                "menu close should cancel active preparation");
        assertContains(runtime, "LevelEvent.Unload",
                "level unload should clear preparation runtime");
        assertContains(runtime, "ServerStoppingEvent",
                "server stop should clear preparation runtime");
    }

    private static void clientIsSoloOnly() throws IOException {
        String screen = read("io/github/naimjeg/obeliskdepths/client/screen/ObeliskPortalScreen.java");
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");

        assertContains(screen, "private int selectedButtonId = ObeliskPortalMenu.BUTTON_SOLO",
                "client should default to SOLO");
        assertNotContains(screen, "openJoin",
                "client should not show open-join controls");
        assertNotContains(screen, "BUTTON_OPEN" + "_JOIN",
                "client should not send unsupported open-join selection");
        assertNotContains(menu, "PortalAdmission" + "Mode",
                "server menu should not decode portal admission modes");
    }

    private static void vanillaDimensionTransitionIsNotIntercepted() throws IOException {
        String client = read("io/github/naimjeg/obeliskdepths/client/ModClientEvents.java");
        assertNotContains(client, "RegisterDimensionTransitionScreenEvent",
                "vanilla dimension transition presentation must remain registered");
        assertNotContains(client, "registerIncomingEffect",
                "client events must not replace the incoming terrain screen");
        check(!Files.exists(path(
                        "io/github/naimjeg/obeliskdepths/client/screen/ObeliskDepthsTransitionScreen.java"
                )),
                "custom transition screen source should be absent");
    }

    private static void staticRegressionScanBlocksSynchronousActivationFallbacks()
            throws IOException {
        List<Path> productionActivationSources = List.of(
                path("io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java"),
                path("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java"),
                path("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java")
        );
        List<String> forbidden = List.of(
                "DungeonActivationPreparationService.prepare",
                "WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite",
                "getChunk(",
                "CompletableFuture.join",
                "CompletableFuture.get",
                ".join(",
                "ObeliskDepthsTeleporter.resolveInstanceStart"
        );

        for (Path source : productionActivationSources) {
            String text = Files.readString(source);
            for (String token : forbidden) {
                assertNotContains(text, token, source + " must not contain " + token);
            }
        }

        check(!Files.exists(path("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationPreparationService.java")),
                "legacy preparation service should be deleted");
        check(!Files.exists(path("io/github/naimjeg/obeliskdepths/dungeon/site/WorldgenDungeonSiteProvisioner.java")),
                "synchronous worldgen provisioner should be deleted");
    }

    private static void fullProductionBlockingCallScanUsesExplicitAllowlist()
            throws IOException {
        List<AllowedBlockingToken> allowlist = List.of(
                new AllowedBlockingToken(
                        "src/main/java/io/github/naimjeg/obeliskdepths/event/DungeonWorldAccessEvents.java",
                        ".getChunk(",
                        "world-access event reads the event chunk object, not a ServerLevel load"
                ),
                new AllowedBlockingToken(
                        "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/state/DungeonSavedDataInvariantValidator.java",
                        ".join(",
                        "uses String.join for invariant violation formatting, not CompletableFuture.join"
                )
        );
        List<String> tokens = List.of(
                "CompletableFuture.join(",
                "CompletableFuture.get(",
                "CompletableFuture.join(",
                ".getChunk("
        );

        List<String> violations = new java.util.ArrayList<>();
        try (var stream = Files.walk(MAIN)) {
            for (Path source : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                String normalized = source.toString().replace('\\', '/');
                String text = Files.readString(source);
                for (String token : tokens) {
                    if (!text.contains(token)) {
                        continue;
                    }
                    boolean allowed = allowlist.stream()
                            .anyMatch(entry -> entry.matches(normalized, token));
                    if (!allowed) {
                        violations.add(normalized + " contains " + token);
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "Production blocking calls must be explicitly allowlisted."
                            + System.lineSeparator()
                            + "Allowlist:"
                            + System.lineSeparator()
                            + String.join(
                                    System.lineSeparator(),
                                    allowlist.stream()
                                            .map(AllowedBlockingToken::describe)
                                            .toList()
                            )
                            + System.lineSeparator()
                            + String.join(System.lineSeparator(), violations)
            );
        }
    }

    private static void hardCutoverTokensAreAbsent() throws IOException {
        List<String> forbidden = List.of(
                "DungeonSiteProjection" + "Cache",
                "findNearestGenerated" + "Site",
                "findNearestReservable" + "Site",
                "readKnownAuthoritative" + "Site",
                "OPEN" + "_JOIN",
                "BUTTON_OPEN" + "_JOIN",
                "findActiveOpenJoin" + "Session",
                "CANDIDATE_NOT" + "_PERSISTED",
                "CANDIDATE_NOT" + "_PERSISTED_TO_STRUCTURE_STARTS",
                "CANDIDATE_NOT" + "_PERSISTED_TO_REQUIRED_STATUS",
                "EXISTING_CHUNK" + "_LOOKUP_FAILED",
                "getChunkFuture(",
                "managedBlock("
        );
        List<String> violations = new java.util.ArrayList<>();
        try (var stream = Files.walk(MAIN)) {
            for (Path source : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                for (String token : forbidden) {
                    if (text.contains(token)) {
                        violations.add(source + " contains " + token);
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    "Hard cut-over tokens must be absent from production source."
                            + System.lineSeparator()
                            + String.join(System.lineSeparator(), violations)
            );
        }
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(true, 1, 1, 0.25F, 1.0F, 2);
    }

    private static String read(String relative) throws IOException {
        return Files.readString(path(relative));
    }

    private static Path path(String relative) {
        return MAIN.resolve(relative);
    }

    private static void assertContains(
            String source,
            String expected,
            String message
    ) {
        if (!source.contains(expected)) {
            throw new AssertionError(message + ": missing '" + expected + "'");
        }
    }

    private static void assertNotContains(
            String source,
            String forbidden,
            String message
    ) {
        if (source.contains(forbidden)) {
            throw new AssertionError(message + ": found '" + forbidden + "'");
        }
    }

    private static void assertOrder(
            String source,
            String before,
            String after,
            String message
    ) {
        int beforeIndex = source.indexOf(before);
        int afterIndex = source.indexOf(after);
        if (beforeIndex < 0 || afterIndex < 0 || beforeIndex >= afterIndex) {
            throw new AssertionError(
                    message + ": expected '" + before + "' before '" + after + "'"
            );
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record AllowedBlockingToken(
            String normalizedPath,
            String token,
            String reason
    ) {
        boolean matches(String source, String token) {
            return this.normalizedPath.equals(source)
                    && this.token.equals(token);
        }

        String describe() {
            return this.normalizedPath + " allows " + this.token + " because " + this.reason;
        }
    }
}
