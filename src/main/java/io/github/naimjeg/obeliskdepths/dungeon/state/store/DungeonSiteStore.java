package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<DungeonSite> snapshot(DungeonSiteKey siteKey) {
        return Optional.ofNullable(this.snapshots.get(siteKey));
    }

    public boolean putSnapshot(DungeonSite site) {
        DungeonSite previous = this.snapshots.put(site.key(), site);

        if (!site.equals(previous)) {
            this.dirty.run();
            return true;
        }

        return false;
    }

    public boolean removeSnapshot(DungeonSiteKey siteKey) {
        boolean changed = this.snapshots.remove(siteKey) != null;

        if (changed) {
            this.dirty.run();
        }

        return changed;
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
        this.dirty.run();
        return true;
    }

    public boolean retireReservation(
            DungeonInstanceId instanceId,
            DungeonSiteKey fallbackSiteKey,
            long firstReservedGameTime,
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
        this.dirty.run();
        return true;
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
        if (this.snapshots.containsKey(snapshot.key())) {
            throw new IllegalStateException(
                    "Duplicate dungeon site snapshot in saved data: " + snapshot.key()
            );
        }

        this.snapshots.put(snapshot.key(), snapshot);
    }
}
