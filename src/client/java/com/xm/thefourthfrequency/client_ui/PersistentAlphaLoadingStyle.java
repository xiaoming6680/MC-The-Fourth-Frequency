package com.xm.thefourthfrequency.client_ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
	public static final Identifier WORLD_LOADING_BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(
			"thefourthfrequency", "textures/gui/persistent_alpha_world_loading_background.png");
	public static final int BACKGROUND_COLOR = 0xFF373363;
	private static final int BAR_COLOR = 0xFFFFFFFF;
	private static final int OUTLINE_COLOR = 0xFF000000;
	private static final int PROGRESS_COLOR = 0xFF8E84FF;
	private static final String LOGO_RESOURCE =
			"/resourcepacks/golden_days_base/assets/minecraft/textures/gui/title/mojangstudios.png";
	private static final String WORLD_LOADING_BACKGROUND_RESOURCE =
			"/resourcepacks/golden_days_base/assets/minecraft/textures/gui/menu_background.png";
	private static TextureManager worldLoadingBackgroundManager;

	private PersistentAlphaLoadingStyle() {
	}

	public static void registerTexture(TextureManager textureManager) {
		textureManager.registerAndLoad(LOGO_TEXTURE,
				new FixedClasspathTexture(LOGO_TEXTURE, LOGO_RESOURCE, true, true));
		registerWorldLoadingBackgroundOnce(textureManager);
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

	public static void drawWorldLoadingBackground(GuiGraphics graphics) {
		Screen.renderMenuBackgroundTexture(graphics, WORLD_LOADING_BACKGROUND_TEXTURE,
				0, 0, 0.0F, 0.0F, graphics.guiWidth(), graphics.guiHeight());
	}

	private static int withOpacity(int color, int opacity) {
		return color & 0x00FFFFFF | Math.clamp(opacity, 0, 255) << 24;
	}

	private static synchronized void registerWorldLoadingBackgroundOnce(
			TextureManager textureManager) {
		if (worldLoadingBackgroundManager == textureManager) return;
		registerPersistentTexture(textureManager, WORLD_LOADING_BACKGROUND_TEXTURE,
				WORLD_LOADING_BACKGROUND_RESOURCE);
		worldLoadingBackgroundManager = textureManager;
	}

	private static void registerPersistentTexture(TextureManager textureManager, Identifier id,
			String classpathResource) {
		try (InputStream input = PersistentAlphaLoadingStyle.class.getResourceAsStream(
				classpathResource)) {
			if (input == null) throw new IOException("Missing embedded Alpha loading texture "
					+ classpathResource);
			NativeImage image = NativeImage.read(input);
			try {
				textureManager.register(id, new DynamicTexture(id::toString, image));
			} catch (RuntimeException exception) {
				image.close();
				throw exception;
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not register persistent Alpha loading texture "
					+ classpathResource, exception);
		}
	}

	private static final class FixedClasspathTexture extends ReloadableTexture {
		private final String classpathResource;
		private final boolean blur;
		private final boolean clamp;

		private FixedClasspathTexture(Identifier id, String classpathResource,
				boolean blur, boolean clamp) {
			super(id);
			this.classpathResource = classpathResource;
			this.blur = blur;
			this.clamp = clamp;
		}

		@Override
		public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
			try (InputStream input = PersistentAlphaLoadingStyle.class.getResourceAsStream(
					classpathResource)) {
				if (input == null) throw new IOException("Missing embedded Alpha loading texture "
						+ classpathResource);
				return new TextureContents(NativeImage.read(input), new TextureMetadataSection(
						blur, clamp, MipmapStrategy.MEAN, 0.0F));
			}
		}
	}
}
