package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.client.DungeonLoadingClient;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationId;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/** Carries only an authoritative server state transition to the loading UI. */
public record ClientboundDungeonLoadingStatePayload(
        DungeonPortalEntryOperationId operationId,
        DungeonPortalEntryOperationState state
) implements CustomPacketPayload {
    public static final Type<ClientboundDungeonLoadingStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "dungeon_loading_state"
            ));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ClientboundDungeonLoadingStatePayload
            > STREAM_CODEC = StreamCodec.composite(
            DungeonPortalEntryOperationId.STREAM_CODEC,
            ClientboundDungeonLoadingStatePayload::operationId,
            ByteBufCodecs.VAR_INT,
            payload -> payload.state().wireCode(),
            (operationId, wireCode) -> new ClientboundDungeonLoadingStatePayload(
                    operationId,
                    DungeonPortalEntryOperationState.fromWireCode(wireCode)
                            .orElse(DungeonPortalEntryOperationState.PREPARING)
            )
    );

    public ClientboundDungeonLoadingStatePayload {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(state, "state");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ClientboundDungeonLoadingStatePayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> DungeonLoadingClient.update(
                payload.operationId(),
                payload.state()
        ));
    }
}
