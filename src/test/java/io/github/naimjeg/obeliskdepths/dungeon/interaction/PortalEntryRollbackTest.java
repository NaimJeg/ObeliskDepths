package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PortalEntryRollbackTest {
    private PortalEntryRollbackTest() {
    }

    public static void main(String[] args) {
        actionsRunInReverseMutationOrderExactlyOnce();
        firstFailureDoesNotStopRemainingActions();
        multipleFailuresAreSuppressedOnOriginal();
        originalErrorRemainsObservableWithOrdinaryCleanupFailures();
        cleanupErrorDominatesAndPreservesEveryFailure();
        rollbackWithoutOriginalAggregatesOrdinaryFailures();
        portalServiceWiresEveryPreTeleportExitThroughRollback();
    }

    private static void actionsRunInReverseMutationOrderExactlyOnce() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        List<String> order = new ArrayList<>();
        rollback.attempt("player binding", () -> order.add("player"));
        rollback.attempt("dungeon session", () -> order.add("dungeon"));
        rollback.attempt("portal session", () -> order.add("portal"));
        rollback.attempt("instance", () -> order.add("instance"));
        rollback.finish(null);

        check(order.equals(List.of("player", "dungeon", "portal", "instance")),
                "rollback order: exact reverse mutation order");
    }

    private static void firstFailureDoesNotStopRemainingActions() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        List<String> executed = new ArrayList<>();
        rollback.attempt("player", () -> {
            executed.add("player");
            throw new IllegalStateException("first");
        });
        rollback.attempt("dungeon", () -> executed.add("dungeon"));
        rollback.attempt("portal", () -> executed.add("portal"));
        rollback.attempt("instance", () -> executed.add("instance"));
        RuntimeException original = new IllegalArgumentException("teleport");
        rollback.finish(original);

        check(executed.size() == 4, "first failure: every action attempted");
        check(original.getSuppressed().length == 1,
                "first failure: attached to original");
    }

    private static void multipleFailuresAreSuppressedOnOriginal() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        rollback.attempt("player", () -> {
            throw new IllegalStateException("player");
        });
        rollback.attempt("dungeon", () -> {
            throw new IllegalArgumentException("dungeon");
        });
        RuntimeException original = new RuntimeException("original");
        rollback.finish(original);

        check(original.getSuppressed().length == 2,
                "multiple failures: both suppressed");
        check(((PortalEntryRollback.CleanupFailure) original.getSuppressed()[0])
                        .step().equals("player"),
                "multiple failures: first step retained");
    }

    private static void originalErrorRemainsObservableWithOrdinaryCleanupFailures() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        rollback.attempt("player", () -> {
            throw new IllegalStateException("cleanup");
        });
        Error original = new AssertionError("original Error");
        rollback.finish(original);

        check(original.getSuppressed().length == 1,
                "original Error: cleanup suppressed on Error");
    }

    private static void cleanupErrorDominatesAndPreservesEveryFailure() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        Error cleanupError = new AssertionError("cleanup Error");
        rollback.attempt("player", () -> {
            throw new IllegalStateException("ordinary cleanup");
        });
        rollback.attempt("dungeon", () -> {
            throw cleanupError;
        });
        rollback.attempt("portal", () -> {
            throw new AssertionError("later cleanup Error");
        });
        RuntimeException original = new RuntimeException("original");

        try {
            rollback.finish(original);
            check(false, "cleanup Error: should dominate");
        } catch (Error observed) {
            check(observed == cleanupError, "cleanup Error: first Error dominates");
            check(observed.getSuppressed().length == 4,
                    "cleanup Error: step, later Error, ordinary failure, original preserved");
        }
    }

    private static void rollbackWithoutOriginalAggregatesOrdinaryFailures() {
        PortalEntryRollback rollback = new PortalEntryRollback();
        rollback.attempt("player", () -> {
            throw new IllegalStateException("cleanup");
        });
        try {
            rollback.finish(null);
            check(false, "empty teleport cleanup: should report failure");
        } catch (IllegalStateException observed) {
            check(observed.getSuppressed().length == 1,
                    "empty teleport cleanup: failure retained");
        }
    }

    private static void portalServiceWiresEveryPreTeleportExitThroughRollback() {
        String source;
        try {
            source = Files.readString(Path.of(
                    "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java"
            ));
        } catch (IOException failure) {
            throw new AssertionError("portal source should be readable", failure);
        }
        check(source.contains("catch (RuntimeException | Error failure)"),
                "begin: RuntimeException and Error both rollback");
        check(source.contains("mutation.rollbackBeforeTeleport(failure)"),
                "begin: original failure supplied to rollback");
        check(source.contains("mutation.rollbackBeforeTeleport(exception)"),
                "teleporter RuntimeException: robust rollback");
        check(source.contains("mutation.rollbackBeforeTeleport(error)"),
                "teleporter Error: robust rollback");
        check(source.contains("if (teleported.isEmpty())"),
                "empty teleport: explicit rollback branch");
        check(source.contains("this.playerBound = false;"),
                "rollback flags: player ownership cleared finally-safely");
        check(source.contains("this.dungeonSessionParticipantAdded = false;"),
                "rollback flags: dungeon-session ownership cleared");
        check(source.contains("this.portalParticipantAdded = false;"),
                "rollback flags: portal ownership cleared");
        check(source.contains("this.instanceParticipantAdded = false;"),
                "rollback flags: instance ownership cleared");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
