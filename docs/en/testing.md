# Testing and acceptance

The test entry points, layered coverage, key invariants and **the evidence actually produced this round** for `1.0.0-rc.1`. Only results that really finished are listed as current evidence; a successful compile is not acceptance.

Release steps and sync rules are in [Repository maintenance](maintenance.md).

## Fixed environment

| Item | Current value |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.4+1.21.11 |
| Loom | 1.17.14 |
| Gradle Wrapper | 9.5.1 |
| Java toolchain | 21 |

## Common commands

```powershell
# Compile and process resources
.\gradlew.bat compileJava compileClientJava processResources --no-daemon

# Aggregate pure JUnit / resource contracts. The classpath reuses the remapped Minecraft runtime
# Loom configures for the test source set, and every compiled test class is enumerated with
# --select-class rather than --scan-class-path, so a test class whose method signatures reference
# Minecraft types can no longer be silently skipped. XML goes to build/test-results/unit
.\gradlew.bat unitTest --no-daemon

# check and build both depend on unitTest above, so it works as an all-green gate
.\gradlew.bat check --no-daemon

# Clean build; the task graph runs unitTest and then the server GameTests
.\gradlew.bat clean build --no-daemon

# Server GameTests
.\gradlew.bat runGameTest --no-daemon

# The currently publishable remapped JAR
.\gradlew.bat remapJar --no-daemon

# Client GameTests, default all
.\gradlew.bat runClientGameTest --no-daemon

# Targeted World Interface suite
.\gradlew.bat runClientGameTest -PtffClientTestSuite=world-interface --no-daemon
```

`build`'s last step runs only after every preceding compile and test succeeds: it copies the runnable JAR produced by `remapJar` into a local instance's `mods` folder. The default path is in `build.gradle`; override it with `-PtffDeployDir=<path>` or disable it with `-PtffDeployDir=`, and a missing drive skips the step instead of failing the build. It does not copy the sources JAR and does not delete other mods; running `remapJar` alone only produces `build/libs/` artefacts and triggers no deployment.

Permitted client suite IDs: `all`, `default`, `mainline`, `tools-ui`, `notice-entry`, `alpha-relaunch`, `anomalies`, `anomaly-meta-smoke`, `rework-forms`, `watcher-model`, `world-interface`, `terminal-3d`, `screen-filters`. `all` covers the mainline, tools UI, anomalies, the Corrector, the Watcher model, the World Interface and the handheld terminal; the notice/relaunch suites still run separately. Only the `anomalies` suite accepts an additional `-PtffAnomaly=<id>`.

### `terminal-3d`: the handheld terminal

`TerminalHandheldClientGameTest`, split out of `M0ClientGameTest` because that suite is a mainline test and only ever glimpses the device once, in one save, on one page, at one facing — and **every problem this device has ever had was outside that view**: both extremes of the view angle, an occupied off-hand, and a camera one frame behind.

It builds a small platform under open sky (indoor walls and ceilings are exactly the occlusion to isolate away), then:

| Check | Form |
|---|---|
| Idle two-handed carry, hand and device at their own depths | Assertion + screenshot |
| **Turning does not move the device** | Assertion (pose z / scale / handSpread must be bit-identical across four view angles) + four screenshots |
| Lift, FOV narrowing, squaring up, settling back, field of view restored | Assertion + screenshot |
| Off-hand occupied falls back to one hand, off-hand item still drawn | Screenshot |
| The six forms one by one | Screenshot |

The turning check is the reason this suite exists and its only real regression barrier. Most of the rest is "evidence for a human" — the frame buffer cannot be read, so "the device is centred" cannot be written as an assertion.

The device must be **the one Station Zero actually issued**: the server validates binding, owner and world id before opening, and a freshly constructed `ItemStack` is rejected outright. The six-form step also copies the real item and edits `custom_model_data` rather than building another one.

`alpha-relaunch` writes a minimal "the first launch already completed" persistence fixture into the cleared test run directory, then starts the client to verify a second launch. It neither reads nor modifies the player's normal `run/client` config.

## Verification layers

| Layer | Coverage focus |
| --- | --- |
| Filtered pure-logic tests | Anomaly pool/pacing, five-form policy, terminal appearance, dynamic chunk window, no-form-skipping |
| Aggregate JUnit / resource contracts | Schema, payload versions, resource keys, data tables, migration, policy formulas, recovery rules |
| Server GameTests | World events, objective advancement, multiplayer authoritative state, block/entity interaction, mirror topology, persistence |
| Client GameTests | Terminal UI, notice/relaunch, anomaly presentation, models, the World Interface, the poem and view distance |
| Manual acceptance | Audiovisual safety, multiplayer feedback, window/desktop sequences, the LAN host experience, the replay flow |

## Current evidence

The following actually completed on **2026-08-08** in the current workspace at `mod_version=1.0.0-rc.1`.

| Verification | Result | Boundary |
| --- | --- | --- |
| `compileJava` / `compileClientJava` / `processResources` | Pass | All four source sets compile |
| Aggregate `unitTest` | **519/519 pass**, 99 containers, 0 failed, 0 skipped | Every compiled test class enumerated explicitly via `--select-class` |
| Server GameTests | **64/64 pass** (batch 0: 50, batch 1: 14) | `All 64 required tests passed` |
| Chinese/English language JSON | **773** keys each, parses, key sets fully symmetric | Current resource tree |
| Full (`all`) client GameTests | **Not run this round** | A targeted smoke test cannot replace the unfiltered client suite |
| Release remapped JAR | **Not built this round** | The RC artefact must be regenerated and recorded per the section below |

The per-change re-run log has been moved out of this document and kept only in the local archive at `archive/superseded-docs/testing-history.md`.

## Key personal-pursuit invariants

- The mainline only raises `allowedForm`; `actualForm` advances at most one step per success, and pending pursuits never form a queue.
- The five form durations are 60/75/85/95/110 seconds, the success interval is 20–30 minutes, and the retry after capture/interruption is 5 minutes.
- Every form must first complete a safe demonstration. Once safety conditions pass, a fixed 200-tick lead-in runs: 80 ticks of terminal reading, 80 ticks of progressive frame-rate decay only, 40 ticks of input lock and hang audio. The lead-in draws no filter or interference overlay.
- The hang audio is a random variant pool rather than a single file: `alpha_corruption_collapse` and `alpha_corruption_warning` have at least 3 each, and `ResourceContractTest` asserts the minimum count, no duplicate entries, that all are real Ogg files and that each is over 16 KB. How the variants *sound* is manual acceptance only; a test cannot stop "it sounds wrong".
- The black screen is switched only by server timing. Entry waits for 7×7 chunks around the player in the destination dimension to be ready and stable for 8 consecutive ticks, up to 200 ticks. From mirror copying through the return to the source world and the loading screen disappearing, loading screens must remain covered.
- At most two concurrent pursuits server-wide; two players use different mirror slots and a third is safely deferred.
- The initial snapshot is 5×5 chunks, ±48 blocks vertically, 8192 blocks per session per tick; it streams horizontally with the player's chunk, with no fixed 30-block turnback.
- Copied chunks are never overwritten; when copying falls behind, the player only pauses at the nearest safe position with the pursuit timer paused.
- The initial spawn probe covers the full ring 25–42 blocks around the player, including directly ahead. 42 blocks for 5 seconds, breaking line of sight beyond 18 blocks for 8 seconds, and the player killing it personally are all authoritative successes.
- A real pursuit has no boss bar; only a red "attempt to escape" is pinned, other prompts are locked until the full return, the client uses a black-and-white low-bit-depth mosaic filter, the heartbeat plays positionally at the Corrector's coordinates (with stereo bearing and distance falloff), and the player has icon-free night vision throughout.
- During a pursuit session, the pause menu's save-and-quit / disconnect buttons must be disabled with replacement copy, restored once the session is fully cleared.
- The capture freeze/fault audio and the green success resolution are both fixed at 60 ticks; capture removes 2 points of maximum health, success adds 2, and a technical interruption penalises nothing.
- After surviving, escaping, killing it or completing a debug pursuit, the temporary warning is deleted; the "the magnetic field around the user is very unstable..." record is written when the return completes.
- The pursuing Corrector uses fast breaching, support demolition and vertical leaps; the player can still kill it normally. Meeting the cave test drops it to 0.25 base speed and a 1.04 path multiplier, restoring to 0.31 and 1.32 on leaving.
- Mirror destruction drops nothing; temporary-placement refunds are idempotent; disconnect/restart never leaves a player in the mirror or swallows items. The cross-dimension return restores visibility first; if death or an admin teleport already removed the player from the mirror, the session is only cleaned up and the player is not dragged back to the entry point.
- Mirror dimensions must never contaminate the mainline, anomalies, navigation, Nether round trips, End entry or finale state.

## Key World Interface invariants

- The roster is 1–8 online non-spectators, withdrawal is possible before submission, and a failed submission must return terminals.
- Health is `600 × (1 + 0.5 × (roster - 1))` — 600 for one, 2700 for eight; the three forms only advance.
- Collapse is 12000 ticks (10 minutes); it pauses when everyone is offline; a same-tick timeout outranks lethal damage.
- The ten stability anchors affect healing, damage taken, cooldown and the radius-8 stability zone by the current formulas, but affect neither boss movement nor collapse progress; the zone protects player damage and terrain together. The first positive-damage player attack breaks an anchor immediately, and each break plays about 60 ticks of golden wash on the HUD with "reconstruction↓ / activity↑".
- Anchors are carried by `StabilityAnchorEntity`. `StabilityAnchorGeometryTest` locks the pure geometry and timing rules (model height 44 units = 2.75-block collision height, width 28 units = 1.75 blocks and never over 2, the relay core uniformly 2 blocks above the origin, four seamless irreversible collapse phases, per-tick and whole-fight particle caps); `StabilityAnchorContractTest` locks the cross-layer contract (entity/model layer/renderer registration, four five-stage claw chains with an open emitter end, texture size and sparse emissive mask, bilingual keys, beam endpoints from shared constants that follow the boss's 3D position, migratable legacy tagged crystals, and a collapse effect free of explosions, block writes and drops). `ResourceContractTest` additionally confirms `EndCrystalMixin` is gone from both the manifest and the source, so ordinary end crystals are back to vanilla behaviour.
- Server runtime is covered by `WorldInterfaceGameTests`: the arena creates exactly 10 anchors with unique indices and deterministic UUIDs, a missing live anchor is recoverable, a destroyed anchor never revives, zero-damage / non-player / spectator / non-combat-phase sources cannot break one, and breaking one updates damage taken and the stability zone on the same tick without changing the fixed collapse timer.
- The eight actions' telegraphs, exact damage, caps and exclusive control stay stable. Only the laser, breath bolt, sky lance, grab-and-throw and tendril lash are targeting locks; weapon impound and the hotbar sweep use unavoidable deprivation notices. Only impounded weapons enter the recovery ledger; hotbar items stay ordinary world drops.
- The drawn body's lower edge hangs at a fixed 8 / 14 / 18 blocks, and the entity origin must always be above ground; clearance is measured against the model's real lowest shell and must not be derived from core height and half-width.
- The central skull must clear the floor **throughout the animation**: `theCentreHeadClearsTheFloorThroughTheWholeAnimation` sweeps 21 health steps × 4 gaze steps × 241 ticks, measuring the **underside of the jaw** rather than the skull hitbox — the jaw hangs below the box, so measuring only the box would miss the part that really enters the ground. The current margin is 1.69 / 1.12 / 0.72 blocks, deliberately what remains after spending the raise, and lowering it requires measuring first.
- The skyhold window for forms 2 and 3 must be **strictly under 40%**: `WorldInterfaceSkyholdPolicyTest` measures the raised proportion tick by tick over the whole collapse timer (rather than dividing two constants), and asserts form 1 never rises, a phase's tick 0 is always at the station, the lift curve is continuous (single-tick displacement < 1.5 blocks), and there is exactly one climb cue per cycle.
- Head gaze must never knot the three neck chains: `theHeadsNeverKnotHoweverTheyAreLooking` asserts the same non-intersection contract as the rest pose across three forms × six actions × 13 yaw steps × 9 pitch steps × 4 moments. Gaze yaw lands mainly on the skull's and `neck_b`'s **own-axis** rotations (rotating about the hanging direction moves no downstream joint); only the 0.20-coefficient roll on `neck_b` really moves a skull.
- Explosion camera shake is broadcast by the server as `WorldInterfaceBlastS2C` with falloff computed on the client. The per-source throttle rule is the pure function `WorldInterfaceBlastService.permits`, covered by `WorldInterfaceBlastServiceTest` (event cap for one laser sweep, a backwards clock must not mute a source permanently, radius and tier envelope boundaries).
- Attackable parts number 14 / 16 / 20: the body, three heads, two segments per neck, one segment per drawn tendril.
- Hitboxes are bound to the **post-animation** rig: `WorldInterfaceRig` poses the skeleton once per tick (bind pose + clips + procedural drift + structural sag), the server places boxes from it and the client drives `ModelPart` from the same evaluation, with clip data in common's `WorldInterfaceClips`. The central skull's hitbox bottom sits about 1.44 / 0.78 / 0.43 blocks above the ground, well inside one swing (4.5 blocks); the measurement uses `headHitRadius` (including `HEAD_HIT_SLACK`'s 45% margin), i.e. the volume a player can really hit, not the bare skull. The box is anchored on the **jaw** rather than the cranium cuboid's centre, or the bottom edge sits nearly a block above the visible jaw. Heads must land **directly in front of** the storm (model -Z maps to entity forward).
- The three neck chains must not intersect in any form or action sequence: `WorldInterfaceRigTest` asserts a strict form for the rest shape (the sum of the two skull radii) and a loose form across the whole animation (never inside each other), with yaw and roll always signed "outward is positive" by `WorldInterfaceAnatomy`.
- Arrows and tridents resolve at 2.5×, with the multiplier applied before the anchor damage coefficient; the two-argument `adjustedIncomingDamage` remains melee semantics and must not quietly gain the bonus.
- A fixed-range sound's registered radius and its `attenuation_distance` in `sounds.json` must match; variable-range events must not declare `attenuation_distance`.
- Multi-track music events (`music_game`, `music_menu`) must not play the same track twice in a row; `MusicRotationPolicy.rotatingEvents()` and the `music_*` events with a pool size over 1 in `sounds.json` must agree in both directions, and single-track events must not rotate.
- The permanent scar budget is 8192 blocks total and 32 per tick, and must not destroy protected structures or block entities.
- Success timing is fixed at: body 0–180 ticks, empty field 180–220, summon 220–340, dragon appears at 340, exit opening 340–500; the first line at 410, the second line and the exit both at 500.
- Resolution opens a 3×3 exit and returns through the vanilla WinScreen/respawn path; only the World Interface branch replaces the poem, credits and `postcredits_*.txt`.
- View distance unlocks permanently to 16 only after the success poem is confirmed and the player is really back in the Overworld.
- A successful ending releases the ending score before restoring resource packs; both endings keep the pause-menu exit path.
- Failing players each see missing textures on their own client, and the LAN host branch must not contaminate the server or its guests; a failure relaunch keeps the Alpha presentation until F8 recovery completes.
- After resolution, ordinary anomalies, gap pressure, decay and pursuits close permanently.
- F8 only handles an ending lock that already exists; save isolation uses a lossless marker only, reading "sealed" on success and "corrupted" on failure.

## Documentation and resource static checks

- Both language JSONs parse, with 773 keys each and fully symmetric key sets.
- All relative links in the READMEs (both languages) and `docs/**` resolve.
- Scans for stale test numbers, the old health formula and retired finale semantics find no residue.

The manual flow for a candidate build is in the [Manual acceptance checklist](acceptance.md).

## Release artefacts

The RC 1.0.0 artefacts **have not been produced yet**. Rebuild them at release time and fill the actual results back into this table:

```powershell
.\gradlew.bat clean build --no-daemon
```

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `build/libs/thefourthfrequency-1.0.0-rc.1.jar` | TBD | TBD |
| `build/libs/thefourthfrequency-1.0.0-rc.1-sources.jar` | TBD | TBD |

Only record a clean build completed **after the last production source/resource change**; an older JAR is not release evidence.

## Still outstanding before release

- Re-run the unfiltered (`all`) client GameTests.
- Verify concurrent different forms, disconnect/reconnect, full-inventory refunds and dynamic chunk catch-up with two real clients.
- Check mixing, strong flicker, multi-monitor/DPI, the LAN host branch and long-session TPS on the target hardware.
- Complete every manual item in the [Manual acceptance checklist](acceptance.md), especially one full multiplayer World Interface fight each at 2 and 4 players.
