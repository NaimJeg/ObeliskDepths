package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

public final class PortalSession {
    public static final Codec<PortalSession> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PortalSessionId.CODEC.fieldOf("id").forGetter(PortalSession::id),
            DungeonInstanceId.CODEC.fieldOf("instance_id").forGetter(PortalSession::instanceId),
            DungeonCodecs.UUID_CODEC.fieldOf("opener").forGetter(PortalSession::opener),
            ResourceKey.codec(Registries.DIMENSION)
                    .fieldOf("source_dimension")
                    .forGetter(PortalSession::sourceDimension),
            BlockPos.CODEC.fieldOf("obelisk_pos").forGetter(PortalSession::obeliskPos),
            BlockPos.CODEC.fieldOf("portal_anchor_pos").forGetter(PortalSession::portalAnchorPos),
            PortalAdmissionMode.CODEC.fieldOf("admission_mode").forGetter(PortalSession::admissionMode),
            Codec.LONG.fieldOf("expires_at_game_time").forGetter(PortalSession::expiresAtGameTime),
            DungeonCodecs.UUID_CODEC.listOf()
                    .optionalFieldOf("participants", List.of())
                    .forGetter(session -> List.copyOf(session.participants))
    ).apply(instance, PortalSession::fromCodec));

    private final PortalSessionId id;
    private final DungeonInstanceId instanceId;
    private final UUID opener;
    private final ResourceKey<Level> sourceDimension;
    private final BlockPos obeliskPos;
    private final BlockPos portalAnchorPos;
    private final PortalAdmissionMode admissionMode;
    private final Set<UUID> participants = new HashSet<>();
    private final long expiresAtGameTime;

    public PortalSession(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            BlockPos portalAnchorPos,
            PortalAdmissionMode admissionMode,
            long expiresAtGameTime
    ) {
        this.id = id;
        this.instanceId = instanceId;
        this.opener = opener;
        this.sourceDimension = sourceDimension;
        this.obeliskPos = obeliskPos.immutable();
        this.portalAnchorPos = portalAnchorPos.immutable();
        this.admissionMode = admissionMode;
        this.expiresAtGameTime = expiresAtGameTime;
    }

    private static PortalSession fromCodec(
            PortalSessionId id,
            DungeonInstanceId instanceId,
            UUID opener,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            BlockPos portalAnchorPos,
            PortalAdmissionMode admissionMode,
            long expiresAtGameTime,
            List<UUID> participants
    ) {
        PortalSession session = new PortalSession(
                id,
                instanceId,
                opener,
                sourceDimension,
                obeliskPos,
                portalAnchorPos,
                admissionMode,
                expiresAtGameTime
        );

        session.participants.addAll(participants);
        return session;
    }

    public PortalSessionId id() {
        return this.id;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public UUID opener() {
        return this.opener;
    }

    public ResourceKey<Level> sourceDimension() {
        return this.sourceDimension;
    }

    public BlockPos obeliskPos() {
        return this.obeliskPos;
    }

    public BlockPos portalAnchorPos() {
        return this.portalAnchorPos;
    }

    public PortalAdmissionMode admissionMode() {
        return this.admissionMode;
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

    public long expiresAtGameTime() {
        return this.expiresAtGameTime;
    }

    public boolean isExpired(long gameTime) {
        return gameTime >= this.expiresAtGameTime;
    }
}
