package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/*
 * ARCHITECTURAL INVARIANT — VANILLA WORLDGEN REMAINS AUTHORITATIVE
 *
 * Physical dungeon geometry must be produced exclusively by Minecraft's
 * structure/chunk world-generation pipeline.
 *
 * Runtime allocation may request bounded vanilla chunk generation for a valid
 * structure-placement candidate when no generated site is available.
 *
 * Runtime code must never manually place dungeon blocks, fabricate a
 * StructureStart, fabricate generated room metadata, or promote prototype
 * planning data into an authoritative DungeonSite.
 *
 * After generation, runtime metadata must always be read back from the actual
 * vanilla StructureStart and serialized ObeliskDungeonPiece instances.
 */
public final class DungeonInstanceService {
    private DungeonInstanceService() {
    }

    /*
     * This method reserves an existing generated site only.
     *
     * Do not add planning, template placement, block writes, piece materialization,
     * terrain repair, or fallback generation here. If no suitable generated site
     * exists, allocation must fail without modifying dungeon geometry.
     */

    public static Optional<DungeonInstance> reserveResolvedWorldgenSite(
            ServerLevel dungeonLevel,
            ResolvedDungeonSite resolved,
            DungeonDifficulty difficulty
    ) {
        long startNanos = System.nanoTime();
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        if (!validateReservableResolvedSite(data, resolved)) {
            return Optional.empty();
        }

        DungeonSite site = resolved.site();

        DungeonRuntimeArtifactCleanupService.reconcileStaleRewardArtifactsForSite(
                dungeonLevel,
                site
        );

        DungeonInstance instance = reserveSiteForNewInstance(
                data,
                difficulty,
                site,
                dungeonLevel.getGameTime()
        );

        io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.debug(
                "[OD reservation] reserved generated site={} source={} instance={} elapsedMicros={}",
                site.key(),
                resolved.source(),
                instance.id(),
                (System.nanoTime() - startNanos) / 1_000L
        );

        return Optional.of(instance);
    }

    private static boolean validateReservableResolvedSite(
            DungeonManagerSavedData data,
            ResolvedDungeonSite resolved
    ) {
        if (resolved == null) {
            throw new IllegalArgumentException("Resolved dungeon site must be present.");
        }

        if (!resolved.authoritative()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected non-authoritative site source={} key={}",
                    resolved.source(),
                    resolved.site().key()
            );
            return false;
        }

        DungeonSite site = resolved.site();
        String rejectionReason =
                data.sites().generatedReservationRejectionReason(site.key());
        if (!"candidate_accepted".equals(rejectionReason)) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] reservation conflict site={} reason={}",
                    site.key(),
                    rejectionReason
            );
            return false;
        }

        Optional<DungeonGeneratedRoom> primaryEntry = site.primaryEntryRoom();
        if (primaryEntry.isEmpty() || !primaryEntry.get().contains(site.startPos())) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected generated site with invalid primary entry site={} start={} source={}",
                    site.key(),
                    site.startPos(),
                    resolved.source()
            );
            return false;
        }

        return true;
    }

    public static boolean releaseFailedReservation(
            ServerLevel dungeonLevel,
            DungeonInstanceId id
    ) {
        if (!dungeonLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Dungeon instance teardown must run on the server thread."
            );
        }
        return DungeonManagerSavedData.get(dungeonLevel)
                .releaseReservedDungeonRuntime(id);
    }

    public static boolean retireRuntimeInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        if (!dungeonLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Dungeon instance teardown must run on the server thread."
            );
        }
        return DungeonManagerSavedData.get(dungeonLevel)
                .retireReservedDungeonRuntime(id, finalStatus, gameTime);
    }

    private static DungeonInstance reserveSiteForNewInstance(
            DungeonManagerSavedData data,
            DungeonDifficulty difficulty,
            DungeonSite site,
            long gameTime
    ) {
        if (!data.sites().isUnreached(site.key())) {
            throw new IllegalStateException(
                    "Dungeon site is already known/reserved/retired: " + site.key()
            );
        }

        DungeonInstanceCreation creation =
                DungeonInstanceFactory.create(
                        difficulty,
                        site,
                        gameTime
                );

        DungeonTerritory territory = creation.territory();
        DungeonInstance instance = creation.instance();

        prevalidateReservation(data, site, territory, instance);

        boolean territoryCreated = false;
        boolean instanceCreated = false;
        boolean siteReserved = false;
        boolean roomStatesCreated = false;
        try {
            data.territories().put(territory);
            territoryCreated = true;

            data.instances().put(instance);
            instanceCreated = true;

            data.sites().reserve(site, instance.id(), gameTime);
            siteReserved = true;

            data.roomStates().initializeRoomStates(instance, site);
            roomStatesCreated = true;
        } catch (RuntimeException exception) {
            rollbackFailedReservation(
                    data,
                    site,
                    territory,
                    instance,
                    roomStatesCreated,
                    siteReserved,
                    instanceCreated,
                    territoryCreated
            );
            throw exception;
        }

        return instance;
    }

    private static void prevalidateReservation(
            DungeonManagerSavedData data,
            DungeonSite site,
            DungeonTerritory territory,
            DungeonInstance instance
    ) {
        if (data.territories().get(territory.id()).isPresent()) {
            throw new IllegalStateException(
                    "Dungeon territory already exists: " + territory.id()
            );
        }

        if (data.instances().get(instance.id()).isPresent()) {
            throw new IllegalStateException(
                    "Dungeon instance already exists: " + instance.id()
            );
        }

        if (!data.sites().isUnreached(site.key())) {
            throw new IllegalStateException(
                    "Dungeon site is already known/reserved/retired: " + site.key()
            );
        }

        if (data.roomStates().hasAnyForInstance(instance.id())) {
            throw new IllegalStateException(
                    "Dungeon room states already exist for instance: " + instance.id()
            );
        }

        data.roomStates().validateCanInitializeRoomStates(instance, site);
    }

    private static void rollbackFailedReservation(
            DungeonManagerSavedData data,
            DungeonSite site,
            DungeonTerritory territory,
            DungeonInstance instance,
            boolean roomStatesCreated,
            boolean siteReserved,
            boolean instanceCreated,
            boolean territoryCreated
    ) {
        if (roomStatesCreated) {
            data.roomStates().removeInstance(instance.id());
        }

        if (siteReserved) {
            data.sites().releaseReservation(instance.id(), site.key());
        }

        if (instanceCreated) {
            data.instances().remove(instance.id());
        }

        if (territoryCreated) {
            data.territories().remove(territory.id());
        }
    }
}
