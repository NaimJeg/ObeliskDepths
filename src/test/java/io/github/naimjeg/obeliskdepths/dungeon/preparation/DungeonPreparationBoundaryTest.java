package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
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
        requestAccessPolicyIsImmutableAndValidated();
        interactionHandlerSubmitsAsyncJobOnly();
        menuKeepsJobIdentityAndCancelsOnClose();
        runtimeOwnsBudgetAndSnapshots();
        activationUsesImmutablePreflightAndTransaction();
        accessPolicyDataFlowIsImmutable();
        compensationIsReverseAndAggregatesFailures();
        preparedEntryReconciliationRunsBeforeSessionPurge();
        recoveryIsSeparateFromActivation();
        safeSpawnWorkIsIncrementalAndShared();
        portalEntryUsesPreparedDataOnly();
        portalEntryDefinesTeleportIrreversibleBoundary();
        lifecycleCancellationHooksAreExplicit();
        clientUsesStarterOnlyMode();
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

    private static void requestAccessPolicyIsImmutableAndValidated() {
        ResolvedTribute tribute = validTribute();
        DungeonPreparationRequest starter =
                DungeonPreparationRequest.forTests(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        OVERWORLD,
                        new BlockPos(0, 64, 0),
                        tribute,
                        43
                );
        check(
                starter.accessPolicy() == SessionAccessPolicy.STARTER_ONLY,
                "request test factory defaults to STARTER_ONLY"
        );

        DungeonPreparationRequest open =
                DungeonPreparationRequest.forTests(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        OVERWORLD,
                        new BlockPos(0, 64, 0),
                        SessionAccessPolicy.OPEN,
                        tribute,
                        44
                );
        check(
                open.accessPolicy() == SessionAccessPolicy.OPEN,
                "request test factory preserves explicit policy"
        );

        try {
            new DungeonPreparationRequest(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    OVERWORLD,
                    new BlockPos(0, 64, 0),
                    null,
                    tribute,
                    45,
                    starter.tributeFingerprint()
            );
            check(false, "request: null access policy rejected");
        } catch (IllegalArgumentException expected) {
            check(
                    expected.getMessage().contains("access policy"),
                    "request: null access policy message"
            );
        }
    }

    private static void interactionHandlerSubmitsAsyncJobOnly() throws IOException {
        String handler = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java");

        assertContains(handler, "DungeonPreparationRuntime.getOrCreate(dungeonLevel).submit(request)",
                "handler should submit to async runtime");
        assertContains(handler, "SessionAccessPolicy accessPolicy",
                "handler should carry canonical SessionAccessPolicy");
        assertContains(handler, "Objects.requireNonNull(accessPolicy, \"accessPolicy\")",
                "handler should reject null access policy");
        assertContains(handler, "accessPolicy,",
                "handler should pass access policy into the request");
        assertNotContains(handler, "PortalAdmission" + "Mode",
                "handler should not accept obsolete PortalAdmission" + "Mode");
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

    private static void accessPolicyDataFlowIsImmutable() throws IOException {
        String request = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRequest.java");
        String handler = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java");
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");
        String preflight = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationPreflight.java");
        String plan = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitPlan.java");
        String backend = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/ServerDungeonActivationTransactionBackend.java");
        String portal = read("io/github/naimjeg/obeliskdepths/dungeon/portal/PortalSession.java");
        String access = read("io/github/naimjeg/obeliskdepths/dungeon/access/DungeonAccessController.java");
        String executionContext = read("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationExecutionContext.java");
        String lifecycle = read("io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java");

        assertContains(request, "SessionAccessPolicy accessPolicy",
                "request owns immutable access policy");
        assertContains(request, "Preparation access policy must be present.",
                "request rejects null access policy");
        assertContains(handler, "Objects.requireNonNull(accessPolicy, \"accessPolicy\")",
                "handler rejects null access policy at entry");
        String requestConstruction = handler.substring(
                handler.indexOf("new DungeonPreparationRequest(")
        );
        assertContains(requestConstruction, "accessPolicy,",
                "handler carries policy into request construction");
        assertContains(menu, "case BUTTON_STARTER_ONLY -> SessionAccessPolicy.STARTER_ONLY",
                "menu maps starter-only button to STARTER_ONLY");
        assertContains(menu, "case BUTTON_OPEN -> SessionAccessPolicy.OPEN",
                "menu maps open button to OPEN");
        assertContains(menu, "public static final int BUTTON_OPEN = 1",
                "menu exposes explicit open wire id");
        assertContains(menu, "SessionAccessPolicy accessPolicy = accessPolicyForButton(buttonId);",
                "menu resolves policy before submission");
        assertOrder(menu,
                "SessionAccessPolicy accessPolicy = accessPolicyForButton(buttonId);",
                "if (this.status.get() == STATUS_SUBMITTING",
                "unknown ids are rejected before submission-state acknowledgement");
        assertContains(menu,
                "|| this.status.get() == STATUS_READY) {",
                "ready state is part of the terminal acknowledgement guard");
        assertContains(menu, "preparationSubmitter.submit(",
                "menu submits through PreparationSubmitter");
        assertNotContains(menu, "submit" + "Policy",
                "arbitrary-policy submit shortcut must be removed");
        assertNotContains(menu, "submitMapped" + "Button",
                "production must not map button id twice");
        String submitCall = menu.substring(
                menu.indexOf("preparationSubmitter.submit(")
        );
        assertContains(submitCall, "accessPolicy,",
                "PreparationSubmitter receives explicit policy");
        String submitterInterface = menu.substring(
                menu.indexOf("interface PreparationSubmitter")
        );
        assertContains(submitterInterface, "SessionAccessPolicy accessPolicy",
                "PreparationSubmitter signature includes access policy");
        assertContains(preflight, "request.accessPolicy()",
                "preflight copies policy exactly");
        assertContains(plan, "SessionAccessPolicy accessPolicy",
                "commit plan owns access policy");
        assertContains(plan, "this.accessPolicy == request.accessPolicy()",
                "commit plan treats policy as commit identity");
        assertContains(backend, "plan.accessPolicy() == SessionAccessPolicy.ALLOWLIST",
                "backend fail-closes unsupported ALLOWLIST plans");
        assertContains(backend, "portal activation does not support access policy",
                "backend guard names unsupported access policy");
        assertContains(backend, "plan.accessPolicy(),",
                "backend persists plan policy into PortalSession");
        assertNotContains(backend, "activation backend does not support access policy yet",
                "Phase 1 temporary backend guard is gone");
        assertNotContains(backend, "SessionAccessPolicy.STARTER_ONLY",
                "backend must not replace plan policy with STARTER_ONLY");
        assertContains(portal, "SessionAccessPolicy accessPolicy",
                "PortalSession owns authoritative access policy");
        assertContains(portal, "\"access_policy\"",
                "PortalSession CODEC serializes access policy");
        assertContains(portal, "SessionAccessPolicy.STARTER_ONLY",
                "PortalSession legacy access policy defaults to STARTER_ONLY");
        assertContains(access, "session.accessPolicy()",
                "portal admission controller switches on PortalSession policy");
        assertNotContains(executionContext, "SessionAccessPolicy",
                "execution context must not duplicate access policy");
        assertContains(lifecycle, "portalSession.accessPolicy()",
                "lifecycle derives policy from PortalSession");
        assertContains(lifecycle, "portalSession.get().accessPolicy()",
                "physical recovery derives policy from PortalSession");
        assertNotContains(lifecycle,
                "requestedAccessPolicy =\n                SessionAccessPolicy.STARTER_ONLY",
                "lifecycle must not independently choose STARTER_ONLY");
        assertNotContains(lifecycle, "SessionAccessPolicy.OPEN",
                "dungeon session lifecycle must not create OPEN sessions");
        assertContains(
                read("io/github/naimjeg/obeliskdepths/dungeon/state/DungeonSavedDataInvariantValidator.java"),
                "portal/dungeon session access policy mismatch",
                "saved-data validator compares PortalSession and DungeonSession policies"
        );
        assertNotContains(menu, "BUTTON_" + "ALLOWLIST",
                "no allowlist menu button exists");
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
        String operations = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryOperationRuntime.java");

        assertContains(entry, "ClientboundOpenDungeonLoadingPayload",
                "portal collision should open loading UI immediately");
        assertContains(entry, "AWAITING_CLIENT_READY",
                "portal collision should register the client-ready barrier");
        assertNotContains(entry, "submitOrReusePreparedEntryRecovery",
                "portal collision service must not start recovery");
        assertOrder(operations,
                "transitionAndSend(operation, DungeonPortalEntryOperationState.PREPARING)",
                "beginOrReusePreparation(operation, player)",
                "recovery must begin only after the client-ready transition");
        assertContains(operations, "preparationRuntime.preparedPortalEntry",
                "post-ready operation should look up prepared entry");
        assertContains(operations, "DESTINATION_NOT_PREPARED",
                "missing prepared entry should have an explicit result");
        assertContains(operations, "submitOrReusePreparedEntryRecovery",
                "post-ready operation should submit or reuse recovery");
        assertContains(operations, "getChunkNow",
                "post-ready operation should verify chunks nonblocking");
        assertContains(operations, "DungeonPreparedEntryValidator.validate",
                "post-ready operation should validate immutable prepared state");
        assertContains(entry, "dungeonLevel.getServer().isSameThread()",
                "portal entry should assert owner-thread execution");
        assertContains(operations, "preparationRuntime.removeAndClosePreparedEntry",
                "post-ready recovery should discard an invalid prepared bundle");
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
        String operations = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryOperationRuntime.java");

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
        assertContains(operations, "preparationRuntime.submitOrReusePreparedEntryRecovery",
                "post-ready portal operation should reuse recovery workflow");
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
        assertContains(runtime, "static record PostTeleportHandoffKey",
                "handoffs are keyed by portal session and player UUID");
        assertContains(runtime, "PostTeleportHandoffKeys",
                "runtime delegates player-keyed handoff bookkeeping to the shared index");
        assertContains(runtime, "closePreparedEntryAfterHandoff(",
                "runtime uses one policy-aware prepared-entry close path after handoff");
        String sessionRemoval = runtime.substring(
                runtime.indexOf("public Optional<DungeonPreparedPortalEntry> removeAndClosePreparedEntry(")
        );
        assertOrder(sessionRemoval,
                "cancelPostTeleportHandoffsForSession(portalSessionId)",
                "removeAndClose(",
                "whole-session prepared-entry close cancels all session handoffs");
        String exactRemoval = runtime.substring(
                runtime.indexOf("public boolean removeAndClosePreparedEntryExact(")
        );
        assertOrder(exactRemoval,
                "cancelPostTeleportHandoffsForEntry(",
                "removeAndCloseExact(",
                "exact prepared-entry close cancels handoffs for that exact entry");
        assertContains(operations, "isPostTeleportHandoffActive(",
                "entry finalization waits for the exact player handoff");
        assertContains(operations, "operation.playerId()",
                "entry finalization is player-specific");
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
        assertContains(entry,
                "Post-teleport handoff registration failed: session={}, instance={}, player={}, preparedEntryRetained={}",
                "handoff failure logs actual entrant and retain/release outcome");
        assertContains(entry, "enteredPlayer.getUUID()",
                "handoff failure diagnostics use the actual entered player");
        assertContains(entry, "preparedEntryRetained",
                "handoff failure diagnostics expose prepared-entry retainment");
        assertContains(entry, "isPreparedEntryStillRetained(",
                "OPEN retention is based on actual registry/open state");
        assertContains(entry, "runtime.preparedPortalEntry(session.id())",
                "OPEN retention checks the exact registered prepared entry");
        assertContains(entry, "SessionAccessPolicy.OPEN",
                "handoff helper uses the normally imported policy enum");
        assertNotContains(entry,
                "io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy.OPEN",
                "fully-qualified policy references are removed");
        assertNotContains(
                entry.substring(entry.indexOf("private static boolean releaseRejectedHandoff(")),
                "enteredPlayerId",
                "cleanup helper no longer carries unused diagnostic parameters");
        assertNotContains(entry, "exact entry was released",
                "handoff failure must not claim exact entry release unconditionally");
        assertNotContains(entry, "session.opener(),",
                "handoff failure must not log the opener instead of the entrant");
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
        String entryOperations = read("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryOperationRuntime.java");

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
        assertContains(playerEvents, "DungeonPortalEntryService.onPlayerLoggedOut",
                "logout should cancel an awaiting/preparing entry operation");
        assertContains(playerEvents, "DungeonPortalEntryService.onPlayerChangedDimension",
                "dimension changes should cancel pre-teleport entry operations");
        assertContains(entryOperations, "LevelEvent.Unload",
                "level unload should clear entry operations");
        assertContains(entryOperations, "ServerStoppingEvent",
                "server stop should clear entry operations");
    }

    private static void clientUsesStarterOnlyMode() throws IOException {
        String screen = read("io/github/naimjeg/obeliskdepths/client/screen/ObeliskPortalScreen.java");
        String menu = read("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");

        assertContains(screen, "private int selectedButtonId = ObeliskPortalMenu.BUTTON_STARTER_ONLY",
                "client should default to STARTER_ONLY");
        assertContains(screen, "private boolean isInputLocked()",
                "client should centralize input locking");
        assertContains(screen, "|| this.menu.isReady()",
                "client input lock should include READY");
        assertContains(screen, "BUTTON_OPEN",
                "client should define an OPEN button");
        assertContains(screen, "gui.obeliskdepths.portal.mode.starter_only",
                "client should use starter-only mode translation");
        assertContains(screen, "gui.obeliskdepths.portal.mode.open",
                "client should use open mode translation");
        assertContains(screen, "gui.obeliskdepths.portal.mode.starter_only.tooltip",
                "client should attach starter-only tooltip");
        assertContains(screen, "gui.obeliskdepths.portal.mode.open.tooltip",
                "client should attach open tooltip");
        assertContains(screen, "selectedModeLabel()",
                "client selected label should be dynamic");
        assertContains(screen, "case ObeliskPortalMenu.BUTTON_OPEN ->",
                "client selected label should branch on open selection");
        assertContains(screen, "selectedButtonId != ObeliskPortalMenu.BUTTON_OPEN",
                "client should lock selected open mode during submission");
        assertOrder(screen,
                "this.minecraft == null",
                "this.localSubmitting = true",
                "client checks gameMode before local submission lock");
        assertContains(screen, "RenderPipelines.GUI_TEXTURED",
                "client should render portal texture with GUI_TEXTURED");
        assertContains(screen, "textures/gui/container/obelisk_portal.png",
                "client should reference portal GUI texture");
        assertContains(screen, "plainSubstrByWidth",
                "client should constrain long footer text");
        assertContains(screen, "private String clippedFooterText(Component footer)",
                "client should clip footers while preserving the beginning");
        assertContains(screen, "STATUS_MAX_WIDTH - this.font.width(ellipsis)",
                "client should reserve footer width for the ellipsis");
        assertNotContains(screen,
                "plainSubstrByWidth(\n                footer.getString(),\n                STATUS_MAX_WIDTH,\n                true",
                "client must not use tail=true footer truncation");
        assertContains(screen, "STATUS_Y = 68",
                "footer should use the reserved status row");
        assertContains(screen, "INVENTORY_SLOT_TOP = 84",
                "layout should preserve inventory slot boundary");
        assertNotContains(screen, "graphics.fill(",
                "client should not recreate static portal fills");
        assertNotContains(screen, "playerInventoryTitle",
                "client should omit player inventory title");
        assertNotContains(screen, "openJoin",
                "client should not show open-join controls");
        assertNotContains(screen, "BUTTON_OPEN" + "_JOIN",
                "client should not send unsupported open-join selection");
        assertNotContains(screen, "mode." + "solo",
                "client should not use obsolete solo mode translation");
        assertNotContains(screen, "selected." + "solo",
                "client should not use obsolete selected-solo translation");
        assertNotContains(menu, "BUTTON_" + "ALLOWLIST",
                "server menu should not define an allowlist button");
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
