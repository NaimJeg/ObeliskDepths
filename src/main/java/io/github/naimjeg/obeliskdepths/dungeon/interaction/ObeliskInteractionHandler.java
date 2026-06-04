package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRequest;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRuntime;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationSubmission;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationSubmissionRejectionReason;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeFingerprint;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ObeliskInteractionHandler {
    private ObeliskInteractionHandler() {
    }

    public static DungeonPreparationSubmission activate(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            BlockPos obeliskPos,
            SessionAccessPolicy accessPolicy,
            int sourceContainerId,
            ItemStack tributeStack
    ) {
        Objects.requireNonNull(accessPolicy, "accessPolicy");

        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.obeliskdepths.obelisk.activation_failed"
            ));
            return rejected(
                    DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED,
                    "source level unavailable"
            );
        }

        long acceptedNanos = System.nanoTime();
        long gameTime = dungeonLevel.getGameTime();
        ObeliskDepths.LOGGER.debug(
                "[OD timing] portalRequestSubmitted player={} obelisk={} sourceDimension={} accessPolicy={} gameTime={}",
                player.getGameProfile().name(),
                obeliskPos,
                sourceLevel.dimension().identifier(),
                accessPolicy.getSerializedName(),
                gameTime
        );

        if (!dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            ObeliskDepths.LOGGER.warn(
                    "[OD activation] rejected wrong target dimension player={} targetDimension={} expected={}",
                    player.getGameProfile().name(),
                    dungeonLevel.dimension().identifier(),
                    ModDimensions.OBELISK_DEPTHS_LEVEL.identifier()
            );
            player.sendOverlayMessage(Component.translatable(
                    "message.obeliskdepths.obelisk.activation_failed"
            ));
            return rejected(
                    DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED,
                    "wrong target dimension"
            );
        }

        if (!isValidBottomObelisk(sourceLevel, obeliskPos)
                || !withinMenuDistance(player, obeliskPos)) {
            player.sendOverlayMessage(Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_obelisk"
            ));
            return rejected(
                    DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED,
                    "invalid source obelisk"
            );
        }

        ResolvedTribute tribute = TributeResolver.resolve(tributeStack);
        if (!tribute.valid()) {
            player.sendOverlayMessage(Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_tribute"
            ));
            return rejected(
                    DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED,
                    "invalid tribute"
            );
        }

        DungeonPreparationRequest request = new DungeonPreparationRequest(
                player.getUUID(),
                sourceLevel.dimension(),
                obeliskPos,
                accessPolicy,
                tribute,
                sourceContainerId,
                TributeFingerprint.from(tributeStack, tribute.amount())
        );

        DungeonPreparationSubmission submission =
                DungeonPreparationRuntime.getOrCreate(dungeonLevel).submit(request);
        if (!submission.accepted()) {
            player.sendOverlayMessage(messageForSubmissionRejection(
                    submission.rejectionReason()
                            .orElse(DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED)
            ));
            return submission;
        }

        ObeliskDepths.LOGGER.debug(
                "[OD timing] activationSubmittedAsync player={} job={} elapsedMicros={}",
                player.getGameProfile().name(),
                submission.jobId().map(Object::toString).orElse("unknown"),
                (System.nanoTime() - acceptedNanos) / 1_000L
        );
        return submission;
    }

    private static DungeonPreparationSubmission rejected(
            DungeonPreparationSubmissionRejectionReason reason,
            String detail
    ) {
        return DungeonPreparationSubmission.rejected(reason, null, detail);
    }

    private static Component messageForSubmissionRejection(
            DungeonPreparationSubmissionRejectionReason reason
    ) {
        return switch (reason) {
            case ACTIVE_JOB_LIMIT,
                    DUPLICATE_PLAYER,
                    DUPLICATE_OBELISK,
                    DUPLICATE_JOB_ID,
                    RUNTIME_CLEARED -> Component.translatable(
                    "message.obeliskdepths.obelisk.activation_failed"
            );
        };
    }

    private static boolean isValidBottomObelisk(
            ServerLevel level,
            BlockPos pos
    ) {
        var state = level.getBlockState(pos);
        return state.is(ModBlocks.OBELISK.get())
                && state.hasProperty(ObeliskBlock.PART)
                && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
    }

    private static boolean withinMenuDistance(
            ServerPlayer player,
            BlockPos pos
    ) {
        return player.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) <= 64.0D;
    }
}
