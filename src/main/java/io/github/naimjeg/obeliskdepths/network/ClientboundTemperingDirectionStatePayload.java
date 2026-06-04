package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundTemperingDirectionStatePayload(
        int containerId,
        TemperingViewState state
) implements CustomPacketPayload {
    public static final Type<ClientboundTemperingDirectionStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "tempering_direction_state"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ClientboundTemperingDirectionStatePayload
            > STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ClientboundTemperingDirectionStatePayload::containerId,
            TemperingViewState.STREAM_CODEC,
            ClientboundTemperingDirectionStatePayload::state,
            ClientboundTemperingDirectionStatePayload::new
    );

    public ClientboundTemperingDirectionStatePayload {
        state = state == null ? TemperingViewState.EMPTY : state;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            ClientboundTemperingDirectionStatePayload payload,
            IPayloadContext context
    ) {
        Player player = context.player();

        if (!(player.containerMenu instanceof ObeliskTemperingMenu menu)) {
            return;
        }

        if (menu.containerId != payload.containerId()) {
            return;
        }

        menu.applyTemperingViewStateFromServer(payload.state());
    }
}
