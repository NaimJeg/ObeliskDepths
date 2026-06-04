package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactType;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuntimeArtifactStore {
    private final List<DungeonRuntimeArtifactRecord> artifacts = new ArrayList<>();
    private final Map<DungeonInstanceId, Set<DungeonRuntimeArtifactRecord>> artifactsByInstance =
            new HashMap<>();
    private final Map<DungeonRuntimeArtifactType, Set<DungeonRuntimeArtifactRecord>> artifactsByType =
            new HashMap<>();
    private final Map<DungeonRewardId, Set<DungeonRuntimeArtifactRecord>> artifactsByReward =
            new HashMap<>();
    private final Runnable dirty;

    public RuntimeArtifactStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonRuntimeArtifactRecord> artifacts) {
        for (DungeonRuntimeArtifactRecord artifact : artifacts) {
            this.putLoaded(artifact);
        }
    }

    public List<DungeonRuntimeArtifactRecord> all() {
        return List.copyOf(this.artifacts);
    }

    public List<DungeonRuntimeArtifactRecord> pending() {
        return this.artifacts.stream()
                .filter(DungeonRuntimeArtifactRecord::pendingCleanup)
                .toList();
    }

    public List<DungeonRuntimeArtifactRecord> forInstance(DungeonInstanceId instanceId) {
        return List.copyOf(this.artifactsByInstance.getOrDefault(instanceId, Set.of()));
    }

    public void add(DungeonRuntimeArtifactRecord artifact) {
        if (this.artifacts.contains(artifact)) {
            return;
        }

        this.artifacts.add(artifact);
        this.index(artifact);
        this.dirty.run();
    }

    public boolean replace(
            DungeonRuntimeArtifactRecord existing,
            DungeonRuntimeArtifactRecord replacement
    ) {
        int index = this.artifacts.indexOf(existing);

        if (index < 0 || this.artifacts.get(index).equals(replacement)) {
            return false;
        }

        this.unindex(existing);
        this.artifacts.set(index, replacement);
        this.index(replacement);
        this.dirty.run();
        return true;
    }

    public boolean remove(DungeonRuntimeArtifactRecord artifact) {
        boolean removed = this.artifacts.remove(artifact);

        if (removed) {
            this.unindex(artifact);
            this.dirty.run();
        }

        return removed;
    }

    public int removeForInstance(
            DungeonInstanceId instanceId,
            DungeonRuntimeArtifactType type
    ) {
        List<DungeonRuntimeArtifactRecord> toRemove =
                this.artifactsByInstance.getOrDefault(instanceId, Set.of())
                        .stream()
                        .filter(artifact -> artifact.type() == type)
                        .toList();

        for (DungeonRuntimeArtifactRecord artifact : toRemove) {
            this.artifacts.remove(artifact);
            this.unindex(artifact);
        }

        if (!toRemove.isEmpty()) {
            this.dirty.run();
        }

        return toRemove.size();
    }

    private void putLoaded(DungeonRuntimeArtifactRecord artifact) {
        if (this.artifacts.contains(artifact)) {
            return;
        }

        this.artifacts.add(artifact);
        this.index(artifact);
    }

    private void index(DungeonRuntimeArtifactRecord artifact) {
        this.artifactsByInstance
                .computeIfAbsent(artifact.instanceId(), ignored -> new HashSet<>())
                .add(artifact);
        this.artifactsByType
                .computeIfAbsent(artifact.type(), ignored -> new HashSet<>())
                .add(artifact);
        artifact.rewardId().ifPresent(rewardId ->
                this.artifactsByReward
                        .computeIfAbsent(rewardId, ignored -> new HashSet<>())
                        .add(artifact)
        );
    }

    private void unindex(DungeonRuntimeArtifactRecord artifact) {
        this.removeIndexValue(this.artifactsByInstance, artifact.instanceId(), artifact);
        this.removeIndexValue(this.artifactsByType, artifact.type(), artifact);
        artifact.rewardId().ifPresent(rewardId ->
                this.removeIndexValue(this.artifactsByReward, rewardId, artifact)
        );
    }

    private <K> void removeIndexValue(
            Map<K, Set<DungeonRuntimeArtifactRecord>> index,
            K key,
            DungeonRuntimeArtifactRecord artifact
    ) {
        Set<DungeonRuntimeArtifactRecord> values = index.get(key);

        if (values == null) {
            return;
        }

        values.remove(artifact);

        if (values.isEmpty()) {
            index.remove(key);
        }
    }
}
