package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonActivationCommitFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonActivationCommitResult;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonActivationCommitService;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonActivationPreparationService;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationFailure;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRequest;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationResult;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparedTarget;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ObeliskInteractionHandler {
    private ObeliskInteractionHandler() {
    }

    public static boolean activate(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            BlockPos obeliskPos,
            PortalAdmissionMode requestedMode,
            ItemStack tributeStack
    ) {
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }

        long acceptedNanos = System.nanoTime();
        long gameTime = dungeonLevel.getGameTime();
        ObeliskDepths.LOGGER.debug(
                "[OD timing] portalRequestAccepted player={} mode={} obelisk={} sourceDimension={} gameTime={}",
                player.getGameProfile().name(),
                requestedMode,
                obeliskPos,
                sourceLevel.dimension().identifier(),
                gameTime
        );

        DungeonPreparationRequest request = new DungeonPreparationRequest(
                player.getUUID(),
                sourceLevel.dimension(),
                obeliskPos,
                requestedMode
        );

        DungeonPreparationResult preparation =
                DungeonActivationPreparationService.prepare(
                        dungeonLevel,
                        request,
                        tributeStack
                );

        if (preparation instanceof DungeonPreparationFailure failure) {
            if (failure.reason() == DungeonPreparationFailureReason.WRONG_TARGET_DIMENSION) {
                ObeliskDepths.LOGGER.warn(
                        "[OD locator] rejected lookup reason=wrong_target_dimension player={} targetDimension={} expected={}",
                        player.getGameProfile().name(),
                        dungeonLevel.dimension().identifier(),
                        ModDimensions.OBELISK_DEPTHS_LEVEL.identifier()
                );
            }
            player.sendOverlayMessage(messageForPreparationFailure(failure.reason()));
            return false;
        }

        if (!(preparation instanceof DungeonPreparedTarget preparedTarget)) {
            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.obelisk.activation_failed")
            );
            return false;
        }

        DungeonActivationCommitResult committed =
                DungeonActivationCommitService.commit(
                        player,
                        sourceLevel,
                        dungeonLevel,
                        preparedTarget,
                        tributeStack
                );

        if (!committed.success()) {
            DungeonActivationCommitFailureReason reason = committed.failureReason()
                    .orElse(DungeonActivationCommitFailureReason.INTERNAL_ERROR);
            player.sendOverlayMessage(messageForCommitFailure(reason));
            return false;
        }

        player.sendOverlayMessage(
                Component.translatable("message.obeliskdepths.portal.opened")
        );
        ObeliskDepths.LOGGER.info(
                "Opened dungeon portal instance={} session={} mode={} anchor={}",
                committed.instanceId().map(Object::toString).orElse("unknown"),
                committed.portalSessionId().map(Object::toString).orElse("unknown"),
                requestedMode,
                obeliskPos
        );
        ObeliskDepths.LOGGER.debug(
                "[OD timing] activationComplete player={} instance={} totalMicros={}",
                player.getGameProfile().name(),
                committed.instanceId().map(Object::toString).orElse("unknown"),
                (System.nanoTime() - acceptedNanos) / 1_000L
        );
        return true;
    }

    private static Component messageForPreparationFailure(
            DungeonPreparationFailureReason reason
    ) {
        return switch (reason) {
            case INVALID_TRIBUTE -> Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_tribute"
            );
            case NO_SITE_AVAILABLE,
                    NON_AUTHORITATIVE_SITE,
                    SITE_NO_LONGER_RESERVABLE -> Component.translatable(
                    "message.obeliskdepths.portal.no_site"
            );
            case INVALID_OBELISK -> Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_obelisk"
            );
            case WRONG_TARGET_DIMENSION,
                    INTERNAL_ERROR -> Component.translatable(
                    "message.obeliskdepths.obelisk.activation_failed"
            );
        };
    }

    private static Component messageForCommitFailure(
            DungeonActivationCommitFailureReason reason
    ) {
        return switch (reason) {
            case INVALID_TRIBUTE -> Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_tribute"
            );
            case PORTAL_SPAWN_FAILED -> Component.translatable(
                    "message.obeliskdepths.portal.spawn_failed"
            );
            case SITE_CONFLICT,
                    NON_AUTHORITATIVE_SITE -> Component.translatable(
                    "message.obeliskdepths.portal.no_site"
            );
            case INVALID_OBELISK -> Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_obelisk"
            );
            case PLAYER_OFFLINE,
                    WRONG_SOURCE_DIMENSION,
                    EXISTING_TARGET_UNAVAILABLE,
                    INTERNAL_ERROR -> Component.translatable(
                    "message.obeliskdepths.obelisk.activation_failed"
            );
        };
    }
}
