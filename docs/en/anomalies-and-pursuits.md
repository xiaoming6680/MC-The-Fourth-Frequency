# Anomalies, terminal forms and personal pursuits

The anomaly tiers, personal Corrector pursuits, mirror dimensions and terminal appearance rules in `1.0.0-rc.1`. Numbers are owned by `AnomalyIntensity`, `AnomalyCatalog`, `PursuitProgressPolicy`, `PursuitFormPolicy` and `PursuitSnapshotBuilder`.

## Overall structure

Three personal progressions constrain each other without substituting for each other:

- **Mainline permission** sets how far anomalies and the Corrector may advance.
- **Anomaly tier** rises through ordinary online exposure and successfully completed anomalies.
- **Resolved pursuits** determine the Corrector form actually faced next.

A player who clears several mainline thresholds early only ever holds one pending pursuit; no queue of debts forms. The anomaly tier likewise rises at most one level at a time.

**After the finale resolves, all background pressure closes permanently.** Once the World Interface enters success or failure resolution (including the exit and complete phases that follow), ordinary anomalies, gap pressure, decay and pursuits never reopen; any pending pursuit is cleared in place and the terminal projection is synced. The `pursuit_test` debug button goes through the same gate and cannot bypass it. That way a player walking out of the exit into the Overworld does not receive an epilogue nobody wrote.

Pursuits also keep a separate **experienced** count: both capture and escape write to it, and only a technical interruption does not. Its only use is unlocking the last eye of ender for the End finale; form advancement still counts resolved pursuits only. They are separate because capture already costs a heart of maximum health — if it also locked the finale, a player who keeps being caught would be permanently shut out of the ending. This threshold means *you must have faced it*, not *you must have beaten it*.

## Five anomaly tiers

The "highest tier threshold" only raises the ceiling; it never jumps a level immediately. Except for tier 0 → 1, each promotion also requires at least 20 minutes of qualifying online time accumulated in the current tier and at least 2 successfully completed anomalies. When the actual tier lags the ceiling by two levels or more, the exposure requirement halves to 10 minutes, so a player racing the mainline does not miss a whole band of higher-tier content while climbing at a fixed rate.

Qualifying online time requires the player to be alive, not spectating, not sleeping, not in the terminal, and not in a gap or a real pursuit. It does not require a fixed home.

The first Nether entry and the first recorded eye-of-ender bearing each schedule a **signature anomaly**: the next anomaly is pulled forward to roughly 30 seconds and the pool is picked against the *mainline ceiling* rather than the current actual tier, preferring content this player has never seen. Those two moments are exactly when the world loses its vocabulary, and unseen higher-tier content should appear once there rather than being reserved for players who grind out every exposure requirement. A signature survives a failed trigger and is consumed by the next successful one; it changes neither the actual tier, the success count, nor the existing strong-interface cooldown.

| Tier | Ceiling threshold | Actual candidate pool | Ordinary interval |
| ---: | --- | --- | --- |
| 1 | Terminal bound | Tier 1 | First 4–7 min; then 8–14 min |
| 2 | Terminal band advanced, any activity proof, or 20 min of qualifying activity | Tier 1 + 2 | 8–13 min |
| 3 | Iron obtained, preparing for or entering the Nether | Tier 2 + 3 | 7–12 min |
| 4 | Blaze rods obtained and returned to the Overworld | Tier 3 + 4 | 6–10 min |
| 5 | Any eye-of-ender bearing recorded, stronghold found, or finale pressure begun | Tier 4 + 5, plus experience gap and local rule collapse | 5–9 min |

### The nineteen anomalies

Three are marked **sustained**: they hang on for minutes at an intensity low enough to be doubted, instead of firing for seconds and letting the world snap back. With only short events, the mod is quiet for roughly 98% of a session and unease has nowhere to live.

**Every anomaly opens with the same cue: the vanilla cave ambience `ambient.cave`, played positionally in the world.** Using a vanilla sound is deliberate — a synthesised cue announces *the mod is doing something*, whereas the cave sound announces *there is something here you did not place*, and it does that before the player consciously identifies what they just heard. Playing it positionally rather than at the ear gives it a bearing: the anomaly came from somewhere, and turning to look is something the player can do. Anchored anomalies sound from the anchor (that is where it is happening anyway); unanchored ones derive a bearing from their own seed and play 9 blocks from the player, so the bearing is stable for the whole instance and identical on every client rather than re-rolled per query. Red horizon's original tuning-sweep intro was removed — two opening cues on the same frame is one too many.

| First tier | Name | Scope | Current behaviour |
| ---: | --- | --- | --- |
| 1 | False echo | Personal | Footsteps or mining sounds nearby that are not there |
| 1 | Light failure | Shared | Ordinary block light sources within 16 blocks go out server-side at once and return when the anomaly ends; only enabled at night (13000–23000), and unrestricted in dimensions without a day cycle |
| 1 | Surface fracture | Personal | False cracks with mining sounds appear on a wall or floor |
| 1 | Silent world | Personal, sustained | Ambience, weather and every creature's sound disappear together; only the player's own actions still answer. Lasts 2–3 minutes. The signal bed runs on the MASTER channel and is not silenced with them |
| 2 | Peripheral residue | Personal | Cold hands reach further in toward the centre from both sides of the screen; both vanish in sync when the flash hits |
| 2 | Observer alignment | Shared | Nearby animals all turn their heads toward the player |
| 2 | Gaze in the dark | Shared | Briefly existing glowing eyes spawn in darkness |
| 2 | Behaviour replay | Personal | The player's actions from seconds ago reappear |
| 2 | Organ misread | Personal | The terminal renders inventory items as eyes and rewrites their names |
| 2 | Temporal drift | Personal, sustained | Celestial position decouples from the local clock; lighting, spawning and rules still run on real time, so the discrepancy is only in the sky. The terminal weather tool's celestial-phase channel reports it while the day/night countdown stays correct — here the terminal is right and the sky is wrong. Lasts 3–5 minutes |
| 3 | Viewpoint separation | Personal | The camera stays where it is while the body can still move |
| 3 | Door cascade | Shared, destructive | Multiple doors within about 20 blocks are really broken, far to near |
| 3 | Experience gap | Shared | The player is moved along a server-validated safe route during a black screen |
| 3 | Local rule collapse | Personal | Nearby exposed blocks briefly show as missing textures, in scattered fragments |
| 3 | Metric drift | Personal, sustained | Distances and coordinates the terminal reports drift continuously, while navigation and arrival checks still use the real position — what bent is the terminal's account, not the world. Lasts 2.5–4 minutes |
| 4 | Red horizon | Personal | Sky, horizon and fog all turn red. The weather tool's horizon channel climbs before the colour is obvious, after which that page shows scan lines, tear rows, glitch and a self-corrupting refresh in turn |
| 4 | Window pulse | Personal, strong interface | The game window scales and flickers rapidly |
| 5 | Channel takeover | Personal, strong interface | The vanilla chat bar types by itself in the first person |
| 5 | Desktop presence | Personal, strong interface | The game minimises and a controlled Notepad types built-in text character by character |

"Shared" means the effect changes entities, blocks or positions in the server world, so nearby players may observe the result. Tier, history, cooldowns and personal client presentation are still stored per player.

The tier-5 pool is `tier >= 4` plus experience gap and local rule collapse, so temporal drift and metric drift never appear in the final stretch of the mainline; they belong to the stages where the world is still trying to stay legible.

### Two permanent sequences that are not in the catalogue

They occupy no anomaly slot, write no anomaly history and are not bound by the strong-interface cooldown, so they are not among the nineteen:

- **The Watcher** appears naturally only while the terminal band has not advanced (`BAND_STAGE == 0`) — the very opening of the story. Attempts begin 2400 ticks after the terminal is issued, then every 2800–6400 ticks, and it must be night (`dayTime >= 12500` or `<= 1000`) or underground (no sky view and local light ≤ 7). It stands 18–32 blocks away with its torso turned 115° but its head already turned back, and lives 900 ticks. Only one exists near a given player at a time.
- **HIM** appears in a direction the player **has not looked at**: 95°–180° off their line of sight, 22–44 blocks away (× 1.6 in daylight, i.e. 35–70 blocks — at twenty-two blocks in daylight a crisply rendered humanoid reads as a spawned mob rather than as something that was already there), on terrain with relief (at least 5 blocks of height difference within the sample ring) or in an enclosed spot. Attempted every 6000–15000 ticks, alive for 600 ticks, one at a time. It **always faces the player** — aiming it once at spawn is not enough, because a player circling to the side would read it as an abandoned statue. Only facing moves; position, gravity and velocity stay pinned. The placement rule *is* the anomaly: it just stands there and then vanishes, and whether that reads as a sighting or a spawn is entirely a function of where it is standing when the player turns around. It vanishes 4 ticks after being seen, and also when the player comes within 4 blocks.

Neither is in the anomaly catalogue, so neither has a list row to click. The toolbar at the top of the debug panel's "anomalies" page is therefore their only manual entry point: currently only `him_spawn` ("spawn HIM") is wired to a button; the server's `watcher_spawn` still exists but has no button and relies on natural triggering. Manual spawning goes through the same `HimService.debugSpawn` as the natural path, so **the placement rules are not bypassed** — the panel refuses when the conditions are not met rather than dropping the figure in front of the player.

### Scheduling and anti-repetition

- At most one ordinary anomaly runs per player at a time.
- The 3 most recently completed anomalies are excluded from candidates; falling back is only allowed when there is nothing else.
- Anomalies newly added at the current tier are weighted 3× against retained ones.
- Window pulse, channel takeover and desktop presence share a 20–30 minute strong-interface cooldown.
- Logging in schedules the next ordinary anomaly 3 minutes out; a real dimension change changes that to 90 seconds.
- If the time arrives while the player is sleeping, in the terminal, in a gap or in another anomaly, the next check is deferred 60 seconds.
- Ordinary anomalies are paused during real pursuits and in mirror dimensions; after a successful pursuit, the next one is scheduled about 6 minutes 30 seconds from the moment of success.
- With `pacing.developerAcceleration=true` the first/subsequent intervals shorten to 5/10 seconds. Regression testing only.

## Five personal pursuit forms

### Permission, actual form and pending pursuit

| Form | Permission threshold | Duration | Core counterplay |
| ---: | --- | ---: | --- |
| 1 Sound-Seeker | Terminal bound and at least one anomaly completed, plus either any proof of mining/exploration/loot/building/trading or 20 minutes of qualifying activity | 60 s | Stop forming a rhythm; sneak and break line of sight |
| 2 Router | Entered the Nether | 75 s | Do not repeat a route; use corners and multiple exits |
| 3 Interceptor | Blaze rods obtained and returned to the Overworld | 85 s | Recognise the predicted route; double back, change direction or change height |
| 4 Trespasser | Three real eye-of-ender bearings recorded | 95 s | Read the glow/sound wind-up and cut away at the end of the lunge |
| 5 Interface Corrector | Stronghold found | 110 s | Ignore contradictory text, coordinates and directions; use the heartbeat only for distance |

`allowedForm` is the highest form the mainline permits; `actualForm` always equals "resolved pursuits + 1", capped at 5. A real trigger requires `actualForm <= allowedForm`.

Every form follows the same teaching chain:

```text
Safe demonstration inside a successful anomaly
→ Wait for a safe environment and a free mirror slot
→ Terminal writes an anomalous-signal warning 10 seconds ahead
→ Terminal vibration, progressive frame-rate decay, 2-second freeze
→ Black screen hides the load and the real pursuit begins
→ Archived on success / pending kept and retried after capture
```

Success advances the actual form by one step, and the next real pursuit waits 20–30 minutes. Capture, disconnection or a technical interruption does not increase the resolved count; the pending state is kept and retried 5 minutes after returning to reality.

### Entry sequence and screen feedback

Once the safety window is confirmed and a mirror slot is taken, the server immediately appends an unread record to the terminal: a green "anomalous signal fluctuation detected, approaching.." followed by a red "prepare yourself...". The action bar shows only "the terminal is vibrating violently". Opening the terminal at this point jumps to the records page. Both the real path and the five-tier test entries in the debug workbench start here.

The full lead-in is fixed at 10 seconds: the first 4 seconds are for reading the terminal record; the next 4 lower the presentation frame rate programmatically from about 60 FPS to about 4 FPS with no filter, vignette, signal band or other screen overlay layered on; the final 2 seconds freeze the picture, lock movement input and repeat a machine-hang fault sound.

That fault sound is not a single file: `alpha_corruption_collapse` and `alpha_corruption_warning` have 3 variants each, drawn at random by the sound engine on every play, all generated procedurally by `tools/generate_alpha_corruption_audio.py`. It replays every 5 ticks during the freeze and again for 3 seconds at capture resolution, so a player might hear it a dozen times in one evening; with a single sample it would be recognised, and a recognised sound has already been filed under "sequence". The three collapse variants are three different ways of hanging (audio buffer lock-up, a high-frequency whine from a driver deadlock, bit depth decaying ring by ring into a square wave), not randomisations of the same clip. Variants of one event are level-matched by RMS rather than by peak, so which one is drawn never changes how loud this moment is.

There was once a fourth collapse variant — clipping into the rails and grinding down — that measured fine and was cut after listening: it sounded like the signal being destroyed rather than the device hanging, and this cue has to say the latter. A technical contract test cannot stop "it sounds wrong", so any future clipped-square-wave variant must be auditioned first.

After the freeze the screen goes solid black, and only then does the server begin copying the initial 5×5 mirror window. The 10-second warning itself does not enable the black loading cover; cross-dimension `LevelLoadingScreen` and resource `LoadingOverlay` are always covered by it. Both entering the mirror and returning to reality must wait until **the destination chunks are actually in hand** before lifting the black screen and restoring rendering and input: first the loading screen must be gone and the dimension correct, then the client must hold the chunks within a 3-chunk radius (7×7) of the player, and only then does the count of consecutive stable ticks (8) begin. The previous rule waited only for the loading screen plus 2 ticks — and the loading screen is removed when vanilla considers the world entered, long before the surrounding terrain arrives, so the black screen lifted after about a hundred milliseconds and the player watched the world assemble itself, which is the exact thing this transition exists to prevent. Having the chunks is not the same as having rendered them, so the stable-tick count went from 2 to 8 to cover section compilation. There is a hard 200-tick (10-second) timeout overall: a stalled or rate-limited chunk stream must never leave a player in the black permanently — a brief pop-in is a blemish, a black screen you cannot leave is a broken save.

During a real pursuit the client runs a **black-and-white, low-bit-depth digital-corruption post-process** (`thefourthfrequency:post/digital_corrupt`); the scan lines and vignette are terms in that chain (`ScanDepth` / `Vignette`) rather than rectangles drawn on the HUD. The old "continuous waveform" boss bar is gone; only a red "attempt to escape" is pinned at the bottom, and any other terminal prompt generated during the pursuit is queued without being shown or sounded until resolution, the black-screen return and the source world's load are all complete. The Esc pause menu still opens, but "Save and Quit"/"Disconnect" are disabled with "you cannot simply walk away..." and restored once the session is fully over. The Corrector uses the vanilla warden heartbeat, whose interval shortens with proximity; it is played positionally at the Corrector's coordinates on the HOSTILE channel rather than at the player's ear, so it carries stereo bearing and linear distance falloff (volume 1.75 gives roughly a 49-block audible radius, covering the whole heartbeat distance band), and only the pitch still tightens with proximity.

### Two filter languages: analog signal vs digital corruption

Glitch across the mod splits into two languages that are never mixed, so a player can tell "the tape is broken" from "the rules are broken" without being told:

| Language | Shader | Chain | Used by |
| --- | --- | --- | --- |
| **Analog signal** (the medium is breaking) | `post/analog_signal.fsh` | `signal_1..4` | Anomaly impacts |
| Same, still variant | Same | `signal_still_1..4` | First-launch loading screen, world-loading-screen corruption |
| **Digital corruption** (the rules are breaking) | `post/digital_corrupt.fsh` | `pursuit_low_res*` | Private pursuits (4 proximity levels) |
| Same, edge mask | Same | `world_interface_lock{,_peak}` / `_expulsion` | World Interface lock / forced expulsion |

**The still variants are tuned separately; they are not the moving family minus two terms.** The two families are for fundamentally different purposes: an impact lasts 18 ticks and nothing in those 18 ticks needs reading, while the corruption loading screen is a full page of **red text** that has to be read for half a minute. The moving family's `Desaturate` is 0.70 at its highest — seventy per cent pulled to grey, plus a near-neutral `Tint` and a heavy bloom — and on red text over black that does not read as "broken", it reads as "the colour is gone".

So the still family: `Wobble` and `RollHeight` go to zero (no shake, no roll), `Desaturate` drops to ≤ 0.05, `Tint` alpha to ≤ 0.07, bloom and overall strength come down with them; grain, scan lines, radial chromatic aberration and vignette are all kept. `PostFilterContractTest` guards a **one-way** rule: **no term on the side that has to be read may be heavier than on the side that does not**. It separately pins "desaturation and tint may not be high enough to replace the colour underneath" and "grain / scan lines / aberration may not be zeroed" — it still has to be the same medium breaking. Mistracking is carried by the loading screen's own slow sweep on the screen clock, so there is exactly one mistrack on the picture rather than two running on separate clocks.

**Two post-processing slots, not one:**

| Slot | Driven by | Scope | Users |
| --- | --- | --- | --- |
| Level slot (vanilla's) | `PostEffectArbiter` | World image only; HUD and terminal prompts stay legible | Pursuits, World Interface lock/expulsion |
| Full-frame slot (self-driven) | `ScreenFilterDriver` + `MinecraftScreenFilterMixin` | World + HUD + current screen, all inside the filter | Anomaly impacts, both loading screens |

The line is deliberate: **a sequence you still have to play through** must keep the instruments readable, so it stops at the "glass" layer; only **a sequence where the whole screen is supposed to be broken** goes into the full-frame slot.

The full-frame slot works because `PostChain.process(RenderTarget, GraphicsResourceAllocator)` is already public, and `Minecraft.runTick` contains exactly one moment where world, HUD and screen have all been composited into the main render target but `blitToScreen` has not run. Injecting on that call rather than at the end of the method lands inside vanilla's own `if (!window.isMinimized())`. **Its claim takes effect per frame**: each frame the claim is consumed and cleared, and callers claim from their own render path — so the entire class of "the shader got stuck on" bugs is structurally impossible. Closing the screen, disconnecting, reloading resources and thrown exceptions cannot carry it.

**Band-style overlays are all disabled, but the source is kept.** Three horizontal band effects — the pursuit interference band (`renderInterference`), the anomaly impact's tear and mistracking bands (`renderTornPicture` / `renderMistrackedBand`) and the loading screen's tracking band (`drawTrackingBand`) — are now carried by shader terms: on the digital side `BandShift`/`BandLoss` drag the picture itself, on the analog side the new `TearBands`/`TearShift`/`TearLoss` do the same, and the loading screen's mistrack became `signal_still_*`'s own slow rolling bar. All four methods remain in source and none are called: they are the reference for what the shader term should look like, and the fallback if some GPU cannot compile a chain. The contract test asserts that **nothing calls them**, not that they do not exist.

**The only thing still drawn with the GUI is the terminal weather card**: it is a small area inside a page and must not cover the tabs and close hint beside it, while a post-processing chain's uniforms are baked at load and the card's screen position varies with window and GUI scale — a full-frame chain cannot express "only this rectangle". That one case uses `AnalogFilter` and the same vocabulary (grain layer, soft scan lines, triangular-decay mistracking bar) drawn inside the rectangle.

Everything on the analog side is **continuous**: radial chromatic aberration (zero at the centre, growing outward), sinusoidal row wobble (two non-integer-ratio periods multiplied, so no predictable pattern forms), cosine scan lines, highlight bloom, an upward-crawling mistracking bar, vignette, grain. Everything on the digital side is **discrete**: whole bands flung sideways, per-band RGB channel offset, a lost band stretching one row across the whole width, hash-selected macroblocks collapsing into mosaic, dithering then quantising to a low bit depth.

**Why a real filter is possible now**: 1.21.11 bakes post-processing uniforms at chain load and offers no public per-frame write — but the `Globals` block from `#moj_import <minecraft:globals.glsl>` carries `GameTime` and `ScreenSize`, and `GlProgram` maintains its own `BUILT_IN_UNIFORMS` (`Projection / Lighting / Fog / Globals`) that get bound whenever the shader declares them, regardless of whether the pipeline declared them (vanilla's own `box_blur.fsh` uses exactly this). So only a few intensity levels need pre-baking; the motion is computed by the shader itself.

`SamplerInfo` is the opposite: every post pass declares it, but it is **only filled for some chains**, and when it is not, the whole block reads as 0 with no error at all — which is what the old `world_interface_edge.fsh` fell into (dividing by `OutSize.y` flattened the radius's horizontal term and painted the entire screen purple). Every shader in this mod therefore takes its size from `Globals.ScreenSize`, and `PostFilterContractTest` asserts that none of them reads `SamplerInfo`.

**The flicker ceiling lands on `HoldTicks`**: it is the re-roll period for every discrete quantity in digital corruption. 3 Hz = 6.67 ticks, so every chain is ≥ 7 ticks, asserted by test. The analog side has no equivalent field — everything it does is continuous — with the single exception of grain, which is zero-mean per-pixel noise and by definition does not change mean brightness, so it is not "flicker".

**There is only one level post-processing slot** and three subsystems want it (pursuit / World Interface lock / anomaly impact). `PostEffectArbiter` holds **claims**, not results: priority follows declaration order `PURSUIT > WORLD_INTERFACE > ANOMALY`, and whichever live claim is highest gets installed; chains this mod did not install are neither overwritten nor cleared. Previously each side wrote its own opposite rule — pursuits pre-empted unconditionally, the World Interface refused pre-emption — and whichever ticked last won.

Night vision is applied to the pursued player for the entire real pursuit: the mirror may be entered from a cave or a night-time Overworld, and a black-and-white low-bit-depth corruption filter on an already contrast-free picture erases the ground along with everything else — the player should lose to the thing behind them, not to the dark. It is ambient, particle-free and shows no HUD icon, and it is renewed on a 600-tick cycle (topped up below 300 ticks) rather than given infinite duration, so if any cleanup path ever misses it, the worst case is half a minute of residue. Pursuit resolution, the return, disconnection and recovery login all remove it actively.

After a pursuit the player returns to the same coordinates in the source world as where they stood in the mirror, rather than being dragged back to where the pursuit started — the mirror is a chunk-for-chunk, coordinate-for-coordinate copy, the distance they ran is real, and erasing it means the escape did not happen. If that coordinate is solid in the source world (the player dug through in the mirror), it falls back to a nearby safe point, then the entry point, and only then the spawn point. After surviving, escaping or killing it, the temporary pursuit-warning record is deleted on return and a new "the magnetic field around the user is very unstable..." record is added; capture also records the field anomaly but keeps the warning for the next retry. The heartbeat provides distance pressure and the Corrector's bearing, but never points to an exit or an escape direction — it answers "where is it and how close", never "which way should I run".

The Corrector's initial spawn and any respawn after an unexpected loss both take a valid foothold from the **full ring** 25–42 blocks around the player, **no longer restricted to behind them**: a probe may land directly ahead and the player may watch it appear. This is deliberate — something that only ever appears behind you can be reasoned about by turning around, and the old rule effectively promised "the direction you are facing is safe". Probes advance through five rings at 26/30/34/38/41 blocks, near to far, with 12 bearings per ring and a randomised start angle, so the first attempted position is equally likely to be in front as behind; when a column has no foothold it advances to the adjacent bearing on the ring rather than jumping to the player's other side. The near-to-far order also has an implementation reason: the initial mirror window is 5×5 chunks around the player's chunk, so a player standing against a chunk edge is only guaranteed a 32-block copy radius in the worst case, and the outer two rings may probe chunks that have not been copied. That does not cause a spawn failure — those columns are simply skipped, and the inner two rings fall inside the guaranteed range from anywhere in the chunk, providing 24 candidate columns on their own.

The pursuit form uses 0.31 base movement speed and a 1.32 pathfinding multiplier in the open; when the player is detected in a cave environment (no direct sky light, low sky light, enclosed on at least four sides) it drops dynamically to 0.25 and 1.04, restoring immediately on leaving. Actual pathing speed is the product of the two, but the nominal value cannot be matched to the player's directly: an entity has to turn between path nodes, slow into corners and re-path, while a player running straight does not — so "level with a sprint in the open" corresponds to a nominal value slightly above sprinting itself. The target feel is that a sprinting player roughly holds distance, and only sprint-jumping actually opens a gap. The previous 0.32 × 1.42 was faster than any player movement, which made distance escapes and line-of-sight escapes operationally impossible and left surviving the timer as the only solution. The pursuit-specific breaching starts breaking block by block after a brief stall; when the player pillars up, the Corrector prioritises removing the support beneath them and periodically leaps vertically. Correctors in the ordinary world are not affected by any of these enhancements.

### Escape and turning the tables

Surviving the form's duration is still the guaranteed success condition, but it is no longer the only one:

- Staying at least 42 blocks from the Corrector for a cumulative 5 seconds cuts tracking and ends the pursuit early;
- Breaking line of sight from at least 18 blocks away for a cumulative 8 seconds also ends it early;
- Both progressions decay quickly once the condition lapses, so a single brief break cannot be banked permanently;
- The pursuing Corrector keeps 36 health and real hit detection. Killed by the pursued player personally, the pursuit resolves as a successful kill and the dead entity is no longer auto-respawned.

On capture, the client freezes on the last frame and plays 3 seconds of the machine-hang fault sound (drawn from the same variant pool); resolution removes one heart of maximum health, then a black screen hides the return to the source dimension. The capture penalty has a 6-heart soft floor: at 6 hearts or below, capture no longer removes any more — a player failing repeatedly is already losing the pursuit itself, and grinding them down to one heart only makes the next pursuit and the finale harder for whoever needs help most. On surviving, opening distance, breaking line of sight or killing it, "attempt to escape" is withdrawn first and replaced by a green "you have escaped it, for now..." for 3 seconds; resolution adds one heart of maximum health (not subject to the soft floor — it always applies), then the black screen hides the return. Maximum health is always clamped between 1 and 20 hearts, and a technical interruption triggers no health penalty.

At the instant a pursuit really begins (the target is moved into the mirror), other players within 64 blocks in the source dimension who hold a bound terminal receive one record line: "a nearby terminal's signal cannot be resolved for now". It names nobody, reveals nothing about the pursuit, and is not sent to the target. It turns the bystander experience from "did they disconnect?" into "it took them", while leaving the loneliness boundary intact.

### The safety window

A real pursuit only starts when all of the following hold:

- The player is alive, not spectating, not sleeping, not flying/gliding/riding;
- Not on fire, not in lava, fall distance no more than 3 blocks;
- Health above `max(6 points, 40% of maximum)`;
- No ordinary hostile within 12 blocks currently attacking them;
- The terminal, ordinary anomalies, gaps and the World Interface finale are all unoccupied;
- The current dimension is a supported vanilla source dimension and a free mirror slot exists.

The Overworld and the Nether currently allow pursuits to start. End mirrors are registered for topological and recovery symmetry, but the v1 safety policy forbids starting a real pursuit in the End; modded dimensions do not trigger one in this version either.

## The private correction layer

A real pursuit is not client-side invisibility; it moves the target into a private mirror slot of the matching dimension. The Overworld, Nether and End each pre-register two slots, with at most two pursuits running server-wide at once.

### Dynamic chunk snapshots

- 5×5 chunks around the player, ±48 blocks vertically, are copied before entry.
- At most 8192 blocks are copied per session per tick.
- After the pursuit starts, a 5×5 window around the player's current chunk is continuously requested; crossing one chunk normally adds only the 5 chunk columns in the direction of travel.
- Chunks already queued or copied in the same session are never overwritten, so the player's digging and temporary placements in the mirror survive.
- There is no fixed 30-block horizontal boundary, and the player is never repeatedly returned to the entry anchor.
- If an extreme teleport or high-speed movement overtakes the copy, the player is briefly held at the nearest safe position and the pursuit timer pauses in step; it resumes once chunks are ready.

The vertical copy range is still fixed at ±48 blocks around the entry height. That is a v1 boundary still awaiting real-hardware verification.

### Blocks and items

- Natural blocks in the mirror can be broken, but drop no items or experience and consume no tool durability while clearing a path.
- Containers and other block entities are replaced with air or stone; redstone, portals, beds, explosives and dangerous interactions are not preserved.
- Only simple building blocks may be placed temporarily; a successful placement is written to a persistent recovery ledger.
- On session success, failure, disconnection or restart recovery, each placement is refunded exactly once; when the inventory is full, the recovery ledger keeps holding it.
- Health, hunger, potions, food, ammunition and combat durability stay real. Capture does not trigger vanilla death or drops.

### Multiplayer and recovery

- Pursuit progress, form, anomaly history, terminal appearance, mirror sessions and refund ledgers are all stored per player UUID.
- A pursued player and players in reality are mutually invisible and share no entities, routes, block modifications or Corrector.
- Slot occupancy is one of the few pieces of world-shared state; with no free slot the pursuit stays pending and never pre-empts another player.
- Disconnection and server restarts cancel the copy queue, release the slot, settle refunds and return the player safely to the source dimension after they log in.
- If the source landing spot is no longer safe, the return locator searches for a nearby safe position and never overwrites real-world blocks.
- Player visibility is restored before the cross-dimension return. If death or an admin teleport already moved the player out of the mirror, the session only clears the copy queue, slot, refunds and visibility state and no longer forces them back to the entry point.

## The three terminal appearances

Terminal appearance is personal state; it does not follow whichever player on the server is furthest ahead:

| Appearance stage | Current condition | Meaning |
| ---: | --- | --- |
| 0 | First pursuit not yet resolved successfully | An old device, able only to receive and record |
| 1 | At least 1 pursuit resolved | The device has met the Corrector and begun to erode |
| 2 | At least 3 resolved, allowed form at least 4, anomaly tier at least 4 | Mainline, anomalies and pursuits converge; the terminal becomes a correction interface |

Unread indication still uses the matching alert model for each appearance; there is no fourth terminal form.

A single CRT shell (scan lines and vignette) is layered on top of all three appearances and does not vary with the stage. That layer must be neutral darkening, static, and confined to the display area: an earlier pure-green overlay tinted every character read through it and crushed the contrast, and it has been deleted and blocked from returning by a contract test. Rolling bands belong to the "damage language" of the weather tool's sky instrument, meaning the instrument is failing, and are not part of the permanent shell. See [Terminal interface and handheld form](terminal-ui.md).

## Boundaries still being tuned

Not hidden design — numbers the current implementation still needs verifying across a real, long multiplayer session:

- Whether the tier-5 5–9 minute ordinary anomaly interval is too dense;
- Door cascade really breaks doors, but there is currently no separate long cooldown for destructive anomalies;
- Login and dimension changes directly overwrite the next schedule, so frequent relogging or round trips may accelerate anomalies;
- TPS, disk growth and the felt experience of chunk catch-up when two players stream simultaneously;
- Whether a fixed ±48 blocks vertically is enough for long shafts or fast ascent/descent routes.
