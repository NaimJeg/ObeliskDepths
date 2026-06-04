package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationCancellationReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationJobFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class DungeonPreparationMenuStateTest {
    private DungeonPreparationMenuStateTest() {
    }

    public static void main(String[] args) throws Exception {
        everyStageUsesStableWireMapping();
        unknownAndReservedStagesAreSafe();
        progressAndSynchronizationValuesAreBounded();
        submissionTokensMatchOnlyTheCurrentIdentity();
        protocolDomainsAndSignedValuesRemainDistinct();
        terminalStatesUnlockInput();
        reasonWireCodesAreExplicitAndUnique();
        lifecycleWiringAndClassloadingRemainSeparated();
        everyDisplayedTranslationExists();
    }

    private static void everyStageUsesStableWireMapping() {
        for (DungeonPreparationStage stage : DungeonPreparationStage.values()) {
            DungeonPreparationMenuState state = state(
                    true, 1, stage.wireCode(), 0, 0,
                    ObeliskPortalMenu.STATUS_SUBMITTING, 0
            );
            check(state.stage().orElseThrow() == stage,
                    "stage mapping: " + stage);
        }
    }

    private static void unknownAndReservedStagesAreSafe() {
        check(state(true, 1, 14, 0, 0, 1, 0).stage().isEmpty(),
                "wire code 14 remains reserved");
        check(state(true, 1, 30_000, 0, 0, 1, 0).stage().isEmpty(),
                "unknown stage falls back safely");
    }

    private static void progressAndSynchronizationValuesAreBounded() {
        DungeonPreparationMenuState state = state(
                true,
                Integer.MAX_VALUE,
                DungeonPreparationStage.SCANNING_EXISTING_SITES.wireCode(),
                Integer.MAX_VALUE,
                12,
                ObeliskPortalMenu.STATUS_SUBMITTING,
                0
        );
        check(state.synchronizationToken() == Short.MAX_VALUE,
                "sync token clamps to DataSlot range");
        check(state.completed() == 12 && state.total() == 12,
                "completed clamps to total");
        check(!state(false, -1, 0, -4, -3, 0, 0).determinate(),
                "negative values normalize to zero");
        check(ObeliskPortalMenu.nextSynchronizationToken(Short.MAX_VALUE) == 1,
                "sync token wraps without zero");
    }

    private static void submissionTokensMatchOnlyTheCurrentIdentity() {
        DungeonPreparationMenuState state = state(true, 7, 0, 0, 0, 1, 0);
        check(state.matchesSubmissionToken(7), "current identity matches");
        check(!state.matchesSubmissionToken(6), "old identity rejected");
        check(!state.matchesSubmissionToken(0), "empty identity rejected");
    }

    private static void protocolDomainsAndSignedValuesRemainDistinct() {
        for (int value : new int[]{
                Short.MIN_VALUE, -10, -1, 0, 1, Short.MAX_VALUE
        }) {
            check((int)(short)value == value,
                    "DataSlot signed-short round trip: " + value);
        }
        for (DungeonPreparationCancellationReason reason
                : DungeonPreparationCancellationReason.values()) {
            int encoded = -reason.wireCode();
            check((int)(short)encoded == encoded,
                    "negative cancellation encoding: " + reason);
        }
        check(DungeonPreparationStage.fromWireCode(14).isEmpty(),
                "reserved stage code remains unmapped");
        check(DungeonPreparationJobFailureReason.fromWireCode(14).isEmpty(),
                "reserved failure code remains unmapped");
    }

    private static void terminalStatesUnlockInput() {
        check(state(true, 1, 0, 0, 0, 1, 0).inputLocked(),
                "active preparation locks input");
        for (int terminal : new int[]{
                ObeliskPortalMenu.STATUS_FAILED,
                ObeliskPortalMenu.STATUS_CANCELLED,
                ObeliskPortalMenu.STATUS_READY
        }) {
            check(!state(false, 1, 0, 0, 0, terminal, 0).inputLocked(),
                    "terminal status unlocks input: " + terminal);
        }
    }

    private static void reasonWireCodesAreExplicitAndUnique() {
        Set<Integer> failureCodes = new HashSet<>();
        for (DungeonPreparationJobFailureReason reason
                : DungeonPreparationJobFailureReason.values()) {
            check(reason.wireCode() > 0 && failureCodes.add(reason.wireCode()),
                    "unique failure wire code: " + reason);
        }
        Set<Integer> cancellationCodes = new HashSet<>();
        for (DungeonPreparationCancellationReason reason
                : DungeonPreparationCancellationReason.values()) {
            check(reason.wireCode() > 0
                            && cancellationCodes.add(reason.wireCode()),
                    "unique cancellation wire code: " + reason);
        }
    }

    private static void lifecycleWiringAndClassloadingRemainSeparated()
            throws IOException {
        String menu = read("src/main/java/io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java");
        String commit = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonActivationCommitService.java");
        check(menu.contains("DungeonPreparationCancellationReason.MENU_CLOSED"),
                "menu close uses authoritative cancellation reason");
        check(menu.contains("matchesActivePreparation"),
                "stale job identity is checked");
        check(menu.contains("slots are not an atomic packet batch"),
                "DataSlot non-atomicity is documented truthfully");
        check(menu.contains("terminateSubmission("),
                "runtime and job loss share one terminal helper");
        check(menu.contains("AUTHORITATIVE_RUNTIME_UNAVAILABLE"),
                "runtime loss has a stable reason");
        check(menu.contains("AUTHORITATIVE_JOB_MISSING"),
                "missing job has a stable reason");
        check(menu.contains("clearProgressSlots();"),
                "terminal and missing-progress paths clear progress");
        check(commit.contains("menu.markActivationCommitted(job.id())"),
                "successful commit marks exact menu job");
        check(commit.contains("player.closeContainer()"),
                "successful commit closes menu server-side");
        check(!menu.contains("net.minecraft.client"),
                "dedicated-server menu has no client imports");
        check(!menu.contains(".ordinal()"),
                "menu synchronization does not use enum ordinals");
    }

    private static void everyDisplayedTranslationExists() throws IOException {
        String screen = read("src/main/java/io/github/naimjeg/obeliskdepths/client/screen/ObeliskPortalScreen.java");
        String language = read("src/generated/resources/assets/obeliskdepths/lang/en_us.json");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"(gui\\.obeliskdepths\\.portal\\.[a-z0-9_.]+)\\\"")
                .matcher(screen);
        int checked = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            check(language.contains("\"" + key + "\""),
                    "translation exists: " + key);
            checked++;
        }
        check(checked > 20, "translation audit inspected displayed states");
    }

    private static DungeonPreparationMenuState state(
            boolean active,
            int token,
            int stage,
            int completed,
            int total,
            int terminal,
            int reason
    ) {
        return new DungeonPreparationMenuState(
                active, token, stage, completed, total, terminal, reason
        );
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
