package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.AlphaResourcePackPlan;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelHiddenPacksMixin {
	@Shadow @Final private List<Pack> selected;
	@Shadow @Final private List<Pack> unselected;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void thefourthfrequency$hideInternalBases(Consumer<PackSelectionModel.EntryBase> onListChanged,
			Function<Pack, Identifier> iconGetter, PackRepository repository,
			Consumer<PackRepository> output, CallbackInfo callback) {
		thefourthfrequency$removeInternalBases();
	}

	@Inject(method = "findNewPacks", at = @At("TAIL"))
	private void thefourthfrequency$hideInternalBasesAfterRefresh(CallbackInfo callback) {
		thefourthfrequency$removeInternalBases();
	}

	private void thefourthfrequency$removeInternalBases() {
		selected.removeIf(pack -> AlphaResourcePackPlan.isHiddenFromSelectionScreen(pack.getId()));
		unselected.removeIf(pack -> AlphaResourcePackPlan.isHiddenFromSelectionScreen(pack.getId()));
	}
}
