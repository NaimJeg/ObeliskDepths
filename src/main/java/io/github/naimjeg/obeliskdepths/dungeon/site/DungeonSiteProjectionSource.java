package io.github.naimjeg.obeliskdepths.dungeon.site;

public enum DungeonSiteProjectionSource {
    /*
     * Authoritative:
     * Derived from an actual vanilla StructureStart and its serialized pieces.
     */
    GENERATED_STRUCTURE_START,

    /*
     * Saved copy of a previously authoritative projection.
     * This is acceptable only when it was originally created from a generated
     * StructureStart projection.
     */
    SAVED_SNAPSHOT,

    /*
     * Prototype/debug only:
     * Derived from deterministic planning without reading a real StructureStart.
     * This may not match actual terrain or actual pieces.
     */
    PLANNED_PROTOTYPE;

    public boolean authoritative() {
        return this == GENERATED_STRUCTURE_START
                || this == SAVED_SNAPSHOT;
    }
}
