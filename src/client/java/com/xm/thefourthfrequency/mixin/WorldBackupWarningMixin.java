package com.xm.thefourthfrequency.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rewrites the experimental-settings prompt shown when an existing save is opened again.
 *
 * <p>The mod ships as an experimental datapack, so every re-entry raises vanilla's generic
 * "experimental settings may stop working" question - which says nothing about what this mod is
 * actually doing to the save, and reads like an unrelated warning the player has to dismiss.</p>
 *
 * <p>Only the two strings change. The screen stays {@code BackupConfirmScreen}, so its "create a
 * backup and load" button, the eraseCache toggle, and the cancel path all behave exactly as
 * vanilla - a warning about irreversible damage would be worse than useless if it took the backup
 * away. The customized-world variant of the same prompt is left alone: it is not about this mod.</p>
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldBackupWarningMixin {
	private static final String VANILLA_EXPERIMENTAL_QUESTION = "selectWorld.backupQuestion.experimental";
	private static final String VANILLA_EXPERIMENTAL_WARNING = "selectWorld.backupWarning.experimental";

	@Redirect(method = "askForBackup", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent thefourthfrequency$replaceBackupWarning(String translationKey) {
		if (VANILLA_EXPERIMENTAL_QUESTION.equals(translationKey)) {
			return Component.translatable("screen.thefourthfrequency.world_backup_warning.question");
		}
		if (VANILLA_EXPERIMENTAL_WARNING.equals(translationKey)) {
			return Component.translatable("screen.thefourthfrequency.world_backup_warning.warning");
		}
		return Component.translatable(translationKey);
	}
}
