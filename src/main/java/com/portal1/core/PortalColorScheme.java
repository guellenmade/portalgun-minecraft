package com.portal1.core;

/**
 * Portal pair color scheme.
 * Index 0 is strictly classic Portal 1 Blue & Orange (used in singleplayer and for the first player).
 * Additional players receive distinct, high-contrast color pairs for clear multiplayer attribution.
 */
public record PortalColorScheme(int primaryRgb, int secondaryRgb, String name) {

    public static final PortalColorScheme CLASSIC = new PortalColorScheme(0x00A2FF, 0xFF7800, "Classic Blue/Orange");

    private static final PortalColorScheme[] PALETTE = new PortalColorScheme[] {
        CLASSIC,
        new PortalColorScheme(0x00E5CC, 0xFF2E93, "Cyan/Magenta"),
        new PortalColorScheme(0x5CE600, 0xFF2233, "Lime/Crimson"),
        new PortalColorScheme(0xFFD000, 0x9B30FF, "Gold/Violet"),
        new PortalColorScheme(0x3399FF, 0xFF5E4D, "Sky/Coral"),
        new PortalColorScheme(0x00FF88, 0xFF9900, "Mint/Amber"),
        new PortalColorScheme(0x6666FF, 0xCCFF00, "Indigo/Chartreuse"),
        new PortalColorScheme(0x00CCFF, 0xFF3366, "Aqua/Rose")
    };

    public static PortalColorScheme forIndex(int index) {
        if (index <= 0) {
            return CLASSIC;
        }
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    public static int paletteSize() {
        return PALETTE.length;
    }

    public int colorFor(boolean secondary) {
        return secondary ? secondaryRgb : primaryRgb;
    }

    public float red(boolean secondary) {
        return ((colorFor(secondary) >> 16) & 0xFF) / 255.0f;
    }

    public float green(boolean secondary) {
        return ((colorFor(secondary) >> 8) & 0xFF) / 255.0f;
    }

    public float blue(boolean secondary) {
        return (colorFor(secondary) & 0xFF) / 255.0f;
    }
}
