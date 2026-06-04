
package io.github.naimjeg.obeliskdepths.dungeon.preparation;

/**
 * Backend abstraction for {@link DungeonSiteClaimManager} thread confinement.
 */
interface DungeonSiteClaimBackend {
    boolean isOwnerThread();
}
