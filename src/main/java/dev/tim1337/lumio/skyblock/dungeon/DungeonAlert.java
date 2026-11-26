package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import dev.tim1337.lumio.skyblock.hud.TitleQueue;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The dungeon helpers' alerts (ability ready, use your ultimate, mask procs), in the way the player chose: the game's title, Lumio's alert box at
 * the top of the screen (as in the F7 mockup: a dot, the alert in bold and a quiet line after it), or a sound alone. One alert at a time; a new one
 * takes the place of the last.
 */
public final class DungeonAlert {
	/** How an alert is shown. */
	public enum Style {
		TITLE("Title"), TOAST("Pop-up"), SOUND("Sound");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final long SHOW_MILLIS = 3_000;
	private static final long FADE_MILLIS = 300;
	private static final int TITLE_FADE_IN_TICKS = 2;
	private static final int TITLE_STAY_TICKS = 30;
	private static final int TITLE_FADE_OUT_TICKS = 8;
	private static final float SOUND_PITCH = 1.4f;
	/** The box: its height, padding, the dot and where it sits, as a share of the screen's height. */
	private static final int HEIGHT = 24;
	private static final int PAD = 12;
	private static final int DOT = 7;
	private static final int GAP = 8;
	private static final float TOP_SHARE = 0.16f;
	private static final float BORDER_OPACITY = 0.7f;

	private static String title = "";
	private static String detail = "";
	private static int color = SystemColors.YELLOW;
	private static long shownAt = Long.MIN_VALUE / 2;
	/** How long the alert shown now stays. */
	private static long showMillis = SHOW_MILLIS;

	private DungeonAlert() {
	}

	/**
	 * Shows an alert, with a short sound in every style.
	 *
	 * @param color the dot's and the border's color, as 0xAARRGGBB
	 */
	public static void show(Style style, String alertTitle, String alertDetail, int alertColor) {
		Minecraft client = Minecraft.getInstance();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), SOUND_PITCH));
		if (style == Style.TITLE) {
			// about this very moment: at once, as before, through the one queue of Lumio's titles
			TitleQueue.show(Component.literal(alertTitle).withColor(alertColor & 0xFFFFFF), Component.literal(alertDetail), TITLE_FADE_IN_TICKS,
				TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS, true);
		} else if (style == Style.TOAST) {
			title = alertTitle;
			detail = alertDetail;
			color = alertColor;
			shownAt = System.currentTimeMillis();
			showMillis = SHOW_MILLIS;
		}
	}

	/** Shows the box for this long, without a sound (the caller plays its own, if any). */
	public static void toast(String alertTitle, String alertDetail, int alertColor, long millis) {
		title = alertTitle;
		detail = alertDetail;
		color = alertColor;
		shownAt = System.currentTimeMillis();
		showMillis = Math.max(2 * FADE_MILLIS, millis);
	}

	/** Draws the box while an alert is shown. */
	public static void draw(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		long age = System.currentTimeMillis() - shownAt;
		if (age > showMillis || age < 0) {
			return;
		}
		float opacity = Math.min(1f, Math.min(age, showMillis - age) / (float) FADE_MILLIS);
		Canvas ui = Draw.hudCanvas(graphics, color, 0f, false);
		float titleWidth = ui.boldWidthScaled(title, TextScale.BODY);
		float detailWidth = detail.isEmpty() ? 0 : GAP + ui.widthScaled(detail, TextScale.SMALL);
		int width = Math.round(PAD + DOT + GAP + titleWidth + detailWidth + PAD);
		int x = (graphics.guiWidth() - width) / 2;
		int y = Math.round(graphics.guiHeight() * TOP_SHARE);
		HudTheme.panel(ui, x, y, width, HEIGHT, Radius.box(HEIGHT), HudTheme.opacity() * opacity);
		ui.ring(x, y, width, HEIGHT, Radius.box(HEIGHT), 1, Draw.withOpacity(color, BORDER_OPACITY * opacity));
		ui.discAt(x + PAD + DOT / 2f, y + HEIGHT / 2f, DOT, Draw.withOpacity(color, opacity));
		float textX = x + PAD + DOT + GAP;
		ui.textAtScaledBold(title, textX, y + (HEIGHT - 8) / 2f, TextScale.BODY, Draw.withOpacity(HudTheme.text(), opacity));
		if (!detail.isEmpty()) {
			ui.textAtScaled(detail, textX + titleWidth + GAP, y + (HEIGHT - 7) / 2f, TextScale.SMALL, Draw.withOpacity(HudTheme.secondary(), opacity));
		}
	}
}
