package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DungeonRewardDelivery {
    static final String TAG_REWARD_ID = "obeliskdepths_reward_id";
    static final String TAG_INSTANCE_ID = "obeliskdepths_instance_id";
    static final String TAG_ROOM_ID = "obeliskdepths_room_id";
    static final String TAG_REWARD_SEED = "obeliskdepths_reward_seed";
    static final String TAG_DELIVERY_ORDINAL = "obeliskdepths_delivery_ordinal";

    private static final double DELIVERY_RECONCILIATION_RADIUS = 64.0D;

    private static final DungeonRewardGenerator GENERATOR =
            DefaultDungeonRewardGenerator.INSTANCE;

    private DungeonRewardDelivery() {
    }

    public static List<ItemStack> generate(
            ServerLevel level,
            DungeonInstance instance,
            DungeonRewardRecord reward
    ) {
        return GENERATOR.generate(createContext(level, instance, reward));
    }

    public static boolean spawnRewardStack(
            ServerLevel level,
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            long rewardSeed,
            BlockPos chestPos,
            int deliveryOrdinal,
            ItemStack stack
    ) {
        if (level == null
                || rewardId == null
                || instanceId == null
                || chestPos == null
                || stack == null
                || stack.isEmpty()) {
            return false;
        }

        Random random = new Random(DefaultDungeonRewardGenerator.mix(
                rewardSeed,
                DefaultDungeonRewardGenerator.SPRAY_SALT ^ (0x9E37_79B9_7F4A_7C15L * Math.max(0L, deliveryOrdinal + 1L))
        ));
        Vec3 spawnPosition = findRewardSpawnPosition(level, chestPos);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double speed = 0.12D + random.nextDouble() * 0.08D;
        ItemEntity entity = new ItemEntity(
                level,
                spawnPosition.x,
                spawnPosition.y,
                spawnPosition.z,
                stack.copy()
        );
        entity.setDeltaMovement(
                Math.cos(angle) * speed,
                0.25D + random.nextDouble() * 0.12D,
                Math.sin(angle) * speed
        );
        entity.setDefaultPickUpDelay();
        entity.getPersistentData().putString(TAG_REWARD_ID, rewardId.toString());
        entity.getPersistentData().putString(TAG_INSTANCE_ID, instanceId.toString());
        roomId.ifPresent(id -> entity.getPersistentData().putString(TAG_ROOM_ID, id.toString()));
        entity.getPersistentData().putLong(TAG_REWARD_SEED, rewardSeed);
        entity.getPersistentData().putInt(TAG_DELIVERY_ORDINAL, deliveryOrdinal);
        return level.addFreshEntity(entity);
    }

    public static boolean hasSpawnedOrdinal(
            ServerLevel level,
            DungeonRewardRecord reward,
            int ordinal,
            BlockPos center
    ) {
        if (level == null || reward == null || center == null || ordinal < 0) {
            return false;
        }

        AABB bounds = AABB.ofSize(
                center.getCenter(),
                DELIVERY_RECONCILIATION_RADIUS,
                DELIVERY_RECONCILIATION_RADIUS,
                DELIVERY_RECONCILIATION_RADIUS
        );

        return !level.getEntities(EntityType.ITEM, bounds, entity ->
                matchesRewardEntity(reward, entity)
                        && entity.getPersistentData()
                        .getInt(TAG_DELIVERY_ORDINAL)
                        .orElse(-1) == ordinal
        ).isEmpty();
    }

    public static void spawnRewardOrdinalOrThrow(
            ServerLevel level,
            DungeonRewardRecord reward,
            BlockPos pos,
            ItemStack stack,
            int ordinal
    ) {
        if (stack.isEmpty()) {
            return;
        }

        boolean spawned = spawnRewardStack(
                level,
                reward.rewardId(),
                reward.instanceId(),
                reward.roomId(),
                reward.rewardSeed(),
                pos,
                ordinal,
                stack
        );

        if (!spawned) {
            throw new IllegalStateException(
                    "Failed to spawn dungeon reward stack: reward="
                            + reward.rewardId()
                            + " ordinal="
                            + ordinal
            );
        }
    }

    private static boolean matchesRewardEntity(
            DungeonRewardRecord reward,
            ItemEntity entity
    ) {
        if (!reward.rewardId().toString().equals(
                entity.getPersistentData().getString(TAG_REWARD_ID).orElse("")
        )) {
            return false;
        }

        if (!reward.instanceId().toString().equals(
                entity.getPersistentData().getString(TAG_INSTANCE_ID).orElse("")
        )) {
            return false;
        }

        if (entity.getPersistentData().getLong(TAG_REWARD_SEED).orElse(Long.MIN_VALUE)
                != reward.rewardSeed()) {
            return false;
        }

        return reward.roomId()
                .map(roomId -> roomId.toString().equals(
                        entity.getPersistentData().getString(TAG_ROOM_ID).orElse("")
                ))
                .orElse(true);
    }

    private static DungeonRewardContext createContext(
            ServerLevel level,
            DungeonInstance instance,
            DungeonRewardRecord reward
    ) {
        DungeonRoomType roomType = reward.roomId()
                .flatMap(roomId -> DungeonManagerSavedData.get(level)
                        .roomStates()
                        .allForInstance(instance.id())
                        .stream()
                        .filter(room -> room.roomId().equals(roomId))
                        .findFirst()
                        .map(DungeonRoomState::type))
                .orElse(DungeonRoomType.BOSS);
        return new DungeonRewardContext(
                level,
                instance,
                instance.id(),
                reward.roomId(),
                roomType,
                reward.rewardSeed(),
                instance.participants().size()
        );
    }

    private static BlockPos findRewardSpawnBase(ServerLevel level, BlockPos pos) {
        BlockPos candidate = pos.above();
        if (!level.getBlockState(candidate).isCollisionShapeFullBlock(level, candidate)) {
            return candidate;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction).above();
            if (level.hasChunkAt(side)
                    && !level.getBlockState(side).isCollisionShapeFullBlock(level, side)) {
                return side;
            }
        }

        return pos.above(2);
    }

    private static Vec3 findRewardSpawnPosition(ServerLevel level, BlockPos pos) {
        BlockPos spawnBase = findRewardSpawnBase(level, pos);
        return new Vec3(
                spawnBase.getX() + 0.5D,
                spawnBase.getY() + 0.15D,
                spawnBase.getZ() + 0.5D
        );
    }
}
