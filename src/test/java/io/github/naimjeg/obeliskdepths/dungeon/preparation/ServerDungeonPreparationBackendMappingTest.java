package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonStructureStartReader;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.LoadedDungeonSiteProjectionFailure;

import java.util.Arrays;

/**
 * Tests exhaustiveness and correctness of the failure-reason mappings in
 * {@link ServerDungeonPreparationExecutionBackend}.
 *
 * <p>The mapping methods are package-private; this test lives in the same
 * package to avoid heavyweight Mock-server setup.</p>
 */
public final class ServerDungeonPreparationBackendMappingTest {
    private ServerDungeonPreparationBackendMappingTest() {
    }

    public static void main(String[] args) {
        structureStartFailureExhaustiveCoverage();
        structureStartFailureMappingsAreDeterministic();
        projectionFailureExhaustiveCoverage();
        projectionFailureMappingsAreDeterministic();
        noneFailureIsTreatedAsInvariantViolation();
    }

    static void structureStartFailureExhaustiveCoverage() {
        for (DungeonStructureStartReader.LoadedStructureStartResult.Failure f
                : DungeonStructureStartReader.LoadedStructureStartResult.Failure.values()) {
            DungeonPreparationJobFailureReason reason =
                    ServerDungeonPreparationExecutionBackend.failureReasonFor(f);
            assertNotNull(reason,
                    "every LoadedStructureStartResult.Failure must map to a reason: "
                            + f.name());
            // Verify no two enum constants map to null/default/unspecified
            assertInstanceOf(
                    DungeonPreparationJobFailureReason.class,
                    reason,
                    "mapped reason for " + f.name() + " is a valid enum constant"
            );
        }
    }

    static void structureStartFailureMappingsAreDeterministic() {
        assertEquals(
                DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.NONE
                ),
                "NONE -> INTERNAL_ERROR"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.CHUNK_LOAD_FAILED,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.CHUNK_NOT_LOADED
                ),
                "CHUNK_NOT_LOADED -> CHUNK_LOAD_FAILED"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.STRUCTURE_TYPE_MISSING
                ),
                "STRUCTURE_TYPE_MISSING -> INTERNAL_ERROR"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.STRUCTURE_START_MISSING,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.STRUCTURE_START_MISSING
                ),
                "STRUCTURE_START_MISSING -> STRUCTURE_START_MISSING"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.STRUCTURE_START_INVALID
                ),
                "STRUCTURE_START_INVALID -> STRUCTURE_START_INVALID"
        );
    }

    static void projectionFailureExhaustiveCoverage() {
        for (LoadedDungeonSiteProjectionFailure f
                : LoadedDungeonSiteProjectionFailure.values()) {
            DungeonPreparationJobFailureReason reason =
                    ServerDungeonPreparationExecutionBackend.projectionFailureReason(f);
            assertNotNull(reason,
                    "every LoadedDungeonSiteProjectionFailure must map to a reason: "
                            + f.name());
            assertInstanceOf(
                    DungeonPreparationJobFailureReason.class,
                    reason,
                    "mapped reason for " + f.name() + " is a valid enum constant"
            );
        }
    }

    static void projectionFailureMappingsAreDeterministic() {
        assertEquals(
                DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID,
                ServerDungeonPreparationExecutionBackend.projectionFailureReason(
                        LoadedDungeonSiteProjectionFailure.NO_PIECES
                ),
                "NO_PIECES -> STRUCTURE_START_INVALID"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID,
                ServerDungeonPreparationExecutionBackend.projectionFailureReason(
                        LoadedDungeonSiteProjectionFailure.INVALID_PRIMARY_ENTRY_COUNT
                ),
                "INVALID_PRIMARY_ENTRY_COUNT -> STRUCTURE_START_INVALID"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.NON_AUTHORITATIVE_SITE,
                ServerDungeonPreparationExecutionBackend.projectionFailureReason(
                        LoadedDungeonSiteProjectionFailure.OUTSIDE_VANILLA_REFERENCE_DISTANCE
                ),
                "OUTSIDE_VANILLA_REFERENCE_DISTANCE -> NON_AUTHORITATIVE_SITE"
        );
        assertEquals(
                DungeonPreparationJobFailureReason.NON_AUTHORITATIVE_SITE,
                ServerDungeonPreparationExecutionBackend.projectionFailureReason(
                        LoadedDungeonSiteProjectionFailure.INCOMPLETE_PROJECTED_METADATA
                ),
                "INCOMPLETE_PROJECTED_METADATA -> NON_AUTHORITATIVE_SITE"
        );
    }

    static void noneFailureIsTreatedAsInvariantViolation() {
        assertEquals(
                DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                ServerDungeonPreparationExecutionBackend.failureReasonFor(
                        DungeonStructureStartReader.LoadedStructureStartResult.Failure.NONE
                ),
                "NONE failure must be treated as an internal invariant violation"
        );
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (!expected.equals(actual)) {
            throw new AssertionError(msg + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertNotNull(Object actual, String msg) {
        if (actual == null) {
            throw new AssertionError(msg);
        }
    }

    private static void assertInstanceOf(
            Class<?> expectedType,
            Object actual,
            String msg
    ) {
        if (actual == null || !expectedType.isInstance(actual)) {
            throw new AssertionError(
                    msg + ": expected instance of " + expectedType.getSimpleName()
                            + " but was " + actual
            );
        }
    }
}
