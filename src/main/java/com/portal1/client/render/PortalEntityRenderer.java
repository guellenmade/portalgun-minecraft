package com.portal1.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.portal1.core.PortalBasis;
import com.portal1.entity.PortalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the oval Portal 1 aperture textured with the live see-through view from PortalViewManager.
 */
public final class PortalEntityRenderer extends EntityRenderer<PortalEntity, PortalEntityRenderer.PortalRenderState> {

    public static final class PortalRenderState extends EntityRenderState {
        public PortalBasis basis;
        public Identifier viewTexture;
        public float openScale = 1.0f;
    }

    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(PortalEntity entity) {
        // Generous culling box ensures Sodium's entity frustum culler never clips wide oval portals at steep angles
        return new net.minecraft.world.phys.AABB(entity.position(), entity.position()).inflate(1.5, 1.5, 1.5);
    }

    @Override
    public PortalRenderState createRenderState() {
        return new PortalRenderState();
    }

    @Override
    public void extractRenderState(PortalEntity entity, PortalRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.basis = entity.getPortalBasis();
        state.openScale = entity.getOpenScale(partialTick);
        var player = Minecraft.getInstance().player;
        Vec3 camPos = player != null ? player.getEyePosition(partialTick) : entity.position().add(state.basis.forward().scale(2.0));
        state.viewTexture = PortalViewManager.getOrUpdateViewTexture(entity, camPos);
    }

    @Override
    public void submit(
        PortalRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        CameraRenderState cameraState
    ) {
        if (state.basis == null || state.viewTexture == null) {
            return;
        }
        PortalBasis basis = state.basis;
        float scale = Math.max(0.05f, state.openScale);
        float hw = (float) (PortalBasis.HALF_WIDTH * scale);
        float hh = (float) (PortalBasis.HALF_HEIGHT * scale);

        Vec3 r = basis.right();
        Vec3 u = basis.up();
        Vec3 f = basis.forward();

        // Quad spanning [-hw, +hw] x [-hh, +hh] slightly in front of the wall (+0.025)
        // Alpha mask inside DynamicTexture cleanly clips the quad into a crisp Portal 1 oval!
        float ox = (float) (f.x * 0.025);
        float oy = (float) (f.y * 0.025);
        float oz = (float) (f.z * 0.025);

        float blX = ox + (float) (-r.x * hw - u.x * hh);
        float blY = oy + (float) (-r.y * hw - u.y * hh);
        float blZ = oz + (float) (-r.z * hw - u.z * hh);

        float brX = ox + (float) (r.x * hw - u.x * hh);
        float brY = oy + (float) (r.y * hw - u.y * hh);
        float brZ = oz + (float) (r.z * hw - u.z * hh);

        float trX = ox + (float) (r.x * hw + u.x * hh);
        float trY = oy + (float) (r.y * hw + u.y * hh);
        float trZ = oz + (float) (r.z * hw + u.z * hh);

        float tlX = ox + (float) (-r.x * hw + u.x * hh);
        float tlY = oy + (float) (-r.y * hw + u.y * hh);
        float tlZ = oz + (float) (-r.z * hw + u.z * hh);

        float nx = (float) f.x;
        float ny = (float) f.y;
        float nz = (float) f.z;

        collector.submitCustomGeometry(
            poseStack,
            RenderTypes.entityTranslucentEmissive(state.viewTexture),
            (pose, consumer) -> emitQuad(
                pose, consumer,
                blX, blY, blZ,
                brX, brY, brZ,
                trX, trY, trZ,
                tlX, tlY, tlZ,
                nx, ny, nz
            )
        );
    }

    private static void emitQuad(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        float blX, float blY, float blZ,
        float brX, float brY, float brZ,
        float trX, float trY, float trZ,
        float tlX, float tlY, float tlZ,
        float nx, float ny, float nz
    ) {
        int light = 0x00F000F0;
        int overlay = OverlayTexture.NO_OVERLAY;

        consumer.addVertex(pose, blX, blY, blZ).setColor(255, 255, 255, 255).setUv(0.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, brX, brY, brZ).setColor(255, 255, 255, 255).setUv(1.0f, 1.0f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, trX, trY, trZ).setColor(255, 255, 255, 255).setUv(1.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, tlX, tlY, tlZ).setColor(255, 255, 255, 255).setUv(0.0f, 0.0f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
