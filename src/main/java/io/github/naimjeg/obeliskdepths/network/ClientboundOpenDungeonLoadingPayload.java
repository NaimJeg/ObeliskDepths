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

/** Opens the presentation-only entry loading screen for one operation. */
public record ClientboundOpenDungeonLoadingPayload(
        DungeonPortalEntryOperationId operationId,
        DungeonPortalEntryOperationState initialStage
) implements CustomPacketPayload {
    public static final Type<ClientboundOpenDungeonLoadingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "open_dungeon_loading"
            ));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ClientboundOpenDungeonLoadingPayload
            > STREAM_CODEC = StreamCodec.composite(
            DungeonPortalEntryOperationId.STREAM_CODEC,
            ClientboundOpenDungeonLoadingPayload::operationId,
            ByteBufCodecs.VAR_INT,
            payload -> payload.initialStage().wireCode(),
            (operationId, wireCode) -> new ClientboundOpenDungeonLoadingPayload(
                    operationId,
                    DungeonPortalEntryOperationState.fromWireCode(wireCode)
                            .orElse(DungeonPortalEntryOperationState
                                    .AWAITING_CLIENT_READY)
            )
    );

    public ClientboundOpenDungeonLoadingPayload {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(initialStage, "initialStage");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ClientboundOpenDungeonLoadingPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> DungeonLoadingClient.open(
                payload.operationId(),
                payload.initialStage()
        ));
    }
}
