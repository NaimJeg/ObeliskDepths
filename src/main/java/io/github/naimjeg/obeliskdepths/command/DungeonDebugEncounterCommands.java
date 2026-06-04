package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonMobResolution;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugEncounterCommands {
    private DungeonDebugEncounterCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("encounter")
                        .then(Commands.literal("current")
                                .executes(context -> current(context.getSource())))
                        .then(Commands.literal("fail")
                                .executes(context -> fail(context.getSource())))
                        .then(Commands.literal("expire")
                                .executes(context -> expire(context.getSource())))
                        .then(Commands.literal("reconcile")
                                .executes(context -> reconcile(context.getSource())))
                        .then(Commands.literal("progress")
                                .executes(context -> progress(context.getSource()))))
                .then(Commands.literal("encounter-debug")
                        .executes(context -> current(context.getSource())));
    }

    private static int current(CommandSourceStack source) {
        Optional<ResolvedEncounter> resolved = resolve(source, true);

        if (resolved.isEmpty()) {
            return 0;
        }

        sendEncounter(source, resolved.get().encounter());
        return Command.SINGLE_SUCCESS;
    }

    private static int fail(CommandSourceStack source) {
        Optional<ResolvedEncounter> resolved = resolve(source, true);

        if (resolved.isEmpty()) {
            return 0;
        }

        boolean changed = DungeonEncounterDirector.failInstance(
                resolved.get().level(),
                resolved.get().instance().id(),
                DungeonMobResolution.INVALIDATED
        );

        if (!changed) {
            DungeonDebugCommandUtil.failure(source, "No non-terminal encounter is available to fail.");
            return 0;
        }

        DungeonDebugCommandUtil.success(
                source,
                "Failed encounter " + resolved.get().encounter().id() + "."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int expire(CommandSourceStack source) {
        Optional<ResolvedEncounter> resolved = resolve(source, true);

        if (resolved.isEmpty()) {
            return 0;
        }

        boolean changed = DungeonEncounterDirector.expireInstance(
                resolved.get().level(),
                resolved.get().instance().id(),
                DungeonMobResolution.CLEANED
        );

        if (!changed) {
            DungeonDebugCommandUtil.failure(source, "No non-terminal encounter is available to expire.");
            return 0;
        }

        DungeonDebugCommandUtil.success(
                source,
                "Expired encounter " + resolved.get().encounter().id() + "."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int reconcile(CommandSourceStack source) {
        Optional<ResolvedEncounter> resolved = resolve(source, true);

        if (resolved.isEmpty()) {
            return 0;
        }

        boolean reconciled = DungeonEncounterDirector.reconcileInstanceNow(
                resolved.get().level(),
                resolved.get().instance().id()
        );

        if (!reconciled) {
            DungeonDebugCommandUtil.failure(
                    source,
                    "Encounter reconcile skipped: missing site, inactive instance, terminal phase, or no active encounter."
            );
            return 0;
        }

        DungeonDebugCommandUtil.success(
                source,
                "Ran one safe encounter reconciliation tick for " + resolved.get().encounter().id() + "."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int progress(CommandSourceStack source) {
        Optional<ResolvedEncounter> resolved = resolve(source, true);

        if (resolved.isEmpty()) {
            return 0;
        }

        DungeonDebugCommandUtil.info(
                source,
                "Encounter progress: quota="
                        + resolved.get().encounter().normalKillQuota()
                        + ", creditedKills="
                        + resolved.get().encounter().creditedNormalKills()
                        + ", complete="
                        + resolved.get().encounter().normalKillQuotaComplete()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<ResolvedEncounter> resolve(
            CommandSourceStack source,
            boolean requireEncounter
    ) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return Optional.empty();
        }

        var instance = DungeonDebugCommandUtil.currentInstance(level.get(), player.get());

        if (instance.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "You are not bound to a dungeon instance.");
            return Optional.empty();
        }

        Optional<DungeonRaidInstance> encounter =
                DungeonDebugCommandUtil.currentEncounter(level.get(), instance.get());

        if (encounter.isEmpty() && requireEncounter) {
            DungeonDebugCommandUtil.failure(source, "Current dungeon has no encounter state.");
            return Optional.empty();
        }

        return encounter.map(raid -> new ResolvedEncounter(
                level.get(),
                instance.get(),
                raid
        ));
    }

    private static void sendEncounter(
            CommandSourceStack source,
            DungeonRaidInstance encounter
    ) {
        DungeonDebugCommandUtil.info(
                source,
                "Encounter current: instance="
                        + encounter.dungeonInstanceId()
                        + ", encounter="
                        + encounter.id()
                        + ", phase="
                        + encounter.encounterPhase().getSerializedName()
                        + ", status="
                        + encounter.status().getSerializedName()
                        + ", normalQuota="
                        + encounter.normalKillQuota()
                        + ", creditedKills="
                        + encounter.creditedNormalKills()
                        + ", trackedMobs="
                        + encounter.trackedMobIds().size()
                        + ", nextSpawn="
                        + encounter.nextSpawnGameTime()
                        + ", spawnFailures="
                        + encounter.spawnFailureCount()
        );
    }

    private record ResolvedEncounter(
            ServerLevel level,
            io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance instance,
            DungeonRaidInstance encounter
    ) {
    }
}
