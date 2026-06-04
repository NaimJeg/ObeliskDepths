package io.github.naimjeg.obeliskdepths.network;

import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record TemperingViewState(
        Optional<Identifier> selectedDirectionId,
        List<TemperingDirectionView> directions,
        List<TemperingAffixPreview> selectedPreviews
) {
    public static final TemperingViewState EMPTY =
            new TemperingViewState(Optional.empty(), List.of(), List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, TemperingViewState>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC),
            TemperingViewState::selectedDirectionId,
            TemperingDirectionView.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TemperingViewState::directions,
            TemperingAffixPreview.STREAM_CODEC.apply(ByteBufCodecs.list()),
            TemperingViewState::selectedPreviews,
            TemperingViewState::new
    );

    public TemperingViewState {
        selectedDirectionId = selectedDirectionId == null
                ? Optional.empty()
                : selectedDirectionId;
        List<TemperingDirectionView> copiedDirections =
                directions == null ? List.of() : List.copyOf(directions);
        boolean selectedIsAvailable = selectedDirectionId
                .map(id -> copiedDirections
                        .stream()
                        .anyMatch(view -> view.id().equals(id)))
                .orElse(false);
        directions = copiedDirections;
        if (!selectedIsAvailable) {
            selectedDirectionId = Optional.empty();
            selectedPreviews = List.of();
        } else {
            selectedPreviews = selectedPreviews == null
                    ? List.of()
                    : List.copyOf(selectedPreviews);
        }
    }
}
