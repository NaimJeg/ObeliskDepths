package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.network.TemperingDirectionView;
import io.github.naimjeg.obeliskdepths.network.TemperingViewState;
import java.util.Objects;

public final class TemperingViewStateFactory {
    private TemperingViewStateFactory() {
    }

    public static TemperingViewState create(ResolvedTemperingState state) {
        if (state == null) {
            return TemperingViewState.EMPTY;
        }

        return new TemperingViewState(
                state.selectedDirectionId(),
                state.orderedDirectionIds()
                        .stream()
                        .map(state.directions()::get)
                        .filter(Objects::nonNull)
                        .map(direction -> new TemperingDirectionView(
                                direction.directionId(),
                                direction.definition().displayName(),
                                direction.definition().description()
                        ))
                        .toList(),
                state.selectedDirectionId().isPresent()
                        ? state.selectedPreviews()
                        : java.util.List.of()
        );
    }
}
