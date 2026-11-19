package com.portal1.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.portal1.Portal1Mod;
import com.portal1.client.render.PortalEntityRenderer;
import com.portal1.client.render.PortalProjectileRenderer;
import com.portal1.client.render.PortalViewManager;
import com.portal1.client.render.WeightedCubeRenderer;
import com.portal1.entity.ModEntities;
import com.portal1.item.PortalGunItem;
import com.portal1.world.PortalNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = Portal1Mod.MOD_ID, dist = Dist.CLIENT)
public final class Portal1ModClient {

    private static final KeyMapping.Category PORTAL1_CATEGORY =
        KeyMapping.Category.register(Portal1Mod.id("controls"));

    private static KeyMapping useObjectKey;
    private static net.minecraft.world.phys.Vec3 pendingExitVelocity = null;
    private static int pendingExitVelocityTicks = 0;

    public static void setPendingExitVelocity(net.minecraft.world.phys.Vec3 vel) {
        pendingExitVelocity = vel;
        pendingExitVelocityTicks = 2;
    }

    public Portal1ModClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerKeyMappings);

        NeoForge.EVENT_BUS.addListener(this::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onLogout);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PORTAL.get(), PortalEntityRenderer::new);
        event.registerEntityRenderer(ModEntities.PORTAL_PROJECTILE.get(), PortalProjectileRenderer::new);
        event.registerEntityRenderer(ModEntities.WEIGHTED_CUBE.get(), WeightedCubeRenderer::new);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        useObjectKey = new KeyMapping(
            "key.portal1.use_object",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            PORTAL1_CATEGORY
        );
        event.register(useObjectKey);
    }

    private void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof PortalGunItem) {
            event.setCanceled(true);
            event.setSwingHand(false);
            ClientPacketDistributor.sendToServer(new PortalNetworking.FirePortalPayload(false));
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && pendingExitVelocity != null && pendingExitVelocityTicks > 0) {
            if (mc.player.getDeltaMovement().lengthSqr() < pendingExitVelocity.lengthSqr() * 0.5) {
                mc.player.setDeltaMovement(pendingExitVelocity);
            }
            pendingExitVelocityTicks--;
            if (pendingExitVelocityTicks <= 0) {
                pendingExitVelocity = null;
            }
        }
        if (useObjectKey != null) {
            while (useObjectKey.consumeClick()) {
                if (mc.player != null) {
                    ClientPacketDistributor.sendToServer(new PortalNetworking.ToggleCarryPayload());
                }
            }
        }
        PortalViewManager.tickCleanup();
    }

    private void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PortalViewManager.clearAll();
    }
}
