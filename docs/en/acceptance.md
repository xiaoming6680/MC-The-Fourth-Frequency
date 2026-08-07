# Manual acceptance checklist

For the `1.0.0-rc.1` candidate build. It lists only what automation cannot cover. Automated results and commands are in [Testing and acceptance](testing.md); anomaly/pursuit rules are in [Anomalies, terminal forms and personal pursuits](anomalies-and-pursuits.md); exact World Interface numbers are in [The World Interface finale](world-interface.md).

## Before starting

- Use Minecraft 1.21.11, Fabric Loader 0.19.3, Fabric API 0.141.4+1.21.11 and Java 21.
- Install `thefourthfrequency-1.0.0-rc.1.jar`; prefer a fresh save for the first pass.
- The End ending creates a local isolation marker for the current save; back it up before a real ending run.
- Keep development acceleration off: `pacing.developerAcceleration=false`.
- To recover from an interrupted ending transaction, launch once with `-Dthefourthfrequency.safeMode=true`.

## First-run safety notice

1. Delete the local safety-notice version file and go to the title screen.
2. Confirm the v3 notice appears on the first `TitleScreen`, covering strong flicker / high contrast, volume and irreversible save sequences.
3. Confirm the same notice version shows only once, and that the state is written to `config/thefourthfrequency-safety-notice.version`.
4. Press F8 with no ending recorded: it must not toggle meta and must not open an ending reset dialog.

## The first-launch corruption loading screen

Appears only on the first world entry of a new save, about 30 seconds. The whole frame now runs through post-processing (`signal_still_*`), so everything below is about effects acting **on** the picture, not drawn over it.

1. **The wall of text must fill the screen in one frame** — no expansion from the centre outward. It is a cut, not a transition.
2. **The picture must not shake**: text rows must be straight, must not sway sideways and must not shift frame to frame. The whole screen should be visibly "dirty" — grain, scan lines, red/cyan separation growing toward the edges, bloom around bright characters, darkened corners — but every character stays exactly where it was. This is the easiest thing to regress: any change that makes the text move is a failure.
3. Failure text, the recording timecode and `OBSERVER DETECTED` must all be **inside** those effects (carrying the same grain and aberration), not floating on top as a clean layer.
4. The one slow mistracking bar sweeping upward through the middle is kept (one slow sweep, not a shake); there should only ever be one on screen at a time.
5. During dead air the picture must not be pure black: it should read as a powered monitor receiving nothing — full-screen snow, per-pixel rather than a few hundred countable dots.
6. After recovery, scan lines and grain **do not disappear entirely**; they settle at a worse baseline. That is intentional.

## New world and the terminal

1. Create a new survival world and confirm Station Zero and the single-terminal flow are reachable. The station must land intact: no floating floor edges, no walls half-buried in a hillside, no tree trunk through the roof, and a porch outside the door you can walk straight down from.
2. Stay inside until nightfall and confirm no hostiles spawn indoors or on the roof; confirm the eastern breach can be seen through but not walked through, and the door is the only way in.
3. Restart the server on the same save repeatedly: a completed station must not rebuild, and blocks the player removed by hand must stay removed.
4. The terminal must have only the four player pages `HOME / TOOLS / RECORDS / FILES`; the wire protocol still uses only `SIGNAL / FILES`.
5. The first terminal opening on a new save should play a boot self-test (about 3 seconds, six lines typed out), then highlight Tools → Records → Files → Home in turn. During onboarding, Esc and clicks elsewhere must do nothing, and the lower-right hint must read "exit unavailable" rather than "press Esc to exit". The fourth step must land on Home, where the progress bar filling to 4/4 and the reward arriving are visible on the spot. Reopening the terminal must not show onboarding again, and an upgraded existing save must not see it at all.
   - Each step must show a **one-line brief** at the bottom of the page describing the **currently displayed page** (not the one the arrow points at), opening with that page's tab name, e.g. "Current page 'Home': current objective, suggested tool and the latest record". The four steps introduce Home → Tools → Records → Files, matching frame for frame what is actually on screen; any step where "the arrow points at Tools while the brief describes Tools" is a failure. The brief appears only during onboarding, must leave no residue afterwards and must not occupy any page's normal layout. Repeat once in English and confirm the copy is complete and not truncated.
6. **Onboarding safety valve**: have a zombie hit the player mid-onboarding. The lock must release immediately, Esc must work at once, and onboarding progress must be preserved so the next opening resumes at the current step.
7. Check the handheld terminal and its open/close sequence item by item against "Handheld terminal appearance and sequence" below.
8. The first task must be "learn the terminal", completing only after all four top tabs have actually been selected in turn. On completion it must advance immediately and grant the reward shown in the upper right; with no inventory space the remainder drops at the player's feet.
   - **Completion must be visible.** After the fourth tab lands, the task card must hold **the just-completed entry** for about 3 seconds: the bar completing to 4/4, the card and reward frame turning to the completion palette, the objective line reading "Learn the terminal: click the four top tabs 4/4", and "task complete" in the lower right. The card must not also carry a line restating what was granted — the reward frame already draws the bread and the ×6. This hold must appear on the same screen as the status bar's "onboarding complete", not before or after it. Only after 3 seconds should the card switch to the next task with the bar climbing from 0 (there must be no animation of it falling from full).
   - Complete any later task with the terminal **closed** (e.g. chopping enough wood): the green notification above the hotbar must report **which task** and **what was granted** together, e.g. "Task complete: Collect wood · Stone Axe ×1". Reporting only the reward is a failure; splitting it across two or three lines is also a failure.
9. The six tools must become available in order: shelter, mineral, portal, weather, navigation, stronghold.
10. Verify the mainline objective order:

   `12 logs/planks → 6 iron samples → the Nether → a Nether fortress → 8 blaze rods → return to the Overworld → craft 4 eyes of ender → record 3 real throws → approach the stronghold → enter the End → defeat the World Interface`

   The "Nether fortress" item needs field acceptance: the check requires the player to stand on a structure piece generated by the fortress itself, and automation cannot build a really generated fortress. Walking inside a fortress should complete it within a second; standing on the ground outside, in the lava sea under the bridge, or on adjacent terrain must not.

11. The stronghold tool opens after three eyes of ender have been obtained; with fewer than three real throws it must not give a complete locating conclusion early.
12. The complete journal must be resident in FILES from the start, with its title fully garbled initially; each damaged file discovered restores about 25% of the title.
13. The four damaged files appear only once discovered. Their titles expose the real name with a fixed 2–3 characters garbled, and the body shows "file contents damaged, restoration attempted" in small italics at the top. First opening each advances 25%, and only reading all four unlocks the complete journal.
14. In multiplayer, discovery state is shared while read state and journal access are individual; unlocking the journal must not generate extra files or world structures. On a real Overworld/Nether round trip carrying the terminal, `RECORDS` should show continuity and the journal should continue itself at the end.
15. When a new file arrives, the top `FILES` tab must show an unread indicator; clicking that tab clears it once, but the complete journal stays locked while the four damaged files have not each been opened.
16. Review all 7 file types and confirm titles, metadata and body text use a legible size and high-contrast palette, with spacing between paragraphs and automatic wrapping to the body column width; long documents show a scroll position on the right that does not cover the text.
17. Create overlong content in `FILES`, `RECORDS`, a tool detail and the navigation candidates, and confirm all four scroll and clip independently without borrowing each other's positions; the `RECORDS` unread count must only count currently visible entries.
18. After a pursuit warning force-opens `RECORDS`, close the terminal and reopen it normally: it must return to the page the player was on, not treat the forced page as the new default.
19. On reaching a side-signal site without opening the navigation tool, confirm the receiver can be tuned directly; the lock must require 20 ticks of correct tuning, with the progress refreshing smoothly over that period rather than jumping to full at once.

### Handheld terminal appearance and sequence

This group must be checked by hand on a real client: visual proportion, animation feel, FOV amplitude and photosensitivity safety cannot be replaced by any automated test.

1. **One-piece shell**: in hand you should see a black cast-iron body, worn brass trim and four corner screws, a large **landscape** CRT on the left, and on the right, top to bottom, an oscilloscope, circular compass, tuning slider, two-line LCD and the close-hint area at the bottom, with a small amber lamp to the right of the oscilloscope. The front must clearly be wide and flat (about 2:1), matching the panel that opens. **At no angle and at no time may it flip, fold, unfold or slide** — the device is sealed.
2. **Two-handed carry**: two hands should support the terminal from the lower corners of the frame. **Hands must not enter the body**: wrists and palms should be outside the side walls and in front of the near face, holding it rather than passing through it.
3. **Idle**: the terminal is tilted back with the screen facing up, resting below the line of sight, readable with a glance down; a slight breathing motion, small enough not to distract.
4. **Open**: right-click lifts, scales and squares it to the lens while the field of view narrows slightly (no more than about ten per cent); the interface opens only after the screen fills the frame.
5. **Size**: **no edge of the body may leave the frame** at any point. Check once in a narrower window such as 4:3 or 16:10 — the wide edge goes out first.
6. **Hands stay with the terminal**: during the lift the terminal nearly doubles in size and the hands do not; both hands must stay against the lower edge and the sides, **must not slide toward the middle as it scales**, and must not detach and float. The hands and the device are drawn at different depths, so this can only be judged by eye.
7. **Not occluded**: check once each **indoors, against a wall, looking up at the ceiling and down at the floor**. The terminal must never disappear entirely or be sliced in half. This is the inherent conflict between first-person hands and the world depth buffer; the device is pulled toward the camera specifically to suppress it.
8. **Turning**: on fast horizontal turns and vertical flicks the terminal should show **slight lag**, consistent with vanilla held items. It must not follow rigidly as if welded to the lens, and must not jitter or separate from the hands.
9. **Close**: leaving the interface should first show the terminal held up in front of you, then a smooth settle back to the carrying position.
10. **Boundaries**: the whole sequence **must not move the player in the world**; switching to third person must show no camera displacement or FOV change, and bystanders' view of the terminal is unaffected.
11. **Switch in / out**: selecting the terminal in the hotbar should raise it into the hand from below; switching to another slot should lower it while the new item rises normally.
12. **State updates must not impersonate a swap**: while the terminal is **open**, receiving a new signal, discovering a file or completing a task (all of which rewrite item components) must **not** make the terminal drop and rise again, and must not replay the equip animation. This is the easiest thing to regress — deliberately trigger an unread with the terminal open and watch the screen.
13. **Smoothness**: with the terminal open the server keeps pushing snapshots; that must not restart the lift or stall it halfway. Pressing close halfway open, or right-clicking again halfway closed, should reverse continuously from the current position rather than snapping back and starting over.
14. **Off-hand occupied**: with something in the off-hand it should fall back to a one-handed grip, and **the off-hand item must still be drawn** — it must not be silently hidden.
15. **Other views**: the inventory slot still shows the original flat icon; ground drops, item frames and head-worn display must show the 3D shell with correct orientation.
16. **Reset**: switching held items, dying, changing dimension, disconnecting or being force-closed by the server mid-animation must immediately reset to idle with the field of view restored at once, never stalling halfway.
17. **The six forms**: check 0/1 green, 2/3 cyan, 4/5 red one by one. **1, 3 and 5 must differ from their even neighbours only by the lamp being lit; no other part of the shell may change.**
18. **The lamp inside the interface**: with the interface open, a separate amber lamp should sit to the right of the oscilloscope in the hardware column, without covering the oscilloscope, compass, slider, LCD or close hint. With nothing unread it is a dark cover in a metal bezel; with an unread signal / unread file / unacknowledged navigation completion / claimable reward it lights and breathes slowly (about once every 2.4 seconds) and **must not flicker rapidly**. It must stay amber when the stage turns cyan or red. **The lamp state must match the item in hand** — lit in hand but dark in the interface (or the reverse) is a failure.

## Anomalies

1. Check the catalogue is 19 triggerable anomalies (including the three sustained ones: silent world, temporal drift, metric drift) across tiers 1–5; `disconnected_base`, `watcher_orbit`, `rework_probe` and `hostile_echo` may only exist as historical/debug name mappings.
2. A new terminal's first anomaly should be scheduled at 4–7 minutes; ordinary intervals by tier should fall in 8–14, 8–13, 7–12, 6–10 and 5–9 minutes.
3. Even with iron obtained early, the actual tier must not skip tier 2; each promotion requires at least 20 minutes of qualifying online time in the current tier and 2 successful anomalies.
4. The last three successful anomalies must not be selected again; after a promotion, new-tier content should be observed preferentially.
5. After window pulse, channel takeover or desktop presence triggers, no strong-interface anomaly may appear again within 20–30 minutes.
6. In multiplayer, personal visual, audio and input effects must apply only to the target client; shared server effects such as observer alignment, door cascade and experience gap may be seen by nearby players.
7. After completing any anomaly, the terminal's records page must not gain a matching entry, unread indicator or dedicated record sound; existing anomaly entries in old saves should be cleaned on sync.
8. Press M (default) to open the debug workbench: it should show the overview, mainline, anomalies and files modules and refresh server state live while open. The files module should list discovery, unlock and read state for all seven narrative files. The server must still validate permission and context for every debug action.

### The weather tool's sky monitoring

1. With no anomaly active, open `TOOLS` → Weather: all four channels (zenith, horizon, star magnitude, celestial phase) should give stable readings, the horizon trend line should be flat, and the page should be **completely clean** — no scan lines, flicker or fault rows. The day/night countdown must never collapse to `——`.
2. Trigger `red_horizon` and open the page immediately: the horizon channel should begin climbing **before** the sky visibly reddens, with the trend line visibly ramping rather than jumping. As intensity rises, scan lines → rolling bands and tear rows → fault rows flooding up from the bottom of the card → short bursts about every two seconds at peak (a full-card refresh plus glitch blocks), each burst with a fault sound. It should be completely clean again after about 40 seconds.
3. At peak intensity, confirm the four top tabs, the tool back button and the lower-right close hint stay **readable and clickable at all times**, and that tear rows and glitch never overflow the card. Verify once at minimum and once at maximum window size.
4. Trigger `temporal_drift`: the day/night countdown **must always be correct**, with only the celestial-phase channel reporting a discrepancy; the sequence must not exceed the lowest level (scan lines and the occasional full row of `——`), with no tearing, glitch or fault refresh at any point. A wrong but plausible number in the countdown is a failure.
5. Pin the weather tool to `HOME`: the home card should say the same thing as the tool page (collapsing to `——` in the same way), but must show no scan lines, refresh or glitch.
6. Open the other five tools and the `HOME`/`RECORDS`/`FILES` pages and confirm they show **no sequence at all** during the anomaly.
7. Fault rows must use instrument language (no carrier, sample rejected, phase reference lost, and so on); no forged exception names or stack traces.

## Personal Corrector pursuits

1. Establish first-form activity proof separately via mining, 128 blocks of cumulative exploration, block-entity/loot interaction, building and trading; none of the five routes may require a fixed home.
2. At least one anomaly must have been completed successfully before the first pursuit. Once the safety window and mirror slot are confirmed, the terminal writes a green "anomalous signal fluctuation detected, approaching.." and a red "prepare yourself..." 10 seconds ahead, with the action bar showing only "the terminal is vibrating violently". Opening the terminal should jump to the records; the debug five-tier pursuit entries start from the same prompt.
3. Verify the five permission thresholds: early activity, entering the Nether, blaze rods and return, 3 real eye throws, finding the stronghold.
4. A player who crosses several thresholds early must always face only the next actual form; no form skipping and no back-to-back make-up pursuits.
5. Verify durations and counterplay: Sound-Seeker 60 s, Router 75 s, Interceptor 85 s, Trespasser 95 s, Interface Corrector 110 s.
6. After a success, the next real pursuit must wait 20–30 minutes; capture must not kill or drop items, must keep the pending pursuit and must retry after 5 minutes.
7. Capture, pursuit success and a deliberate disconnect must all return the player to a safe position in the source dimension; when the source position is occupied, real blocks must not be overwritten.
8. During a pursuit, ordinary anomalies, mainline sampling, navigation, world decay and World Interface services must not write real progress from the mirror dimension.
9. Verify the 10-second lead-in timing: 4 s of terminal reading, 4 s of presentation frame-rate decay only, 2 s of hang audio. The WARNING phase must show no filter, vignette, signal band or solid black cover at any point, after which the black screen must seamlessly cover the mirror load page and any resource-loading overlay. The black screen must wait at least until 7×7 chunks around the player in the destination dimension are ready and stable for 8 consecutive ticks; if it can never stabilise it must continue at the 200-tick (10-second) hard timeout at the latest, and must never hang indefinitely.
10. During a real pursuit only "attempt to escape" is pinned at the bottom; other prompts and their sounds must be deferred until the black-screen return is fully complete. There must be no boss bar.
11. Sprint continuously on flat ground while pillaring up or walling in, and confirm the pursuing Corrector visibly closes, breaches quickly, removes the support beneath the player and pursues vertically, while still being attackable and killable. Then enter an enclosed cave with no sky light and confirm the Corrector slows dynamically, restoring speed on returning to the open.
12. On capture, confirm the last frame freezes with the hang audio for 3 seconds and maximum health drops by one heart before the black-screen return; a technical interruption must not remove maximum health.
12a. Trigger 4–5 freezes in a row (warning freezes or capture resolutions both count) and confirm the hang audio is clearly not one clip: the buffer lock-up, the high-frequency whine and the decay into a square wave should be distinguishable. Also confirm the variants are level-matched — none should be suddenly louder or quieter.
13. On a successful escape or kill, confirm "attempt to escape" disappears first, the green "you have escaped it, for now..." shows for 3 seconds, maximum health increases by one heart, then the black-screen return. Back at the original location, the old pursuit warning should be deleted and the terminal should gain a "the magnetic field around the user is very unstable..." record.
14. Press Esc at any point from the terminal warning until the return load completes and confirm "Save and Quit"/"Disconnect" are disabled with "you cannot simply walk away..."; the buttons must be restored once the pursuit is entirely over.

## The private mirror and multiplayer isolation

1. Have two players enter pursuits of different forms simultaneously and confirm they occupy different mirror slots and cannot see each other's player, Corrector, block modifications or drops.
2. A third player with both slots full must only hold a pending pursuit; no pre-emption and no failed teleport.
3. The initial terrain on entry should match the source position's 5×5 chunks and ±48 blocks vertically; running horizontally out of the initial range should keep loading newly copied chunks with no turnback at 30 blocks.
4. On an extreme teleport into uncopied chunks, only a brief hold at the nearest safe position with the pursuit countdown paused is permitted; it must resume once chunks are ready.
5. Mining in the mirror must yield no blocks, experience or path-clearing durability loss; redstone, portals, containers, TNT, beds and fluid interactions must be rejected or sanitised.
6. Place ordinary building blocks and then end the pursuit, confirming items are refunded exactly once; a full inventory, a disconnect and a restart must all still deliver via the recovery ledger.
7. This version must only allow pursuits to start from the Overworld and the Nether; the End and modded dimensions must not trigger a real one.
8. Simulate a dimension change before the return and confirm the server restores player visibility before teleporting. Then use death or an admin teleport to remove the player from the mirror: the session should release the slot, settle refunds and restore visibility without dragging the player back to the entry point.

## The altar and the collective ritual

1. With no personal pursuit experienced, inserting the last eye into a complete stronghold frame that already has 11 must be rejected without consuming the eye; after at least one, it should activate normally. Deliberately get captured in the first pursuit and confirm the last eye becomes available immediately — capture no longer blocks the finale, though the Corrector form stays at form 1.
2. In the End, verify the ground-flush 11×11 altar, twenty inert gateways and ten stability anchors on the native main island; there must be no large artificial combat platform.
3. The ritual freezes the current online non-spectator roster, supporting 1–8. Every player on it must hold their own valid bound terminal and right-click the resonance core to insert it.
4. Withdrawal and cancellation must be possible before everyone has submitted; a roster change, an indeterminate reload or a failed transaction must return terminals.
5. Once everyone has submitted, escrow must commit atomically and move into summoning and combat.

## The World Interface fight

- Maximum health is `600 × (1 + 0.5 × (frozen roster - 1))`: 600 for one, 900 for two, 1200 for three, +300 per player, 2700 for eight. The pool grows with the roster but more slowly than the team's damage output — an extra player should not be dead weight, but should not make the same 10-minute timer harder either.
- Combat forms advance one way by health: form 1 `>70%`, form 2 `>35%`, form 3 `≤35%`.
- The collapse timer is 12000 ticks (10 minutes). It continues while any frozen member is online and pauses when all are offline.
- Each surviving anchor restores 0.02% of maximum health per second (0.20% for ten) and projects a visible radius-8 stability zone; players inside take 20% less World Interface damage, and terrain inside shows no combat scorching or missing-texture erosion. At 0 / 5 / 10 broken, damage taken should be 60% / 80% / 100% and the attack cooldown coefficient 1.15 / 1.00 / 0.85, with movement speed and the collapse timer unchanged. The first positive-damage player attack should break an anchor immediately, and each break should show the golden wash and "reconstruction↓ / activity↑" on the HUD for about 3 seconds. The target itself must not be able to break an anchor.
- **Anchor appearance and destruction sequence (client acceptance required)**: the pillar top must carry a four-way clamp rather than an end crystal — consistent from front, side and back, clearly readable from above as four claws clamping down, with four feet gripping the single bedrock cap at the pillar top and wrapping down over its edge, with no base and no platform. On top there is only an exposed relay core on a very thin shaft; four calibration petals sit below its equator, disconnected from each other, forming no ring, cage or upward aperture. Only the chest core, the relay core and the joint gold seams emit; the whole entity must not become a lantern. Breaking one should show about 16 ticks of collapse, **explosion first then recall**: the relay core detonates on the spot (explosion emitter) → three detonation rings step outward across the bedrock cap with fragments thrown outward → a column of light fires vertically → a radius-26 shockwave ring leaves the pillar base → the traction beam converges to a bright point at the relay core and retracts toward the boss → the claws fold onto the bedrock cap and pixelate away → a few violet motes disperse. Two audio layers: the vanilla explosion over a pitched-down anchor voice. **The camera should shake from the anchor's position** — standing beside the anchor being broken should shake more than standing at the island's centre (this is the direction of the fix; it used to be exactly the opposite). After the sequence the pillar top must hold only the intact bedrock cap and the obsidian pillar, with no fire, smoke column, drops, debris or block change; ten anchors broken in quick succession must not produce full-screen flicker.
- **Beam endpoint**: the traction beam should start at the relay core (2 blocks above the anchor block) and connect live to the moving boss core, not from inside the anchor body and not as a fixed vertical column. Beam angles should change as the boss rises, descends and circles.
- **Legacy save migration**: enter the End on a save whose arena was prepared by Beta 0.4.0 or earlier; all 10 anchors should appear as the new entity. Anchors persisted as broken must not revive, and no end crystal should remain on any pillar top. Ordinary end crystals outside the arena must behave exactly as vanilla (ignitable, detonatable by the player, with normal explosion damage and terrain destruction).
- Complete the success ending separately with 0, 1–9 and 10 anchors broken; the vanilla End poem should use the "all preserved", "partial" and "all broken" passages accordingly, never claiming all ten were destroyed regardless.
- All three forms' bodies should visibly hang in the air, with no part sunk into the ground at any moment; the summon descent must stop in the air rather than slam into it. Standing directly beneath and looking up, the body's lower edge should be roughly 8 / 14 / 18 blocks.
- **The head must not clip underground (client acceptance required)**: take the boss to low health (structural sag is worst at low health) and observe the central skull directly from the side; the whole skull including **jaw and teeth** must stay above ground, with no frame entering the end stone. This is the direct purpose of raising the whole thing; check all three forms.
- **The summon stops higher**: at the end of the thirteen-second arrival, the body should stop above the combat station and hover for a moment, then press slowly down to the station over the first few seconds of combat; it should not already be at combat height on arrival.
- **The skyhold window for forms 2 and 3 (client acceptance required)**: about every 45 seconds the body should climb 26 blocks above the combat station in about 2.5 seconds, stay for about 8 seconds, then descend in about 2.5 seconds. The climb should start with a flight sound and a low roar. While raised: melee cannot reach head, neck or tendrils, while bow/crossbow/trident can still hit the body; melee must become usable again after the descent. **The total must not exceed 40% of the fight** — timing one 45-second cycle should give around 13 seconds raised. Form 1 must never rise.
- **Form change is a departure (client acceptance required)**: on switching to forms 2 and 3, the body should gather, climb rapidly out of sight, change form up there and land back in the arena; it must not morph in place in front of the player. There is a shockwave ring on departure and on landing, plus a roar on landing. Having no attackable target for those 4 seconds is expected behaviour.
- **The three heads watch the target (client acceptance required)**: circle around the side of the boss and all three heads should turn toward you — the central head most, the side heads less. The body turns slowly, so the heads should turn before it does. Also confirm the neck chains **do not knot or clip each other** while turning, especially in form 2 and during forced expulsion (necks near horizontal).
- Attack **the central skull and its neck** with a melee weapon from the ground: all three forms must be hittable, with hit feedback landing on the visible skull/neck segment rather than the air beside it. The side heads are higher and smaller and may be out of reach.
- **Head hitboxes must match the visuals and actually be hittable**: enable hitbox display (debug panel) and the head's box should sit on the **jaw** rather than floating above it, with the top still covering the brow; the horns being outside the box is expected. All three forms should be reliably hittable from the ground (box bottom about 1.4 / 0.8 / 0.4 blocks above the ground).
- **The player should be able to catch the boss**: run straight at the body and it must not retreat at the same speed. It only approaches while further than the standoff (body radius + 3 blocks), then stops horizontally and lets you walk the last stretch.
- **Heads must not teleport**: circling the boss, the three heads should turn **continuously** with no "still for several frames then a jump". Also confirm it really is looking at you — the pitch cap went from 30° to 60°, so it should be able to stare down at someone directly below rather than always pointing at empty space in front of them.
- **The roar should be two layers**: an ender dragon's low roar with a lower wither throat underneath, dropping overall as forms grow; it must not read as a plain dragon call.
- **Phase 1 must be noticeably slower**: 7.5–10 seconds between attacks, with a genuinely quiet stretch long enough to work out the last telegraph. Phases 2 and 3 should be visibly faster in turn.
- **It keeps attacking while raised**: during the dozen or so seconds aloft you should still be taking hits (laser, breath bolt, sky lance, tendril), but grab-and-throw should never appear — that move necessarily misses from the ceiling and is excluded from the aloft candidates.
- **Hitboxes must follow the animation**: while the interface is mid-action (gaze lock, sky lance, tendril lash, forced expulsion, form change), swinging at the visible skull should still hit; conversely, swinging where the skull just left should miss. Confirm particularly that the skull is **directly in front of** the body rather than behind it — that is an axis that was once inverted.
- **The three heads must not clip each other**: across all three forms and the full sequence of every action, there should be visible gaps between the two side heads and between a side head and the central neck; side necks should splay outward and never cross the centreline. Form 2 and forced expulsion (necks pressed close to horizontal) are the two easiest cases to reproduce.
- Tendril tips **do not reach the ground** (drawn ends stop 4–7 blocks below the body), so ground melee cannot hit tendrils — that is the current design. Tendril hitboxes only cover the last drawn segment; verify with ranged or from higher ground.
- Ability sounds in phases 2 and 3 must be as clearly audible as in phase 1 (previously audible only within 16 blocks while the body's core rises to 9 / 16 / 19 blocks, making phases 2 and 3 effectively silent). Confirm one by one that laser, breath bolt, sky lance, grab, weapon impound, gaze sweep and tendril lash can be heard from anywhere in the arena.
- **Ability and hurt sounds must not disappear after a long phase**: keep attacking in the same phase for **over 3 minutes**, especially triggering grab, impound and the sweep repeatedly in a solo phase 2. The boss must skip unavailable actions while the exclusive-control target is still inside its 600-tick protection and keep using other abilities; it must not stop and wait for the protection to expire. Every hit that really deducts virtual health should give a hurt sound at the hit point (at most every 4 ticks), with particles still appearing on every hit.
- **The screen centre must be clean while locked**: when locked by the laser, sky lance, grab, tendril or gaze, purple/red interference should appear only at the edges of the frame (the radial mask `CenterClear` starts at radius 0.55 / 0.42), and the middle must have no displacement, colour shift or blocks — that is where you dodge. Also confirm the edge interference is **discrete digital corruption** (whole bands flung aside, channel offset, macroblock collapse) rather than uniform mosaic or blur.
- **The music must stay audible throughout**: play a stretch of each phase, especially the densest moments — volleyed breath bolts, sky lance landings and laser sweeps. Boss sounds may cover the music for an instant, but the track must not disappear from beginning to end. Encounter cues are all multiplied by `ENCOUNTER_MIX_TRIM` (0.72) and explosions are additionally reduced by reach (`BLAST_REACH_BLOCKS` 72), while roars keep a 96-block radius and should still be audible from the far side of the island.
- **Blast abilities must shake the camera (client acceptance required)**: each of the following should produce a perceptible camera shake when it happens near the player, **at the same instant as the explosion** and never early — a laser impact sweeping past, a sky lance landing, a breath bolt detonating (including several arriving together on the phase-3 volley channel), a tendril landing, a stability anchor breaking, and any shockwave ring passing. **Check the sky lance especially**: the shake used to precede the crater by 1.35 seconds. Shake should weaken with distance and be absent entirely outside the blast radius.
- All eight actions should be observable in a targeted acceptance run: laser sweep, breath bolt, sky lance, weapon impound, grab and throw, gaze sweep, tendril lash, forced expulsion.
- Only impounded weapons enter the recovery ledger; gaze-sweep hotbar items are ordinary world drops and must not be duplicated or returned by the ledger. Only the laser, breath bolt, sky lance, grab-and-throw and tendril lash show a targeting lock: past 70% both the lock tone and `! LOCKED !` appear. Weapon impound and the gaze sweep are undodgeable deprivation countdowns using only the sinking low tone. Exclusive controls must not stack on one target, and a target should have 600 ticks of protection.
- The fight shows only one custom HUD. The World Interface's permanent scarring budget is 8192 blocks total, at most 32 per tick, and protects the return portal, obsidian pillars, block entities, key mod blocks and the immunity tags.

### Multiplayer target allocation (requires one full run each at 2 and 4 players)

Automation can only prove the rules are monotonic, bounded and tick-identical for a solo fight; every item below must be confirmed on a real client.

1. **Nobody is named twice in a row.** Record who is locked each time. As long as another valid target is present, the same player must not be named twice consecutively. If it happens, first check whether the other player was inside a 600-tick exclusive-control immunity or more than 256 blocks from the arena — that is the fallback the rules allow.
2. **Shares are roughly even.** Over a whole fight, the most and least targeted players should not differ by more than a third. Shares are not exactly even, and that is intentional.
3. **Phase 3 does not put four telegraphs on one player.** Watch at the densest volley moments: the player the schedule is currently locking should not also be targeted by a volleyed breath bolt.
4. **The tendril lash's three strikes hit three people.** Have three players spread out (more than 10 blocks apart) and confirm the three landings do not all fall on whoever is nearest.
5. **Forced expulsion does not kick the same person repeatedly.** Trigger at least two expulsions in a 4-player fight (3600-tick cooldown) and confirm the second picks someone the first did not; the integrated-server host must never be selected.
6. **The body no longer parks over one player.** With four players spread out, the body should sit between the team rather than pinned above the nearest player; solo standoff, hover height and turn rate should be exactly as before. The three heads' gaze still follows the nearest player — that is **retained**, not a regression.
7. **Is the pacing right? (the one open question at RC)** Record clear times, deaths per player and "was there idle time" for 2- and 4-player fights. The interval tightens by `1 / (1 + 0.18 × (roster - 1))` (floor 0.45) and volleys widen with the roster; both coefficients were derived rather than measured. If a 4-player fight is clearly too dense or still too idle, adjust those two coefficients rather than the health pool.
8. **Grab-and-throw no longer sends false warnings.** In multiplayer, confirm the player who gets the full-screen lock border and lock tone is always the one the tendril subsequently takes; nobody should receive a complete lock presentation and then have nothing happen.

## Success and failure

### Success

1. Deal lethal damage before collapse reaches 100%.
2. Verify the resolution order, five beats with no overlap: **9-second death sequence** (the body **ascends** rather than falls — no toppling; tendrils detach one by one with a burst and a sound at each detachment; ash streams from the whole body and falls opposite to the body's rise; a howl about every 2 seconds, volume and pitch descending) → the body **leaves** (a large ash burst + shockwave ring + a low howl cut off mid-breath — it must not vanish silently) → two seconds of nothing → **6-second summon sequence** (a bright point completing a circuit of the orbit, a column of light rising from the altar, the sound pulse tightening and rising) → the ender dragon appears **inside the altar's column of light** and bursts, then spirals out over 70 ticks onto the already-lit ring → the dragon presses toward the altar and takes 8 seconds to force the exit open → after the exit lands, it takes 10 seconds to **fly back** to the high orbit. The body must already be gone when the dragon appears; the opening particles and sound should be emitted along the line between the dragon and the altar rather than rising out of the altar by itself.

  **The dragon's flight must be continuous throughout**: from appearance, spiral exit, descent and opening the exit to climbing back to the high orbit, no segment may show per-frame jitter or convulsion (the body snapping back and forth in place, the head flipping every other frame) and no position or facing jumps — watch **the moment the exit lands** in particular, where there used to be a teleport of roughly sixty blocks.
3. Check the line placement: the first line "I have seen the way it looked at this place…" appears while the dragon is still opening the exit; the second line "Leave the rest to me. Go back…" appears at the same moment the exit lands, never before it.
4. Save and restart during the summon sequence: it should resume from the current resolution progress rather than replaying or skipping, and a second dragon must not appear.
5. Confirm the altar opens a 3×3 return exit after resolution.
6. Entering the exit must follow the vanilla `showEndCredits → WinScreen → PERFORM_RESPAWN` flow, replacing only this run's poem, credits and post-credits text; ordinary vanilla resolutions must still use the original resources.
7. After the poem is confirmed and the player is really back in the Overworld, view distance unlocks permanently from the per-dimension lock to 16.
8. The title screen should establish the ending lock: only Singleplayer, Multiplayer and Realms are disabled, while Options, Accessibility and Quit remain; the local successful save reads "sealed" and cannot be entered.
9. After resolution begins, ordinary anomalies, gap pressure, decay and personal pursuits must stop permanently; the success score must be released before the resource packs are restored, so it does not restart on the way home.
10. **After returning to the Overworld, quit to the title screen: the music must not break and must not switch to the menu track.** Wait in the Overworld for `music_game` to start, then pause menu → "Save and Quit". Through the save screen and until the title screen is up, the track currently playing must **continue uninterrupted** (not fade out and restart, and not become `music_menu`); later track changes on the title screen should still draw from `music_game`. Then enter any world and confirm the menu fade-out and load silence return to normal. This can only be judged by ear; adding `-Dthefourthfrequency.musicDebug=true` and reading the `[music]` lines helps confirm the track ID did not change.

### Failure

1. Let collapse reach 100% first; a lethal hit on the same tick must resolve as failure.
2. After an ordinary singleplayer/remote client completes the fixed failure sequence, the game must not close: it should return to the Overworld with the whole world rendered as missing textures, and "the run has ended, please return to the main menu" repeating at the bottom of the screen.
3. A published-LAN integrated-server host must not shut their server down; after returning to the Overworld, only their own client shows blocks, entities and fluids as missing textures, and LAN guests and the server world are unaffected.
4. Failure also establishes the title-screen ending lock; the local failed save reads "corrupted" and cannot be entered.
5. After a failure, return to the main menu from the pause menu and relaunch the client, confirming that quitting is always available and the Alpha presentation is retained until F8 recovery completes; a pending Windows recovery transaction must not retire Alpha early.

## F8 reset and save isolation

1. With an ending lock present, press F8 and confirm the recovery/replay confirmation text matches the success/failure outcome.
2. Confirming should restore the desktop, Notepad, window and resource-pack state the mod owns, clear the client sequence markers, and close normally.
3. After the restart, the exactly-matching local ending save should be un-enterable due to the `.thefourthfrequency-corrupted` isolation marker; the marker shows "sealed" for a recorded success and "corrupted" for a recorded failure.
4. Verify `level.dat`, region files and player data have not been rewritten by the mod; a new game must use a new save.

## Acceptance record template

| Item | Result | Evidence / notes |
| --- | --- | --- |
| First-run safety notice | ☐ | |
| New world and terminal mainline | ☐ | |
| 19 anomalies, tier pacing and anti-repetition | ☐ | |
| Weather sky monitoring and the two sky anomalies | ☐ | |
| Five-form teaching, pursuit and terminal appearance | ☐ | |
| Two-player mirror, streaming edges and refund recovery | ☐ | |
| Altar escrow/rollback transaction | ☐ | |
| Three forms and eight actions | ☐ | |
| Multiplayer target allocation (2 and 4 players) | ☐ | |
| Success poem, return and view distance | ☐ | |
| Ordinary failure and the LAN host branch | ☐ | |
| F8 recovery and save isolation | ☐ | |
