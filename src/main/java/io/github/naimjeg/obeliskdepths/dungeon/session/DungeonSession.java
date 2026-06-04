package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * Runtime sessions are intentionally separate from worldgen DungeonSite metadata.
 * DungeonSite describes generated physical layout; DungeonSession describes one
 * active run. Cleanup removes runtime entities/state only and must not delete
 * worldgen-owned blocks.
 */
public final class DungeonSession {
    public static final Codec<DungeonSession> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonCodecs.UUID_CODEC.fieldOf("id")
                            .forGetter(DungeonSession::id),
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonSession::instanceId),
                    DungeonCodecs.UUID_CODEC.fieldOf("starter_player_id")
                            .forGetter(DungeonSession::starterPlayerId),
                    DungeonSiteKey.CODEC.fieldOf("site_key")
                            .forGetter(DungeonSession::siteKey),
                    DungeonSessionState.CODEC
                            .optionalFieldOf("state", DungeonSessionState.ACTIVE)
                            .forGetter(DungeonSession::state),
                    SessionAccessPolicy.CODEC
                            .optionalFieldOf("access_policy", SessionAccessPolicy.OPEN)
                            .forGetter(DungeonSession::accessPolicy),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("participants", List.of())
                            .forGetter(session -> List.copyOf(session.participants)),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("physical_participants", List.of())
                            .forGetter(session -> List.copyOf(session.physicalParticipants)),
                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("spawned_entity_ids", List.of())
                            .forGetter(session -> List.copyOf(session.spawnedEntityIds)),
                    Codec.LONG.optionalFieldOf("created_at_game_time", 0L)
                            .forGetter(DungeonSession::createdAtGameTime),
                    Codec.LONG.optionalFieldOf("last_starter_inside_game_time", 0L)
                            .forGetter(DungeonSession::lastStarterInsideGameTime),
                    Codec.BOOL.optionalFieldOf("tribute_bonus_active", false)
                            .forGetter(DungeonSession::tributeBonusActive)
            ).apply(instance, DungeonSession::new));

    private final UUID id;
    private final DungeonInstanceId instanceId;
    private final UUID starterPlayerId;
    private final DungeonSiteKey siteKey;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> physicalParticipants = new HashSet<>();
    private final Set<UUID> spawnedEntityIds = new HashSet<>();
    private final long createdAtGameTime;

    private DungeonSessionState state;
    private final SessionAccessPolicy accessPolicy;
    private long lastStarterInsideGameTime;
    private boolean tributeBonusActive;

    public DungeonSession(
            UUID id,
            DungeonInstanceId instanceId,
            UUID starterPlayerId,
            DungeonSiteKey siteKey,
            DungeonSessionState state,
            SessionAccessPolicy accessPolicy,
            Collection<UUID> participants,
            Collection<UUID> physicalParticipants,
            Collection<UUID> spawnedEntityIds,
            long createdAtGameTime,
            long lastStarterInsideGameTime,
            boolean tributeBonusActive
    ) {
        this.id = requireNonNull(id, "session id");
        this.instanceId = requireNonNull(instanceId, "instance id");
        this.starterPlayerId = requireNonNull(starterPlayerId, "starter player id");
        this.siteKey = requireNonNull(siteKey, "site key");
        this.state = state == null ? DungeonSessionState.ACTIVE : state;
        this.accessPolicy = accessPolicy == null ? SessionAccessPolicy.OPEN : accessPolicy;
        this.createdAtGameTime = createdAtGameTime;
        this.lastStarterInsideGameTime = lastStarterInsideGameTime;
        this.tributeBonusActive = tributeBonusActive;

        if (participants != null) {
            this.participants.addAll(participants);
        }

        if (physicalParticipants != null) {
            this.physicalParticipants.addAll(physicalParticipants);
        }

        if (spawnedEntityIds != null) {
            this.spawnedEntityIds.addAll(spawnedEntityIds);
        }

        this.participants.add(starterPlayerId);
    }

    public static DungeonSession createForPortal(
            DungeonInstance instance,
            UUID starterPlayerId,
            SessionAccessPolicy accessPolicy,
            boolean tributeBonusActive,
            long gameTime
    ) {
        return createWithState(
                instance,
                starterPlayerId,
                accessPolicy,
                tributeBonusActive,
                gameTime,
                DungeonSessionState.WAITING_FOR_ENTRY
        );
    }

    public static DungeonSession createActive(
            DungeonInstance instance,
            UUID starterPlayerId,
            SessionAccessPolicy accessPolicy,
            boolean tributeBonusActive,
            long gameTime
    ) {
        return createWithState(
                instance,
                starterPlayerId,
                accessPolicy,
                tributeBonusActive,
                gameTime,
                DungeonSessionState.ACTIVE
        );
    }

    private static DungeonSession createWithState(
            DungeonInstance instance,
            UUID starterPlayerId,
            SessionAccessPolicy accessPolicy,
            boolean tributeBonusActive,
            long gameTime,
            DungeonSessionState initialState
    ) {
        return new DungeonSession(
                UUID.randomUUID(),
                instance.id(),
                starterPlayerId,
                instance.siteKey(),
                initialState,
                accessPolicy,
                Set.of(starterPlayerId),
                Set.of(),
                Set.of(),
                gameTime,
                gameTime,
                tributeBonusActive
        );
    }

    public UUID id() {
        return this.id;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public UUID starterPlayerId() {
        return this.starterPlayerId;
    }

    public DungeonSiteKey siteKey() {
        return this.siteKey;
    }

    public DungeonSessionState state() {
        return this.state;
    }

    public SessionAccessPolicy accessPolicy() {
        return this.accessPolicy;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(this.participants);
    }

    public Set<UUID> physicalParticipants() {
        return Collections.unmodifiableSet(this.physicalParticipants);
    }

    public Set<UUID> spawnedEntityIds() {
        return Collections.unmodifiableSet(this.spawnedEntityIds);
    }

    public long createdAtGameTime() {
        return this.createdAtGameTime;
    }

    public long lastStarterInsideGameTime() {
        return this.lastStarterInsideGameTime;
    }

    public boolean tributeBonusActive() {
        return this.tributeBonusActive;
    }

    public boolean registerParticipant(UUID playerId) {
        return this.participants.add(playerId);
    }

    public boolean removeParticipant(UUID playerId) {
        if (this.starterPlayerId.equals(playerId)) {
            return false;
        }

        this.physicalParticipants.remove(playerId);
        return this.participants.remove(playerId);
    }

    public boolean registerPhysicalParticipant(UUID playerId) {
        return this.physicalParticipants.add(playerId);
    }

    public boolean unregisterPhysicalParticipant(UUID playerId) {
        return this.physicalParticipants.remove(playerId);
    }

    public boolean markPortalEntrySucceeded(
            UUID playerId,
            long gameTime
    ) {
        if (!this.state.acceptsPortalEntry()) {
            return false;
        }

        boolean changed = this.participants.add(playerId);
        changed |= this.physicalParticipants.add(playerId);

        if (this.state == DungeonSessionState.WAITING_FOR_ENTRY) {
            this.state = DungeonSessionState.ACTIVE;
            changed = true;
        }

        if (this.starterPlayerId.equals(playerId)) {
            changed |= markStarterInside(gameTime);
        }

        return changed;
    }

    public boolean isParticipant(UUID playerId) {
        return this.participants.contains(playerId);
    }

    public boolean isPhysicalParticipant(UUID playerId) {
        return this.physicalParticipants.contains(playerId);
    }

    public boolean setState(DungeonSessionState state) {
        if (this.state == state) {
            return false;
        }

        this.state = state;
        return true;
    }

    public boolean markStarterInside(long gameTime) {
        boolean changed = this.lastStarterInsideGameTime != gameTime;
        this.lastStarterInsideGameTime = gameTime;

        if (this.state == DungeonSessionState.ABANDON_PENDING) {
            this.state = DungeonSessionState.ACTIVE;
            changed = true;
        }

        return changed;
    }

    public boolean markAbandonPending() {
        return setState(DungeonSessionState.ABANDON_PENDING);
    }

    public boolean markAbandoned() {
        boolean changed = setState(DungeonSessionState.ABANDONED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean markCleaned() {
        boolean changed = setState(DungeonSessionState.CLEANED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean markCompleted() {
        boolean changed = setState(DungeonSessionState.COMPLETED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public boolean markFailed() {
        boolean changed = setState(DungeonSessionState.FAILED);

        if (this.tributeBonusActive) {
            this.tributeBonusActive = false;
            changed = true;
        }

        return changed;
    }

    public int clearSpawnedEntityIds() {
        int count = this.spawnedEntityIds.size();
        this.spawnedEntityIds.clear();
        return count;
    }

    private static <T> T requireNonNull(
            T value,
            String name
    ) {
        if (value == null) {
            throw new IllegalArgumentException("Dungeon session requires " + name);
        }

        return value;
    }
}
