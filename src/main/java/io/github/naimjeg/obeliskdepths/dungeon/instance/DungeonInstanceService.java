package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.site.*;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.List;
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
    public static Optional<DungeonInstance> reserveNearestUnreachedWorldgenSite(
            ServerLevel dungeonLevel,
            BlockPos origin,
            DungeonDifficulty difficulty
    ) {
        long startNanos = System.nanoTime();
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        Optional<ResolvedDungeonSite> resolved =
                WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite(
                        dungeonLevel,
                        origin,
                        data
                );

        if (resolved.isEmpty()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] no generated dungeon site available origin={} elapsedMicros={}",
                    origin,
                    (System.nanoTime() - startNanos) / 1_000L
            );
            return Optional.empty();
        }

        if (!resolved.get().authoritative()) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected non-authoritative site source={} key={}",
                    resolved.get().source(),
                    resolved.get().site().key()
            );
            return Optional.empty();
        }

        DungeonSite site = resolved.get().site();

        if (data.sites().isReserved(site.key())) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] reservation conflict site={}",
                    site.key()
            );
            return Optional.empty();
        }

        Optional<DungeonGeneratedRoom> primaryEntry = site.primaryEntryRoom();

        if (primaryEntry.isEmpty() || !primaryEntry.get().contains(site.startPos())) {
            io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                    "[OD reservation] rejected generated site with invalid primary entry site={} start={} source={}",
                    site.key(),
                    site.startPos(),
                    resolved.get().source()
            );
            return Optional.empty();
        }

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

        DungeonSiteProjectionCache.putAuthoritative(dungeonLevel, resolved.get());

        io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.debug(
                "[OD reservation] reserved generated site={} source={} instance={} elapsedMicros={}",
                site.key(),
                resolved.get().source(),
                instance.id(),
                (System.nanoTime() - startNanos) / 1_000L
        );

        return Optional.of(instance);
    }

    public static boolean releaseFailedReservation(
            ServerLevel dungeonLevel,
            DungeonInstanceId id
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonInstance> instance = data.instances().get(id);
        if (instance.isEmpty()) {
            return false;
        }

        if (!data.sites().isReservedFor(id, instance.get().siteKey())) {
            return false;
        }

        Optional<DungeonInstance> removed = removeRuntimeState(data, id);
        if (removed.isEmpty()) {
            return false;
        }

        if (!data.sites().releaseReservation(id, removed.get().siteKey())) {
            throw new IllegalStateException(
                    "Prevalidated dungeon site release failed: " + id
            );
        }
        return true;
    }

    public static boolean retireRuntimeInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId id,
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                    "Runtime instance can only retire a site with terminal status."
            );
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonInstance> instance = data.instances().get(id);
        if (instance.isEmpty()) {
            return false;
        }

        if (!data.sites().isReservedFor(id, instance.get().siteKey())) {
            return false;
        }

        Optional<DungeonInstance> removed = removeRuntimeState(data, id);
        if (removed.isEmpty()) {
            return false;
        }

        if (!data.sites().retireReservation(
                id,
                removed.get().siteKey(),
                removed.get().createdGameTime(),
                finalStatus,
                gameTime
        )) {
            throw new IllegalStateException(
                    "Prevalidated dungeon site retirement failed: " + id
            );
        }
        return true;
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

    private static Optional<DungeonInstance> removeRuntimeState(
            DungeonManagerSavedData data,
            DungeonInstanceId id
    ) {
        Optional<DungeonRuntimeRemovalSnapshot> snapshot = prevalidateRuntimeRemoval(data, id);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        boolean raidsRemoved = false;
        boolean roomStatesRemoved = false;
        boolean territoryRemoved = false;
        boolean instanceRemoved = false;
        try {
            data.sessions().markCleanedForInstance(id);
            data.raids().removeAllForInstance(id);
            raidsRemoved = true;
            data.roomStates().removeInstanceStates(id);
            roomStatesRemoved = true;
            data.territories().remove(snapshot.get().territory().id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Prevalidated dungeon territory removal failed: "
                                    + snapshot.get().territory().id()
                    ));
            territoryRemoved = true;
            data.instances().remove(id)
                    .orElseThrow(() -> new IllegalStateException(
                            "Prevalidated dungeon instance removal failed: " + id
                    ));
            instanceRemoved = true;
        } catch (RuntimeException exception) {
            rollbackRuntimeRemoval(
                    data,
                    snapshot.get(),
                    instanceRemoved,
                    territoryRemoved,
                    roomStatesRemoved,
                    raidsRemoved
            );
            throw exception;
        }

        return Optional.of(snapshot.get().instance());
    }

    private static Optional<DungeonRuntimeRemovalSnapshot> prevalidateRuntimeRemoval(
            DungeonManagerSavedData data,
            DungeonInstanceId id
    ) {
        Optional<DungeonInstance> instance = data.instances().get(id);

        if (instance.isEmpty()) {
            return Optional.empty();
        }

        DungeonTerritory territory = data.territories().get(instance.get().territoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "Dungeon runtime removal missing territory: "
                                + instance.get().territoryId()
                ));
        Collection<DungeonRoomState> roomStates = data.roomStates().allForInstance(id);
        if (roomStates.isEmpty()) {
            throw new IllegalStateException(
                    "Dungeon runtime removal missing room states: " + id
            );
        }

        return Optional.of(new DungeonRuntimeRemovalSnapshot(
                instance.get(),
                territory,
                List.copyOf(roomStates),
                data.raids().allForInstance(id)
        ));
    }

    private static void rollbackRuntimeRemoval(
            DungeonManagerSavedData data,
            DungeonRuntimeRemovalSnapshot snapshot,
            boolean instanceRemoved,
            boolean territoryRemoved,
            boolean roomStatesRemoved,
            boolean raidsRemoved
    ) {
        if (instanceRemoved) {
            data.instances().put(snapshot.instance());
        }

        if (territoryRemoved) {
            data.territories().put(snapshot.territory());
        }

        if (roomStatesRemoved) {
            data.roomStates().restoreInstanceStates(
                    snapshot.instance().id(),
                    snapshot.roomStates()
            );
        }

        if (raidsRemoved) {
            data.raids().restoreRemovedRaids(snapshot.raids());
        }
    }

    private record DungeonRuntimeRemovalSnapshot(
            DungeonInstance instance,
            DungeonTerritory territory,
            List<DungeonRoomState> roomStates,
            List<DungeonRaidInstance> raids
    ) {
    }

}
