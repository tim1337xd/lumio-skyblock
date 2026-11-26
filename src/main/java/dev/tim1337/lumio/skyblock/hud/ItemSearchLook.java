package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import net.minecraft.network.chat.Component;

/**
 * The item search's own look: a theme, an accent and how strong the panels' background is. It applies to all of its panels, the search bar, the
 * hover card, the details window and its settings window. By default it follows the menu, as before there was a choice. Their saved names never
 * change: the settings keep them.
 */
public final class ItemSearchLook {
	/** The theme: the menu's, or its own. */
	public enum Theme {
		MENU("Menu", null),
		GLASS("Glass", PanelLook.GLASS),
		DARK("Dark", PanelLook.DARK),
		LIGHT("Light", PanelLook.LIGHT),
		COLORED("Colored", PanelLook.COLORED);

		final String label;
		/** The look it is drawn in, or null for the menu's theme. */
		final PanelLook look;

		Theme(String label, PanelLook look) {
			this.label = label;
			this.look = look;
		}

		public Component displayName() {
			return Component.literal(label);
		}
	}

	/** The accent: the menu's, or one of its own. */
	public enum Accent {
		MENU("Like the menu", 0),
		PURPLE("Purple", SystemColors.PURPLE_RGB),
		BLUE("Blue", SystemColors.BLUE_RGB),
		GREEN("Green", SystemColors.GREEN_RGB),
		ORANGE("Orange", SystemColors.ORANGE_RGB);

		final String label;
		/** Its color as 0xRRGGBB; of no meaning for the menu's. */
		final int rgb;

		Accent(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}

		public Component displayName() {
			return Component.literal(label);
		}

		/** @return the accent to draw with, as 0xRRGGBB */
		int rgb(int menuRgb) {
			return this == MENU ? menuRgb : rgb;
		}
	}

	/** The background's strength in percent: the weakest, and the full one, which is the theme's own. */
	public static final float LEAST_STRENGTH = 20f;
	public static final float FULL_STRENGTH = 100f;

	/** The share of the theme's opacity the panels are drawn with this frame. */
	private static float strength = 1f;

	private ItemSearchLook() {
	}

	/** Sets the background's strength for what is drawn next, in percent. */
	static void setStrength(float percent) {
		strength = share(percent);
	}

	/** @return a strength in percent as a share of the theme's opacity, within its range */
	static float share(float percent) {
		return Math.clamp(percent, LEAST_STRENGTH, FULL_STRENGTH) / FULL_STRENGTH;
	}

	/** @return the share of the full background strength the panels are drawn with this frame */
	static float strength() {
		return strength;
	}

	/** @return how opaque the glass of the theme is at the chosen strength */
	static float opacity() {
		return HudTheme.opacity() * strength;
	}

	/** Draws in the theme: the menu's as it is, an own one through the HUD's look of a single element. */
	static void draw(Theme theme, Runnable draw) {
		if (theme.look == null) {
			draw.run();
		} else {
			HudTheme.withLook(theme.look, draw);
		}
	}
}
