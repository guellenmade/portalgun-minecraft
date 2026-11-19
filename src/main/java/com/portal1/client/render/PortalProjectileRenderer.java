package com.portal1.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portal1.Portal1Mod;
import com.portal1.entity.PortalProjectileEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Renders the visible traveling Portal 1 energy projectile orb in the shooter's exact portal color.
 */
public final class PortalProjectileRenderer extends EntityRenderer<PortalProjectileEntity, PortalProjectileRenderer.ProjectileState> {

    private static final Identifier ORB_TEXTURE = Portal1Mod.id("textures/entity/portal_orb.png");

    public static final class ProjectileState extends EntityRenderState {
        public int colorRgb = 0x00A2FF;
    }

    public PortalProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ProjectileState createRenderState() {
        return new ProjectileState();
    }

    @Override
    public void extractRenderState(PortalProjectileEntity entity, ProjectileState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.colorRgb = entity.getColorRgb();
    }

    @Override
    public void submit(
        ProjectileState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        int r = (state.colorRgb >> 16) & 0xFF;
        int g = (state.colorRgb >> 8) & 0xFF;
        int b = state.colorRgb & 0xFF;

        poseStack.pushPose();
        poseStack.mulPose(cameraState.orientation);
        float s = 0.24f;
        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucentEmissive(ORB_TEXTURE),
            (pose, consumer) -> {
                int light = 0x00F000F0;
                int overlay = OverlayTexture.NO_OVERLAY;
                consumer.addVertex(pose, -s, -s, 0.0f).setColor(r, g, b, 255).setUv(0.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose,  s, -s, 0.0f).setColor(r, g, b, 255).setUv(1.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose,  s,  s, 0.0f).setColor(r, g, b, 255).setUv(1.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose, -s,  s, 0.0f).setColor(r, g, b, 255).setUv(0.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, 0, 0, 1);
            }
        );
        poseStack.popPose();
    }
}
