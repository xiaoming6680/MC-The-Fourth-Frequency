# Background music

`MusicDirector` takes over two vanilla seams through `MinecraftMusicMixin`: `getSituationalMusic` decides what plays, and `getMusicVolume` decides the target gain for fades. Scheduling, fading and the "now playing" toast therefore all remain vanilla behaviour.

This document covers music only. Sound-effect mix headroom is in [The World Interface finale](world-interface.md); client lifecycle is in [Architecture](architecture.md).

## Situation table

The first matching row from the top wins.

| Situation | Result |
| --- | --- |
| Pursuit mirror world (already active during the black screen and loading screen) | `music_pursuit` (1 track, looping) |
| All other pursuit phases (warning, capture, escape resolution, return) | Silence |
| After stepping into the finale portal (including the End poem, credits and post-credits) | Success `music_ending` / failure `music_ending_failure` (1 track each, looping) |
| World Interface summoning and phases 1–2 | `music_encounter` (1 track, looping) — **from the first tick of the summon**, not from the ritual's last beat |
| World Interface phase 3 | `music_encounter_final` (1 track, looping) |
| Both resolutions and the portal opening | Silence |
| Loading and transition screens (`LevelLoadingScreen` / `ProgressScreen` / `GenericMessageScreen` / `ConnectScreen` / any overlay) | Silence |
| Main menu / no player | `music_menu` (4 tracks), but only once the safety notice releases it |
| Safety notice not yet released | Silence |
| Any other boss bar flagged with music (ender dragon, wither) | Silence |
| Ordinary gameplay | `music_game` (7 tracks, 4800-tick / 4-minute interval, still subject to the music-frequency option) |

Silence before the notice is released exists because that screen is still body text the player has to read, and music would compete with it. "Released" is taken at the moment the acknowledge button is pressed, not at `FirstRunNoticeController.acknowledge()` — the latter waits out a 28-tick exit animation. Waiting for it would put the music a second and a half behind the player's decision, with the fade-in after that, landing the music behind the very main menu it is supposed to underscore. By the time the button is pressed the text has long been read, and silence is no longer protecting anything. The same entry also zeroes `nextSongDelay`: the menu track's own 20-tick gap is for the space *between* tracks and should not sit in front of this one starting.

The quiet after defeating the World Interface is deliberate in the same way: the ending track waits until the player actually steps into the portal. At that moment the server sends the poem packet, `WorldInterfaceVanillaPoemClient` latches the ending kind, and the score picks one of the two tracks accordingly. A win releases that latch after poem confirmation but before the resource packs are restored, so the reload cannot pull the ending track back up; a loss does not release it, because everything after that point is the remainder of this run.

A loading screen is not a *scene*; it is the gap between two scenes. Letting the menu track sit under the progress bar means carrying whatever the menu randomly picked into the place the player just went.

## The menu fade-out on world entry

This is the only fade-out **racing something else**, so it does not use the vanilla curve: it is a 10-tick (0.5 s) linear ramp.

Entering a world hard-kills whatever is playing twice, and neither kill can be lengthened: `Minecraft#updateLevelInEngines` calls `soundManager.stop()` outright, and switching to the Alpha resource pack makes `SoundManager#apply` call `SoundEngine#reload()`, rebuilding the whole engine. Worse, the window available for a fade is neither fixed-length nor continuous — the main thread blocks while save data is read, and not a single tick runs during that time; the usable ticks only start once it begins waiting for the server to be ready. A fade measured in seconds necessarily loses that race and gets cut off half way, which sounds identical to just disappearing. That is exactly why the earlier "steepen it to 1.5 seconds" was still not enough.

Linear rather than eased, because easing crowds most of the decay into the first few ticks and leaves a long tail — compressed to 10 ticks that sounds like something was sliced off rather than faded out. An equal-length straight ramp is short, but it is unambiguously a fade. The ramp is recomputed each tick from the recorded start value (rather than multiplied tick by tick), so vanilla easing the same value on the same tick cannot pull it off course. Completing the ramp actively calls `stopPlaying()`: by then it is inaudible, but the track has to be genuinely handed back, or a zero-gain song holds the channel until the world switch wipes it.

A single `stopPlaying()` fallback remains on the load-complete edge, for loads too short to fit even a 0.5-second ramp. Pursuits are exempt from both, because what the black screen is covering *is* the mirror-dimension switch, and the pursuit track is fading in underneath it.

## Quitting to the title after the win: the score follows the player

This is the third exemption, and the only time the score has to keep playing across a world unload.

The state machine is the pure class `client_ui.EndingScoreHandoff` (main source set, directly unit-testable), with three states:

| State | Entered when | Effect |
|---|---|---|
| `OFF` | Default | Business as usual: the menu track owns the title screen, loading screens are silent |
| `ARMED` | The ending is confirmed **and** the player is back in the world **and** not loading | Loading gaps score as ordinary gameplay music; the entry fade-out and the load fallback are skipped; the music channel survives the disconnect |
| `HOLDING` | No world and no loading screen (already on the title screen) | The title screen keeps scoring from `music_game`; the menu track does not take over |
| Back to `OFF` | Any loading screen or world is entered again | Normal behaviour resumes, entry fade-out included |

Three details are required:

1. **It cannot arm at poem confirmation.** Win cleanup restores the resource packs, which is a full resource reload and raises a loading overlay. Armed at that point, the reload would be underscored by ordinary gameplay music — exactly the "menu track under the progress bar" this chapter has been preventing, only in the other direction. So it waits for the world to come back.
2. **It must arm before the quit.** Quitting gives no lead time: `Minecraft#disconnect` tears down the world internally and stops all sound in the same call.
3. **It must bypass the engine-wide stop.** The `soundManager.stop()` inside `Minecraft#updateLevelInEngines(ClientLevel, boolean)` clears every channel, music included, and no fade can lengthen it. `MinecraftEndingScoreCarryMixin` redirects only that one call, and only when the hold is `ARMED` **and** the level being loaded is null — entering a world goes through the same line, and the deliberately timed menu fade there must not be touched. After the redirect it stops per `SoundSource`, skipping `MUSIC`: every instance the engine registers belongs to some category, so nothing but music survives.

The failure ending does not participate: its `scoredOutcome` latch is never released, `music_ending_failure` scores all the way to the locked menu, there is no gap for the menu track to take over, and the whole failure sequence has its own audio plan.

## Why the pursuit track waits for the mirror world

`music_pursuit` keys on "the client has the mirror world", not "the black screen started". The reason is vanilla: `ClientPacketListener#handleRespawn` contains an unconditional `getMusicManager().stopPlaying()` that runs on every dimension change. A track started *before* the teleport gets cut off mid-fade-in, and because `music_pursuit` has `minDelay` 0 the manager immediately replays it from the first bar — which sounds like the track starting and then snapping back to the beginning. Starting it *after* the teleport costs nothing: the black screen covers both the teleport and the loading screen, so the fade-in still happens underneath it.

## No click on the first note

Gain is only pushed into the audio engine inside `MusicManager`'s own fade (`updateCategoryVolume`), and that fade only runs while something is already playing. So on the "previous track hard-stopped → next track starts" path, the engine still holds the previous track's category volume: the new track's first tick plays at that volume for a full 50 ms before the next tick drags it back to the fade-in start. That is a click, landing exactly where a fade-in was supposed to be. `silenceGain` therefore zeroes the gain **and** the category volume together, so the entry really does start from silence.

## The same track never plays twice in a row

Minecraft has no playlists. Multiple `sounds` entries under one event in `sounds.json` are a **weighted random pool** drawn independently on every play, so seven ordinary gameplay tracks will play the one that just finished again roughly every seventh handover. That is the kind of repetition a listener always notices — two halves back to back do not sound like a random collision, they sound like something got stuck.

The rule is declared by `audio/MusicRotationPolicy` and enforced by `AbstractSoundInstanceRotationMixin`. The single place the draw happens is `AbstractSoundInstance#resolve` (`this.sound = events.getSound(this.random)`), which is also the last point the choice can still be changed; past it the engine holds a `Sound`, not a pool. **It cannot hang off `WeighedSoundEvents`** — that class does not know its own event id, so hanging there could not tell the score apart from the eight attack sounds that are supposed to draw freely.

On a repeat it **re-draws** rather than picking by index, so vanilla weights are preserved: hand-picking another entry quietly flattens the weighting, while re-drawing and rejecting the repeat leaves every other entry's relative probability intact. Re-draws are capped at 8, because a repeat is a blemish and a hang is a fault.

Only `music_game` (7 tracks) and `music_menu` (4 tracks) rotate. Single-track events (pursuit, both encounter phases, both endings) have nothing to say about repeats and are excluded automatically; the World Interface's attack sounds carry three variants each precisely so the same move sounds different, and banning repeats there would cancel the point of having variants. `MusicRotationPolicyTest` cross-checks the declared list against the actual pool sizes in `sounds.json` in both directions, so adding a track and forgetting about it is a test failure rather than something an ear finds months later.

## The ordinary gameplay interval

`music_game` uses `Music(holder, 4800, 7200, false)` rather than vanilla's `Musics.createGameMusic` (10–20 minutes). **Only the lower bound actually matters**: the manager re-rolls within that range every tick and takes the minimum against the current value, including during the several thousand ticks a track is playing, so the drawn value is pushed to the bottom of the range and the upper bound is essentially a statement of intent.

Vanilla's pacing was designed for "one playlist shared by the whole game". Seven tracks averaging 2 min 11 s at a 10-minute interval means music is audible less than a fifth of the time and one full rotation takes over eighty minutes — a player could finish a whole stretch of the mainline without hearing half of them. At 4 minutes the cycle is about 6 min 10 s, audibility about a third, and a rotation about 43 minutes, which fits inside one normal session. Pushing it lower would start contradicting the work: here silence is the default state, and music starting tells the player this moment was composed and therefore safe. The gaps do not sound like a disconnection because the signal bed is underneath them.

The player's music-frequency option still applies: "Frequent" caps at 12000 ticks, above 4800, so it has no effect; "Constant" is hard-coded to 100 ticks in the manager and overrides any track's own pacing.

## Music from the first tick of the summon

The summon used to be entirely silent until the ritual's `MUSIC_HANDOVER` beat, on the grounds that thirteen seconds of descent were already carried by the rising cue and ten anchor chains, and a track underneath would flatten both. The actual effect was that the mod's largest entrance **had no music**, and the track arrived after the thing had already landed. It now returns `music_encounter` from the summon's first tick and fades in during the descent — a fade-in has contrast precisely because it starts from zero; and colliding with the previous track is what the handover mechanism is for.

## The combat handover

Every other track change has a deliberate silence in between that vanilla's fade curves cover exactly: target gain drops to 0, the old track fades out over roughly 15 seconds and the new one fades in over roughly 10. The boss fight is the only exception — ordinary gameplay music yields directly to the summon, and the first two phases yield directly to the third. At vanilla speed those two handovers are either a hard cut or a 25-second hole in the middle of a fight.

So `MusicDirector` takes over both handovers using the same two curves, much steeper:

1. **Fade-out** — `situationalMusic` returns null during the handover (so target gain is 0) while each tick multiplies an extra 0.80 on top of vanilla's 0.97 decay, clearing the channel in about 1.5 seconds, after which vanilla calls `stopPlaying()` itself.
2. **Start** — `nextSongDelay` is zeroed. Every stop adds 100 ticks to it, and the "Constant" music-frequency option pins the interval at 100 ticks; either would conjure 5 seconds of extra silence.
3. **Fade-in** — seven extra vanilla fade-in steps per tick, back to target gain in about 1.2 seconds.

The whole handover is slightly under 3 seconds, which fits inside the transformation animation. `replaceCurrentMusic` must therefore be **false** for `music_encounter` and `music_encounter_final`: that flag is evaluated inside the music manager's own tick, before this class sees the frame, and leaving it set would cut the old track before the handover begins. All other tracks keep 0 delay and immediate replacement.

Silence is reached by fading the gain rather than stopping outright, so leaving a situation is a track exiting rather than being cut. Vanilla's gain only eases while something is playing and starts at 1, so `MusicManagerGainAccessor` pushes it to 0 on the "nothing playing, but something is about to" edge, making every entry a fade-in. That write is conditional on `currentMusic == null`, so a single frame of situation jitter cannot cut a track that is currently fading out. There are exactly two hard cuts — the pursuit black screen and being captured — where the fiction is that the signal was severed; the black-screen cut exists precisely so the pursuit track fades in out of real silence.

## Assets

Audio ships as 44.1 kHz stereo Ogg Vorbis (q4). Master gain is baked into the files at 40% by `tools/import_music.py` at import time rather than written into `sounds.json`. That ratio is always relative to the lossless master, so re-importing does not compound it. See [Art and asset pipeline](art-pipeline.md).
