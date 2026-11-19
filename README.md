# Portal 1 Gun — Minecraft 26.2 NeoForge Mod

A faithful recreation of the **Portal 1 (Valve)** Aperture Science Handheld Portal Device for **Minecraft 26.2 (NeoForge)**, featuring a custom Blender-modeled 3D Portal Gun, 4 color-coded independent portal pairs, lightweight unloaded-chunk portal persistence, smooth Immersive-Portals-inspired transitions, Sodium compatibility, and multiplayer support.

## Features
- **3D Blender-Modeled Portal Gun** (first-person, third-person, and 3D GUI preview) with 4 color-coded Portal Guns:
  - **Portal Gun (Blue / Orange)** (`portal1:portal_gun`)
  - **Portal Gun (Emerald / Magenta)** (`portal1:portal_gun_emerald`)
  - **Portal Gun (Gold / Violet)** (`portal1:portal_gun_gold`)
  - **Portal Gun (Cyan / Crimson)** (`portal1:portal_gun_crimson`)
- **Visible Traveling Portal Projectiles** that open oval see-through portals on any full solid surface (rejecting glass, slime/honey blocks, crafting tables, and interactive/block-entity blocks).
- **Momentum Preservation & Smooth Exits** ("speedy thing goes in, speedy thing comes out") with true per-tick player velocity tracking, floor-portal funneling, and zero-jitter camera rotation sync.
- **Lightweight Unloaded-Chunk Persistence**: Distant portals stay linked and teleportable at any distance without force-loading chunks or ticking farms/machines.
- **Weighted Storage Cubes** with Portal 1 carry behavior (`R` key, rebindable, no Gravity Gun punt).
- **Multiplayer Isolation**: Each player's portal pairs are independent; any player can traverse any portal into its owner's linked partner portal.
- **Sodium Compatible**: Uses standard `DynamicTexture` + `SubmitNodeCollector` rendering with zero hooks into chunk rendering internals.

## Requirements & Build
- **Minecraft**: `26.2`
- **NeoForge**: `26.2.0.95`
- **JDK**: Java 25+ (`--release 25`)
- **Gradle**: Included wrapper (`./gradlew` / `.\gradlew.bat`, Gradle `9.8.2`)

```bash
./gradlew build
```

The compiled mod JAR is output to `build/libs/portal1-neoforge-1.3.0+26.2.jar`.
