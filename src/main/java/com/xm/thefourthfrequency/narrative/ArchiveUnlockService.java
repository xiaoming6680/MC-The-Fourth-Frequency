package com.xm.thefourthfrequency.narrative;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.terminal.SignalBand;
import com.xm.thefourthfrequency.terminal.TerminalNoticeService;
import com.xm.thefourthfrequency.terminal.TerminalSignalService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Unlocks the personal witness archive after all four damaged files have been read. */
public final class ArchiveUnlockService {
	private ArchiveUnlockService() {
	}

	public static boolean unlockFromHiddenFiles(ServerPlayer player) {
		FrequencyWorldData data = FrequencyWorldData.get(player.level().getServer());
		CompoundTag terminal = data.terminalRecord(player.getUUID()).orElse(null);
		if (terminal == null || !HiddenFilePolicy.allDiscovered(terminal)
				|| !HiddenFilePolicy.allRead(terminal)) {
			return false;
		}
		if (terminal.getBooleanOr(TerminalData.LOCAL_FILE_UNLOCKED, false)
				&& TerminalFileState.unlocked(terminal, HiddenFilePolicy.COMPLETE_FILE_ID)) {
			return true;
		}
		WitnessArchive archive = WitnessArchive.get();
		long now = player.level().getGameTime();
		long dayTime = player.level().getDayTime();
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putBoolean(TerminalData.LOCAL_FILE_UNLOCKED, true);
			record.putInt(TerminalData.LOCAL_FILE_VERSION, archive.version());
			record.putString(TerminalData.LOCAL_FILE_HASH, archive.contentHash());
			record.putInt(TerminalData.PLOT_STAGE, Math.max(4, record.getIntOr(TerminalData.PLOT_STAGE, 1)));
			TerminalFileState.discover(record, HiddenFilePolicy.COMPLETE_FILE_ID, now, dayTime, true);
		});
		TerminalSignalService.record(player, SignalBand.UNKNOWN, "witness_file_unlocked", 0, 2, true);
		TerminalLifecycleService.ensureCarried(player, false);
		TerminalNoticeService.send(player,
				Component.translatable("message.thefourthfrequency.archive.unlocked"));
		return true;
	}
}
