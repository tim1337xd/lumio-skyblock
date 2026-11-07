package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** What lies behind a full overlay: the world and what else is on the screen, blurred, dimmed or both. Shared by the overlays with a Background setting. */
final class OverlayBackdrop {
	/** Default of the Strength setting, in percent. */
	static final float DEFAULT_STRENGTH_PERCENT = 60f;
	/** How dark the screen gets at full strength; the default strength gives the dim the overlays always had. */
	private static final float DIM_AT_FULL = 2f / 3f;
	private static final float PERCENT = 100f;

	/** The choices of the Background setting. */
	enum Kind {
		OFF("Off", false, false),
		DIM("Dim", false, true),
		BLUR("Blur", true, false),
		BLUR_AND_DIM("Blur and dim", true, true);

		private final Component label;
		private final boolean blurs;
		private final boolean dims;

		Kind(String label, boolean blurs, boolean dims) {
			this.label = Component.literal(label);
			this.blurs = blurs;
			this.dims = dims;
		}

		Component label() {
			return label;
		}

		/** @return how dark the screen gets, from 0 to 1, at a strength in percent */
		float dimAt(float strengthPercent, boolean blurDrawn) {
			// without a blur to show for it, the graphics settings leave the dim to cover the world
			return dims || blurs && !blurDrawn ? strengthPercent / PERCENT * DIM_AT_FULL : 0f;
		}
	}

	private OverlayBackdrop() {
	}

	/**
	 * Draws the backdrop over the whole screen, before the overlay.
	 *
	 * @param strengthPercent the Strength setting
	 * @param fade            how far it has faded in, from 0 to 1
	 */
	static void draw(Canvas canvas, GuiGraphicsExtractor graphics, int width, int height, Kind kind, float strengthPercent, float fade) {
		if (kind == Kind.OFF || fade <= 0f) {
			return;
		}
		boolean blurred = kind.blurs && HudTheme.blurBehind(canvas, width, height, strengthPercent / PERCENT, fade);
		float dim = kind.dimAt(strengthPercent, blurred) * fade;
		if (dim > 0f) {
			graphics.fill(0, 0, width, height, Draw.withOpacity(0xFF000000, dim));
		}
	}
}
