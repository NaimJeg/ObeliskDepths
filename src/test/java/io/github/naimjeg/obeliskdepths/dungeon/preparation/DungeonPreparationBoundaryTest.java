package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionSource;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPreparationBoundaryTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld")
            );

    private DungeonPreparationBoundaryTest() {
    }

    public static void main(String[] args) throws IOException {
        invalidTributeFailsBeforeSiteResolution();
        existingOpenJoinTargetWinsBeforeTributeValidation();
        newTargetPreparationReturnsAuthoritativeSite();
        nonAuthoritativePreparedSiteIsRejected();
        preparationServiceDoesNotMutateSavedData();
        commitServiceOwnsMutationAndRollback();
        existingOpenJoinCommitRollbackIsScoped();
        rollbackFailureMappingIsTypedAndSuppressed();
        newTargetRollbackIsSafeAndExhaustive();
        interactionHandlerNoLongerOwnsTransaction();
        siteResolutionAndReservationApisAreSplit();
        wireFormatsRemainUnchanged();
    }

    private static void invalidTributeFailsBeforeSiteResolution() {
        DungeonPreparationRequest request = request(PortalAdmissionMode.SOLO);
        CountingLookup lookup = new CountingLookup(true);

        DungeonPreparationResult result =
                DungeonActivationPreparationService.prepareWithLookup(
                        request,
                        ResolvedTribute::invalid,
                        lookup
                );

        assertFailure(result, DungeonPreparationFailureReason.INVALID_TRIBUTE);
        assertEquals(0, lookup.existingLookups.get(), "solo request should not look for open join");
        assertEquals(0, lookup.siteLookups.get(), "invalid tribute must not resolve a site");
    }

    private static void existingOpenJoinTargetWinsBeforeTributeValidation() {
        DungeonPreparationRequest request = request(PortalAdmissionMode.OPEN_JOIN);
        ExistingOpenJoinTarget existing = new ExistingOpenJoinTarget(
                request,
                DungeonInstanceId.create(),
                PortalSessionId.create()
        );
        CountingLookup lookup = new CountingLookup(true);
        lookup.existingTarget = Optional.of(existing);
        Supplier<ResolvedTribute> tribute = () -> {
            throw new AssertionError("existing OPEN_JOIN target must not resolve tribute");
        };

        DungeonPreparationResult result =
                DungeonActivationPreparationService.prepareWithLookup(
                        request,
                        tribute,
                        lookup
                );

        assertSame(existing, result, "existing target should be returned directly");
        assertEquals(1, lookup.existingLookups.get(), "open join lookup should run once");
        assertEquals(0, lookup.siteLookups.get(), "existing target must not reserve or resolve a new site");
    }

    private static void newTargetPreparationReturnsAuthoritativeSite() {
        DungeonPreparationRequest request = request(PortalAdmissionMode.SOLO);
        CountingLookup lookup = new CountingLookup(true);
        ResolvedDungeonSite site = resolvedSite(DungeonSiteProjectionSource.GENERATED_STRUCTURE_START);
        ResolvedTribute tribute = validTribute();
        lookup.generatedSite = Optional.of(site);

        DungeonPreparationResult result =
                DungeonActivationPreparationService.prepareWithLookup(
                        request,
                        () -> tribute,
                        lookup
                );

        assertInstanceOf(result, NewAuthoritativeSiteTarget.class, "new target expected");
        NewAuthoritativeSiteTarget target = (NewAuthoritativeSiteTarget) result;
        assertEquals(site, target.resolvedSite(), "prepared target should preserve exact site");
        assertEquals(tribute, target.tribute(), "prepared target should preserve resolved tribute");
        assertEquals(1, lookup.siteLookups.get(), "new target should resolve one authoritative site");
    }

    private static void nonAuthoritativePreparedSiteIsRejected() {
        DungeonPreparationRequest request = request(PortalAdmissionMode.SOLO);
        CountingLookup lookup = new CountingLookup(true);
        lookup.generatedSite = Optional.of(resolvedSite(DungeonSiteProjectionSource.PLANNED_PROTOTYPE));

        DungeonPreparationResult result =
                DungeonActivationPreparationService.prepareWithLookup(
                        request,
                        DungeonPreparationBoundaryTest::validTribute,
                        lookup
                );

        assertFailure(result, DungeonPreparationFailureReason.NON_AUTHORITATIVE_SITE);
    }

    private static void preparationServiceDoesNotMutateSavedData() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationPreparationService.java"
        );

        assertNotContains(source, "portalSessions().add", "preparation must not create portal sessions");
        assertNotContains(source, "sessions().add", "preparation must not create dungeon sessions");
        assertNotContains(source, "instances().put", "preparation must not create instances");
        assertNotContains(source, "sites().reserve", "preparation must not reserve sites");
        assertNotContains(source, "ensurePortal", "preparation must not create portal entities");
        assertNotContains(source, ".shrink(", "preparation must not consume tribute");
        assertNotContains(source, "setData(", "preparation must not mutate attachments");
    }

    private static void commitServiceOwnsMutationAndRollback() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitService.java"
        );

        assertContains(source, "reserveResolvedWorldgenSite", "commit should reserve the prepared site");
        assertContains(source, "portalSessions().add", "commit should create portal sessions");
        assertContains(source, "DungeonSessionLifecycle.acquireForPortal", "commit should create dungeon sessions");
        assertContains(source, "ensurePortal", "commit should create portal entities");
        assertContains(source, "consumeTributeIfNeeded", "commit should own tribute consumption");
        assertContains(source, "rollbackCreatedTargetSafely", "commit should own rollback");
        assertContains(source, "NON_AUTHORITATIVE_SITE", "commit should reject non-authoritative sites");
    }

    private static void existingOpenJoinCommitRollbackIsScoped() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitService.java"
        );
        String existingCommit = slice(
                source,
                "private static DungeonActivationCommitResult commitExistingOpenJoin",
                "private static DungeonActivationCommitResult commitNewTarget"
        );
        assertContains(existingCommit, "DungeonSessionAcquisition acquisition",
                "existing-open-join should use explicit session acquisition");
        assertContains(existingCommit, "DungeonSession dungeonSession",
                "existing-open-join should capture recreated session");
        assertContains(existingCommit, "boolean createdDungeonSession",
                "existing-open-join should track commit-created session");
        assertContains(existingCommit, "createdDungeonSession = acquisition.created()",
                "existing-open-join should use lifecycle-created ownership");
        assertContains(existingCommit, "rollbackExistingOpenJoinSessionSafely",
                "existing-open-join should use protected scoped rollback");
        assertContains(existingCommit, "PORTAL_SPAWN_FAILED",
                "portal empty failure should be mapped");
        assertContains(existingCommit, "catch (RuntimeException originalException)",
                "runtime exceptions should be caught");
        assertContains(existingCommit, "originalException.addSuppressed(rollbackFailure)",
                "rollback failure should be suppressed on original exception");
        assertContains(existingCommit, "safeFailureDetail(originalException)",
                "exception detail should be safe and non-null");
        assertContains(existingCommit, "INTERNAL_ERROR",
                "runtime exceptions should map to internal error");
        assertNotContains(existingCommit, "rollbackCreatedTargetSafely",
                "existing-open-join must not rollback owned portal/session/instance state");
        assertNotContains(existingCommit, "consumeTributeIfNeeded",
                "existing-open-join must not consume tribute");

        String rollback = slice(
                source,
                "private static RuntimeException rollbackExistingOpenJoinSessionSafely",
                "private static RuntimeException appendCleanupFailure"
        );
        assertContains(rollback, "!createdByCommit || dungeonSession == null",
                "rollback should only remove commit-created dungeon sessions");
        assertContains(rollback, "catch (RuntimeException exception)",
                "rollback should trap ordinary cleanup failure");
        assertContains(rollback, "return exception",
                "rollback should return cleanup failure");
        assertContains(rollback, "DungeonSessionLifecycle.removeSession",
                "rollback should remove recreated dungeon session");
        assertNotContains(rollback, "portalSessions().remove",
                "rollback must not remove existing portal session");
        assertNotContains(rollback, "releaseFailedReservation",
                "rollback must not mutate existing instance reservation");
    }

    private static void rollbackFailureMappingIsTypedAndSuppressed() {
        RuntimeException missingMessage = new RuntimeException();
        assertEquals(
                "RuntimeException",
                DungeonActivationCommitService.safeFailureDetail(missingMessage),
                "safe detail should use class name when message is missing"
        );
        RuntimeException blankMessage = new RuntimeException(" ");
        assertEquals(
                "RuntimeException",
                DungeonActivationCommitService.safeFailureDetail(blankMessage),
                "safe detail should use class name when message is blank"
        );
        RuntimeException aggregate =
                DungeonActivationCommitService.appendCleanupFailureForTests(
                        null,
                        "aggregate",
                        new IllegalStateException("first")
                );
        aggregate = DungeonActivationCommitService.appendCleanupFailureForTests(
                aggregate,
                "aggregate",
                new IllegalArgumentException("second")
        );
        assertEquals(2, aggregate.getSuppressed().length,
                "cleanup failures should be retained as suppressed exceptions");
    }

    private static void newTargetRollbackIsSafeAndExhaustive() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitService.java"
        );
        String newCommit = slice(
                source,
                "private static DungeonActivationCommitResult commitNewTarget",
                "private static Optional<DungeonActivationCommitFailureReason> validateCommon"
        );
        assertContains(newCommit, "DungeonSessionLifecycle.acquireForPortal",
                "new target should use explicit session acquisition");
        assertContains(newCommit, "createdDungeonSession = new CreatedDungeonSession",
                "new target should track only newly created sessions");
        assertContains(newCommit, "rollbackCreatedTargetSafely",
                "new target should use protected rollback");
        assertContains(newCommit, "originalException.addSuppressed(rollbackFailure)",
                "new target rollback failure should be suppressed on original");
        assertContains(newCommit, "safeFailureDetail(originalException)",
                "new target exception detail should be safe");
        assertOrder(
                newCommit,
                "DungeonPortalEntityService.ensurePortal",
                "consumeTributeIfNeeded",
                "tribute must only be consumed after portal creation succeeds"
        );

        String rollback = slice(
                source,
                "private static RuntimeException rollbackCreatedTargetSafely",
                "private static RuntimeException rollbackExistingOpenJoinSessionSafely"
        );
        assertContains(rollback, "DungeonPortalEntityService.removePortalsForSession",
                "new-target rollback should remove created portal entities");
        assertContains(rollback, "portalSessions().remove(session.id())",
                "new-target rollback should remove created portal session");
        assertContains(rollback, "DungeonSessionLifecycle.removeSession",
                "new-target rollback should remove created dungeon session");
        assertContains(rollback, "releaseFailedReservation",
                "new-target rollback should release created reservation");
        assertContains(rollback, "appendCleanupFailure",
                "new-target rollback should aggregate cleanup failures");
        assertContains(rollback, "return aggregate",
                "new-target rollback should return cleanup failures");
        assertContains(rollback, "createdDungeonSession != null",
                "new-target rollback must not remove pre-existing dungeon sessions");
        assertOrder(
                rollback,
                "DungeonPortalEntityService.removePortalsForSession",
                "portalSessions().remove(session.id())",
                "portal entity cleanup should not block portal session cleanup"
        );
        assertOrder(
                rollback,
                "portalSessions().remove(session.id())",
                "DungeonSessionLifecycle.removeSession",
                "portal session cleanup should not block dungeon session cleanup"
        );
        assertOrder(
                rollback,
                "DungeonSessionLifecycle.removeSession",
                "releaseFailedReservation",
                "dungeon session cleanup should not block reservation cleanup"
        );
    }

    private static void interactionHandlerNoLongerOwnsTransaction() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java"
        );

        assertContains(source, "DungeonActivationPreparationService.prepare", "handler should call preparation");
        assertContains(source, "DungeonActivationCommitService.commit", "handler should call commit");
        assertNotContains(source, "new PortalSession", "handler must not create portal sessions inline");
        assertNotContains(source, "reserveNearestUnreachedWorldgenSite", "handler must not reserve sites inline");
        assertNotContains(source, "rollbackCreatedTargetSafely", "handler must not own rollback");
        assertNotContains(source, "consumeTributeIfNeeded", "handler must not consume tribute inline");
        assertNotContains(source, "DungeonPortalEntityService", "handler must not spawn portal entities inline");
        assertNotContains(source, "DungeonSessionLifecycle.acquireForPortal", "handler must not create sessions inline");
    }

    private static void siteResolutionAndReservationApisAreSplit() throws IOException {
        String instanceService = read(
                "io/github/naimjeg/obeliskdepths/dungeon/instance/DungeonInstanceService.java"
        );
        String preparationService = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationPreparationService.java"
        );

        assertNotContains(
                instanceService,
                "findOrGenerateReservableWorldgenSite",
                "instance service must not own site discovery"
        );
        assertNotContains(
                instanceService,
                "findOrGenerateReservableSite",
                "instance service must not own site discovery"
        );
        assertContains(
                instanceService,
                "reserveResolvedWorldgenSite",
                "site reservation API should exist"
        );
        assertContains(
                preparationService,
                "WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite",
                "preparation should delegate site discovery to the worldgen provisioner"
        );
        assertContains(
                instanceService,
                "reserveSiteForNewInstance",
                "reservation should keep existing rollback rules"
        );
        assertContains(
                instanceService,
                "DungeonSiteProjectionCache.putAuthoritative",
                "projection cache should update after reservation"
        );
    }

    private static void wireFormatsRemainUnchanged() throws IOException {
        String portalEntity = read(
                "io/github/naimjeg/obeliskdepths/entity/DungeonPortalEntity.java"
        );
        String obeliskDepths = read("io/github/naimjeg/obeliskdepths/ObeliskDepths.java");
        String savedData = read(
                "io/github/naimjeg/obeliskdepths/dungeon/state/DungeonManagerSavedData.java"
        );

        assertContains(portalEntity, "portal_session_id", "portal entity serialization key should remain");
        assertNotContains(portalEntity, "prepared", "portal entity must not serialize prepared targets in phase 1");
        assertNotContains(obeliskDepths, "preparation", "phase 1 must not register preparation payloads");
        assertContains(savedData, "DungeonManagerSavedData", "saved data class should remain present");
        assertNotContains(savedData, "prepared_target", "phase 1 must not add prepared target save fields");
    }

    private static DungeonPreparationRequest request(PortalAdmissionMode mode) {
        return new DungeonPreparationRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                mode
        );
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(
                true,
                1,
                1,
                0.25F,
                1.0F,
                2
        );
    }

    private static ResolvedDungeonSite resolvedSite(DungeonSiteProjectionSource source) {
        BlockPos start = new BlockPos(8, 64, 8);
        DungeonRoomId startRoomId = DungeonRoomId.of("start");
        DungeonBounds bounds = new DungeonBounds(0, 60, 0, 15, 72, 15);
        DungeonGeneratedRoom startRoom = new DungeonGeneratedRoom(
                startRoomId,
                DungeonRoomType.START,
                bounds,
                start
        );
        DungeonSite site = new DungeonSite(
                new DungeonSiteKey(0, 0),
                bounds,
                startRoomId,
                start,
                java.util.List.of(startRoom)
        );
        return new ResolvedDungeonSite(site, source);
    }

    private static void assertFailure(
            DungeonPreparationResult result,
            DungeonPreparationFailureReason reason
    ) {
        assertInstanceOf(result, DungeonPreparationFailure.class, "failure expected");
        DungeonPreparationFailure failure = (DungeonPreparationFailure) result;
        assertEquals(reason, failure.reason(), "wrong failure reason");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Could not slice source between " + start + " and " + end);
        }
        return source.substring(startIndex, endIndex);
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

    private static void assertInstanceOf(
            Object value,
            Class<?> expectedType,
            String message
    ) {
        if (!expectedType.isInstance(value)) {
            throw new AssertionError(message + ": got " + value);
        }
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected same instance");
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + " actual=" + actual
            );
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

    private static final class CountingLookup
            implements DungeonActivationPreparationService.PreparationLookup {
        private final boolean targetDimensionValid;
        private final AtomicInteger existingLookups = new AtomicInteger();
        private final AtomicInteger siteLookups = new AtomicInteger();
        private Optional<ExistingOpenJoinTarget> existingTarget = Optional.empty();
        private Optional<ResolvedDungeonSite> generatedSite = Optional.empty();

        private CountingLookup(boolean targetDimensionValid) {
            this.targetDimensionValid = targetDimensionValid;
        }

        @Override
        public boolean targetDimensionValid() {
            return this.targetDimensionValid;
        }

        @Override
        public Optional<ExistingOpenJoinTarget> findExistingOpenJoinTarget(
                DungeonPreparationRequest request
        ) {
            this.existingLookups.incrementAndGet();
            return this.existingTarget;
        }

        @Override
        public Optional<ResolvedDungeonSite> findOrGenerateAuthoritativeSite(
                DungeonPreparationRequest request
        ) {
            this.siteLookups.incrementAndGet();
            return this.generatedSite;
        }
    }
}
