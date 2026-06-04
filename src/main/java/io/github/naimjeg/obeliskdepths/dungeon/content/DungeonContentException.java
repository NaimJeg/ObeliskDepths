package io.github.naimjeg.obeliskdepths.dungeon.content;

import net.minecraft.resources.Identifier;

public final class DungeonContentException extends RuntimeException {
    private final Identifier resourceId;
    private final String reason;

    public DungeonContentException(
            Identifier resourceId,
            String reason
    ) {
        super("Dungeon content error: resource="
                + resourceId
                + " reason="
                + reason);
        this.resourceId = resourceId;
        this.reason = reason;
    }

    public DungeonContentException(
            Identifier resourceId,
            String reason,
            Throwable cause
    ) {
        super("Dungeon content error: resource="
                + resourceId
                + " reason="
                + reason, cause);
        this.resourceId = resourceId;
        this.reason = reason;
    }

    public Identifier resourceId() {
        return this.resourceId;
    }

    public String reason() {
        return this.reason;
    }
}
