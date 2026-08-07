package com.xm.thefourthfrequency.client_ui;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The analog-signal treatment for the one surface a filter cannot reach.
 *
 * <p>{@link ScreenFilterDriver} puts {@code analog_signal.fsh} over the whole finished frame, which
 * is how every other corrupted surface in this mod is treated now. It cannot serve a surface that is
 * only <em>part</em> of the frame: a post chain's uniforms are fixed when it loads, and the terminal
 * weather card's position on screen is not - it moves with the window and the GUI scale. That card
 * also has tabs and a close hint around it that the player needs in order to leave the page, so
 * "filter everything" is not an option there either.
 *
 * <p>So this is the same vocabulary - grain, scanlines, a mistracked bar - expressed in what a GUI
 * draw can actually do, clipped to a rectangle. It is deliberately the same vocabulary and not a
 * second idea of what is wrong: a player who meets tape damage on the loading screen, in the world,
 * and then inside their own terminal is meeting one fault.
 *
 * <p>The one thing here that is not a rectangle is the grain, because grain is the one thing
 * {@code GuiGraphics} has no primitive for at all - it is a tiled noise plate, drawn twice, tinted
 * white and then black, since source-over is the only blend available and neither half alone is
 * noise.
 *
 * <p>Nothing here decides when. Callers own their own envelopes ({@code AlphaLoadTimeline},
 * {@code SkyInstrumentPolicy}); this only knows what a given strength looks like.
 */
public final class AnalogFilter {
	private static final Identifier GRAIN = plate("tape_grain");
	/** The plate is square and this size; see tools/generate_analog_filter_textures.py. */
	private static final int PLATE = 256;

	/**
	 * Peak alpha of one half of the grain.
	 *
	 * <p>Grain is drawn twice, white then black, because source-over is the only blend a GUI draw
	 * has and neither half alone is grain - white alone fogs the picture, black alone soots it. Two
	 * passes with independent offsets is additive noise built out of the one operation available.
	 */
	private static final int GRAIN_PEAK_ALPHA = 118;
	/** Darkest a scanline trough gets, before the caller's strength scales it. */
	private static final int SCANLINE_PEAK_ALPHA = 96;
	private static final int SCANLINE_COLOR = 0x000306;

	private AnalogFilter() {
	}

	private static Identifier plate(String name) {
		return Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID,
				"textures/gui/filter/" + name + ".png");
	}

	/**
	 * Zero-mean per-pixel noise over the whole viewport.
	 *
	 * @param frame advances the plate's offset; pass a tick, or a tick divided down for slower grain
	 */
	public static void grain(GuiGraphics graphics, float strength, int frame) {
		grain(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), strength, frame);
	}

	/**
	 * The same noise, confined to one rectangle.
	 *
	 * <p>For the surfaces that are not the whole screen - the terminal's instrument card, which has a
	 * border and a layout around it that must stay clean. Clipped by shrinking the source rectangle
	 * rather than by a scissor, because {@code enableScissor} takes its bounds through the current
	 * pose and the terminal draws under one; handing it screen coordinates clips the entire card away.
	 */
	public static void grain(GuiGraphics graphics, int left, int top, int right, int bottom,
			float strength, int frame) {
		int alpha = Math.round(GRAIN_PEAK_ALPHA * Math.clamp(strength, 0.0F, 1.0F));
		if (alpha <= 0) return;
		// The two halves are offset independently, or they cancel where they overlap and the noise
		// collapses into a grid of the places they did not.
		tile(graphics, left, top, right, bottom, offset(frame * 2), offset(frame * 2 + 1),
				alpha << 24 | 0xFFFFFF);
		tile(graphics, left, top, right, bottom, offset(frame * 2 + 7919),
				offset(frame * 2 + 104729), alpha << 24);
	}

	/**
	 * Scanlines with a soft profile: a dark row, and a half-strength row under it.
	 *
	 * <p>Not a plate. Scanlines are a regular pattern whose pitch has to land on the viewport's own
	 * pixel grid, and a stretched texture aliases against exactly that - which is how you get the
	 * wide moire bands that read as a bug rather than as a raster. Two fills per pitch is the whole
	 * cost, and {@code GuiGraphics} batches them into one draw.
	 *
	 * @param phase shifts the pattern; a slow drift keeps the lines from reading as part of the
	 *              window furniture, but it must stay slow or they crawl
	 */
	public static void scanlines(GuiGraphics graphics, int pitch, float strength, int phase) {
		scanlines(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), pitch, strength, phase);
	}

	/** The same raster, confined to one rectangle. */
	public static void scanlines(GuiGraphics graphics, int left, int top, int right, int bottom,
			int pitch, float strength, int phase) {
		int alpha = Math.round(SCANLINE_PEAK_ALPHA * Math.clamp(strength, 0.0F, 1.0F));
		if (alpha <= 0 || right <= left) return;
		int spacing = Math.max(2, pitch);
		int start = top + Math.floorMod(phase, spacing) - spacing;
		for (int y = start; y < bottom; y += spacing) {
			if (y >= top) graphics.fill(left, y, right, y + 1, alpha << 24 | SCANLINE_COLOR);
			int soft = y + 1;
			if (soft >= top && soft < bottom && spacing >= 3) {
				graphics.fill(left, soft, right, soft + 1, alpha / 2 << 24 | SCANLINE_COLOR);
			}
		}
	}

	/**
	 * The mistracked bar: bright in the middle, falling off to nothing at both edges.
	 *
	 * <p>The version this replaces was a flat translucent rectangle with a hard white line on top
	 * and a hard black line under it. Three hard edges is a rectangle with decoration; a bar of lost
	 * tracking has no edges at all, it has a middle.
	 */
	public static void rollBar(GuiGraphics graphics, int barTop, int barHeight, float strength) {
		rollBar(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight(), barTop, barHeight,
				strength);
	}

	/**
	 * The same bar, clipped to one rectangle.
	 *
	 * @param barTop    where the bar's own top edge is, in the same space as {@code top}
	 * @param barHeight how tall the bar is; the falloff is measured across this, not across the clip
	 */
	public static void rollBar(GuiGraphics graphics, int left, int top, int right, int bottom,
			int barTop, int barHeight, float strength) {
		float clamped = Math.clamp(strength, 0.0F, 1.0F);
		if (clamped <= 0.0F || barHeight <= 0 || right <= left) return;
		for (int row = 0; row < barHeight; row++) {
			int y = barTop + row;
			if (y < top || y >= bottom) continue;
			// Triangular falloff either side of the middle, squared so the shoulders stay faint.
			float centred = 1.0F - Math.abs(row * 2.0F / barHeight - 1.0F);
			float weight = centred * centred;
			int alpha = Math.round(96 * weight * clamped);
			if (alpha <= 0) continue;
			graphics.fill(left, y, right, y + 1, alpha << 24 | 0xD6D2C8);
		}
		// The trailing edge underneath it, where the picture has not caught up yet. Also softened,
		// and shorter than the bar, so it reads as a wake rather than as a border.
		int wake = Math.max(1, barHeight / 3);
		for (int row = 0; row < wake; row++) {
			int y = barTop + barHeight + row;
			if (y < top || y >= bottom) continue;
			int alpha = Math.round(72 * (1.0F - row / (float) wake) * clamped);
			if (alpha > 0) graphics.fill(left, y, right, y + 1, alpha << 24);
		}
	}

	/** Deterministic per-frame plate offset, so consecutive frames never land on the same noise. */
	private static int offset(int frame) {
		return Math.floorMod(AlphaLoadTimeline.noise(frame), PLATE);
	}

	private static void tile(GuiGraphics graphics, int clipLeft, int clipTop, int clipRight,
			int clipBottom, int offsetX, int offsetY, int tint) {
		if (clipRight <= clipLeft || clipBottom <= clipTop) return;
		// Started one plate early and clipped by shrinking the source rectangle rather than by a
		// scissor: enableScissor takes its bounds through the current pose, and these are viewport
		// coordinates. Handing screen coordinates to a transformed scissor clips the whole draw away.
		for (int top = clipTop - offsetY; top < clipBottom; top += PLATE) {
			for (int left = clipLeft - offsetX; left < clipRight; left += PLATE) {
				int u = Math.max(0, clipLeft - left);
				int v = Math.max(0, clipTop - top);
				int x = Math.max(clipLeft, left);
				int y = Math.max(clipTop, top);
				int drawWidth = Math.min(PLATE - u, clipRight - x);
				int drawHeight = Math.min(PLATE - v, clipBottom - y);
				if (drawWidth <= 0 || drawHeight <= 0) continue;
				graphics.blit(RenderPipelines.GUI_TEXTURED, GRAIN, x, y, u, v,
						drawWidth, drawHeight, drawWidth, drawHeight, PLATE, PLATE, tint);
			}
		}
	}
}
