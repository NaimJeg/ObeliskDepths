package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Thin orchestration boundary around preflighted atomic activation. */
public final class DungeonActivationCommitService {
    private DungeonActivationCommitService() {
    }

    static DungeonActivationCommitResult commitPreparedSolo(
            ServerLevel dungeonLevel,
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparedEntryRegistry preparedEntryRegistry,
            DungeonSiteClaimManager claimManager
    ) {
        DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
        long startNanos = profiler.start();
        try {
            DungeonActivationCommitPlan plan = context.commitPlan()
                    .orElseThrow(() -> new IllegalStateException(
                            "Activation commit requires an immutable preflight plan"
                    ));
            return new DungeonActivationTransaction(
                    plan,
                    new ServerDungeonActivationTransactionBackend(
                            dungeonLevel,
                            job,
                            context,
                            preparedEntryRegistry,
                            claimManager
                    ),
                    DungeonActivationTransactionMetrics.global(),
                    DungeonActivationFailureInjector.NONE
            ).execute();
        } finally {
            try {
                profiler.record(
                        DungeonPreparationProfiler.Operation.ACTIVATION_COMMIT,
                        startNanos,
                        dungeonLevel.getServer().isSameThread()
                );
            } catch (RuntimeException telemetryFailure) {
                ObeliskDepths.LOGGER.warn(
                        "Activation commit profiler record failed",
                        telemetryFailure
                );
            }
        }
    }

    static void finishSuccessfulPreparedSolo(
            ServerLevel dungeonLevel,
            DungeonPreparationJob job,
            DungeonActivationCommitResult result
    ) {
        ServerPlayer player = dungeonLevel.getServer()
                .getPlayerList()
                .getPlayer(job.request().playerId());
        if (player == null) {
            return;
        }
        if (player.containerMenu instanceof ObeliskPortalMenu menu
                && menu.containerId == job.request().sourceContainerId()) {
            menu.markActivationCommitted(job.id());
            player.closeContainer();
        }
        player.sendOverlayMessage(
                net.minecraft.network.chat.Component.translatable(
                        "message.obeliskdepths.portal.opened"
                )
        );
        ObeliskDepths.LOGGER.info(
                "Opened prepared SOLO dungeon portal instance={} session={} anchor={}",
                result.instanceId().map(Object::toString).orElse("unknown"),
                result.portalSessionId().map(Object::toString).orElse("unknown"),
                job.request().obeliskPos()
        );
    }
}
