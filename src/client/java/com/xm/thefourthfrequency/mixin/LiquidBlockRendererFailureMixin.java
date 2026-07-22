package com.xm.thefourthfrequency.mixin;

import com.xm.thefourthfrequency.client_ui.LanHostFailureVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Completes the LAN-host material loss for water and lava, which bypass baked block models. */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererFailureMixin {
	@Shadow @Final private TextureAtlasSprite lavaStill;
	@Shadow @Final private TextureAtlasSprite lavaFlowing;
	@Shadow @Final private TextureAtlasSprite waterStill;
	@Shadow @Final private TextureAtlasSprite waterFlowing;
	@Shadow @Final private TextureAtlasSprite waterOverlay;

	@Redirect(method = "tesselate", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;lavaStill:Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite thefourthfrequency$missingLavaStill(LiquidBlockRenderer renderer) {
		return replacement(lavaStill);
	}

	@Redirect(method = "tesselate", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;lavaFlowing:Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite thefourthfrequency$missingLavaFlowing(LiquidBlockRenderer renderer) {
		return replacement(lavaFlowing);
	}

	@Redirect(method = "tesselate", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;waterStill:Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite thefourthfrequency$missingWaterStill(LiquidBlockRenderer renderer) {
		return replacement(waterStill);
	}

	@Redirect(method = "tesselate", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;waterFlowing:Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite thefourthfrequency$missingWaterFlowing(LiquidBlockRenderer renderer) {
		return replacement(waterFlowing);
	}

	@Redirect(method = "tesselate", at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;waterOverlay:Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private TextureAtlasSprite thefourthfrequency$missingWaterOverlay(LiquidBlockRenderer renderer) {
		return replacement(waterOverlay);
	}

	private static TextureAtlasSprite replacement(TextureAtlasSprite original) {
		return LanHostFailureVisualState.active()
				? Minecraft.getInstance().getModelManager().getMissingBlockStateModel().particleIcon()
				: original;
	}
}
