package io.github.naimjeg.obeliskdepths.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.item.ReturnScrollItem;
import io.github.naimjeg.obeliskdepths.item.ReturnScrollUseMath;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

public final class ReturnScrollFirstPersonRenderer {
    // Transform constants and pose order are adapted from the current vanilla map first-person renderer.
    private static final Identifier OPEN_TEXTURE = Identifier.fromNamespaceAndPath(
            ObeliskDepths.MOD_ID,
            "textures/item/return_scroll_open.png"
    );
    private static final Identifier RUNES_TEXTURE = Identifier.fromNamespaceAndPath(
            ObeliskDepths.MOD_ID,
            "textures/item/return_scroll_runes.png"
    );
    private static final float MAP_BASE_SCALE = 0.38F;
    private static final float MAP_PIXEL_SCALE = 1.0F / 128.0F;
    private static final float MAP_CENTER_Y = 0.04F;
    private static final float MAP_CENTER_Z = -0.72F;
    private static final float MAP_TILT_DEGREES = -85.0F;

    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    private ReturnScrollFirstPersonRenderer() {
    }

    public static boolean render(
            InteractionHand hand,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float frameInterp,
            float xRot,
            float attackValue,
            float inverseArmHeight,
            ItemStack stack
    ) {
        if (!stack.is(ModItems.RETURN_SCROLL.get())) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        boolean twoHanded = hand == InteractionHand.MAIN_HAND && player.getOffhandItem().isEmpty();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? player.getMainArm()
                : player.getMainArm().getOpposite();

        poseStack.pushPose();
        if (twoHanded) {
            renderTwoHanded(
                    minecraft,
                    player,
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    frameInterp,
                    xRot,
                    inverseArmHeight,
                    attackValue
            );
        } else {
            renderOneHanded(
                    minecraft,
                    player,
                    poseStack,
                    submitNodeCollector,
                    lightCoords,
                    frameInterp,
                    inverseArmHeight,
                    arm,
                    attackValue
            );
        }
        poseStack.popPose();
        return true;
    }

    private static void renderTwoHanded(
            Minecraft minecraft,
            LocalPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float frameInterp,
            float xRot,
            float inverseArmHeight,
            float attackValue
    ) {
        float progress = currentProgress(player, frameInterp);
        float sqrtAttack = Mth.sqrt(attackValue);
        float ySwing = -0.2F * Mth.sin(attackValue * (float) Math.PI);
        float zSwing = -0.4F * Mth.sin(sqrtAttack * (float) Math.PI);
        poseStack.translate(0.0F, -ySwing / 2.0F, zSwing);

        float mapTilt = calculateMapTilt(xRot);
        float raise = ReturnScrollUseMath.raiseProgress(progress);
        float finalShake = ReturnScrollUseMath.finalShakeProgress(progress);
        poseStack.translate(
                deterministicShake(player, frameInterp, finalShake, 0.006F),
                Mth.lerp(raise, -0.35F, MAP_CENTER_Y + inverseArmHeight * -1.2F + mapTilt * -0.5F)
                        + deterministicShake(player, frameInterp + 13.0F, finalShake, 0.004F),
                Mth.lerp(raise, -0.95F, MAP_CENTER_Z)
        );
        poseStack.mulPose(Axis.XP.rotationDegrees(mapTilt * MAP_TILT_DEGREES));

        if (!player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderMapHand(minecraft, player, poseStack, submitNodeCollector, lightCoords, HumanoidArm.RIGHT);
            renderMapHand(minecraft, player, poseStack, submitNodeCollector, lightCoords, HumanoidArm.LEFT);
            poseStack.popPose();
        }

        float xzSwingRotation = Mth.sin(sqrtAttack * (float) Math.PI);
        poseStack.mulPose(Axis.XP.rotationDegrees(xzSwingRotation * 20.0F));
        poseStack.scale(2.0F, 2.0F, 2.0F);
        renderScroll(poseStack, submitNodeCollector, lightCoords, player, frameInterp, progress);
    }

    private static void renderOneHanded(
            Minecraft minecraft,
            LocalPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float frameInterp,
            float inverseArmHeight,
            HumanoidArm arm,
            float attackValue
    ) {
        float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float progress = currentProgress(player, frameInterp);
        poseStack.translate(invert * 0.125F, -0.125F, 0.0F);
        if (!player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(invert * 10.0F));
            renderPlayerArm(minecraft, player, poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attackValue, arm);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(invert * 0.51F, -0.08F + inverseArmHeight * -1.2F, -0.75F);
        float sqrtAttack = Mth.sqrt(attackValue);
        float xSwing = Mth.sin(sqrtAttack * (float) Math.PI);
        poseStack.translate(
                invert * -0.5F * xSwing + deterministicShake(player, frameInterp, ReturnScrollUseMath.finalShakeProgress(progress), 0.004F),
                0.4F * Mth.sin(sqrtAttack * (float) (Math.PI * 2)) - 0.3F * xSwing,
                -0.3F * Mth.sin(attackValue * (float) Math.PI)
        );
        poseStack.mulPose(Axis.XP.rotationDegrees(xSwing * -45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * xSwing * -30.0F));
        renderScroll(poseStack, submitNodeCollector, lightCoords, player, frameInterp, progress);
        poseStack.popPose();
    }

    private static void renderMapHand(
            Minecraft minecraft,
            LocalPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            HumanoidArm arm
    ) {
        AvatarRenderer<AbstractClientPlayer> renderer =
                minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
        poseStack.pushPose();
        float invert = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * -41.0F));
        poseStack.translate(invert * 0.3F, -1.1F, 0.45F);
        Identifier skin = player.getSkin().body().texturePath();
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), player);
        } else {
            renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player);
        }
        poseStack.popPose();
    }

    private static void renderPlayerArm(
            Minecraft minecraft,
            LocalPlayer player,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float inverseArmHeight,
            float attackValue,
            HumanoidArm arm
    ) {
        boolean rightArm = arm != HumanoidArm.LEFT;
        float invert = rightArm ? 1.0F : -1.0F;
        float sqrtAttack = Mth.sqrt(attackValue);
        poseStack.translate(
                invert * (-0.3F * Mth.sin(sqrtAttack * (float) Math.PI) + 0.64000005F),
                0.4F * Mth.sin(sqrtAttack * (float) (Math.PI * 2)) - 0.6F + inverseArmHeight * -0.6F,
                -0.4F * Mth.sin(attackValue * (float) Math.PI) - 0.71999997F
        );
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * 45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * Mth.sin(sqrtAttack * (float) Math.PI) * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * Mth.sin(attackValue * attackValue * (float) Math.PI) * -20.0F));
        poseStack.translate(invert * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(invert * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(invert * -135.0F));
        poseStack.translate(invert * 5.6F, 0.0F, 0.0F);
        AvatarRenderer<AbstractClientPlayer> renderer =
                minecraft.getEntityRenderDispatcher().getPlayerRenderer(player);
        Identifier skin = player.getSkin().body().texturePath();
        if (rightArm) {
            renderer.renderRightHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE), player);
        } else {
            renderer.renderLeftHand(poseStack, submitNodeCollector, lightCoords, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE), player);
        }
    }

    private static void renderScroll(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            LocalPlayer player,
            float frameInterp,
            float progress
    ) {
        float unfold = ReturnScrollUseMath.unfoldProgress(progress);
        float width = Mth.lerp(unfold, 0.20F, 1.0F);
        float attune = ReturnScrollUseMath.attunementProgress(progress);
        float finale = ReturnScrollUseMath.finalShakeProgress(progress);

        int overlayAlpha = (int) (
                Mth.clamp(
                        0.25F + attune * 0.35F + finale * 0.4F,
                        0.0F,
                        1.0F
                ) * 255.0F
        );

        int pulse = 120 + (int) (
                80.0F * (
                        0.5F
                                + 0.5F * Mth.sin(
                                (player.tickCount + frameInterp) * 0.35F
                        )
                )
        );

        int overlayColor =
                (overlayAlpha << 24)
                        | (pulse << 16)
                        | (220 << 8)
                        | 255;

        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(MAP_BASE_SCALE, MAP_BASE_SCALE, MAP_BASE_SCALE);
        poseStack.translate(-0.5F, -0.5F, 0.0F);
        poseStack.scale(MAP_PIXEL_SCALE, MAP_PIXEL_SCALE, MAP_PIXEL_SCALE);

        drawMapQuad(
                poseStack,
                submitNodeCollector,
                OPEN_TEXTURE,
                lightCoords,
                -7.0F,
                135.0F,
                135.0F,
                -7.0F,
                width,
                -1,
                0.0F
        );

        drawMapQuad(
                poseStack,
                submitNodeCollector,
                RUNES_TEXTURE,
                FULL_BRIGHT_LIGHT,
                -7.0F,
                135.0F,
                135.0F,
                -7.0F,
                width,
                overlayColor,
                -0.02F
        );
    }

    private static void drawMapQuad(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Identifier texture,
            int lightCoords,
            float left,
            float bottom,
            float right,
            float top,
            float widthScale,
            int color,
            float z
    ) {
        float center = (left + right) * 0.5F;
        float halfWidth =
                (right - left)
                        * 0.5F
                        * Math.max(0.05F, widthScale);

        float scaledLeft = center - halfWidth;
        float scaledRight = center + halfWidth;

        submitNodeCollector.submitCustomGeometry(
                poseStack,
                RenderTypes.text(texture),
                (pose, buffer) -> {
                    buffer.addVertex(pose, scaledLeft, bottom, z)
                            .setColor(color)
                            .setUv(0.0F, 1.0F)
                            .setLight(lightCoords);

                    buffer.addVertex(pose, scaledRight, bottom, z)
                            .setColor(color)
                            .setUv(1.0F, 1.0F)
                            .setLight(lightCoords);

                    buffer.addVertex(pose, scaledRight, top, z)
                            .setColor(color)
                            .setUv(1.0F, 0.0F)
                            .setLight(lightCoords);

                    buffer.addVertex(pose, scaledLeft, top, z)
                            .setColor(color)
                            .setUv(0.0F, 0.0F)
                            .setLight(lightCoords);
                }
        );
    }

    private static void drawQuad(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            RenderType renderType,
            int lightCoords,
            float left,
            float bottom,
            float right,
            float top,
            float widthScale,
            int color,
            float z
    ) {
        float center = (left + right) * 0.5F;
        float halfWidth = (right - left) * 0.5F * Math.max(0.05F, widthScale);
        float scaledLeft = center - halfWidth;
        float scaledRight = center + halfWidth;
        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            buffer.addVertex(pose, scaledLeft, bottom, z).setColor(color).setUv(0.0F, 1.0F).setLight(lightCoords);
            buffer.addVertex(pose, scaledRight, bottom, z).setColor(color).setUv(1.0F, 1.0F).setLight(lightCoords);
            buffer.addVertex(pose, scaledRight, top, z).setColor(color).setUv(1.0F, 0.0F).setLight(lightCoords);
            buffer.addVertex(pose, scaledLeft, top, z).setColor(color).setUv(0.0F, 0.0F).setLight(lightCoords);
        });
    }

    private static float currentProgress(LocalPlayer player, float frameInterp) {
        if (!player.isUsingItem() || !player.getUseItem().is(ModItems.RETURN_SCROLL.get())) {
            return 0.0F;
        }

        return ReturnScrollUseMath.activationProgress(
                ReturnScrollItem.USE_DURATION_TICKS,
                player.getUseItemRemainingTicks(),
                frameInterp
        );
    }

    private static float calculateMapTilt(float pitch) {
        float tilt = 1.0F - pitch / 45.0F + 0.1F;
        tilt = Mth.clamp(tilt, 0.0F, 1.0F);
        return -Mth.cos(tilt * (float) Math.PI) * 0.5F + 0.5F;
    }

    private static float deterministicShake(LocalPlayer player, float frameInterp, float progress, float amplitude) {
        if (progress <= 0.0F) {
            return 0.0F;
        }

        return Mth.sin((player.tickCount + frameInterp) * 2.15F) * amplitude * progress;
    }
}
