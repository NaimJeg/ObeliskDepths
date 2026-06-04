package io.github.naimjeg.obeliskdepths.world;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Cross-dimension teleportation utility for the Obelisk Depths dungeon.
 *
 * <p>All methods perform vanilla {@link ServerPlayer#teleport(TeleportTransition)}
 * calls only. This class does not scan disk, load chunks, read structures,
 * or perform synchronous preparation. Destination resolution must be
 * completed through the asynchronous preparation system before invoking
 * a teleport method on this class. Teleportation must not create,
 * materialize, or repair dungeon geometry.</p>
 */
public final class ObeliskDepthsTeleporter {
    private ObeliskDepthsTeleporter() {
    }

    /**
     * Teleports the player to a previously resolved dungeon entry.
     *
     * @param player the player to teleport
     * @param entry  a resolved entry with target level, position, and rotation
     * @return the teleported player, or empty if the teleport was rejected
     */
    public static Optional<ServerPlayer> teleportToResolvedEntry(
            ServerPlayer player,
            ResolvedDungeonEntry entry
    ) {
        return teleportToLevel(
                player,
                entry.targetLevel(),
                entry.destination(),
                entry.yaw(),
                entry.pitch()
        );
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            BlockPos targetPos
    ) {
        return teleportToLevel(player, targetLevel, Vec3.atCenterOf(targetPos));
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            Vec3 target
    ) {
        return teleportToLevel(
                player,
                targetLevel,
                target,
                player.getYRot(),
                player.getXRot()
        );
    }

    public static Optional<ServerPlayer> teleportToLevel(
            ServerPlayer player,
            ServerLevel targetLevel,
            Vec3 target,
            float yaw,
            float pitch
    ) {
        long startNanos = System.nanoTime();
        ServerPlayer teleportedPlayer = player.teleport(new TeleportTransition(
                targetLevel,
                target,
                Vec3.ZERO,
                yaw,
                pitch,
                TeleportTransition.DO_NOTHING
        ));

        ObeliskDepths.LOGGER.debug(
                "[OD timing] teleport player={} target={} elapsedMicros={}",
                player.getGameProfile().name(),
                target,
                (System.nanoTime() - startNanos) / 1_000L
        );

        return Optional.ofNullable(teleportedPlayer);
    }
}
