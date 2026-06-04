package io.github.naimjeg.obeliskdepths.item;

import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;

public final class ReturnScrollUseRules {
    private ReturnScrollUseRules() {
    }

    public static String translationKey(PlayerDungeonReturnResult result) {
        return switch (result) {
            case SUCCESS -> "message.obeliskdepths.return_scroll.success";
            case SUCCESS_EMERGENCY_FALLBACK -> "message.obeliskdepths.return_scroll.emergency_fallback";
            case NO_DUNGEON_BINDING -> "message.obeliskdepths.return_scroll.no_binding";
            case INCOMPLETE_RETURN_DATA -> "message.obeliskdepths.return_scroll.incomplete_data";
            case RETURN_LEVEL_MISSING -> "message.obeliskdepths.return_scroll.return_level_missing";
            case DUNGEON_LEVEL_MISSING -> "message.obeliskdepths.return_scroll.dungeon_level_missing";
            case TELEPORT_FAILED -> "message.obeliskdepths.return_scroll.teleport_failed";
            case NOT_IN_DUNGEON_DIMENSION -> "message.obeliskdepths.return_scroll.not_in_depths";
            case NO_SAFE_RETURN_DESTINATION -> "message.obeliskdepths.return_scroll.no_safe_destination";
        };
    }

    public static int resultingStackCountAfterFinish(
            int startingCount,
            boolean instabuild,
            PlayerDungeonReturnResult result
    ) {
        if (!isSuccessful(result) || instabuild) {
            return startingCount;
        }

        return Math.max(0, startingCount - 1);
    }

    public static boolean isSuccessful(PlayerDungeonReturnResult result) {
        return result == PlayerDungeonReturnResult.SUCCESS
                || result == PlayerDungeonReturnResult.SUCCESS_EMERGENCY_FALLBACK;
    }
}
