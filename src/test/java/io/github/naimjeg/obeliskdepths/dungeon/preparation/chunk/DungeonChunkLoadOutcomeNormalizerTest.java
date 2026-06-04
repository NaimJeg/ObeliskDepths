package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import net.minecraft.server.level.ChunkResult;

public final class DungeonChunkLoadOutcomeNormalizerTest {
    private DungeonChunkLoadOutcomeNormalizerTest() {
    }

    public static void main(String[] args) {
        exceptionalCompletion();
        normalNullCompletion();
        successfulNonNullChunkResult();
        failedChunkResult();
        successfulChunkResultWithNullContent();
        unexpectedRuntimeType();
        cancellationException();
        nullEmptyMessageException();
    }

    private static void exceptionalCompletion() {
        RuntimeException cause = new RuntimeException("load failed");
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(null, cause);

        check(outcome instanceof DungeonChunkLoadOutcome.ExceptionalCompletion,
                "exception: classification");
        DungeonChunkLoadOutcome.ExceptionalCompletion exceptional =
                (DungeonChunkLoadOutcome.ExceptionalCompletion) outcome;
        check(exceptional.throwable() == cause, "exception: cause preserved");
        check(exceptional.detail().contains("load failed"),
                "exception: detail contains message");
    }

    private static void normalNullCompletion() {
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(null, null);

        check(outcome instanceof DungeonChunkLoadOutcome.UnexpectedResultType,
                "null: classification");
        check("Unexpected chunk-load completion type: null".equals(outcome.detail()),
                "null: stable detail");
    }

    private static void successfulNonNullChunkResult() {
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(
                        ChunkResult.of("loaded"),
                        null
                );

        check(outcome instanceof DungeonChunkLoadOutcome.Success,
                "success: classification");
        check(outcome.isSuccess(), "success: flag");
    }

    private static void failedChunkResult() {
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(
                        ChunkResult.error("unloaded"),
                        null
                );

        check(outcome instanceof DungeonChunkLoadOutcome.UnloadedResult,
                "failed result: classification");
        check(outcome.detail().contains("unloaded"),
                "failed result: detail");
    }

    private static void successfulChunkResultWithNullContent() {
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(
                        ChunkResult.of(null),
                        null
                );

        check(outcome instanceof DungeonChunkLoadOutcome.UnloadedResult,
                "null content: classification");
        check(outcome.detail().contains("contained value is null"),
                "null content: detail");
    }

    private static void unexpectedRuntimeType() {
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(42, null);

        check(outcome instanceof DungeonChunkLoadOutcome.UnexpectedResultType,
                "unexpected type: classification");
        check(outcome.detail().contains(Integer.class.getName()),
                "unexpected type: runtime type detail");
    }

    private static void cancellationException() {
        java.util.concurrent.CancellationException cause =
                new java.util.concurrent.CancellationException("cancelled");
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(null, cause);

        check(outcome instanceof DungeonChunkLoadOutcome.ExceptionalCompletion,
                "cancellation: classification is exceptional");
        DungeonChunkLoadOutcome.ExceptionalCompletion exceptional =
                (DungeonChunkLoadOutcome.ExceptionalCompletion) outcome;
        check(exceptional.throwable() == cause,
                "cancellation: cause preserved");
        check(!outcome.isSuccess(), "cancellation: not success");
    }

    private static void nullEmptyMessageException() {
        RuntimeException cause = new RuntimeException();
        DungeonChunkLoadOutcome outcome =
                DungeonChunkLoadOutcomeNormalizer.normalize(null, cause);

        check(outcome instanceof DungeonChunkLoadOutcome.ExceptionalCompletion,
                "null message: classification");
        check(outcome.detail() != null && !outcome.detail().isEmpty(),
                "null message: has fallback detail");
        check(outcome.detail().contains(RuntimeException.class.getName()),
                "null message: class name in detail");
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }
}
