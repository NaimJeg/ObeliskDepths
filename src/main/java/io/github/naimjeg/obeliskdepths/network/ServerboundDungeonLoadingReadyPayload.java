package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationId;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

/** Immutable acknowledgement that the operation-specific loading UI is open. */
public record ServerboundDungeonLoadingReadyPayload(
        DungeonPortalEntryOperationId operationId
) implements CustomPacketPayload {
    public static final Type<ServerboundDungeonLoadingReadyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "dungeon_loading_ready"
            ));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ServerboundDungeonLoadingReadyPayload
            > STREAM_CODEC = StreamCodec.composite(
            DungeonPortalEntryOperationId.STREAM_CODEC,
            ServerboundDungeonLoadingReadyPayload::operationId,
            ServerboundDungeonLoadingReadyPayload::new
    );

    public ServerboundDungeonLoadingReadyPayload {
        Objects.requireNonNull(operationId, "operationId");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ServerboundDungeonLoadingReadyPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> DungeonPortalEntryService.clientReady(
                player,
                payload.operationId()
        ));
    }
}
