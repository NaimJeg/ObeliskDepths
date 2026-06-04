package io.github.naimjeg.obeliskdepths.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ObeliskChestRenderer implements BlockEntityRenderer<
        ObeliskChestBlockEntity,
        ObeliskChestRenderer.RenderState
> {
    public static final Identifier BOTTOM_MODEL_ID = modelId("obelisk_chest_bottom");
    public static final Identifier LID_MODEL_ID = modelId("obelisk_chest_lid");
    public static final Identifier LOCK_MODEL_ID = modelId("obelisk_chest_lock");

    public static final StandaloneModelKey<BlockStateModelPart> BOTTOM_MODEL =
            modelKey("obelisk_chest_bottom");
    public static final StandaloneModelKey<BlockStateModelPart> LID_MODEL =
            modelKey("obelisk_chest_lid");
    public static final StandaloneModelKey<BlockStateModelPart> LOCK_MODEL =
            modelKey("obelisk_chest_lock");

    private static final float MAX_LID_ANGLE = 80.0F * Mth.DEG_TO_RAD;
    private static final float HINGE_X = 8.0F / 16.0F;
    private static final float HINGE_Y = 19.0F / 16.0F;
    private static final float HINGE_Z = 31.0F / 16.0F;
    private static final float UNLOCK_PHASE_END = 0.20F;
    private static final float LOCK_OUTWARD_DISTANCE = -2.0F / 16.0F;
    private static final float LOCK_DOWN_DISTANCE = -3.0F / 16.0F;

    public ObeliskChestRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(
            ObeliskChestBlockEntity blockEntity,
            RenderState state,
            float partialTick,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity,
                state,
                partialTick,
                cameraPosition,
                breakProgress
        );
        state.facing = blockEntity.getBlockState().hasProperty(ObeliskChestBlock.FACING)
                ? blockEntity.getBlockState().getValue(ObeliskChestBlock.FACING)
                : Direction.NORTH;
        float interpolatedTicks = Mth.lerp(
                partialTick,
                blockEntity.getPreviousAnimationTicks(),
                blockEntity.getAnimationTicks()
        );
        state.progress = Mth.clamp(
                interpolatedTicks / ObeliskChestBlockEntity.OPEN_DURATION_TICKS,
                0.0F,
                1.0F
        );
    }

    @Override
    public void submit(
            RenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        BlockStateModelPart bottom = model(BOTTOM_MODEL);
        BlockStateModelPart lock = model(LOCK_MODEL);
        BlockStateModelPart lid = model(LID_MODEL);

        float unlockProgress = smoothstep(Mth.clamp(
                state.progress / UNLOCK_PHASE_END,
                0.0F,
                1.0F
        ));
        float lidProgress = Mth.clamp(
                (state.progress - UNLOCK_PHASE_END) / (1.0F - UNLOCK_PHASE_END),
                0.0F,
                1.0F
        );
        // Baked block-model Y points upward (unlike entity-model Y), so positive X lifts
        // the negative-Z front edge around the positive-Z rear hinge.
        float lidAngle = easeOutCubic(lidProgress) * MAX_LID_ANGLE;

        poseStack.pushPose();
        applyControllerAndFacingTransform(poseStack, state.facing);

        poseStack.pushPose();
        submitPart(bottom, poseStack, submitNodeCollector, state);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(
                0.0F,
                unlockProgress * LOCK_DOWN_DISTANCE,
                unlockProgress * LOCK_OUTWARD_DISTANCE
        );
        submitPart(lock, poseStack, submitNodeCollector, state);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(HINGE_X, HINGE_Y, HINGE_Z);
        poseStack.mulPose(Axis.XP.rotation(lidAngle));
        poseStack.translate(-HINGE_X, -HINGE_Y, -HINGE_Z);
        submitPart(lid, poseStack, submitNodeCollector, state);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void applyControllerAndFacingTransform(PoseStack poseStack, Direction facing) {
        Direction right = facing.getClockWise();
        poseStack.translate(right.getStepX() + 0.5F, 0.0F, right.getStepZ() + 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        }));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
    }

    private static void submitPart(
            BlockStateModelPart part,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            RenderState state
    ) {
        submitNodeCollector.submitBlockModel(
                poseStack,
                Sheets.cutoutBlockSheet(),
                List.of(part),
                BlockModelRenderState.EMPTY_TINTS,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                0
        );
    }

    private static BlockStateModelPart model(StandaloneModelKey<BlockStateModelPart> key) {
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        if (model == null) {
            throw new IllegalStateException("Missing Obelisk Chest standalone model: " + key.getName());
        }
        return model;
    }

    private static float smoothstep(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return value * value * (3.0F - 2.0F * value);
    }

    private static float easeOutCubic(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    private static Identifier modelId(String name) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "block/" + name);
    }

    private static StandaloneModelKey<BlockStateModelPart> modelKey(String name) {
        return new StandaloneModelKey<>(
                () -> ObeliskDepths.MOD_ID + ":block/" + name
        );
    }

    @Override
    public AABB getRenderBoundingBox(ObeliskChestBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(
                pos.getX() - 1.0,
                pos.getY(),
                pos.getZ() - 1.0,
                pos.getX() + 3.0,
                pos.getY() + 2.0,
                pos.getZ() + 3.0
        );
    }

    public static final class RenderState extends BlockEntityRenderState {
        private Direction facing = Direction.NORTH;
        private float progress;
    }
}
