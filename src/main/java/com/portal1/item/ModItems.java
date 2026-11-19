package com.portal1.item;

import com.portal1.Portal1Mod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Portal1Mod.MOD_ID);

    public static final DeferredItem<PortalGunItem> PORTAL_GUN = ITEMS.registerItem(
        "portal_gun",
        PortalGunItem::new,
        props -> props.stacksTo(1).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<WeightedCubeItem> WEIGHTED_CUBE = ITEMS.registerItem(
        "weighted_cube",
        WeightedCubeItem::new,
        props -> props.stacksTo(16).rarity(Rarity.UNCOMMON)
    );

    public static void addCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PORTAL_GUN);
            event.accept(WEIGHTED_CUBE);
        }
    }

    private ModItems() {}
}
