package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

public final class ObeliskDungeonPlacementSettings {
    /*
     * These constants mirror data/obeliskdepths/worldgen/structure_set/
     * obelisk_dungeons.json and are used by cheap candidate lookup/debug
     * diagnostics. Vanilla random-spread placement is authoritative; the
     * temporary radial debug site's conservative radius is intentionally not
     * used to reject structure starts at runtime or during worldgen.
     */
    public static final int SPACING = 16;
    public static final int SEPARATION = 8;
    public static final int SALT = 91827364;
    public static final int MAX_SITE_RADIUS_BLOCKS = 256;
    public static final int MAX_LOOKUP_CANDIDATES = 128;

    private ObeliskDungeonPlacementSettings() {
    }
}
