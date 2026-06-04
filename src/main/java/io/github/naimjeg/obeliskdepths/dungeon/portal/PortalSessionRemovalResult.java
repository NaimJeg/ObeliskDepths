package io.github.naimjeg.obeliskdepths.dungeon.portal;

import java.util.List;

/**
 * Result of a portal session removal operation.
 *
 * @param sessionExisted   whether a persistent session was found
 * @param sessionRemoved   whether the persistent session was actually removed
 * @param preparedEntryRemoved whether the transient prepared entry was removed
 * @param sourcePortalsRemoved number of source-portal entities discarded
 * @param cleanupFailures  failures encountered during removal, never null
 */
public record PortalSessionRemovalResult(
        boolean sessionExisted,
        boolean sessionRemoved,
        boolean preparedEntryRemoved,
        int sourcePortalsRemoved,
        List<Throwable> cleanupFailures
) {
}
