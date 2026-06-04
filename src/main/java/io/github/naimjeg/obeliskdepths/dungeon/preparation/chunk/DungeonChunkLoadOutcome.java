package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import java.util.Objects;

/**
 * Normalized result of a single chunk-load ticket request.
 *
 * <p>The preparation system needs only to know whether the requested chunk
 * reached the required state; it does not retain mutable vanilla chunk
 * collections. Implementations contain only completion classification,
 * diagnostics, and (for exceptional completion) the original failure.
 *
 * <p>Every non-success variant is a failure from the lease manager perspective.
 */
public sealed interface DungeonChunkLoadOutcome
        permits DungeonChunkLoadOutcome.Success,
                DungeonChunkLoadOutcome.ExceptionalCompletion,
                DungeonChunkLoadOutcome.UnloadedResult,
                DungeonChunkLoadOutcome.UnexpectedResultType {

    /** Whether the chunk reached the required state. */
    boolean isSuccess();

    /** A human-readable diagnostic detail, never null. */
    String detail();

    /** The chunk was loaded successfully. */
    record Success() implements DungeonChunkLoadOutcome {
        static final Success INSTANCE = new Success();

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String detail() {
            return "";
        }
    }

    /**
     * The backing {@code CompletableFuture} completed exceptionally or was
     * cancelled.
     *
     * @param throwable the exception; never null
     * @param detail    optional diagnostic detail
     */
    record ExceptionalCompletion(Throwable throwable, String detail)
            implements DungeonChunkLoadOutcome {
        public ExceptionalCompletion {
            Objects.requireNonNull(throwable, "throwable");
            detail = detail == null ? "" : detail;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The chunk-load future completed normally but the {@code ChunkResult}
     * indicated failure or contained no value.
     */
    record UnloadedResult(String detail) implements DungeonChunkLoadOutcome {
        public UnloadedResult {
            detail = detail == null ? "" : detail;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    /**
     * The completed future value was not a recognised {@code ChunkResult}.
     */
    record UnexpectedResultType(String detail) implements DungeonChunkLoadOutcome {
        public UnexpectedResultType {
            detail = detail == null ? "" : detail;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }
}
