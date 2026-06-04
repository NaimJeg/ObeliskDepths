package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationCancellationReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationJobFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationSubmission;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationSubmissionRejectionReason;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.DataSlot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
        buttonPolicyMappingIsAuthoritative();
        buttonPolicySubmissionIsBehavioral();
        buttonClickValidationOrder();
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

    private static void buttonPolicyMappingIsAuthoritative() {
        check(ObeliskPortalMenu.BUTTON_STARTER_ONLY == 0,
                "starter-only wire id remains 0");
        check(ObeliskPortalMenu.BUTTON_OPEN == 1,
                "open wire id is explicit 1");
        check(
                ObeliskPortalMenu.accessPolicyForButton(
                        ObeliskPortalMenu.BUTTON_STARTER_ONLY
                ) == SessionAccessPolicy.STARTER_ONLY,
                "starter-only button maps to STARTER_ONLY"
        );
        check(
                ObeliskPortalMenu.accessPolicyForButton(
                        ObeliskPortalMenu.BUTTON_OPEN
                ) == SessionAccessPolicy.OPEN,
                "open button maps to OPEN"
        );
        check(
                ObeliskPortalMenu.accessPolicyForButton(-1) == null,
                "unknown negative menu button does not map to a policy"
        );
        check(
                ObeliskPortalMenu.accessPolicyForButton(2) == null,
                "unknown positive menu button does not map to a policy"
        );
    }

    private static void buttonPolicySubmissionIsBehavioral() {
        List<SessionAccessPolicy> captured = new ArrayList<>();
        ObeliskPortalMenu.PreparationSubmitter submitter =
                (player, level, pos, policy, containerId, stack) -> {
                    captured.add(policy);
                    return DungeonPreparationSubmission.rejected(
                            DungeonPreparationSubmissionRejectionReason
                                    .DUPLICATE_JOB_ID,
                            null,
                            "behavior test"
                    );
                };

        submitter.submit(
                null,
                null,
                BlockPos.ZERO,
                ObeliskPortalMenu.accessPolicyForButton(
                        ObeliskPortalMenu.BUTTON_STARTER_ONLY
                ),
                1,
                ItemStack.EMPTY
        );
        check(captured.get(0) == SessionAccessPolicy.STARTER_ONLY,
                "starter-only submission captures STARTER_ONLY");

        submitter.submit(
                null,
                null,
                BlockPos.ZERO,
                ObeliskPortalMenu.accessPolicyForButton(
                        ObeliskPortalMenu.BUTTON_OPEN
                ),
                1,
                ItemStack.EMPTY
        );
        check(captured.get(1) == SessionAccessPolicy.OPEN,
                "open submission captures OPEN");
        check(captured.size() == 2,
                "only the two legal policies are captured");
    }

    private static void buttonClickValidationOrder() throws Exception {
        AtomicInteger submitCalls = new AtomicInteger();
        ObeliskPortalMenu.PreparationSubmitter submitter =
                (player, level, pos, policy, containerId, stack) -> {
                    submitCalls.incrementAndGet();
                    return DungeonPreparationSubmission.rejected(
                            DungeonPreparationSubmissionRejectionReason
                                    .DUPLICATE_JOB_ID,
                            null,
                            "locked state must not submit"
                    );
                };

        ObeliskPortalMenu idle =
                allocateMenu(ObeliskPortalMenu.STATUS_IDLE, submitter);
        ObeliskPortalMenu submitting =
                allocateMenu(ObeliskPortalMenu.STATUS_SUBMITTING, submitter);
        ObeliskPortalMenu ready =
                allocateMenu(ObeliskPortalMenu.STATUS_READY, submitter);

        check(!idle.clickMenuButton(null, -1),
                "idle menu rejects unknown button id");
        check(!idle.clickMenuButton(null, 2),
                "idle menu rejects unknown positive button id");
        check(!submitting.clickMenuButton(null, -1),
                "submitting menu rejects unknown button id");
        check(!submitting.clickMenuButton(null, 999),
                "submitting menu rejects unknown positive button id");
        check(submitting.clickMenuButton(null, ObeliskPortalMenu.BUTTON_OPEN),
                "submitting menu acknowledges legal button id without resubmitting");
        check(ready.clickMenuButton(null, ObeliskPortalMenu.BUTTON_OPEN),
                "ready menu acknowledges legal open button id without resubmitting");
        check(ready.clickMenuButton(null, ObeliskPortalMenu.BUTTON_STARTER_ONLY),
                "ready menu acknowledges legal starter-only button id without resubmitting");
        check(!ready.clickMenuButton(null, 999),
                "ready menu rejects unknown button id");
        check(submitCalls.get() == 0,
                "submitting and ready guards never invoke the submitter");

        ObeliskPortalMenu failed =
                allocateMenu(ObeliskPortalMenu.STATUS_FAILED, submitter);
        ObeliskPortalMenu cancelled =
                allocateMenu(ObeliskPortalMenu.STATUS_CANCELLED, submitter);
        check(!failed.clickMenuButton(null, ObeliskPortalMenu.BUTTON_OPEN),
                "failed retry path remains outside the terminal acknowledgement guard");
        check(!cancelled.clickMenuButton(null, ObeliskPortalMenu.BUTTON_OPEN),
                "cancelled retry path remains outside the terminal acknowledgement guard");
        check(submitCalls.get() == 0,
                "non-server retry clicks do not submit");
    }

    private static ObeliskPortalMenu allocateMenu(
            int status,
            ObeliskPortalMenu.PreparationSubmitter submitter
    ) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocateInstance = unsafeClass.getMethod(
                "allocateInstance",
                Class.class
        );
        ObeliskPortalMenu menu = (ObeliskPortalMenu) allocateInstance.invoke(
                unsafe,
                ObeliskPortalMenu.class
        );

        Field statusField = ObeliskPortalMenu.class.getDeclaredField("status");
        statusField.setAccessible(true);
        DataSlot statusSlot = DataSlot.standalone();
        statusSlot.set(status);
        statusField.set(menu, statusSlot);

        Field submitterField =
                ObeliskPortalMenu.class.getDeclaredField("preparationSubmitter");
        submitterField.setAccessible(true);
        submitterField.set(menu, submitter);
        return menu;
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
