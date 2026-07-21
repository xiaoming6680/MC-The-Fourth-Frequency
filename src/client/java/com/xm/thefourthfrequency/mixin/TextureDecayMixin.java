package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.WorldDecayClient;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(TextureManager.class)
public abstract class TextureDecayMixin {
	@Shadow private Map<Identifier, AbstractTexture> byPath;

	@Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
	private void thefourthfrequency$selectiveTextureLoss(Identifier id,
			CallbackInfoReturnable<AbstractTexture> callback) {
		if (!WorldDecayClient.corruptTexture(id)) return;
		AbstractTexture missing = byPath.get(MissingTextureAtlasSprite.getLocation());
		if (missing == null) missing = byPath.get(TextureManager.INTENTIONAL_MISSING_TEXTURE);
		if (missing != null) callback.setReturnValue(missing);
	}
}
