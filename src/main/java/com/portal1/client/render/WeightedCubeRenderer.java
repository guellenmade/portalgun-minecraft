package com.portal1.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.portal1.Portal1Mod;
import com.portal1.entity.WeightedCubeEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Renders the 3D Aperture Science Weighted Storage Cube (0.85 x 0.85 x 0.85 blocks).
 */
public final class WeightedCubeRenderer extends EntityRenderer<WeightedCubeEntity, WeightedCubeRenderer.CubeState> {

    private static final Identifier CUBE_TEXTURE = Portal1Mod.id("textures/entity/weighted_cube.png");

    public static final class CubeState extends EntityRenderState {
        public float yaw;
    }

    public WeightedCubeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.45f;
    }

    @Override
    public CubeState createRenderState() {
        return new CubeState();
    }

    @Override
    public void extractRenderState(WeightedCubeEntity entity, CubeState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = entity.getYRot();
    }

    @Override
    public void submit(
        CubeState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        float h = WeightedCubeEntity.CUBE_SIZE * 0.5f;
        poseStack.pushPose();
        poseStack.translate(0.0f, h, 0.0f);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));

        int light = state.lightCoords;
        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entitySolid(CUBE_TEXTURE),
            (pose, consumer) -> emitBox(pose, consumer, h, light)
        );
        poseStack.popPose();
    }

    private static void emitBox(PoseStack.Pose pose, VertexConsumer c, float h, int light) {
        // Front (+Z)
        face(pose, c, -h, -h,  h,  h, -h,  h,  h,  h,  h, -h,  h,  h,  0,  0,  1, light);
        // Back (-Z)
        face(pose, c,  h, -h, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h,  0,  0, -1, light);
        // East (+X)
        face(pose, c,  h, -h,  h,  h, -h, -h,  h,  h, -h,  h,  h,  h,  1,  0,  0, light);
        // West (-X)
        face(pose, c, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h,  h, -h, -1,  0,  0, light);
        // Top (+Y)
        face(pose, c, -h,  h,  h,  h,  h,  h,  h,  h, -h, -h,  h, -h,  0,  1,  0, light);
        // Bottom (-Y)
        face(pose, c, -h, -h, -h,  h, -h, -h,  h, -h,  h, -h, -h,  h,  0, -1,  0, light);
    }

    private static void face(
        PoseStack.Pose pose, VertexConsumer c,
        float x1, float y1, float z1,
        float x2, float y2, float z2,
        float x3, float y3, float z3,
        float x4, float y4, float z4,
        float nx, float ny, float nz,
        int light
    ) {
        int ov = OverlayTexture.NO_OVERLAY;
        c.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(0, 1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(1, 1).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(1, 0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
        c.addVertex(pose, x4, y4, z4).setColor(255, 255, 255, 255).setUv(0, 0).setOverlay(ov).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
