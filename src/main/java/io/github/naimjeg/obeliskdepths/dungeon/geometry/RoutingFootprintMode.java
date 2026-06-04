package io.github.naimjeg.obeliskdepths.dungeon.geometry;

/**
 * Describes whether a footprint is derived from exact template bounds or an
 * authored routing-cell override. Neither mode is physical template geometry.
 */
public enum RoutingFootprintMode {
    AUTO,
    EXPLICIT_MASK
}
