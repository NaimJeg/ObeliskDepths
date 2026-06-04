package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public final class DungeonLayoutGenerationException extends RuntimeException {
    public DungeonLayoutGenerationException(String message) {
        super(message);
    }

    public DungeonLayoutGenerationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
