package com.portal1;

import com.portal1.entity.ModEntities;
import com.portal1.item.ModItems;
import com.portal1.world.CarryManager;
import com.portal1.world.PortalNetworking;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Portal1Mod.MOD_ID)
public final class Portal1Mod {
    public static final String MOD_ID = "portal1";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public Portal1Mod(IEventBus modEventBus) {
        ModEntities.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeTabs);
        modEventBus.addListener(PortalNetworking::registerPayloads);

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            com.portal1.world.PlayerVelocityTracker.tickServer(event.getServer());
            CarryManager.tickServer(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            CarryManager.onPlayerDisconnect(event.getEntity().getUUID());
            com.portal1.world.PlayerVelocityTracker.onDisconnect(event.getEntity().getUUID());
        });

        LOGGER.info("Portal 1 Gun mod (NeoForge) initialized for Minecraft 26.2");
    }
}
