package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectTemperingDirectionPayload(
        int containerId,
        Identifier directionId
) implements CustomPacketPayload {
    public static final Type<SelectTemperingDirectionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "select_tempering_direction"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectTemperingDirectionPayload
            > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SelectTemperingDirectionPayload::containerId,
            Identifier.STREAM_CODEC,
            SelectTemperingDirectionPayload::directionId,
            SelectTemperingDirectionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            SelectTemperingDirectionPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.containerMenu instanceof ObeliskTemperingMenu menu)) {
            return;
        }

        if (menu.containerId != payload.containerId()) {
            return;
        }

        menu.selectDirectionFromClient(payload.directionId());
    }
}
