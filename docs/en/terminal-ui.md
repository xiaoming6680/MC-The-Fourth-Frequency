# Terminal interface and handheld form

This is the single detailed description of the terminal's **appearance layer**: coordinate space, layout constants, palette, animation timing, the first-run boot sequence, and the item's own 3D model and handheld animation.

Gameplay truth is not here. Page semantics, tool unlock conditions and anomaly forms are in [Anomalies, terminal forms and personal pursuits](anomalies-and-pursuits.md); the mainline task chain is in [The World Interface finale](world-interface.md); protocol and persistence boundaries are in [Architecture](architecture.md).

## Coordinate space

The terminal draws on a 512×256 virtual canvas. `TerminalScreen#render` blits the panel backdrop in screen space, then `pushMatrix` → `translate(left, top)` → `scale(scale, scale)`, after which everything is drawn in canvas coordinates. The scale comes from `panelScale()`, clamped to `[0.55, 2.0]`.

`local(mouseX, mouseY)` converts screen → canvas, and all hit testing happens in canvas coordinates.

### The scissor trap

`GuiGraphics#enableScissor` **runs the current pose itself**. So the clip rectangle must be given in the terminal's own canvas coordinates and must never be pre-converted to screen pixels — that applies the transform twice and clips the whole picture away.

For the same reason, the clip must be opened **before** pushing the content offset, or the clip rectangle travels with the content and sliding content is never clipped:

```java
graphics.enableScissor(body.left(), body.top(), body.right(), body.bottom());
graphics.pose().pushMatrix();
graphics.pose().translate(dx, 0.0F);
// …draw…
graphics.pose().popMatrix();
graphics.disableScissor();   // must come before the outer popMatrix
```

Vanilla's `ScissorStack#push` intersects with the parent rectangle, so nesting is safe (the weather tool's instrument card clips inside the page clip).

## Layout

All `Bounds` constants live in `terminal/TerminalUiLayout` (**main source set**, so JUnit can test them without a client). `TerminalUiLayoutTest` pins: text and interactive areas must fall inside `DISPLAY`; the hardware column must fall inside `HARDWARE_SAFE` and must not enter `DISPLAY`; the three structural layers (tab strip / `PAGE_BODY` / `STATUS_BAR`) must not intersect.

| Layer | y range | Contents |
|---|---|---|
| Tab strip | 45–62 | HOME / TOOLS / RECORDS / FILES |
| `PAGE_BODY` | 70–199 | Current page content |
| `STATUS_BAR` | 202–215 | Holder / world time / link / protocol number |

Hardware column (x ≥ 389, outside `DISPLAY`): oscilloscope, compass, tuning slider, LCD, close hint.

> `FOOTER` is a historical alias for `STATUS_BAR`, kept only so the by-name containment lists in the tests are not broken.

That status-bar cell is called "link" rather than "band": the hardware column already has a tuning slider and an LCD talking about frequency, and a second "band" at the bottom would make players think the two are the same number. It reads `bandStage` — what level this device is authorised to connect at — and has nothing to do with the knob in their hand.

Every cell is a **dark fixed label plus a bright adaptive reading**. When the reading does not fit, it scales down first, and if it still does not fit at the pixel font's legibility floor it truncates with an ellipsis; the label **never scales**. Scaling label and value together as one unit meant a sixteen-character player name dragged "Holder" down with it, and past the scaling floor it simply overflowed into the neighbouring cell.

## No backing plates

**Hierarchy in this interface comes from lines, whitespace and value — never from smearing opaque blocks on top.**

The display area is already one sheet of glass (`GLASS_BACKDROP` translucent darkening) plus the CRT layer. Filling an opaque rectangle over it looks like a sticker on the device rather than the device's own screen — this rule was walked back from real-hardware observation, after the boot self-test, the onboarding brief line and the status bar had each smeared a plate of their own.

Only three kinds of fill remain permitted:

- **Translucent card bodies** (`CARD_BODY`) — already the page's language, and the glass shows through.
- **Instrument recesses** (oscilloscope, compass, slider, progress-bar track) — those are part of the hardware, not something laid over the screen.
- **Onboarding dimming** (`ONBOARD_DIM`) — it darkens existing content rather than replacing it.

When an area needs to be "cleared", the correct approach is to **not draw that area's content**, not to draw a plate over it. The boot self-test does exactly that: during `BOOT` the whole page is not rendered, because the terminal genuinely has not finished starting and no card should exist yet, so the self-test text lands on empty glass. Likewise, when onboarding writes guidance into the status bar, the resident readings **give way** rather than being covered.

## Palette

Everything is in `client_ui/TerminalVisualTheme`. `ResourceContractTest` pins nine core colours: they must be defined in that class and referenced inside the terminal screen in the form `TerminalVisualTheme.X`.

> **The 17 `import static` lines at the top of `TerminalScreen` must not be deleted.** The contract asserts that the source text contains substrings like `TerminalVisualTheme.GREEN`, and the import lines are the only place they match. An IDE's "optimize imports" silently breaks it.

### The CRT layer

The permanent overlay is drawn by `client_ui/TerminalChrome` and covers only `DISPLAY`:

- **Scan lines**: pitch 3, alpha 14, **pure black darkening**, **absolutely static**.
- **Vignette**: 6 rings of decreasing alpha.

Where the three constraints come from:

1. **No tinting.** There was once an alpha-7 pure green overlay; it tinted every character on screen and crushed the contrast. It was deleted, and a contract test forbids its return. Darkening is neutral; tinting is not.
2. **No scrolling.** A moving high-contrast edge across a whole readable area is the single most likely thing to draw a photosensitivity complaint. Rolling bands belong to the weather tool's sky-instrument "damage language", not to the permanent shell.
3. **Pitch 3, not 2.** At this alpha, pitch 2 halves the apparent brightness of the pixel font.

### Per-frame cost

The CRT layer plus structural decoration is about 105 quads per frame (59 scan lines + 24 vignette + roughly 20 for corner marks and the title bar), roughly double the ~120 before the rework and still inside a single GUI batch. Check against this number before adding another layer.

## Animation

Timing constants and easing curves live in `terminal/TerminalMotion` (main source set, a pure class, directly tested by `TerminalMotionTest`). Runtime state is in `client_ui/TerminalMotionState`.

### Which clock does what

| Purpose | Clock | Why |
|---|---|---|
| Page transitions, tab indicator, press feedback, staggered card fade-in, boot typewriter | Linear on a millisecond timestamp | Has a definite start instant; JUnit can assert "given t, return what" directly |
| Smooth scrolling, task progress bar | Frame-delta exponential `1 - exp(-dt/τ)` | The target changes repeatedly in flight (rapid wheel input), and a restart-style transition stutters |
| Breathing pulse | `renderAge` (ticks) | The 3 Hz safety test reasons in ticks |

> **New code must not express duration with `renderAge`.** Its integer part is pinned to the tick rate, so server lag directly changes animation speed. Use the millisecond clock already computed in `render()`.

Frame delta is clamped to 100 ms, so alt-tabbing back does not run the whole animation in one frame.

### Smooth scrolling

The four scroll positions (records / tool detail / file list / file body) keep their **integer fields authoritative** — those are simultaneously the hit test, the clamp ceiling and the contract assertion point. Smoothing is a display layer only: each frame chases the target in pixels, and at draw time it converts to a first-row index plus a pixel offset, draws one extra row, and wraps it in the page clip.

**Hit testing does not follow the animation.** During the animation, the row clicked is the one in the integer sense, matching where it settles on release; otherwise clicking mid-scroll misses.

Three places must snap instantly: changing pages, resetting the file view, and a snapshot update clearing the cache. Otherwise a new page plays a scroll it never had.

## First-run boot sequence

Appears only the first time a new save opens the terminal, and is **one-off, not re-watchable**. The phase policy is in `terminal/TerminalOnboardingPolicy` (main, pure class).

| Phase | Advance condition | Permitted input |
|---|---|---|
| `BOOT` | Self-test finishes (about 3.1 s) | All swallowed; any key completes the current line at once |
| `STEP_1`–`STEP_4` | The player clicks the current target tab | Only the target tab and its number key |
| `RELEASED` | Damage fallback triggered | Everything restored |
| `DONE` | 40 ticks after the fourth step lands | Everything |

The step order is **TOOLS → RECORDS → FILES → HOME**. The terminal opens on HOME, so leading with HOME would make the first step a same-page click — correct data, but nothing changes on screen, and a new player's first guided action gets zero visual feedback. With the reordering every step has a real transition, and the last step returns the player to HOME where the task card is, so they see the progress bar fill and the reward arrive on the same screen.

### Relationship with the first task

Every onboarding step issues a **real page-change request** on the same server path as the player clicking a tab themselves. After four steps, `learn_terminal` naturally reaches 4/4 and the reward is granted by the server's existing completion path.

**The client has no route by which it can advance a task itself**, and it writes no authoritative state. This is deliberate: a "the animation finished" trust hole immediately becomes a reward farm.

### The completion hold (`COMPLETION_HOLD_TICKS`)

The reward and the completion happen on the same server tick, and the snapshot carrying that news **is already talking about the next task**. So before the hold was added, the task card had never once drawn a completed task: the progress bar the player was watching was replaced by an empty bar for a new objective they had not read, while the reward had already gone into the inventory, and nothing on screen accounted for it. The first task made this worst — it completes inside the boot sequence, so the player clicked four tabs and 6 bread appeared out of nowhere.

The trigger is `objectiveIndex` increasing. That index only moves forward, and only **after** a task's reward has actually been granted, so an increment is the server saying "the thing you were just looking at is done and paid". At that point the client pins the **previous** snapshot's objective line (rewritten as n/n), reward item and count onto the card for 60 ticks (3 seconds), during which:

- The card border, progress bar and reward frame all use the `CLAIMABLE` palette;
- The progress bar completes its last segment rather than jumping — the fill is precisely what the hold exists to show;
- The objective line reads n/n, with a "task complete" label kept in the lower right;
- After the hold, the progress bar climbs from 0 toward the new task rather than falling from full — those two numbers belong to different objectives, and the animation would draw a regression that never happened.

Three seconds is longer than onboarding's own 40-tick closing prompt, so "onboarding complete" and the card's completed state appear on the same screen rather than one after the other.

**The card does not carry a line like "granted: bread ×6".** The card has already said the same thing four times — objective line n/n, the bar filled, the whole card in the completion palette, the reward frame drawing the item and its count — and one more sentence is the card still talking after it has finished.

The same event is carried by the notification stack while the terminal is **closed**: `message.thefourthfrequency.task.completed_reward_claimed` announces the task name together with the reward ("Task complete: Learn the terminal · Bread ×6"). Task names are a separate key group `terminal.thefourthfrequency.task.name.*`, not the objective line — the objective line carries an instruction and a count, which fits the card being read but is far too long for the line above the hotbar. `TerminalTaskService.taskName` writes those keys out case by case rather than assembling them from an id, for the same reason as the six boot self-test lines: an assembled key evades the contract test. The notification stack is **frozen** while any screen is open, so during onboarding that notification only surfaces after the player closes the terminal — which is exactly why the card hold has to exist, not a reason the notification could replace it.

### The exit lock

The terminal cannot be closed during onboarding. This is the only exception to the "exit paths" boundary in the [World bible](world-bible.md), and it carries four testable conditions (one-off, definite end, safe release, server-overridable) that the implementation must match one by one.

- While locked, the close hint is replaced by "exit unavailable". **A screen that says "press Esc to exit" while Esc does nothing is absolutely forbidden.**
- Any damage taken unlocks it immediately with progress preserved. The terminal does not pause the world, so "cannot close" during a fight means "cannot fight back".
- A server-forced close always outranks it.
- Disconnection, rejoining and server restarts resume from the current step; once any tab has been clicked, the boot self-test never replays.

### The per-step brief

The pointer only says which tab to click, which tells a new player where to put the mouse but not what they are looking at. So every step adds a **one-line brief** at the bottom of the page describing what the **currently displayed page** does, opening with that page's tab name.

The brief body (`onboarding.brief.*`) and its wrapper (`onboarding.brief.current`, of the form "Current page 'Home': …") are two separate keys, and the tab name comes straight from the tab strip's own `tab.*`, so the name at the start of the brief matches the text printed on the tab in front of the player character for character.

| Current page | Brief |
|---|---|
| Home | Current objective, suggested tool and the latest record |
| Tools | Six field tools, unlocked one by one along the mainline |
| Records | Events the terminal has logged, newest first |
| Files | Recovered files, and that incomplete journal |

**It describes the current page, not the page the arrow points at.** An early version described the arrow's target, which produced two contradictory lines on one screen: the status bar said "select the 'Tools' tab" while the line below introduced tools as though the player were already there, and the Home page actually in front of them went unexplained. The choice is made by the pure function `TerminalOnboardingPolicy.briefSubject(phase, currentPage)` and is unit-tested directly.

Switching to the current page also happened to give perfect coverage: the terminal opens on Home and the step order is Tools → Records → Files → Home, so the pages standing behind the four steps are Home, Tools, Records and Files — each page introduced exactly once.

Its position is `TerminalUiLayout.ONBOARD_BRIEF`, inside the page area onboarding has **already dimmed**, and **drawn only during onboarding** — so it never occupies the normal layout and can never conflict long-term with any page's own content.

One line is deliberate: this is guidance a player sees once in their life, and four things are already competing for attention on that screen. A paragraph would go unread and would push the pointer off the page. Long translations scale by the terminal's general rule and are not truncated.

### How highlighting works

Darken the complement, no full-screen mask: the four bands of `DISPLAY` minus the target tab are darkened, and **not one pixel of the target tab is covered** — its background, text and unread blink all continue as normal. The hardware column, status bar and close-hint area are not darkened.

The outline pulse is a continuous raised cosine with a 2-second period (0.5 Hz), far below the 3 Hz ceiling. A continuous function has no "state change" to speak of, so the minimum hold time is trivially satisfied — but the test still asserts that the difference between adjacent ticks is bounded, because that is the testable form of "it really is continuous and not a square wave wearing a sine's name".

## The item itself: 3D model and handheld animation

### Model

The terminal is **a fixed, one-piece, heavy handheld device**. It does not flip open, fold, unfold, slide, or deform mechanically in any way. This is a product boundary, not modelling laziness: the device is a sealed instrument, and the moment it opens and closes, "is this machine working?" becomes something the player reads off a hinge rather than off the screen.

- Six 128×128 UV atlases `textures/item/old_terminal_shell_0.png` … `_5.png`, generated by `tools/generate_terminal_3d_assets.py`.
- Six item models `models/item/old_terminal_held_0.json` … `_5.json`, each with all eight `display` perspectives.
- Blockbench-editable source: `docs/art/terminal/old_terminal_shell.bbmodel` (geometry + form 0 atlas).

The geometry is 5 elements: a 14×7×2.5 body carrying the whole front face, plus four protruding brass trim pieces (top/bottom bars and left/right corner guards), giving 15×8×3 including the trim. **The geometry of all six forms is byte-identical**; only the atlas differs, and the contract test compares the serialised `elements` directly.

The front is 2:1, matching the 512×256 panel backdrop, and the CRT takes about 62% of the width — the same proportion `TerminalUiLayout.DISPLAY` has in the panel. That ratio is guarded by a contract test: an early version made the body nearly square, turning the horizontal CRT into a portrait screen, and the thing in hand immediately stopped looking like the thing that opens. The atlas is 128 rather than 64 because at 64 the right-hand hardware column is only 11 pixels wide and the oscilloscope and compass cannot be told apart.

The front arrangement follows the UI and the concept sheet `docs/art/terminal/terminal_six_forms_full_controls_concept.png` exactly: a large recessed CRT on the left; on the right, top to bottom, the signal oscilloscope, a circular compass, the tuning slider, a two-line LCD and the close-hint area at the bottom; **plus a separate small amber unread lamp to the right of the oscilloscope**. Black cast iron, worn brass corners and four corner screws are retained, and the cyan and red stages add oxidation cracks creeping along the trim.

The item definition branches on `minecraft:display_context`: **the inventory slot** uses the existing flat icons `old_terminal_0`–`old_terminal_5`, while every other view (first person, third person, ground, item frame, head) uses the 3D shell. Both branches take the same 0–5 from **slot 0** of `custom_model_data`, so the device in hand and the icon in the inventory cannot disagree about the form.

Keeping the flat icon in the inventory is deliberate: it carries two pieces of information — the stage and the unread lamp — and an angled box is harder to read in an item slot than a drawn icon.

### The six forms

| Form | Stage | Unread lamp |
| ---: | --- | --- |
| 0 | Green, ordinary | Off |
| 1 | Green, ordinary | **On** |
| 2 | Cyan, active | Off |
| 3 | Cyan, active | **On** |
| 4 | Red, anomalous | Off |
| 5 | Red, anomalous | **On** |

An odd form is its even neighbour **with the lamp lit** and not one other pixel changed. The contract test compares pixel by pixel: all differences between 0 and 1 must fall inside the lamp's 6×6 window, and must be non-empty. That constraint *is* the lamp's semantics — the player sees amber come on and knows it means "something is waiting for you", not "this machine changed".

The mapping is still `visual stage × 2 + (attentionActive ? 1 : 0)`, written to `custom_model_data` slot 0 and projected by `TerminalData.applyAttentionProjection`.

### Animation

The state machine is `client_ui/TerminalHandheldAnimator`; every number lives in the pure class `terminal/TerminalHandheldPose` (common source set, directly unit-testable).

The device itself produces no opening animation at all. It is **a heavy two-handed instrument**: the player holds it in both hands, and what is animated is **the camera moving up to the screen**.

1. **Idle**: held in both hands below the line of sight, body tilted back about 25° so the screen faces up — a glance down is enough to read it. Low-amplitude breathing sway; this is the permanent picture, so the amplitude must be small.
2. **Open**: right-click → the terminal lifts, scales up and rotates square to the camera while the FOV narrows moderately → `TerminalScreen` opens only once the screen fills the frame.
3. **Close**: the reverse — the UI disappears first, the player sees the terminal held up in front of them, then it settles smoothly back into the carrying position.

**The player's position in the world does not move, and neither does the third-person camera.** The FOV is multiplied by a factor at `getFov`'s return by `GameRendererTerminalFovMixin`, active only in first person, deviating from 1 only during the sequence, and narrowing by at most 12%.

The sequence takes over the whole first-person path of `ItemInHandRenderer.renderHandsWithItems`, rather than adding transforms to vanilla's one-handed pose. Every position is written as **absolute camera-space coordinates**, and the device hangs off neither arm. That is the precondition for controlling its size: an early version let the device follow vanilla's item transform toward the near clip plane, scaling it 2.5×, at which point the brass trim went through all four edges of the frame and through the arms.

> **Taking over that method means flushing yourself.** It ends with two lines:
>
> ```java
> this.minecraft.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
> this.minecraft.renderBuffers().bufferSource().endBatch();
> ```
>
> Rendering in 1.21 is two-stage: `renderItem` only **submits** nodes to `SubmitNodeCollector`, and the actual drawing happens in that flush. Cancelling the whole method cancels the flush too, and the submitted terminal is stranded until the **next frame's** flush at the top of `renderItemInHand`, using that frame's matrices — **always one frame late, stuck to the previous frame's camera pose**. Standing still it is invisible; the faster the head turns the further it drifts, and a fast look-around flings the device out of frame entirely, only to return to the centre when you stop. This is not a pose problem — the pose has no view term in it at all; it is drawing deferred into a different matrix context. The `terminal-3d` client suite's four-angle assertion exists for this regression.

#### Equip animation on switching in and out

Equip height still reads vanilla's own `mainHandHeight`, so selecting the terminal in the hotbar still raises it into frame from below, and switching away still lowers it. That is deliberately kept, but it conflicts directly with "the equip animation must not replay repeatedly", and the conflict is in how vanilla decides "the item changed":

`ItemInHandRenderer.tick` calls `shouldInstantlyReplaceVisibleItem`, which compares the two stacks' components and skips only component types that declare `ignoreSwapAnimation`. **Neither `custom_data` nor `custom_model_data` declares it.** The terminal changes both: the former on every sync, the latter whenever the unread lamp or visual stage changes. Left alone, an open terminal drops out of the hand and climbs back several times per second, and receiving one signal looks like the player swapping items.

So the mixin also hooks `shouldInstantlyReplaceVisibleItem`: **terminal-to-terminal replaces instantly with no animation**, everything else takes the vanilla path. The scope is exactly that one case — switching to the terminal, switching away from it, and swapping between two other items all keep vanilla's equip animation.

#### The size is pinned by the frame, not dialled in

`OPEN_SCALE`'s upper bound in `TerminalHandheldPose` is determined by two things, both easy to miss:

- **Narrowing the FOV magnifies everything in frame.** A size computed against the un-narrowed field of view overshoots by roughly 15% once drawn.
- **The constraint is on the wide edge.** The device is 2:1, so what pins it is the **width** of the narrowest window (4:3), not the height of the widest.

At the current value the device occupies about 92% of a 4:3 frame's width and 66% of its height. The unit test computes that geometry directly and asserts it stays inside.

#### The device is drawn nearer than the hands

`BASE_Z = -0.45`, whereas vanilla held items sit at -0.72. **First-person hands are depth-tested against the world**, and any surface nearer than them eats them — that is why vanilla hands sink into a wall when you stand against it. Vanilla gets away with it because held items are small and in the corner of the frame; this device is large and centred, so the same occlusion erases it **entirely**, and a glance at the ceiling is enough. Halving the distance roughly halves how near a surface has to be to occlude it.

Visually nothing changes: apparent size and screen position are both "offset ÷ depth", so `REST_SCALE`, `OPEN_SCALE`, `REST_Y` and `OPEN_Y` were all scaled by the same factor as the depth came in.

#### Where the hands go

The arms are **human-sized geometry and must stay at vanilla's own depth** (-0.72) — pulled in with the device they would scale up into giant hands. So the two sit at different depths, and under perspective **the same world offset lands at two different screen positions at two depths**.

The hand position is therefore not taken from the device's world coordinates. Instead the device's lower edge and side wall are converted to **screen angles**, and those are converted back into world offsets at the arm's depth (`TerminalHandheldPose.screenAligned`). Handing the device's coordinates straight to the arms gives arms at a plausible distance but in the wrong place on screen — which reads as **the hands not gripping the machine**.

| Axis | Basis |
|---|---|
| Y | Screen angle of the device's lower edge → world height at the arm's depth, plus vanilla's own sink amount |
| Z | Fixed at vanilla's depth; does not follow the device |
| X | Screen angle of the device's side wall → world lateral distance at the arm's depth, minus vanilla's own hand spacing |

It must also vary with scale: the device nearly doubles in size from idle to open and the arms do not. A fixed offset only looks right at one openness; everywhere else the hands slide inward along the body — which looks like losing grip.

#### Turn inertia

Vanilla has this inside `renderHandsWithItems`:

```java
poseStack.mulPose(Axis.XP.rotationDegrees((getViewXRot(pt) - xBob) * 0.1F));
poseStack.mulPose(Axis.YP.rotationDegrees((getViewYRot(pt) - yBob) * 0.1F));
```

`xBob`/`yBob` are the smoothed view angles, so the difference is how much further the head turned than the hands, and one tenth of it is how far the hands should lag. **Taking over the whole method skips it**, so it must be reproduced — otherwise the device is welded rigidly to the lens, which players notice first precisely because it is large and centred.

#### When not to take over

- **Something in the off-hand**: filling both hands with the terminal would silently stop drawing the other item — which is also vanilla's rule for maps. In that case it falls back to the one-handed path, with only a little tilt and breathing added by the `renderItem` hook.
- **An anomaly is hiding the hands**: `ItemInHandRendererAnomalyMixin` cancels this method for the detached second-person camera, and taking over would draw the hands back in.

Third person and all other display contexts pass straight through.

**Nothing is written to the item stack — not one byte.** The old implementation wrote a frame number to the client-side copy of `custom_model_data` slot 1; that slot is now retired entirely. Vanilla's `ItemInHandRenderer.tick` replays the equip swing the moment it sees the visible stack change, so any per-frame component write means re-equipping the terminal several times per second for the whole sequence.

**Abort paths must be complete**: switching held items, dying, changing dimension, disconnecting or receiving a server close command during the animation must all reset immediately and discard the staged snapshot. Also, the server pushes a snapshot roughly once per second while the terminal is open — a duplicate snapshot may only update the staged content and must never restart the opening animation, or the device stops half way forever and the interface never appears. The reverse must also be continuous: pressing close half way open, or reopening half way closed, derives the new phase's start time from the current openness rather than starting over.

## The unread lamp

### Position

`TerminalUiLayout.UNREAD_LAMP = (472, 46) – (488, 62)`, in the strip freed up **to the right of the oscilloscope**. The oscilloscope's right edge moved from 484 to 468 to give up those 16 pixels; `COMPASS`, `RECEIVER_SLIDER`, `RECEIVER_LCD` and `CLOSE_HINT` did not move by a pixel.

It gets an area of **its own** rather than borrowing a corner from a neighbour: every instrument in the hardware column already answers a question (where is north, how strong is the carrier, what does the receiver think), and layering a second meaning onto any of them makes that instrument ambiguous exactly when it most needs to be trusted. The test asserts the strong form — the lamp does not intersect any of those five controls (including both LCD text rows).

### State

The lamp's on/off state and the item's six forms use the **same** `attentionActive`, from four sources:

- An unread signal;
- An unread file;
- Navigation complete but unacknowledged;
- A claimable task reward.

The pure rule is `TerminalAttentionPolicy.attentionActive(int, int, boolean, boolean)`; the single entry point that reads the fields out of a record is `TerminalData.attentionActive(CompoundTag)`, used by both the item projection and the snapshot send. The UI **does not reimplement an approximation** — "navigation complete but unacknowledged" is not on the wire at all, and any approximation assembled from existing fields will contradict the item in the player's hand. The boolean ships with the main snapshot as `attentionActive` (v13).

### Appearance

- `attentionActive=false`: dark lamp cover plus brass bezel. The cover is dead, but the housing is still there, so the player knows an indicator lives here.
- `attentionActive=true`: the amber core lights and breathes slowly and slightly.
- **The unread lamp stays amber when the stage turns cyan or red.** It reports whether something is waiting for you, and the stage does not change the answer to that question; recolouring it with the stage would suggest it started reporting something else.

The pulse is a continuous raised cosine with a 2.4-second period (0.42 Hz) — one seventh of the 3 Hz ceiling — with intensity between 0.55 and 1.0, **never falling to zero**. Not a blink: blinking is what an eye reads as "alarm", and this lamp may stay lit for a whole session as long as something is unread. The curve is `TerminalUiLayout.unreadLampIntensity(double)`, which accepts fractional ticks (callers pass `renderAge`), and the test samples at 0.05-tick intervals asserting bounded adjacent differences — otherwise it is a 20 Hz staircase wearing a sine's name.

Drawing is in `client_ui/TerminalLampRenderer`, a separate file (see the technical-debt note below).

## Known technical debt

`ResourceContractTest` uses **source-text assertions** on `TerminalScreen.java` (about 18 asserted strings exist only inside drawing method bodies), which effectively pins it as a two-thousand-line file that cannot be refactored — any attempt to move drawing into a new file turns the contract red.

The real fix is replacing those assertions with behavioural tests (the class already exposes about 50 `*ForTesting` hooks), not moving code to dodge them. Until then, new drawing code goes into new files and existing drawing stays put.
