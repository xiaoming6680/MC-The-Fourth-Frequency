package com.xm.thefourthfrequency.client_ui;

import com.mojang.blaze3d.audio.Channel;
import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.mixin.ChannelStateInvoker;
import com.xm.thefourthfrequency.mixin.MusicManagerGainAccessor;
import com.xm.thefourthfrequency.mixin.SoundEngineStateAccessor;
import com.xm.thefourthfrequency.mixin.SoundManagerEngineAccessor;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;

import java.util.List;
import java.util.Map;

/**
 * Watches how much of the client's sound-channel pool the encounter is using, and writes it down.
 *
 * <p><b>Why this exists.</b> Players report that after a long stretch in one phase the boss's attack
 * cues go quiet while the music carries on. The obvious explanation is that the sound engine has run
 * out of channels - it holds a fixed pool, and when the pool is empty {@code SoundEngine.play}
 * <em>returns without playing anything</em>. The problem is that it does so silently: the one log
 * line that would have said so is guarded by {@code SharedConstants.IS_RUNNING_IN_IDE}, so on a
 * launcher instance a dropped sound leaves no trace at all. Rate-limiting the loudest emitters was a
 * reasonable first move and did not fix it, which means the guess needs to be replaced by a
 * measurement rather than by a better guess.
 *
 * <p>So this samples the same counter the F3 overlay shows, once a second, for as long as an
 * encounter is running, and logs the high-water mark whenever it climbs. Two possible readings, both
 * useful:
 *
 * <ul>
 *   <li>the peak approaches the pool size - the theory is right and the remaining work is to find
 *       what is holding channels, because the emission rate alone no longer explains it;</li>
 *   <li>the peak stays low - the theory is wrong, the sounds are being dropped or silenced somewhere
 *       else entirely, and the search moves off the channel pool.</li>
 * </ul>
 *
 * <p>Costs one string format a second while the finale is running and nothing at all otherwise.
 */
public final class WorldInterfaceAudioWatchdog {
	private static final int SAMPLE_INTERVAL_TICKS = 20;
	/** Only report a new peak once it has moved enough to mean something. */
	private static final int REPORT_STEP = 8;
	/**
	 * Ticks between plain "this is where it is now" lines.
	 *
	 * <p>Peaks alone were not enough to read the last report: the fight peaked at fifteen channels
	 * twenty-six seconds in and then said nothing for another two minutes, which is equally
	 * consistent with "it stayed busy" and with "it went completely silent". A heartbeat every thirty
	 * seconds is a handful of lines per encounter and turns that into a trajectory.
	 */
	private static final int HEARTBEAT_TICKS = 600;

	private static boolean armed;
	private static int ticksUntilSample;
	private static int ticksUntilHeartbeat;
	private static int peak;
	private static int reportedPeak;
	private static String peakDetail = "";
	private static int receivedCues;
	private static int receivedHurtCues;
	private static double farthestCueBlocks;
	private static final SoundEventListener CUE_LISTENER =
			WorldInterfaceAudioWatchdog::onPlaySound;

	private WorldInterfaceAudioWatchdog() {
	}

	/** Called once per client tick while an encounter snapshot exists. */
	public static void tick(Minecraft client) {
		if (!armed) {
			armed = true;
			peak = 0;
			reportedPeak = -1;
			peakDetail = "";
			receivedCues = 0;
			receivedHurtCues = 0;
			farthestCueBlocks = 0.0D;
			ticksUntilSample = 0;
			// Counted in samples, not ticks: the heartbeat is decremented on the sampling path.
			ticksUntilHeartbeat = HEARTBEAT_TICKS / SAMPLE_INTERVAL_TICKS;
			client.getSoundManager().addListener(CUE_LISTENER);
			TheFourthFrequency.LOGGER.info("World-interface audio watchdog armed: {}",
					describe(client));
		}
		if (--ticksUntilSample > 0) return;
		ticksUntilSample = SAMPLE_INTERVAL_TICKS;
		resumePausedHostileSources(client);
		repairHostileGain(client);
		String detail = describe(client);
		int playing = leadingCount(detail);
		if (--ticksUntilHeartbeat <= 0) {
			ticksUntilHeartbeat = HEARTBEAT_TICKS / SAMPLE_INTERVAL_TICKS;
			TheFourthFrequency.LOGGER.info("World-interface audio now: {} (peak {})", detail, peak);
		}
		if (playing <= peak) return;
		peak = playing;
		peakDetail = detail;
		if (playing >= reportedPeak + REPORT_STEP) {
			reportedPeak = playing;
			TheFourthFrequency.LOGGER.info("World-interface audio peak: {}", detail);
		}
	}

	/**
	 * Resumes only boss-category channels which OpenAL itself says are paused.
	 *
	 * <p>Calling the manager's global {@code resume()} as a heartbeat is not safe: OpenAL's play call
	 * can restart a source which is already playing. Reading each channel on the sound executor and
	 * touching only {@code AL_PAUSED} repairs a stranded pause without disturbing healthy one-shots,
	 * the encounter bed, or the score.</p>
	 */
	private static void resumePausedHostileSources(Minecraft client) {
		SoundEngine engine = soundEngine(client);
		Map<SoundInstance, ChannelAccess.ChannelHandle> channels =
				((SoundEngineStateAccessor) engine).thefourthfrequency$instanceToChannel();
		for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry :
				List.copyOf(channels.entrySet())) {
			if (entry.getKey().getSource() != SoundSource.HOSTILE) continue;
			entry.getValue().execute(WorldInterfaceAudioWatchdog::resumeIfPaused);
		}
	}

	private static void resumeIfPaused(Channel channel) {
		if (((ChannelStateInvoker) channel).thefourthfrequency$getState() == AL10.AL_PAUSED) {
			channel.unpause();
		}
	}

	/**
	 * Reasserts the invariant for the category that carries every boss attack and hurt cue.
	 *
	 * <p>The option slider is only one of two multipliers. {@code SoundEngine} keeps a separate
	 * {@code gainBySource} map which is applied to both existing and future sounds; its public debug
	 * string does not report that map. If the HOSTILE entry is ever left at zero, the option can still
	 * read 100%, packets still arrive and channels can still be occupied by always-play sounds, while
	 * every ordinary attack and hurt cue is rejected at zero volume. HOSTILE has no authored fade in
	 * either vanilla or this mod, so its engine multiplier has exactly one valid steady value: 1.</p>
	 */
	private static void repairHostileGain(Minecraft client) {
		float gain = engineGain(client, SoundSource.HOSTILE);
		if (Math.abs(gain - 1.0F) < 0.0001F) return;
		client.getSoundManager().updateCategoryVolume(SoundSource.HOSTILE, 1.0F);
		TheFourthFrequency.LOGGER.warn(
				"Repaired world-interface HOSTILE engine gain from {} to 1.000",
				String.format("%.3f", gain));
	}

	/** Called when the encounter goes away, so the session leaves one line with the whole answer. */
	public static void reset() {
		if (!armed) return;
		armed = false;
		Minecraft.getInstance().getSoundManager().removeListener(CUE_LISTENER);
		TheFourthFrequency.LOGGER.info(
				"World-interface audio watchdog finished; peak channel use was {}",
				peakDetail.isEmpty() ? "unsampled" : peakDetail);
	}

	/** Records cues after resolution, at the last point before the engine rejects or opens a source. */
	private static void onPlaySound(SoundInstance sound, WeighedSoundEvents events, float audibleRange) {
		if (!TheFourthFrequency.MOD_ID.equals(sound.getIdentifier().getNamespace())
				|| !sound.getIdentifier().getPath().startsWith("world_interface_")) return;
		receivedCues++;
		if (sound.getIdentifier().getPath().equals("world_interface_hurt")) receivedHurtCues++;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || sound.isRelative()) return;
		double dx = client.player.getX() - sound.getX();
		double dy = client.player.getY() - sound.getY();
		double dz = client.player.getZ() - sound.getZ();
		farthestCueBlocks = Math.max(farthestCueBlocks, Math.sqrt(dx * dx + dy * dy + dz * dz));
	}

	/**
	 * The sound engine's own summary, in whatever shape this version writes it.
	 *
	 * <p>Logged verbatim rather than parsed into fields: the format is vanilla's and the point of
	 * this is to record what the game says, not to re-state it.
	 */
	private static String describe(Minecraft client) {
		try {
			return client.getSoundManager().getDebugString() + " | " + describeMix(client);
		} catch (RuntimeException failure) {
			return "unavailable (" + failure.getClass().getSimpleName() + ")";
		}
	}

	/**
	 * The per-category volumes, and the music fade's own gain.
	 *
	 * <p>These are here because of what survived the last report: the one cue still audible was the
	 * lock tone, which is the only sound in the encounter played through {@code SimpleSoundInstance
	 * .forUI} - that is, on {@code MASTER}. The engine's volume lookup returns a flat 1.0 for
	 * {@code MASTER} and the player's option for every other category, so "only the master-category
	 * cue can be heard" is precisely the shape of a per-category volume that has gone to zero. Which
	 * category, and whether it is the option or the music manager's fade gain, is the difference
	 * between three completely different faults - so all of them are written down rather than
	 * reasoned about.
	 */
	private static String describeMix(Minecraft client) {
		StringBuilder mix = new StringBuilder("volumes");
		for (SoundSource source : new SoundSource[]{SoundSource.MASTER, SoundSource.MUSIC,
				SoundSource.HOSTILE, SoundSource.AMBIENT, SoundSource.PLAYERS}) {
			mix.append(' ').append(source.getName()).append('=')
					.append(String.format("%.2f", client.options.getSoundSourceVolume(source)));
		}
		mix.append(" engineHostile=")
				.append(String.format("%.3f", engineGain(client, SoundSource.HOSTILE)));
		mix.append(" clientCues=").append(receivedCues)
				.append(" clientHurt=").append(receivedHurtCues)
				.append(" farthestCue=").append(String.format("%.1f", farthestCueBlocks));
		MusicManagerGainAccessor manager = (MusicManagerGainAccessor) client.getMusicManager();
		mix.append(" musicGain=").append(String.format("%.3f", manager.thefourthfrequency$currentGain()));
		SoundInstance music = manager.thefourthfrequency$currentMusic();
		mix.append(" musicTrack=").append(music == null ? "none" : music.getIdentifier());
		return mix.toString();
	}

	private static float engineGain(Minecraft client, SoundSource source) {
		SoundEngine engine = soundEngine(client);
		Object2FloatMap<SoundSource> gains = ((SoundEngineStateAccessor) engine)
				.thefourthfrequency$gainBySource();
		return gains.getFloat(source);
	}

	private static SoundEngine soundEngine(Minecraft client) {
		return ((SoundManagerEngineAccessor) client.getSoundManager())
				.thefourthfrequency$soundEngine();
	}

	/**
	 * The first integer in the summary, which is the count of sounds currently holding a channel.
	 *
	 * <p>Only used to decide whether this sample is a new high; a format change makes it zero, which
	 * degrades the watchdog to "logs the first line and nothing else" rather than breaking anything.
	 */
	private static int leadingCount(String detail) {
		int start = -1;
		for (int index = 0; index < detail.length(); index++) {
			if (!Character.isDigit(detail.charAt(index))) {
				if (start >= 0) break;
				continue;
			}
			if (start < 0) start = index;
		}
		if (start < 0) return 0;
		int end = start;
		while (end < detail.length() && Character.isDigit(detail.charAt(end))) end++;
		try {
			return Integer.parseInt(detail.substring(start, end));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}
}
