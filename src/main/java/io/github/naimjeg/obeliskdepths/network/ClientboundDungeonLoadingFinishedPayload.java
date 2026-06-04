package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.client.DungeonLoadingClient;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationId;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationState;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/** Closes one matching loading UI and supplies a typed terminal result. */
public record ClientboundDungeonLoadingFinishedPayload(
        DungeonPortalEntryOperationId operationId,
        DungeonPortalEntryOperationState terminalState,
        DungeonPortalEntryResult result
) implements CustomPacketPayload {
    public static final Type<ClientboundDungeonLoadingFinishedPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "dungeon_loading_finished"
            ));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ClientboundDungeonLoadingFinishedPayload
            > STREAM_CODEC = StreamCodec.composite(
            DungeonPortalEntryOperationId.STREAM_CODEC,
            ClientboundDungeonLoadingFinishedPayload::operationId,
            ByteBufCodecs.VAR_INT,
            payload -> payload.terminalState().wireCode(),
            ByteBufCodecs.VAR_INT,
            payload -> payload.result().wireCode(),
            (operationId, stateCode, resultCode) ->
                    new ClientboundDungeonLoadingFinishedPayload(
                            operationId,
                            DungeonPortalEntryOperationState
                                    .fromWireCode(stateCode)
                                    .filter(DungeonPortalEntryOperationState::terminal)
                                    .orElse(DungeonPortalEntryOperationState.FAILED),
                            DungeonPortalEntryResult.fromWireCode(resultCode)
                                    .orElse(DungeonPortalEntryResult.PREPARATION_FAILED)
                    )
    );

    public ClientboundDungeonLoadingFinishedPayload {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(terminalState, "terminalState");
        Objects.requireNonNull(result, "result");
        if (!terminalState.terminal()) {
            throw new IllegalArgumentException("terminalState must be terminal");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ClientboundDungeonLoadingFinishedPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> DungeonLoadingClient.finish(
                payload.operationId(),
                payload.terminalState(),
                payload.result()
        ));
    }
}
