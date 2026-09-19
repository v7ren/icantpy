# icantpy

Client Fabric mod for Hypixel SkyBlock dungeons on Minecraft **26.1.2** and **26.2**.

Open the menu with `/icantpy` or `/crypt`.

The `icantpy-loader` jar downloads and hot-reloads the payload, so most feature updates do not need a Minecraft restart.

## Install

Put the jar that matches your Minecraft version in `.minecraft/mods/`:

- Minecraft 26.2: [`releases/icantpy 1.0.0 - 26.2.jar`](releases/icantpy%201.0.0%20-%2026.2.jar)
- Minecraft 26.1.2: [`releases/icantpy 1.0.0 - 26.1.2.jar`](releases/icantpy%201.0.0%20-%2026.1.2.jar)

Use only one of those jars. They run as a normal Fabric mod when `icantpy-loader` is not installed. Do not put both version jars in `mods/` at the same time, and do not place a payload jar next to the loader.

## Features

### Clocks

Dungeon phase clocks for F1/F7 and their Master Mode variants:

- F1: Undead, Bonzo, Boss
- F2: First Phase, Second Phase, Boss
- F3: Guardians, Human, Guardian, Boss
- F4: Thorn, Boss
- F5: Livid, Boss
- F6: Terracottas, Giants, Sadan, Boss
- F7: Maxor spawn, Storm, Terminals, S1–S4, Goldor, Necron, Boss

- Storm pad, lightning, purple-yellow, and Storm elapsed
- Goldor tick and start
- Necron drop
- Secret-spawn ticks while clearing

Positions, fonts, prefixes, and tick vs seconds are configurable. A HUD editor lets you drag and scale each piece.

### Alerts

When any dungeon phase clock or mechanic clock hits a chosen time:

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

Leap → Menu includes a configurable target gap and an editor for dragging all five cards.
Large corner click regions have dead zones around the center and between quadrants; enable
Only visible cards for bounded hit areas. Moving cards also switches to their visible bounds.
Right-click in the layout editor resets positions, and Esc returns to configuration.

Optional left-click auto leap opens the real Spirit Leap menu and clicks the captured oriented
target after a configurable delay (100 ms by default). Boss only is enabled by default.
The separate Door opener leap toggle uses the latest named Wither/Blood door opener outside
boss, with the same delay. Anonymous Blood-door messages retain the last named opener.
Both automation toggles are off by default; missing target heads never select another player.
Current loaders intercept left clicks immediately. Older loaders can use the payload tick
fallback, which may miss taps shorter than a client tick and allows the initial vanilla swing.

### Rename

NEU-style item customizer for held items. Opens from `/icantpy rename` or `/icantpy custom`.

- Formatted names (chroma and master-star glyphs)
- Custom tooltip
- Glint on/off/color
- Leather dye (including Hypixel animated UUID-less frames, matched by UUID, SkyBlock id, or name)
- Optional sharing so other icantpy users see your worn cosmetics (`/icantpy share on`, or Cosmetics → Items)

Dyes and models apply to your items only. Other players' armor stays vanilla unless they also run icantpy and publish.

### Morph

Change only your local player model with `/icantpy morph <entity>`, for example `/icantpy morph cat`.
Use `/icantpy morph off` to restore the player model. Other icantpy clients render the same morph;
worn dyes and names still need **Share with other icantpy users**. Armor is shown only for humanoid targets whose
renderer supports armor; non-humanoid targets such as cats never show your armor.

`/icantpy morph camera on` follows the disguise eye height. Cosmetics → Morph → Camera alignment
switches between two saved modes (or `/icantpy morph camera mode crosshair|look`):

- **Shifted crosshair** keeps the camera/model parallel to vanilla facing and projects the crosshair
  and attack indicator onto the actual vanilla target, like the original mob-eye mode.
- **Look-at camera** follows a fixed point four blocks along the player's original vanilla look
  direction, not the hit block. Yaw stays vanilla; only cosmetic pitch compensates for eye height.
  Changing hit-block distance no longer rotates the camera or mob model. The vanilla crosshair
  and attack indicator stay fixed at screen center. This is the default.

Only shifted-crosshair mode projects the actual vanilla target onto the HUD. In look-at mode,
the centered crosshair can differ from the actual vanilla target/block outline at other distances.
Neither changes real player yaw/pitch, picking, reach, hitboxes, movement, or gameplay packets.
Changing camera-only settings does not publish appearance updates. Look-at requires loader `1.0.3.30`
or newer; if already installed, use `/icantpy reload` without replacing it or restarting.
Older loaders with the HUD hook can use shifted-crosshair mode. Selecting look-at keeps the
crosshair centered even without the camera-look hook; loaders without the HUD hook keep vanilla
first-person height. The selected mode remains saved for a future loader upgrade.
Client-side rendering does not imply Hypixel permission; altered eye height can expose normally hidden
views. Do not treat this mode as Hypixel-approved or ban-safe.

### Stats armor

Fast `/stats` menu for swapping saved helmet, chest, legs, and boots presets.

Cards can show Bonzo Mask, Spirit Mask, and Phoenix Pet cooldown/invuln timers.

### Loadouts

Fast `/loadout` menu for clicking loadout icons without the vanilla chest grid.

### Attribute shards

`/icantpy shards` opens QoL → Shards in the main icantpy GUI, using your chosen layout, theme and fonts. Search by shard or ability, switch between all and missing entries, scroll through the list, and use Bazaar on a row to buy that shard. The page also links to `/am` Advanced and `/huntingbox`, and converts pasted bullet lists into an importable checklist.

The default checklist contains all 45 bundled entries. Open `/am` Advanced, then manually visit every page, and visit all `/huntingbox` pages to scan. Scan progress is session-only and resets on disconnect/profile change; an unknown level (`?`) means the level is unread, while box counts may still be known.

Import accepts direct JSON, Base64 export, or user bullet lists. For bullet lists, the first parenthetical mob name is preferred as the shard name; otherwise the ability or name is used. Targets must be 1–10 and default to 10. Base64 exports are UTF-8 JSON and can be shared directly. For example:

```json
{ "version": 1, "name": "Blaze Slayer", "shards": [{ "name": "Flash", "targetLevel": 10 }] }
```

The offline catalog is bundled from [NEU's attribute shard data](https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/attribute_shards.json).

### Look

Config chrome:

- Layouts: rail, ribbon, compact
- Themes: Midnight, Phosphor, Paper
- System fonts, accent color, corner radii, menu blur

## Acknowledgements

icantpy is its own client, but several features began as ports of other SkyBlock mods. Those UIs and behaviours were rebuilt for Minecraft 26.1.2 and 26.2, then fixed or extended in this project. The original authors are not affiliated with icantpy.

- **[Skyblocker](https://github.com/SkyblockerMod/Skyblocker)** — the tabbed item customizer (`/icantpy custom`) follows Skyblocker's custom-item screen and was reworked for the current game versions.
- **[NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates)** — the rename editor follows NEU's item customizer, ported and corrected for modern Minecraft. Some original NEU textures remain under LGPL-3.0; see the bundled notice with those assets.
- **[SkyHanni](https://github.com/hannibal002/SkyHanni)** — special thanks for SkyBlock data that icantpy relies on.

Dungeon clocks and the leap menu also draw on ideas from [Odin](https://github.com/odtheking/Odin). Please support the original mods if you use their standalone clients.

## License

[GPL-3.0-only](LICENSE). Source must stay available under the same terms if you distribute the mod or a modified version.

Bundled NEU item-customizer textures remain under LGPL-3.0; see the notice shipped with those assets.
