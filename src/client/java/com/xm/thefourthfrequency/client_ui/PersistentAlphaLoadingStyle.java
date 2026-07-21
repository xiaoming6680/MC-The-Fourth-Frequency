package com.xm.thefourthfrequency.client_ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;

/** Fixed startup assets that cannot switch back to the vanilla logo during resource reload. */
public final class PersistentAlphaLoadingStyle {
	public static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "textures/gui/persistent_alpha_mojangstudios.png");
	public static final int BACKGROUND_COLOR = 0xFF373363;
	private static final int BAR_COLOR = 0xFFFFFFFF;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int PROGRESS_COLOR = 0xFF8E84FF;
	private static final String LOGO_RESOURCE =
			"/resourcepacks/golden_days_base/assets/minecraft/textures/gui/title/mojangstudios.png";

	private PersistentAlphaLoadingStyle() {
	}

	public static void registerTexture(TextureManager textureManager) {
		textureManager.registerAndLoad(LOGO_TEXTURE, new FixedLogoTexture());
	}

	public static void drawProgressBar(GuiGraphics graphics, int left, int top, int right,
			int bottom, float alpha, float progress) {
		int opacity = Math.round(Math.clamp(alpha, 0.0F, 1.0F) * 255.0F);
		int outline = withOpacity(OUTLINE_COLOR, opacity);
		int bar = withOpacity(BAR_COLOR, opacity);
		int filled = withOpacity(PROGRESS_COLOR, opacity);
		graphics.fill(left, top, right, bottom, outline);
		int innerLeft = left + 1;
		int innerTop = top + 1;
		int innerRight = Math.max(innerLeft, right - 1);
		int innerBottom = Math.max(innerTop, bottom - 1);
		graphics.fill(innerLeft, innerTop, innerRight, innerBottom, bar);
		int progressRight = innerLeft + Math.round((innerRight - innerLeft)
				* Math.clamp(progress, 0.0F, 1.0F));
		graphics.fill(innerLeft, innerTop, progressRight, innerBottom, filled);
	}

	private static int withOpacity(int color, int opacity) {
		return color & 0x00FFFFFF | Math.clamp(opacity, 0, 255) << 24;
	}

	private static final class FixedLogoTexture extends ReloadableTexture {
		private FixedLogoTexture() {
			super(LOGO_TEXTURE);
		}

		@Override
		public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
			try (InputStream input = PersistentAlphaLoadingStyle.class.getResourceAsStream(
					LOGO_RESOURCE)) {
				if (input == null) throw new IOException("Missing embedded Alpha loading logo "
						+ LOGO_RESOURCE);
				return new TextureContents(NativeImage.read(input), new TextureMetadataSection(
						true, true, MipmapStrategy.MEAN, 0.0F));
			}
		}
	}
}

