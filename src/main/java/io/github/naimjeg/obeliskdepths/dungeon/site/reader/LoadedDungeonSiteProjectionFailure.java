package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

/**
 * Structured failure reason for loaded dungeon site projection.
 */
public enum LoadedDungeonSiteProjectionFailure {
    /** The structure start contains zero pieces. */
    NO_PIECES,
    /** The structure start does not have exactly one primary entry piece. */
    INVALID_PRIMARY_ENTRY_COUNT,
    /** One or more pieces intersect chunks beyond vanilla reference distance (8). */
    OUTSIDE_VANILLA_REFERENCE_DISTANCE,
    /** The projected site metadata is incomplete or structurally invalid. */
    INCOMPLETE_PROJECTED_METADATA,
}