# The Fourth Frequency · 第四频段

**English** · [中文](README.md)

A Fabric survival-horror narrative mod for Minecraft 1.21.11.

You wake at Station Zero — the only one there is — holding an old terminal that gives you real survival information. Nothing is added to frighten you. Instead the blocks, menus, sounds and rules you already know gradually stop meaning what they used to. The terminal is your one stable instrument of explanation, and it is subject to the same thing.

> Current version **RC 1.0.0** (`1.0.0-rc.1`). All gameplay is implemented and frozen; this is the release-candidate stage.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 or a compatible newer release |
| Fabric API | 0.141.4+1.21.11 |
| Java | 21 |

Both client and server need this mod and Fabric API. Licence: [All Rights Reserved](LICENSE).

## Install

1. Obtain `build/libs/thefourthfrequency-1.0.0-rc.1.jar`.
2. Drop it, together with the matching Fabric API, into the `mods` folder of the client and the server.
3. Launch Fabric 1.21.11 on Java 21 and **create a new world**, or join a server.

Build from source:

```powershell
.\gradlew.bat clean build --no-daemon
```

Artefacts land in `build/libs/`. More commands in [Testing and acceptance](docs/en/testing.md).

## What you play

### Mainline and the terminal

On first world entry Station Zero is built on a fixed per-tick budget near the spawn point: the site is chosen for flat, non-flooded ground with no block entities in the footprint, then foundation, floor, walls and roof go up from the bottom. The station lights itself, spawns no hostiles indoors, and holds no loot. The world spawn point is moved to its centre at the same time.

Every player receives exactly one valid personal terminal. Right-click it to open a private screen that does not pause the game and never writes into chat. It is a sealed two-handed instrument — cast-iron body, brass trim, a large CRT on the left, oscilloscope and compass on the right. Opening it moves the camera up to the screen; the device itself never folds open.

| Page | Contents |
|---|---|
| `HOME` | Current server-authoritative objective, tool suggestion, latest record |
| `TOOLS` | Shelter, mineral, portal, weather, navigation, stronghold — unlocked along the mainline |
| `RECORDS` | Story, milestones, file investigation and pursuit warnings; ordinary anomalies are not logged here |
| `FILES` | Four damaged files plus a complete journal that unlocks only once all four are read |

The mainline follows vanilla survival: learn the terminal → wood → iron → the Nether → a fortress → blaze rods → return → eyes of ender → three real throws → stronghold → the End → defeat the World Interface. Task rewards are granted the moment a task completes; there is nothing to claim.

See [Terminal interface and handheld form](docs/en/terminal-ui.md).

### Anomalies and personal pursuits

Nineteen anomalies advance through five sliding stages, moving from environmental error into the interface and rule layers. Three of them are low-intensity *sustained* anomalies that hang on for minutes rather than firing for seconds. The mainline only raises the ceiling; the actual stage rises at most one level at a time.

The Corrector has five personal forms: Sound-Seeker, Router, Interceptor, Trespasser and Interface Corrector. Every form is first demonstrated safely by an anomaly. A real pursuit moves the player into a **private mirror** of their current dimension: terrain is streamed in around the player's chunk while other players, entities, items and the refund ledger stay fully isolated. At most two pursuits run server-wide at once.

> The world is not real. The damage is.

See [Anomalies, terminal forms and personal pursuits](docs/en/anomalies-and-pursuits.md).

### The End finale: World Interface

The last eye of ender only prepares the finale once the player has personally been through at least one pursuit. The fight happens on the **native End main island**; only an 11×11 resonance altar, twenty inert gateways and ten stability anchors are placed, all flush with the terrain.

The ritual freezes 1–8 online non-spectators. Each of them must hold their own bound terminal and insert it by hand; the escrow commits atomically once everyone has submitted. The boss has `600 × (1 + 0.5 × (roster - 1))` maximum health across three forms that only ever advance, against a 12000-tick (10-minute) collapse timer. Stability anchors heal it and slow its attacks while projecting zones that protect both players and terrain — breaking them is a genuine trade, not a "towers first" checklist.

See [The World Interface finale](docs/en/world-interface.md).

### Endings, F8 and saves

Success and failure both return through the vanilla End-poem path, replacing only that run's poem, credits and post-credits text, and both write a local ending lock: from then on the title screen disables Singleplayer, Multiplayer and Realms, while Options and Quit always stay available.

Failure replaces the desktop wallpaper and opens a Notepad file whose text tells the player to press `F8` to undo every local change. `F8` is not an always-on switch — it opens a reset confirmation only when an ending has been recorded. After the restart, that save is isolated by a lossless marker (success reads "sealed", failure reads "corrupted"); `level.dat`, region files and player data are never rewritten. Replaying means a new world.

If a sequence is interrupted, launch once with `-Dthefourthfrequency.safeMode=true` for a safe recovery.

## Safety boundaries

- Horror comes from familiar systems being reinterpreted — **never** from silent save deletion, item theft, faked crashes or unrecoverable desktop changes.
- Actionable information fails **visibly**. It never quietly turns into a wrong but plausible value.
- Page tabs, back, close, the pause menu and safe recovery are never permanently taken over by a sequence.
- Flicker stays under 3 Hz; high contrast and peak volume are bounded by the first-run safety notice and by config.
- Client-side hallucination never contaminates server-authoritative state, and a LAN host's local sequence never affects their guests.

The full account is in the [World bible](docs/en/world-bible.md).

## Config and debugging

First launch writes `config/thefourthfrequency.json`.

| Key | Default | Purpose |
|---|---:|---|
| `meta.enabled` | `true` | Allow the fixed allow-list of local meta sequences |
| `meta.peakVolume` | `0.8` | Peak volume for this mod's sounds, validated to 0–1 |
| `pacing.developerAcceleration` | `false` | Shorten development waits without skipping real interaction |
| `clientState.alphaDowngradeComplete` | `false` | Whether the one-off Alpha downgrade has played |
| `clientState.viewDistanceUnlocked` | `false` | Whether the successful return permanently unlocked view distance |

An operator running `/tff debug true` on themselves can press the rebindable `M` to open the test workbench (overview, mainline, anomalies, files). Every button is re-checked server-side against authorisation, action id, target and current context. `/tff debug false` revokes it.

## Documentation

| Document | Purpose |
|---|---|
| [Documentation index](docs/README.md) | Bilingual index and the maintenance rules |
| [World bible](docs/en/world-bible.md) | Narrative facts and safety boundaries that must not be rewritten |
| [Architecture](docs/en/architecture.md) | Authoritative data flow, persistence, protocol, budgets |
| [Background music](docs/en/audio.md) | Situation table, fade seams, track rotation |
| [Terminal interface and handheld form](docs/en/terminal-ui.md) | Coordinate space, layout, palette, animation, 3D model |
| [Anomalies, terminal forms and personal pursuits](docs/en/anomalies-and-pursuits.md) | Five stages, five forms, the private mirror |
| [The World Interface finale](docs/en/world-interface.md) | Ritual, state machine, eight actions, endings, F8 |
| [Art and asset pipeline](docs/en/art-pipeline.md) | Deterministic generators, UV and emissive contracts |
| [Testing and acceptance](docs/en/testing.md) | Gradle entry points, layered coverage, current evidence |
| [Manual acceptance checklist](docs/en/acceptance.md) | Item-by-item pre-release verification |
| [Repository maintenance](docs/en/maintenance.md) | Directory layout, doc sync rules, release process |
