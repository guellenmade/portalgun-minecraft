package com.portal1.core;

/**
 * Portal pair color schemes.
 *
 * Four Portal Gun channels:
 * - Channel 0 (Classic): Blue (#00A2FF) / Orange (#FF7800)
 * - Channel 1 (Emerald): Emerald (#00E676) / Magenta (#FF2E93)
 * - Channel 2 (Gold):    Gold (#FFD000) / Violet (#9B30FF)
 * - Channel 3 (Crimson): Cyan (#00E5FF) / Crimson (#FF2233)
 *
 * Additional palette entries are used in multiplayer so different players using the same
 * Classic gun channel still get visually distinguishable portal colors.
 */
public record PortalColorScheme(int primaryRgb, int secondaryRgb, String name) {

    public static final PortalColorScheme CLASSIC = new PortalColorScheme(0x00A2FF, 0xFF7800, "Classic Blue/Orange");
    public static final PortalColorScheme EMERALD_MAGENTA = new PortalColorScheme(0x00E676, 0xFF2E93, "Emerald/Magenta");
    public static final PortalColorScheme GOLD_VIOLET = new PortalColorScheme(0xFFD000, 0x9B30FF, "Gold/Violet");
    public static final PortalColorScheme CYAN_CRIMSON = new PortalColorScheme(0x00E5FF, 0xFF2233, "Cyan/Crimson");

    private static final PortalColorScheme[] CHANNEL_SCHEMES = new PortalColorScheme[] {
        CLASSIC,
        EMERALD_MAGENTA,
        GOLD_VIOLET,
        CYAN_CRIMSON
    };

    private static final PortalColorScheme[] MULTIPLAYER_PALETTE = new PortalColorScheme[] {
        CLASSIC,
        new PortalColorScheme(0x00E5CC, 0xFF2E93, "Teal/Magenta"),
        new PortalColorScheme(0x5CE600, 0xFF2233, "Lime/Crimson"),
        new PortalColorScheme(0xFFD000, 0x9B30FF, "Gold/Violet"),
        new PortalColorScheme(0x3399FF, 0xFF5E4D, "Sky/Coral"),
        new PortalColorScheme(0x00FF88, 0xFF9900, "Mint/Amber"),
        new PortalColorScheme(0x6666FF, 0xCCFF00, "Indigo/Chartreuse"),
        new PortalColorScheme(0x00CCFF, 0xFF3366, "Aqua/Rose")
    };

    public static PortalColorScheme forChannel(int channel) {
        if (channel < 0 || channel >= CHANNEL_SCHEMES.length) {
            return CLASSIC;
        }
        return CHANNEL_SCHEMES[channel];
    }

    public static PortalColorScheme forIndex(int index) {
        if (index <= 0) {
            return CLASSIC;
        }
        return MULTIPLAYER_PALETTE[Math.floorMod(index, MULTIPLAYER_PALETTE.length)];
    }

    /**
     * Resolves the color scheme for a (channel, playerColorIndex) pair:
     * - Colored Portal Guns (channels 1..3) always use their distinct gun color scheme.
     * - Classic Portal Gun (channel 0) uses classic Portal 1 Blue/Orange for player 0 / singleplayer,
     *   and unique multiplayer colors for additional players.
     */
    public static PortalColorScheme forChannelAndPlayer(int channel, int playerColorIndex) {
        if (channel >= 1 && channel < CHANNEL_SCHEMES.length) {
            return CHANNEL_SCHEMES[channel];
        }
        return forIndex(playerColorIndex);
    }

    public static int paletteSize() {
        return MULTIPLAYER_PALETTE.length;
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
