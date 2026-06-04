package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

final class PostTeleportHandoffKeys {
    private PostTeleportHandoffKeys() {
    }

    static boolean isActive(
            Map<DungeonPreparationRuntime.PostTeleportHandoffKey, ?> handoffs,
            PortalSessionId portalSessionId,
            UUID playerId
    ) {
        Objects.requireNonNull(handoffs, "handoffs");
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        Objects.requireNonNull(playerId, "playerId");

        return handoffs.containsKey(
                new DungeonPreparationRuntime.PostTeleportHandoffKey(
                        portalSessionId,
                        playerId
                )
        );
    }

    static boolean hasSession(
            Map<DungeonPreparationRuntime.PostTeleportHandoffKey, ?> handoffs,
            PortalSessionId portalSessionId
    ) {
        Objects.requireNonNull(handoffs, "handoffs");
        Objects.requireNonNull(portalSessionId, "portalSessionId");

        return !keysForSession(handoffs, portalSessionId).isEmpty();
    }

    static List<DungeonPreparationRuntime.PostTeleportHandoffKey> keysForSession(
            Map<DungeonPreparationRuntime.PostTeleportHandoffKey, ?> handoffs,
            PortalSessionId portalSessionId
    ) {
        Objects.requireNonNull(handoffs, "handoffs");
        Objects.requireNonNull(portalSessionId, "portalSessionId");

        return handoffs.keySet().stream()
                .filter(key -> key.portalSessionId().equals(portalSessionId))
                .toList();
    }

    static boolean remove(
            Map<DungeonPreparationRuntime.PostTeleportHandoffKey, ?> handoffs,
            ArrayDeque<DungeonPreparationRuntime.PostTeleportHandoffKey> order,
            DungeonPreparationRuntime.PostTeleportHandoffKey key
    ) {
        Objects.requireNonNull(handoffs, "handoffs");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(key, "key");

        if (handoffs.remove(key) == null) {
            return false;
        }

        order.remove(key);
        return true;
    }

    static void removeIf(
            Map<DungeonPreparationRuntime.PostTeleportHandoffKey, ?> handoffs,
            ArrayDeque<DungeonPreparationRuntime.PostTeleportHandoffKey> order,
            Predicate<DungeonPreparationRuntime.PostTeleportHandoffKey> predicate
    ) {
        Objects.requireNonNull(handoffs, "handoffs");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(predicate, "predicate");

        for (DungeonPreparationRuntime.PostTeleportHandoffKey key
                : List.copyOf(handoffs.keySet())) {
            if (predicate.test(key)) {
                remove(handoffs, order, key);
            }
        }
    }
}
