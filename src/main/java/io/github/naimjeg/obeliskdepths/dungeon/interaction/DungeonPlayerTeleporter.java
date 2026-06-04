package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.world.ResolvedDungeonEntry;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

@FunctionalInterface
interface DungeonPlayerTeleporter {
    Optional<ServerPlayer> teleport(ServerPlayer player, ResolvedDungeonEntry entry);
}
