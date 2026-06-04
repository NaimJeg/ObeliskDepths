package io.github.naimjeg.obeliskdepths.dungeon.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonInstanceStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonRaidStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonRewardStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSessionStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSiteStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonTerritoryStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.PortalSessionStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RuntimeArtifactStore;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RoomStateStore;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.List;

public final class DungeonManagerSavedData extends SavedData {

    private final DungeonInstanceStore instances = new DungeonInstanceStore(this::setDirty);
    private final DungeonSessionStore sessions = new DungeonSessionStore(this::setDirty);
    private final PortalSessionStore portalSessions = new PortalSessionStore(this::setDirty);
    private final RoomStateStore roomStates = new RoomStateStore(this::setDirty);
    private final DungeonSiteStore sites = new DungeonSiteStore(this::setDirty);
    private final DungeonRewardStore rewards = new DungeonRewardStore(this::setDirty);
    private final RuntimeArtifactStore runtimeArtifacts = new RuntimeArtifactStore(this::setDirty);
    private final DungeonRaidStore raids = new DungeonRaidStore(this::setDirty);
    private final DungeonTerritoryStore territories = new DungeonTerritoryStore(this::setDirty);


    private static final Identifier FILE_ID =
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "dungeons");

    public static final Codec<DungeonManagerSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DungeonInstance.CODEC.listOf()
                    .optionalFieldOf("instances", List.of())
                    .forGetter(data -> List.copyOf(data.instances.all())),
            DungeonTerritory.CODEC.listOf()
                    .optionalFieldOf("territories", List.of())
                    .forGetter(data -> List.copyOf(data.territories.all())),
            PortalSession.CODEC.listOf()
                    .optionalFieldOf("portal_sessions", List.of())
                    .forGetter(data -> List.copyOf(data.portalSessions.all())),
            DungeonRaidInstance.CODEC.listOf()
                    .optionalFieldOf("raids", List.of())
                    .forGetter(data -> List.copyOf(data.raids.all())),
            DungeonSession.CODEC.listOf()
                    .optionalFieldOf("sessions", List.of())
                    .forGetter(data -> List.copyOf(data.sessions.all())),
            DungeonRoomState.CODEC.listOf()
                    .optionalFieldOf("room_states", List.of())
                    .forGetter(data -> data.roomStates.flatten()),
            DungeonSiteRecord.CODEC.listOf()
                    .optionalFieldOf("site_records", List.of())
                    .forGetter(data -> List.copyOf(data.sites.records())),
            DungeonSite.CODEC.listOf()
                    .optionalFieldOf("site_snapshots", List.of())
                    .forGetter(data -> List.copyOf(data.sites.snapshots())),
            DungeonRewardRecord.CODEC.listOf()
                    .optionalFieldOf("rewards", List.of())
                    .forGetter(data -> List.copyOf(data.rewards.all())),
            DungeonRuntimeArtifactRecord.CODEC.listOf()
                    .optionalFieldOf("runtime_artifacts", List.of())
                    .forGetter(data -> List.copyOf(data.runtimeArtifacts.all()))
    ).apply(instance, DungeonManagerSavedData::new));

    public static final SavedDataType<DungeonManagerSavedData> TYPE =
            new SavedDataType<>(
                    FILE_ID,
                    DungeonManagerSavedData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA
            );

    public DungeonManagerSavedData() {
    }

    private DungeonManagerSavedData(
            List<DungeonInstance> instances,
            List<DungeonTerritory> territories,
            List<PortalSession> portalSessions,
            List<DungeonRaidInstance> raids,
            List<DungeonSession> sessions,
            List<DungeonRoomState> roomStates,
            List<DungeonSiteRecord> siteRecords,
            List<DungeonSite> siteSnapshots,
            List<DungeonRewardRecord> rewards,
            List<DungeonRuntimeArtifactRecord> runtimeArtifacts
    ) {
        this.instances.load(instances);

        this.territories.load(territories);

        this.portalSessions.load(portalSessions);

        this.raids.load(raids);

        this.sessions.load(sessions);

        this.sites.loadRecords(siteRecords);
        this.sites.loadSnapshots(siteSnapshots);
        this.rewards.load(rewards);
        this.runtimeArtifacts.load(runtimeArtifacts);

        this.roomStates.load(roomStates);

    }

    public static DungeonManagerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public DungeonInstanceStore instances() {
        return this.instances;
    }

    public DungeonSessionStore sessions() {
        return this.sessions;
    }

    public PortalSessionStore portalSessions() {
        return this.portalSessions;
    }

    public RoomStateStore roomStates() {
        return this.roomStates;
    }

    public DungeonSiteStore sites() {
        return this.sites;
    }

    public DungeonRewardStore rewards() {
        return this.rewards;
    }

    public RuntimeArtifactStore runtimeArtifacts() {
        return this.runtimeArtifacts;
    }

    public DungeonRaidStore raids() {
        return this.raids;
    }

    public DungeonTerritoryStore territories() {
        return this.territories;
    }

}
