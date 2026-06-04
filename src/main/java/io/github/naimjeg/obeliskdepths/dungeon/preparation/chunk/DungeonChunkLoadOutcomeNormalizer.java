package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import net.minecraft.server.level.ChunkResult;

final class DungeonChunkLoadOutcomeNormalizer {
    private DungeonChunkLoadOutcomeNormalizer() {
    }

    /**
     * Validates the runtime value of the public wildcard future without an
     * unchecked cast and returns an immutable outcome. A normal null value,
     * unexpected type, failed {@link ChunkResult}, exception, and cancellation
     * are all terminal non-success outcomes.
     */
    static DungeonChunkLoadOutcome normalize(
            Object completedValue,
            Throwable completionFailure
    ) {
        if (completionFailure != null) {
            return new DungeonChunkLoadOutcome.ExceptionalCompletion(
                    completionFailure,
                    diagnosticDetail(completionFailure)
            );
        }

        if (completedValue == null) {
            return new DungeonChunkLoadOutcome.UnexpectedResultType(
                    "Unexpected chunk-load completion type: null"
            );
        }

        if (completedValue instanceof ChunkResult<?> chunkResult) {
            if (chunkResult.isSuccess()) {
                Object value = ChunkResult.orElse(chunkResult, null);
                if (value != null) {
                    return DungeonChunkLoadOutcome.Success.INSTANCE;
                }
                return new DungeonChunkLoadOutcome.UnloadedResult(
                        "ChunkResult indicates success but the contained value is null"
                );
            }

            String error = chunkResult.getError();
            return new DungeonChunkLoadOutcome.UnloadedResult(
                    "ChunkResult indicates failure"
                            + (error == null || error.isBlank() ? "" : ": " + error)
            );
        }

        return new DungeonChunkLoadOutcome.UnexpectedResultType(
                "Unexpected chunk-load completion type: "
                        + completedValue.getClass().getName()
        );
    }

    private static String diagnosticDetail(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }
}
