# icantpy

Client Fabric mod for Hypixel SkyBlock dungeons on Minecraft **26.1.2** and **26.2**.

Open the menu with `/icantpy` or `/crypt`.

The `icantpy-loader` jar downloads and hot-reloads the payload, so most feature updates do not need a Minecraft restart.

## Features

### Clocks

HUD timers for F7/M7:

- Storm pad, lightning, purple-yellow, and Storm elapsed
- Goldor tick and start
- Necron drop
- Secret-spawn ticks while clearing

Positions, fonts, prefixes, and tick vs seconds are configurable. A HUD editor lets you drag and scale each piece.

### Alerts

When a clock hits a chosen time:

- Flash on-screen text
- Play a vanilla sound, or a custom `.wav` from `config/icantpy/sounds/`

### Waypoints

World boxes that run a chat command when you walk onto them.

- Place at your feet or the block you look at
- Scope to dungeon, hub, island, or garden
- Optional one-shot fire

### Spirit Leap

Replaces the Spirit Leap chest with a class-aware menu (sort, keybinds, scale, Odin-style layout).

Route triggers arm a local center card from:

- Boss lines
- Relics
- Clocks
- Leap chat

The server inventory order stays unchanged. Optional party announce, lock HUD, and boss-death notifier.

### Rename

NEU-style item customizer for held items. Opens from `/icantpy rename`.

- Formatted names (chroma and master-star glyphs)
- Custom tooltip
- Glint on/off/color
- Leather dye

### Stats armor

Fast `/stats` menu for swapping saved helmet, chest, legs, and boots presets.

Cards can show Bonzo Mask, Spirit Mask, and Phoenix Pet cooldown/invuln timers.

### Loadouts

Fast `/loadout` menu for clicking loadout icons without the vanilla chest grid.

### Look

Config chrome:

- Layouts: rail, ribbon, compact
- Themes: Midnight, Phosphor, Paper
- System fonts, accent color, corner radii, menu blur

## License

[GPL-3.0-only](LICENSE). Source must stay available under the same terms if you distribute the mod or a modified version.
