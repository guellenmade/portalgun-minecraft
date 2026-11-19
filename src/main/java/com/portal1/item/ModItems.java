package com.portal1.item;

import com.portal1.Portal1Mod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Portal1Mod.MOD_ID);

    public static final DeferredItem<PortalGunItem> PORTAL_GUN = ITEMS.registerItem(
        "portal_gun",
        props -> new PortalGunItem(props, 0),
        props -> props.stacksTo(1).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<PortalGunItem> PORTAL_GUN_EMERALD = ITEMS.registerItem(
        "portal_gun_emerald",
        props -> new PortalGunItem(props, 1),
        props -> props.stacksTo(1).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<PortalGunItem> PORTAL_GUN_GOLD = ITEMS.registerItem(
        "portal_gun_gold",
        props -> new PortalGunItem(props, 2),
        props -> props.stacksTo(1).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<PortalGunItem> PORTAL_GUN_CRIMSON = ITEMS.registerItem(
        "portal_gun_crimson",
        props -> new PortalGunItem(props, 3),
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
            event.accept(PORTAL_GUN_EMERALD);
            event.accept(PORTAL_GUN_GOLD);
            event.accept(PORTAL_GUN_CRIMSON);
            event.accept(WEIGHTED_CUBE);
        }
    }

    private ModItems() {}
}
