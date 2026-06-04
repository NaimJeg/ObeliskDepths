package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;

import java.util.*;

public final class DungeonSiteStore {
    private final Map<DungeonSiteKey, DungeonSiteRecord> records = new HashMap<>();
    private final Map<DungeonSiteKey, DungeonSite> snapshots = new HashMap<>();
    private final Map<DungeonInstanceId, DungeonSiteKey> reservedSiteByInstance =
            new HashMap<>();
    private final Map<DungeonSiteKey, DungeonInstanceId> reservedInstanceBySite =
            new HashMap<>();
    private final Runnable dirty;

    public DungeonSiteStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void loadRecords(Collection<DungeonSiteRecord> records) {
        for (DungeonSiteRecord record : records) {
            this.putLoadedRecord(record);
        }
    }

    public void loadSnapshots(Collection<DungeonSite> snapshots) {
        for (DungeonSite snapshot : snapshots) {
            this.putLoadedSnapshot(snapshot);
        }
    }

    public Collection<DungeonSiteRecord> records() {
        return List.copyOf(this.records.values());
    }

    public Collection<DungeonSite> snapshots() {
        return List.copyOf(this.snapshots.values());
    }

    public Optional<DungeonSite> snapshot(DungeonSiteKey siteKey) {
        Objects.requireNonNull(siteKey, "siteKey");
        return Optional.ofNullable(this.snapshots.get(siteKey));
    }

    public boolean isUnreached(DungeonSiteKey siteKey) {
        return !this.records.containsKey(siteKey);
    }

    public boolean isReserved(DungeonSiteKey siteKey) {
        DungeonSiteRecord record = this.records.get(siteKey);

        return record != null && record.status() == DungeonSiteUsageStatus.RESERVED;
    }

    public String generatedReservationRejectionReason(DungeonSiteKey siteKey) {
        DungeonSiteRecord record = this.records.get(siteKey);

        if (record == null) {
            return "candidate_accepted";
        }

        if (record.status() == DungeonSiteUsageStatus.RESERVED) {
            return "candidate_reserved";
        }

        if (record.status().isTerminal()) {
            return "candidate_already_reached";
        }

        return "candidate_predicate_rejected";
    }

    public Optional<DungeonSiteRecord> record(DungeonSiteKey siteKey) {
        return Optional.ofNullable(this.records.get(siteKey));
    }

    public int recordCount() {
        return this.records.size();
    }

    public long reservedCount() {
        return this.records.values()
                .stream()
                .filter(record -> record.status() == DungeonSiteUsageStatus.RESERVED)
                .count();
    }

    public long retiredCount() {
        return this.records.values()
                .stream()
                .filter(record -> record.status().isTerminal())
                .count();
    }

    public Optional<DungeonSiteKey> reservedSite(DungeonInstanceId instanceId) {
        return Optional.ofNullable(this.reservedSiteByInstance.get(instanceId));
    }

    public boolean isReservedFor(
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        DungeonSiteKey reservedSite = this.reservedSiteByInstance.get(instanceId);
        DungeonInstanceId reservedInstance = this.reservedInstanceBySite.get(siteKey);
        DungeonSiteRecord record = this.records.get(siteKey);
        return siteKey.equals(reservedSite)
                && instanceId.equals(reservedInstance)
                && record != null
                && record.isReservedFor(instanceId);
    }

    public Optional<ReservedSiteState> reservedState(
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(siteKey, "siteKey");

        DungeonSiteKey reservedSite = this.reservedSiteByInstance.get(instanceId);
        if (!siteKey.equals(reservedSite)) {
            return Optional.empty();
        }

        DungeonInstanceId reservedInstance = this.reservedInstanceBySite.get(siteKey);
        if (!instanceId.equals(reservedInstance)) {
            return Optional.empty();
        }

        DungeonSiteRecord record = this.records.get(siteKey);
        if (record == null || !record.isReservedFor(instanceId)) {
            return Optional.empty();
        }

        DungeonSite snapshot = this.snapshots.get(siteKey);
        if (snapshot == null || !snapshot.key().equals(siteKey)) {
            return Optional.empty();
        }

        return Optional.of(new ReservedSiteState(record, snapshot));
    }

    public void reserve(
            DungeonSite site,
            DungeonInstanceId instanceId,
            long gameTime
    ) {
        DungeonSiteKey existingSite = this.reservedSiteByInstance.get(instanceId);
        if (existingSite != null) {
            throw new IllegalStateException(
                    "Dungeon instance already has a reserved site: "
                            + instanceId
                            + " site="
                            + existingSite
            );
        }

        DungeonInstanceId existingInstance = this.reservedInstanceBySite.get(site.key());
        if (existingInstance != null) {
            throw new IllegalStateException(
                    "Dungeon site is already reserved by another instance: "
                            + site.key()
                            + " instance="
                            + existingInstance
            );
        }

        if (!this.isUnreached(site.key())) {
            throw new IllegalStateException(
                    "Dungeon site is already known/reserved/retired: " + site.key()
            );
        }

        DungeonSiteRecord record = DungeonSiteRecord.reserved(
                site.key(),
                instanceId,
                gameTime
        );

        this.records.put(site.key(), record);
        this.reservedSiteByInstance.put(instanceId, site.key());
        this.reservedInstanceBySite.put(site.key(), instanceId);
        this.snapshots.put(site.key(), site);
        this.assertReservedState(instanceId, site.key());
        this.dirty.run();
    }

    public boolean releaseReservation(
            DungeonInstanceId instanceId,
            DungeonSiteKey fallbackSiteKey
    ) {
        DungeonSiteKey siteKey = this.reservedSiteByInstance.get(instanceId);
        if (siteKey == null || !siteKey.equals(fallbackSiteKey)) {
            return false;
        }

        DungeonSiteRecord record = this.records.get(siteKey);
        if (record == null || !record.isReservedFor(instanceId)) {
            return false;
        }

        this.reservedSiteByInstance.remove(instanceId);
        this.reservedInstanceBySite.remove(siteKey);
        this.snapshots.remove(siteKey);
        this.records.remove(siteKey);
        this.assertNoLiveSnapshot(siteKey);
        this.dirty.run();
        return true;
    }

    public boolean retireReservation(
            DungeonInstanceId instanceId,
            DungeonSiteKey fallbackSiteKey,
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                    "Runtime instance can only retire a site with terminal status."
            );
        }

        DungeonSiteKey siteKey = this.reservedSiteByInstance.get(instanceId);
        if (siteKey == null || !siteKey.equals(fallbackSiteKey)) {
            return false;
        }

        DungeonSiteRecord record = this.records.get(siteKey);
        if (record == null || !record.isReservedFor(instanceId)) {
            return false;
        }

        this.reservedSiteByInstance.remove(instanceId);
        this.reservedInstanceBySite.remove(siteKey);
        this.snapshots.remove(siteKey);
        this.records.put(siteKey, record.retire(finalStatus, gameTime));
        this.assertNoLiveSnapshot(siteKey);
        this.dirty.run();
        return true;
    }

    private void assertReservedState(
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        if (this.reservedState(instanceId, siteKey).isEmpty()) {
            throw new IllegalStateException(
                    "Reserved dungeon site invariant was not established: instance="
                            + instanceId
                            + " site="
                            + siteKey
            );
        }
    }

    private void assertNoLiveSnapshot(DungeonSiteKey siteKey) {
        if (this.snapshots.containsKey(siteKey)) {
            throw new IllegalStateException(
                    "Released or retired dungeon site retained runtime snapshot: "
                            + siteKey
            );
        }
    }

    private void putLoadedRecord(DungeonSiteRecord record) {
        if (this.records.containsKey(record.siteKey())) {
            throw new IllegalStateException(
                    "Duplicate dungeon site record in saved data: " + record.siteKey()
            );
        }

        if (record.status() == DungeonSiteUsageStatus.RESERVED) {
            DungeonInstanceId instanceId = record.activeInstanceId()
                    .orElseThrow(() -> new IllegalStateException(
                            "Reserved dungeon site record has no active instance id: "
                                    + record.siteKey()
                    ));
            DungeonSiteKey previousSite = this.reservedSiteByInstance.get(instanceId);
            if (previousSite != null && !previousSite.equals(record.siteKey())) {
                throw new IllegalStateException(
                        "Dungeon instance has multiple reserved sites in saved data: "
                                + instanceId
                );
            }

            DungeonInstanceId previousInstance =
                    this.reservedInstanceBySite.get(record.siteKey());
            if (previousInstance != null && !previousInstance.equals(instanceId)) {
                throw new IllegalStateException(
                        "Dungeon site has multiple reserved instances in saved data: "
                                + record.siteKey()
                );
            }

            this.reservedSiteByInstance.put(instanceId, record.siteKey());
            this.reservedInstanceBySite.put(record.siteKey(), instanceId);
        }

        this.records.put(record.siteKey(), record);
    }

    private void putLoadedSnapshot(DungeonSite snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        if (this.snapshots.containsKey(snapshot.key())) {
            throw new IllegalStateException(
                    "Duplicate dungeon site snapshot in saved data: " + snapshot.key()
            );
        }

        this.snapshots.put(snapshot.key(), snapshot);
    }

    /**
     * Restores a complete reserved site state after a failed teardown
     * transaction. Validates ownership invariants before applying any
     * mutation.
     */
    /**
     * Restores the exact reserved site state captured before a failed teardown.
     *
     * Accepted current states are:
     * - the exact original reserved state;
     * - a partially removed original reservation;
     * - no record/snapshot/indexes after release;
     * - a compatible terminal record after retirement.
     *
     * Any unrelated record, snapshot or ownership mapping is rejected.
     */
    public void restoreReservedStateForTransaction(
            DungeonInstanceId instanceId,
            ReservedSiteState state
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(state, "state");

        DungeonSiteRecord originalRecord = state.record();
        DungeonSite originalSnapshot = state.snapshot();
        DungeonSiteKey siteKey = originalRecord.siteKey();

        if (!siteKey.equals(originalSnapshot.key())) {
            throw new IllegalArgumentException(
                    "ReservedSiteState record key does not match snapshot key: record="
                            + siteKey
                            + " snapshot="
                            + originalSnapshot.key()
            );
        }

        if (originalRecord.status() != DungeonSiteUsageStatus.RESERVED
                || !originalRecord.isReservedFor(instanceId)) {
            throw new IllegalArgumentException(
                    "ReservedSiteState record must be RESERVED for instance "
                            + instanceId
            );
        }

        DungeonSiteKey existingSiteForInstance =
                this.reservedSiteByInstance.get(instanceId);
        if (existingSiteForInstance != null
                && !existingSiteForInstance.equals(siteKey)) {
            throw new IllegalStateException(
                    "Restore would overwrite another site for instance: instance="
                            + instanceId
                            + " existing="
                            + existingSiteForInstance
                            + " requested="
                            + siteKey
            );
        }

        DungeonInstanceId existingInstanceForSite =
                this.reservedInstanceBySite.get(siteKey);
        if (existingInstanceForSite != null
                && !existingInstanceForSite.equals(instanceId)) {
            throw new IllegalStateException(
                    "Restore would overwrite another instance for site: site="
                            + siteKey
                            + " existing="
                            + existingInstanceForSite
                            + " requested="
                            + instanceId
            );
        }

        /*
         * Detect hidden duplicate forward mappings, even if the direct reverse
         * index currently contains the expected value.
         */
        for (Map.Entry<DungeonInstanceId, DungeonSiteKey> entry
                : this.reservedSiteByInstance.entrySet()) {
            if (entry.getValue().equals(siteKey)
                    && !entry.getKey().equals(instanceId)) {
                throw new IllegalStateException(
                        "Another instance index points to restoration site: site="
                                + siteKey
                                + " instance="
                                + entry.getKey()
                );
            }
        }

        for (Map.Entry<DungeonSiteKey, DungeonInstanceId> entry
                : this.reservedInstanceBySite.entrySet()) {
            if (entry.getValue().equals(instanceId)
                    && !entry.getKey().equals(siteKey)) {
                throw new IllegalStateException(
                        "Restoration instance is indexed by another site: instance="
                                + instanceId
                                + " site="
                                + entry.getKey()
                );
            }
        }

        for (DungeonSiteRecord record : this.records.values()) {
            if (!record.siteKey().equals(siteKey)
                    && record.isReservedFor(instanceId)) {
                throw new IllegalStateException(
                        "Restoration instance already owns another reserved record: instance="
                                + instanceId
                                + " site="
                                + record.siteKey()
                );
            }
        }

        DungeonSiteRecord currentRecord = this.records.get(siteKey);
        if (currentRecord != null
                && !currentRecord.equals(originalRecord)
                && !isCompatibleRetiredRollbackRecord(
                currentRecord,
                originalRecord
        )) {
            throw new IllegalStateException(
                    "Restore would overwrite an unrelated site record: site="
                            + siteKey
                            + " current="
                            + currentRecord
                            + " original="
                            + originalRecord
            );
        }

        DungeonSite currentSnapshot = this.snapshots.get(siteKey);
        if (currentSnapshot != null
                && !currentSnapshot.equals(originalSnapshot)) {
            throw new IllegalStateException(
                    "Restore would overwrite a different site snapshot: site="
                            + siteKey
            );
        }

        boolean alreadyRestored =
                originalRecord.equals(currentRecord)
                        && originalSnapshot.equals(currentSnapshot)
                        && siteKey.equals(
                        this.reservedSiteByInstance.get(instanceId)
                )
                        && instanceId.equals(
                        this.reservedInstanceBySite.get(siteKey)
                );

        if (alreadyRestored) {
            return;
        }

        this.records.put(siteKey, originalRecord);
        this.snapshots.put(siteKey, originalSnapshot);
        this.reservedSiteByInstance.put(instanceId, siteKey);
        this.reservedInstanceBySite.put(siteKey, instanceId);

        this.assertReservedState(instanceId, siteKey);
        this.dirty.run();
    }

    private static boolean isCompatibleRetiredRollbackRecord(
            DungeonSiteRecord current,
            DungeonSiteRecord originalReserved
    ) {
        return current.siteKey().equals(originalReserved.siteKey())
                && current.status().isTerminal()
                && current.activeInstanceId().isEmpty()
                && current.firstReservedGameTime()
                == originalReserved.firstReservedGameTime();
    }

    public record ReservedSiteState(
            DungeonSiteRecord record,
            DungeonSite snapshot
    ) {
        public ReservedSiteState {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
