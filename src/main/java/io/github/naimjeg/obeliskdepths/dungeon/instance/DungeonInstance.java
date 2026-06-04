package io.github.naimjeg.obeliskdepths.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.core.BlockPos;

import java.util.*;

public final class DungeonInstance {
    public static final Codec<DungeonInstance> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonInstanceId.CODEC.fieldOf("id")
                            .forGetter(DungeonInstance::id),

                    DungeonSiteKey.CODEC.fieldOf("site_key")
                            .forGetter(DungeonInstance::siteKey),

                    DungeonDifficulty.CODEC.fieldOf("difficulty")
                            .forGetter(DungeonInstance::difficulty),

                    DungeonTerritoryId.CODEC.fieldOf("territory_id")
                            .forGetter(DungeonInstance::territoryId),

                    BlockPos.CODEC.fieldOf("start_pos")
                            .forGetter(DungeonInstance::startPos),

                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("participants", List.of())
                            .forGetter(dungeon -> List.copyOf(dungeon.participants)),

                    DungeonStatus.CODEC
                            .optionalFieldOf("status", DungeonStatus.ACTIVE)
                            .forGetter(DungeonInstance::status),

                    Codec.LONG.optionalFieldOf("created_game_time", 0L)
                            .forGetter(DungeonInstance::createdGameTime),

                    Codec.LONG.optionalFieldOf("activated_game_time", 0L)
                            .forGetter(DungeonInstance::activatedGameTime),

                    Codec.LONG.optionalFieldOf("closed_game_time", -1L)
                            .forGetter(DungeonInstance::closedGameTime),

                    Codec.LONG.optionalFieldOf("last_active_game_time", 0L)
                            .forGetter(DungeonInstance::lastActiveGameTime)
            ).apply(instance, DungeonInstance::fromCodec));

    private final DungeonSiteKey siteKey;
    private final DungeonInstanceId id;
    private final DungeonDifficulty difficulty;
    private final DungeonTerritoryId territoryId;
    private final BlockPos startPos;
    private final Set<UUID> participants = new HashSet<>();

    private final long createdGameTime;
    private long activatedGameTime;
    private long closedGameTime;
    private long lastActiveGameTime;

    private DungeonStatus status = DungeonStatus.ACTIVE;

    public DungeonInstance(
            DungeonInstanceId id,
            DungeonSiteKey siteKey,
            DungeonDifficulty difficulty,
            DungeonTerritoryId territoryId,
            BlockPos startPos,
            long createdGameTime
    ) {
        this.id = id;
        this.siteKey = siteKey;
        this.difficulty = difficulty;
        this.territoryId = territoryId;
        this.startPos = startPos;
        this.createdGameTime = createdGameTime;
        this.activatedGameTime = createdGameTime;
        this.lastActiveGameTime = createdGameTime;
        this.closedGameTime = -1L;
    }

    private static DungeonInstance fromCodec(
            DungeonInstanceId id,
            DungeonSiteKey siteKey,
            DungeonDifficulty difficulty,
            DungeonTerritoryId territoryId,
            BlockPos startPos,
            List<UUID> participants,
            DungeonStatus status,
            long createdGameTime,
            long activatedGameTime,
            long closedGameTime,
            long lastActiveGameTime
    ) {
        DungeonInstance instance = new DungeonInstance(
                id,
                siteKey,
                difficulty,
                territoryId,
                startPos,
                createdGameTime
        );

        instance.participants.addAll(participants);
        instance.status = status;
        instance.activatedGameTime = activatedGameTime;
        instance.closedGameTime = closedGameTime;
        instance.lastActiveGameTime = lastActiveGameTime;

        return instance;
    }

    public DungeonInstanceId id() {
        return this.id;
    }

    public DungeonDifficulty difficulty() {
        return this.difficulty;
    }

    public DungeonTerritoryId territoryId() {
        return this.territoryId;
    }

    public BlockPos startPos() {
        return this.startPos;
    }

    public DungeonStatus status() {
        return this.status;
    }

    public boolean setStatus(DungeonStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }

    public DungeonSiteKey siteKey() {
        return this.siteKey;
    }

    public void markActiveAt(long gameTime) {
        this.lastActiveGameTime = gameTime;
    }

    public void markClosedAt(long gameTime) {
        this.closedGameTime = gameTime;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public long activatedGameTime() {
        return this.activatedGameTime;
    }

    public long closedGameTime() {
        return this.closedGameTime;
    }

    public long lastActiveGameTime() {
        return this.lastActiveGameTime;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(this.participants);
    }

    public boolean addParticipant(UUID playerId) {
        return this.participants.add(playerId);
    }

    public boolean removeParticipant(UUID playerId) {
        return this.participants.remove(playerId);
    }

    public boolean isParticipant(UUID playerId) {
        return this.participants.contains(playerId);
    }
}