package io.github.naimjeg.obeliskdepths.item;

import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class ReturnScrollItem extends Item {
    public static final int USE_DURATION_TICKS = 60;

    public ReturnScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        PlayerDungeonReturnResult result = validateCanStart(serverPlayer);
        if (result != PlayerDungeonReturnResult.SUCCESS) {
            sendMessage(serverPlayer, result);
            return InteractionResult.FAIL;
        }

        serverPlayer.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return itemStack;
        }

        PlayerDungeonReturnResult result = PlayerDungeonReturnService.returnPlayerFromScroll(player);
        if (ReturnScrollUseRules.isSuccessful(result)) {
            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }

            sendMessage(player, result);
            playSuccessFeedback(player);
        } else {
            sendMessage(player, result);
        }

        return itemStack;
    }

    @Override
    public int getUseDuration(ItemStack itemStack, LivingEntity user) {
        return USE_DURATION_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack itemStack) {
        return ItemUseAnimation.NONE;
    }

    public static PlayerDungeonReturnResult validateCanStart(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return PlayerDungeonReturnResult.NO_DUNGEON_BINDING;
        }

        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return PlayerDungeonReturnResult.NOT_IN_DUNGEON_DIMENSION;
        }

        return PlayerDungeonReturnService.checkScrollReturn(player);
    }

    public static String translationKey(PlayerDungeonReturnResult result) {
        return ReturnScrollUseRules.translationKey(result);
    }

    public static int resultingStackCountAfterFinish(
            int startingCount,
            boolean instabuild,
            PlayerDungeonReturnResult result
    ) {
        return ReturnScrollUseRules.resultingStackCountAfterFinish(startingCount, instabuild, result);
    }

    private static void sendMessage(ServerPlayer player, PlayerDungeonReturnResult result) {
        player.sendOverlayMessage(Component.translatable(translationKey(result)));
    }

    private static void playSuccessFeedback(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.PORTAL_TRAVEL,
                SoundSource.PLAYERS,
                0.35F,
                1.35F
        );
    }
}
