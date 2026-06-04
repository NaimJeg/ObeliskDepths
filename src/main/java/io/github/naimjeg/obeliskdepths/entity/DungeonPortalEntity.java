package io.github.naimjeg.obeliskdepths.entity;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryResult;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionRemovalReason;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class DungeonPortalEntity extends Entity {
    private static final String SESSION_ID_KEY = "portal_session_id";
    private static final int VALIDATION_INTERVAL_TICKS = 20;
    private static final int ENTRY_CHECK_INTERVAL_TICKS = 5;
    private static final long PLAYER_RETRY_COOLDOWN_TICKS = 30L;
    private static final double ANCHOR_TOLERANCE_SQR = 2.25D;

    private PortalSessionId portalSessionId;
    private final Map<UUID, Long> playerRetryCooldowns = new HashMap<>();
    private final Set<UUID> ignoredUntilExit = new HashSet<>();
    private boolean capturedInitialOverlaps;

    public DungeonPortalEntity(
            EntityType<DungeonPortalEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DungeonPortalEntity(Level level) {
        this(ModEntityTypes.DUNGEON_PORTAL.get(), level);
    }

    public void initialize(
            PortalSessionId sessionId,
            BlockPos anchorPos
    ) {
        this.portalSessionId = sessionId;
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(
                anchorPos.getX() + 0.5D,
                anchorPos.getY(),
                anchorPos.getZ() + 0.5D
        );
    }

    public Optional<PortalSessionId> portalSessionId() {
        return Optional.ofNullable(this.portalSessionId);
    }

    public boolean isCloseToAnchor(BlockPos anchorPos) {
        return this.distanceToSqr(Vec3.atBottomCenterOf(anchorPos)) <= ANCHOR_TOLERANCE_SQR;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.portalSessionId = input.read(SESSION_ID_KEY, PortalSessionId.CODEC)
                .orElse(null);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.portalSessionId != null) {
            output.store(SESSION_ID_KEY, PortalSessionId.CODEC, this.portalSessionId);
        }
    }

    @Override
    public boolean hurtServer(
            ServerLevel level,
            DamageSource source,
            float amount
    ) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(Entity entity) {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(Vec3 movement) {
    }

    @Override
    public void push(
            double x,
            double y,
            double z
    ) {
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);

        if (!(this.level() instanceof ServerLevel sourceLevel)) {
            return;
        }

        if (this.portalSessionId == null) {
            discardWithReason("missing_session_id");
            return;
        }

        if (this.tickCount % VALIDATION_INTERVAL_TICKS == 0
                && resolveValidSession(sourceLevel).isEmpty()) {
            return;
        }

        if (this.tickCount % ENTRY_CHECK_INTERVAL_TICKS == 0) {
            handleOverlaps(sourceLevel);
        }
    }

    private Optional<PortalSession> resolveValidSession(ServerLevel sourceLevel) {
        ServerLevel dungeonLevel = sourceLevel.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            discardWithReason("missing_dungeon_level");
            return Optional.empty();
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session =
                data.portalSessions().get(this.portalSessionId);

        if (session.isEmpty()) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    this.portalSessionId,
                    PortalSessionRemovalReason.SOURCE_PORTAL_INVALID
            );
            discardWithReason("session_missing");
            return Optional.empty();
        }

        if (session.get().isExpired(dungeonLevel.getGameTime())) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.EXPIRED
            );
            discardWithReason("session_expired");
            return Optional.empty();
        }

        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());

        if (instance.isEmpty()) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.INSTANCE_MISSING
            );
            discardWithReason("instance_missing");
            return Optional.empty();
        }

        if (instance.get().status() != DungeonStatus.ACTIVE) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.INSTANCE_INACTIVE
            );
            discardWithReason("instance_not_active");
            return Optional.empty();
        }

        if (!sourceLevel.dimension().equals(session.get().sourceDimension())) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.SOURCE_PORTAL_INVALID
            );
            discardWithReason("wrong_source_dimension");
            return Optional.empty();
        }

        if (!isCloseToAnchor(session.get().portalAnchorPos())) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.SOURCE_PORTAL_INVALID
            );
            discardWithReason("anchor_mismatch");
            return Optional.empty();
        }

        if (!isValidBottomObelisk(sourceLevel, session.get().obeliskPos())) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    session.get().id(),
                    PortalSessionRemovalReason.SOURCE_OBELISK_INVALID
            );
            discardWithReason("source_obelisk_missing");
            return Optional.empty();
        }

        return session;
    }

    private void handleOverlaps(ServerLevel sourceLevel) {
        Optional<PortalSession> session = resolveValidSessionReadOnly(sourceLevel);

        if (session.isEmpty()) {
            return;
        }

        ServerLevel dungeonLevel = sourceLevel.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            discardWithReason("missing_dungeon_level");
            return;
        }

        long gameTime = dungeonLevel.getGameTime();
        pruneCooldowns(gameTime);

        List<ServerPlayer> overlappingPlayers = sourceLevel.getEntities(
                EntityTypeTest.forClass(ServerPlayer.class),
                this.getBoundingBox().inflate(0.1D),
                player -> player.isAlive() && !player.isSpectator()
        );
        Set<UUID> overlappingIds = new HashSet<>();

        for (ServerPlayer player : overlappingPlayers) {
            overlappingIds.add(player.getUUID());
        }

        // Anyone already intersecting the portal when it appears must leave
        // the trigger before they can enter through it.
        if (!this.capturedInitialOverlaps) {
            this.ignoredUntilExit.addAll(overlappingIds);
            this.capturedInitialOverlaps = true;
            return;
        }

        this.ignoredUntilExit.removeIf(playerId -> !overlappingIds.contains(playerId));

        for (ServerPlayer player : overlappingPlayers) {
            if (this.ignoredUntilExit.contains(player.getUUID())) {
                continue;
            }

            long retryAfter = this.playerRetryCooldowns.getOrDefault(player.getUUID(), 0L);

            if (gameTime < retryAfter) {
                continue;
            }

            this.playerRetryCooldowns.put(
                    player.getUUID(),
                    gameTime + PLAYER_RETRY_COOLDOWN_TICKS
            );
            ObeliskDepths.LOGGER.debug(
                    "Dungeon portal overlap entry attempt: player={}, session={}",
                    player.getGameProfile().name(),
                    session.get().id()
            );
            DungeonPortalEntryResult result = DungeonPortalEntryService.enter(
                    player,
                    dungeonLevel,
                    session.get().id(),
                    this
            );

            if (!result.accepted()) {
                ObeliskDepths.LOGGER.debug(
                        "Dungeon portal overlap entry failed: player={}, session={}, result={}",
                        player.getGameProfile().name(),
                        session.get().id(),
                        result
                );
            }
        }
    }

    /**
     * Collision-path validation that reads loaded state only. Destructive
     * session/portal maintenance remains in the periodic validation path.
     */
    private Optional<PortalSession> resolveValidSessionReadOnly(
            ServerLevel sourceLevel
    ) {
        if (this.portalSessionId == null) {
            return Optional.empty();
        }
        ServerLevel dungeonLevel = sourceLevel.getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
        if (dungeonLevel == null) {
            return Optional.empty();
        }
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session =
                data.portalSessions().get(this.portalSessionId);
        if (session.isEmpty()
                || session.get().isExpired(dungeonLevel.getGameTime())) {
            return Optional.empty();
        }
        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());
        if (instance.isEmpty()
                || instance.get().status() != DungeonStatus.ACTIVE
                || !sourceLevel.dimension().equals(
                session.get().sourceDimension()
        )
                || !isCloseToAnchor(session.get().portalAnchorPos())
                || !sourceLevel.hasChunk(
                        SectionPos.blockToSectionCoord(session.get().obeliskPos().getX()),
                        SectionPos.blockToSectionCoord(session.get().obeliskPos().getZ())
                )
                || !isValidBottomObelisk(
                sourceLevel,
                session.get().obeliskPos()
        )) {
            return Optional.empty();
        }
        return session;
    }

    private void pruneCooldowns(long gameTime) {
        Iterator<Map.Entry<UUID, Long>> iterator = this.playerRetryCooldowns.entrySet().iterator();

        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= gameTime) {
                iterator.remove();
            }
        }
    }

    private static boolean isValidBottomObelisk(
            ServerLevel level,
            BlockPos pos
    ) {
        var state = level.getBlockState(pos);

        return state.is(ModBlocks.OBELISK.get())
                && state.hasProperty(ObeliskBlock.PART)
                && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
    }

    private void discardWithReason(String reason) {
        ObeliskDepths.LOGGER.debug(
                "Dungeon portal discarded: entity={}, session={}, reason={}",
                this.getUUID(),
                this.portalSessionId,
                reason
        );
        this.discard();
    }
}
