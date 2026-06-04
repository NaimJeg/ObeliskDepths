package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Arrays;
import java.util.Optional;

public final class DungeonPreparationProgressSnapshotTest {
    private DungeonPreparationProgressSnapshotTest() {
    }

    public static void main(String[] args) {
        initialQueuedSnapshotIsEmpty();
        activeScannerProgressAcceptsMonotonicCounts();
        entryCountsRejectImpossibleValues();
        generationAttemptsAreBounded();
        preparationStageWireCodesRemainStable();
        serializationBoundsRejectOversizedDataSlotValues();
        safeSpawnProgressAllowsLargeExactTotals();
        safeSpawnProgressRejectsCheckedBeyondTotal();
        terminalCauseIsImmutableOptional();
    }

    private static void initialQueuedSnapshotIsEmpty() {
        DungeonPreparationProgressSnapshot snapshot =
                DungeonPreparationProgressSnapshot.queued();
        check(snapshot.stage() == DungeonPreparationStage.QUEUED,
                "queued progress: stage");
        check(snapshot.totalCandidateChunks() == 0,
                "queued progress: candidates");
        check(snapshot.totalEntryChunks() == 0,
                "queued progress: entries");
        check(snapshot.terminalCause().isEmpty(),
                "queued progress: no terminal cause");
    }

    private static void activeScannerProgressAcceptsMonotonicCounts() {
        DungeonPreparationProgressSnapshot snapshot =
                new DungeonPreparationProgressSnapshot(
                        DungeonPreparationStage.SCANNING_EXISTING_SITES,
                        64,
                        17,
                        11,
                        6,
                        0,
                        0,
                        0,
                        0L,
                        0L,
                        0,
                        4,
                        Optional.empty()
                );
        check(snapshot.completedCandidateChunks() == 11,
                "scanner progress: completed");
    }

    private static void entryCountsRejectImpossibleValues() {
        expectRejected(() -> new DungeonPreparationProgressSnapshot(
                DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
                0,
                0,
                0,
                0,
                6,
                3,
                4,
                0L,
                0L,
                0,
                4,
                Optional.empty()
        ));
    }

    private static void generationAttemptsAreBounded() {
        expectRejected(() -> new DungeonPreparationProgressSnapshot(
                DungeonPreparationStage.SELECTING_CANDIDATE,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                5,
                4,
                Optional.empty()
        ));
    }

   private static void preparationStageWireCodesRemainStable() {
       check(DungeonPreparationStage.QUEUED.wireCode() == 0,
               "wire: queued");
       check(DungeonPreparationStage.VALIDATING.wireCode() == 1,
               "wire: validating");
       check(DungeonPreparationStage.SCANNING_EXISTING_SITES.wireCode() == 2,
               "wire: scanning");
       check(DungeonPreparationStage.SELECTING_CANDIDATE.wireCode() == 3,
               "wire: selecting");
       check(DungeonPreparationStage.WAITING_FOR_START_CHUNK.wireCode() == 4,
               "wire: waiting start");
       check(DungeonPreparationStage.READING_STRUCTURE_START.wireCode() == 5,
               "wire: reading");
       check(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS.wireCode() == 6,
               "wire: waiting entries");
       check(DungeonPreparationStage.VALIDATING_ENTRY.wireCode() == 7,
               "wire: validating entry");
       check(DungeonPreparationStage.READY_TO_COMMIT.wireCode() == 8,
               "wire: ready commit");
       check(DungeonPreparationStage.COMMITTING.wireCode() == 9,
               "wire: committing");
       check(DungeonPreparationStage.READY.wireCode() == 10,
               "wire: ready");
       check(DungeonPreparationStage.FAILED.wireCode() == 11,
               "wire: failed");
       check(DungeonPreparationStage.CANCELLED.wireCode() == 12,
               "wire: cancelled");
       check(DungeonPreparationStage.fromWireCode(4)
                       .orElseThrow() == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
               "wire: reverse existing code");
       check(DungeonPreparationStage.fromWireCode(13)
                       .orElseThrow() == DungeonPreparationStage.REQUESTING_START_CHUNK,
               "wire: reverse new code 13 -> REQUESTING_START_CHUNK");
       check(DungeonPreparationStage.REQUESTING_START_CHUNK.wireCode() == 13,
               "wire: requesting start chunk");
       check(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS.wireCode() == 15,
               "wire: planning entry chunks");
       check(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS.wireCode() == 16,
               "wire: requesting entry chunks");
       check(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS.wireCode() == 17,
               "wire: validating entry chunks");

       check(DungeonPreparationStage.fromWireCode(15)
                       .orElseThrow() == DungeonPreparationStage.PLANNING_ENTRY_CHUNKS,
               "wire: reverse 15 -> PLANNING_ENTRY_CHUNKS");
       check(DungeonPreparationStage.fromWireCode(16)
                       .orElseThrow() == DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS,
               "wire: reverse 16 -> REQUESTING_ENTRY_CHUNKS");
       check(DungeonPreparationStage.fromWireCode(17)
                       .orElseThrow() == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
               "wire: reverse 17 -> VALIDATING_ENTRY_CHUNKS");

       check(DungeonPreparationStage.fromWireCode(14).isEmpty(),
               "wire: code 14 reserved and unmapped");

       check(DungeonPreparationStage.fromWireCode(18).isEmpty(),
               "wire: unknown large code returns empty");
       check(DungeonPreparationStage.fromWireCode(100).isEmpty(),
               "wire: far unknown code returns empty");
       check(DungeonPreparationStage.fromWireCode(Integer.MAX_VALUE).isEmpty(),
               "wire: extreme positive unknown returns empty");
       check(DungeonPreparationStage.fromWireCode(-1).isEmpty(),
               "wire: unknown code");
       check(DungeonPreparationStage.fromWireCode(-100).isEmpty(),
               "wire: far negative unknown returns empty");
       check(DungeonPreparationStage.fromWireCode(Integer.MIN_VALUE).isEmpty(),
               "wire: extreme negative unknown returns empty");

       boolean hasProjecting = Arrays.stream(DungeonPreparationStage.values())
               .anyMatch(s -> s.name().equals("PROJECTING_STRUCTURE_SITE"));
       check(!hasProjecting, "wire: PROJECTING_STRUCTURE_SITE not present");

       long uniqueWireCodes = Arrays.stream(DungeonPreparationStage.values())
               .mapToInt(DungeonPreparationStage::wireCode)
               .distinct()
               .count();
       check(uniqueWireCodes == DungeonPreparationStage.values().length,
               "wire: all enum constants have unique wire codes");
   }

    private static void serializationBoundsRejectOversizedDataSlotValues() {
        expectRejected(() -> new DungeonPreparationProgressSnapshot(
                DungeonPreparationStage.SCANNING_EXISTING_SITES,
                DungeonPreparationProgressSnapshot.MAX_MENU_DATA_VALUE + 1,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0,
                4,
                Optional.empty()
        ));
    }

    private static void terminalCauseIsImmutableOptional() {
        DungeonPreparationTerminalCause cause =
                new DungeonPreparationCancellationCause(
                        DungeonPreparationCancellationReason.USER_CANCELLED,
                        "cancelled"
                );
        DungeonPreparationProgressSnapshot snapshot =
                DungeonPreparationProgressSnapshot.empty(
                        DungeonPreparationStage.CANCELLED,
                        Optional.of(cause)
                );
        check(snapshot.terminalCause().orElseThrow() == cause,
                "terminal progress: cause");
    }

    private static void safeSpawnProgressAllowsLargeExactTotals() {
        long total = (long)DungeonPreparationProgressSnapshot.MAX_MENU_DATA_VALUE
                + 1_000_000L;
        DungeonPreparationProgressSnapshot snapshot =
                new DungeonPreparationProgressSnapshot(
                        DungeonPreparationStage.VALIDATING_ENTRY,
                        0, 0, 0, 0,
                        0, 0, 0,
                        total,
                        total - 1L,
                        0,
                        4,
                        Optional.empty()
                );
        check(snapshot.totalSafeSpawnCandidates() == total,
                "safe progress: exact large total retained");
    }

    private static void safeSpawnProgressRejectsCheckedBeyondTotal() {
        expectRejected(() -> new DungeonPreparationProgressSnapshot(
                DungeonPreparationStage.VALIDATING_ENTRY,
                0, 0, 0, 0,
                0, 0, 0,
                10L,
                11L,
                0,
                4,
                Optional.empty()
        ));
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected progress snapshot rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
