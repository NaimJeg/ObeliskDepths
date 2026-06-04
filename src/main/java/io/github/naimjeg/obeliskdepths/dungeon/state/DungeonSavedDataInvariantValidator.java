package io.github.naimjeg.obeliskdepths.dungeon.state;

import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;

import java.util.*;

public final class DungeonSavedDataInvariantValidator {
    private DungeonSavedDataInvariantValidator() {
    }

    public static void validateOrThrow(DungeonManagerSavedData data) {
        List<String> violations = validate(data);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid Obelisk Depths dungeon saved data:"
                            + System.lineSeparator()
                            + String.join(System.lineSeparator(), violations)
            );
        }
    }

    public static List<String> validate(DungeonManagerSavedData data) {
        Objects.requireNonNull(data, "data");
        List<String> violations = new ArrayList<>();
        Map<DungeonSiteKeyView, DungeonSite> snapshotsBySite = snapshotsBySite(data, violations);
        validateSitesAndInstances(data, snapshotsBySite, violations);
        validateTerritoriesAndRooms(data, violations);
        validatePortalAndDungeonSessions(data, violations);
        validateRaidsRewardsAndArtifacts(data, violations);
        return List.copyOf(violations);
    }

    private static void validateSitesAndInstances(
            DungeonManagerSavedData data,
            Map<DungeonSiteKeyView, DungeonSite> snapshotsBySite,
            List<String> violations
    ) {
        Map<DungeonSiteKeyView, DungeonSiteRecord> recordsBySite = new LinkedHashMap<>();
        Map<DungeonInstanceId, DungeonSiteRecord> reservedRecordByInstance =
                new LinkedHashMap<>();

        for (DungeonSiteRecord record : data.sites().records()) {
            DungeonSiteKeyView key = DungeonSiteKeyView.of(record.siteKey());
            DungeonSiteRecord previous = recordsBySite.put(key, record);
            if (previous != null) {
                violations.add("duplicate site record site=" + record.siteKey());
            }

            if (record.status() == DungeonSiteUsageStatus.RESERVED) {
                Optional<DungeonInstanceId> activeInstance = record.activeInstanceId();
                if (activeInstance.isEmpty()) {
                    violations.add("reserved site record missing active instance site="
                            + record.siteKey());
                    continue;
                }
                DungeonSiteRecord previousRecord =
                        reservedRecordByInstance.put(activeInstance.get(), record);
                if (previousRecord != null
                        && !previousRecord.siteKey().equals(record.siteKey())) {
                    violations.add("instance has multiple reserved site records instance="
                            + activeInstance.get()
                            + " first="
                            + previousRecord.siteKey()
                            + " second="
                            + record.siteKey());
                }
                Optional<DungeonInstance> instance =
                        data.instances().get(activeInstance.get());
                if (instance.isEmpty()) {
                    violations.add("reserved site points to missing instance site="
                            + record.siteKey()
                            + " instance="
                            + activeInstance.get());
                } else if (!DungeonManagerSavedData.requiresReservedSite(
                        instance.get().status())) {
                    violations.add("reserved site points to non-live instance site="
                            + record.siteKey()
                            + " instance="
                            + activeInstance.get()
                            + " status="
                            + instance.get().status().getSerializedName());
                }
                if (!snapshotsBySite.containsKey(key)) {
                    violations.add("reserved site missing authoritative snapshot site="
                            + record.siteKey()
                            + " instance="
                            + activeInstance.get());
                }
            } else {
                if (record.activeInstanceId().isPresent()) {
                    violations.add("terminal site record retains active instance site="
                            + record.siteKey()
                            + " instance="
                            + record.activeInstanceId().get());
                }
                if (snapshotsBySite.containsKey(key)) {
                    violations.add("terminal site record retains runtime snapshot site="
                            + record.siteKey());
                }
            }
        }

        for (DungeonSite site : data.sites().snapshots()) {
            DungeonSiteRecord record = recordsBySite.get(DungeonSiteKeyView.of(site.key()));
            if (record == null) {
                violations.add("orphan site snapshot without site record site=" + site.key());
            } else if (record.status() != DungeonSiteUsageStatus.RESERVED) {
                violations.add("site snapshot belongs to non-reserved record site="
                        + site.key()
                        + " status="
                        + record.status().getSerializedName());
            }
        }

        for (DungeonInstance instance : data.instances().all()) {
            if (!DungeonManagerSavedData.requiresReservedSite(instance.status())) {
                continue;
            }
            DungeonSiteRecord record = reservedRecordByInstance.get(instance.id());
            if (record == null) {
                violations.add("live instance missing reserved site record instance="
                        + instance.id()
                        + " site="
                        + instance.siteKey()
                        + " status="
                        + instance.status().getSerializedName());
                continue;
            }
            if (!record.siteKey().equals(instance.siteKey())) {
                violations.add("live instance reserved site mismatch instance="
                        + instance.id()
                        + " instanceSite="
                        + instance.siteKey()
                        + " recordSite="
                        + record.siteKey());
            }
            if (!data.sites().isReservedFor(instance.id(), instance.siteKey())) {
                violations.add("site reservation indexes disagree instance="
                        + instance.id()
                        + " site="
                        + instance.siteKey());
            }
            violations.addAll(data.validateReservedDungeon(
                    instance.id(),
                    instance.siteKey()
            ).violations());
        }
    }

    private static void validateTerritoriesAndRooms(
            DungeonManagerSavedData data,
            List<String> violations
    ) {
        Map<DungeonInstanceId, List<DungeonTerritory>> territoriesByInstance =
                new LinkedHashMap<>();
        for (DungeonTerritory territory : data.territories().all()) {
            territoriesByInstance
                    .computeIfAbsent(territory.instanceId(), ignored -> new ArrayList<>())
                    .add(territory);
            if (data.instances().get(territory.instanceId()).isEmpty()) {
                violations.add("territory references missing instance territory="
                        + territory.id()
                        + " instance="
                        + territory.instanceId());
            }
        }

        for (DungeonInstance instance : data.instances().all()) {
            if (!DungeonManagerSavedData.requiresReservedSite(instance.status())) {
                continue;
            }
            List<DungeonTerritory> territories =
                    territoriesByInstance.getOrDefault(instance.id(), List.of());
            if (territories.size() != 1) {
                violations.add("live instance requires exactly one territory instance="
                        + instance.id()
                        + " count="
                        + territories.size());
            } else if (!territories.getFirst().id().equals(instance.territoryId())) {
                violations.add("instance territory id mismatch instance="
                        + instance.id()
                        + " expected="
                        + instance.territoryId()
                        + " actual="
                        + territories.getFirst().id());
            }
        }

        for (DungeonRoomState state : data.roomStates().flatten()) {
            Optional<DungeonInstance> instance = data.instances().get(state.instanceId());
            if (instance.isEmpty()) {
                violations.add("room state references missing instance instance="
                        + state.instanceId()
                        + " room="
                        + state.roomId());
                continue;
            }
            if (!DungeonManagerSavedData.requiresReservedSite(instance.get().status())) {
                violations.add("room state references non-live instance instance="
                        + state.instanceId()
                        + " room="
                        + state.roomId()
                        + " status="
                        + instance.get().status().getSerializedName());
            }
        }
    }

    private static void validatePortalAndDungeonSessions(
            DungeonManagerSavedData data,
            List<String> violations
    ) {
        Map<DungeonInstanceId, SessionAccessPolicy> portalPolicyByInstance =
                new HashMap<>();
        for (PortalSession session : data.portalSessions().all()) {
            SessionAccessPolicy existing =
                    portalPolicyByInstance.putIfAbsent(
                            session.instanceId(),
                            session.accessPolicy()
                    );
            if (existing != null && existing != session.accessPolicy()) {
                violations.add("portal sessions for same instance disagree on access policy instance="
                        + session.instanceId()
                        + " first="
                        + existing.getSerializedName()
                        + " second="
                        + session.accessPolicy().getSerializedName());
            }
            Optional<DungeonInstance> instance = data.instances().get(session.instanceId());
            if (instance.isEmpty()) {
                violations.add("portal session references missing instance session="
                        + session.id()
                        + " instance="
                        + session.instanceId());
                continue;
            }
            if (instance.get().status()
                    != io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus.ACTIVE) {
                violations.add("portal session references non-active instance session="
                        + session.id()
                        + " instance="
                        + session.instanceId()
                        + " status="
                        + instance.get().status().getSerializedName());
            }
            if (session.opener() == null) {
                violations.add("portal session missing opener session=" + session.id());
            }
            if (session.accessPolicy() == SessionAccessPolicy.ALLOWLIST) {
                violations.add("portal session uses unsupported ALLOWLIST access policy session="
                        + session.id()
                        + " instance="
                        + session.instanceId());
            } else if (session.accessPolicy() == SessionAccessPolicy.STARTER_ONLY) {
                if (session.participants().size() > 1) {
                    violations.add("starter-only portal session has too many participants session="
                            + session.id()
                            + " count="
                            + session.participants().size());
                }
                for (UUID participant : session.participants()) {
                    if (!participant.equals(session.opener())) {
                        violations.add("starter-only portal session contains non-opener participant session="
                                + session.id()
                                + " opener="
                                + session.opener()
                                + " participant="
                                + participant);
                    }
                }
            }
        }

        for (DungeonSession session : data.sessions().all()) {
            Optional<DungeonInstance> instance = data.instances().get(session.instanceId());
            if (instance.isEmpty()) {
                violations.add("dungeon session references missing instance session="
                        + session.id()
                        + " instance="
                        + session.instanceId());
                continue;
            }
            if (!session.siteKey().equals(instance.get().siteKey())) {
                violations.add("dungeon session site key mismatch session="
                        + session.id()
                        + " expected="
                        + instance.get().siteKey()
                        + " actual="
                        + session.siteKey());
            }
            SessionAccessPolicy portalPolicy =
                    portalPolicyByInstance.get(session.instanceId());
            if (portalPolicy != null
                    && session.accessPolicy() != portalPolicy) {
                violations.add("portal/dungeon session access policy mismatch session="
                        + session.id()
                        + " instance="
                        + session.instanceId()
                        + " portal="
                        + portalPolicy.getSerializedName()
                        + " dungeon="
                        + session.accessPolicy().getSerializedName());
            }
        }
    }

    private static void validateRaidsRewardsAndArtifacts(
            DungeonManagerSavedData data,
            List<String> violations
    ) {
        for (DungeonRaidInstance raid : data.raids().all()) {
            Optional<ReservedDungeonAggregate> aggregate =
                    aggregateFor(data, raid.dungeonInstanceId(), violations,
                            "raid " + raid.id());
            raid.roomId().ifPresent(roomId ->
                    requireRoom(aggregate, roomId, violations,
                            "raid " + raid.id()));
        }

        Map<io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId, DungeonRewardRecord> rewardsById =
                new LinkedHashMap<>();
        for (DungeonRewardRecord reward : data.rewards().all()) {
            rewardsById.put(reward.rewardId(), reward);
            Optional<ReservedDungeonAggregate> aggregate =
                    aggregateFor(data, reward.instanceId(), violations,
                            "reward " + reward.rewardId());
            reward.roomId().ifPresent(roomId ->
                    requireRoom(aggregate, roomId, violations,
                            "reward " + reward.rewardId()));
        }

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifacts().all()) {
            aggregateFor(data, artifact.instanceId(), violations,
                    "runtime artifact " + artifact);
            artifact.rewardId().ifPresent(rewardId -> {
                DungeonRewardRecord reward = rewardsById.get(rewardId);
                if (reward == null) {
                    violations.add("runtime artifact references missing reward artifact="
                            + artifact
                            + " reward="
                            + rewardId);
                } else if (!reward.instanceId().equals(artifact.instanceId())) {
                    violations.add("runtime artifact reward owner mismatch artifact="
                            + artifact
                            + " reward="
                            + rewardId
                            + " rewardInstance="
                            + reward.instanceId());
                }
            });
        }
    }

    private static Optional<ReservedDungeonAggregate> aggregateFor(
            DungeonManagerSavedData data,
            DungeonInstanceId instanceId,
            List<String> violations,
            String owner
    ) {
        Optional<DungeonInstance> instance = data.instances().get(instanceId);
        if (instance.isEmpty()) {
            violations.add(owner + " references missing instance " + instanceId);
            return Optional.empty();
        }
        DungeonManagerSavedData.ReservedDungeonValidation validation =
                data.validateReservedDungeon(instanceId, instance.get().siteKey());
        if (validation.aggregate().isEmpty()) {
            violations.add(owner + " references invalid reserved aggregate instance="
                    + instanceId
                    + " violations="
                    + validation.violations());
        }
        return validation.aggregate();
    }

    private static void requireRoom(
            Optional<ReservedDungeonAggregate> aggregate,
            DungeonRoomId roomId,
            List<String> violations,
            String owner
    ) {
        if (aggregate.isEmpty()) {
            return;
        }
        if (aggregate.get().site().room(roomId).isEmpty()) {
            violations.add(owner + " references missing site room instance="
                    + aggregate.get().instance().id()
                    + " site="
                    + aggregate.get().site().key()
                    + " room="
                    + roomId);
        }
    }

    private static Map<DungeonSiteKeyView, DungeonSite> snapshotsBySite(
            DungeonManagerSavedData data,
            List<String> violations
    ) {
        Map<DungeonSiteKeyView, DungeonSite> result = new LinkedHashMap<>();
        for (DungeonSite snapshot : data.sites().snapshots()) {
            DungeonSite previous = result.put(DungeonSiteKeyView.of(snapshot.key()), snapshot);
            if (previous != null) {
                violations.add("duplicate site snapshot site=" + snapshot.key());
            }
        }
        return result;
    }

    private record DungeonSiteKeyView(int chunkX, int chunkZ) {
        static DungeonSiteKeyView of(
                io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey key
        ) {
            return new DungeonSiteKeyView(key.startChunkX(), key.startChunkZ());
        }
    }
}
