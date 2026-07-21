package com.xm.thefourthfrequency.audio;

import com.xm.thefourthfrequency.bootstrap.RuntimeServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class AudioService {
	private AudioService() {
	}

	public static void play(ServerLevel level, BlockPos position, Cue cue) {
		SoundEvent event = cue.captioned;
		float volume = (float) Math.clamp(
				RuntimeServices.config().meta().peakVolume() * cue.relativeVolume, 0.0D, 1.0D);
		if (volume <= 0.0F) return;
		level.playSound(null, position, event, cue.source, volume, cue.pitch);
		level.playSound(null, position, cue.secondary, cue.source, volume * 0.45F,
				Math.min(2.0F, cue.pitch * 1.08F));
	}

	public enum Cue {
		EMPTY_VIEWPOINT(ModSounds.EMPTY_VIEWPOINT, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.STONE_STEP,
				SoundSource.AMBIENT, 0.55F, 0.72F),
		EMPTY_BASE(ModSounds.EMPTY_BASE, SoundEvents.CHEST_CLOSE, SoundEvents.WOODEN_DOOR_CLOSE,
				SoundSource.AMBIENT, 0.62F, 0.78F),
		EMPTY_EXPERIENCE(ModSounds.EMPTY_EXPERIENCE, SoundEvents.STONE_PLACE, SoundEvents.CHEST_CLOSE,
				SoundSource.AMBIENT, 0.50F, 0.60F),
		FOURTH_BAND(ModSounds.FOURTH_BAND, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.BEACON_DEACTIVATE,
				SoundSource.AMBIENT, 0.72F, 0.58F),
		REWORK_JOINT(ModSounds.REWORK_JOINT, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.DEEPSLATE_BREAK,
				SoundSource.HOSTILE, 0.68F, 0.64F),
		MISREAD_BODY(ModSounds.MISREAD_BODY, SoundEvents.WARDEN_HEARTBEAT, SoundEvents.RAVAGER_STEP,
				SoundSource.HOSTILE, 0.85F, 0.52F),
		MISREAD_ADAPTATION(ModSounds.MISREAD_ADAPTATION, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.COMPARATOR_CLICK,
				SoundSource.HOSTILE, 0.82F, 0.72F),
		TERMINATION(ModSounds.TERMINATION, SoundEvents.BEACON_DEACTIVATE, SoundEvents.AMETHYST_BLOCK_BREAK,
				SoundSource.AMBIENT, 0.90F, 0.82F),
		ANOMALY_ECHO(ModSounds.ANOMALY_ECHO, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.COMPARATOR_CLICK,
				SoundSource.AMBIENT, 0.46F, 0.68F),
		WINDOW_GLITCH(ModSounds.WINDOW_GLITCH, SoundEvents.BEACON_DEACTIVATE, SoundEvents.COMPARATOR_CLICK,
				SoundSource.AMBIENT, 0.38F, 0.92F),
		DOOR_CASCADE(ModSounds.DOOR_CASCADE, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.WOODEN_DOOR_CLOSE,
				SoundSource.AMBIENT, 0.66F, 0.61F),
		RULE_COLLAPSE(ModSounds.RULE_COLLAPSE, SoundEvents.BEACON_DEACTIVATE, SoundEvents.DEEPSLATE_BREAK,
				SoundSource.AMBIENT, 0.82F, 0.48F);

		private final SoundEvent captioned;
		private final SoundEvent familiar;
		private final SoundEvent secondary;
		private final SoundSource source;
		private final float relativeVolume;
		private final float pitch;

		Cue(SoundEvent captioned, SoundEvent familiar, SoundEvent secondary, SoundSource source,
				float relativeVolume, float pitch) {
			this.captioned = captioned;
			this.familiar = familiar;
			this.secondary = secondary;
			this.source = source;
			this.relativeVolume = relativeVolume;
			this.pitch = pitch;
		}
	}
}
