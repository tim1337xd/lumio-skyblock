package dev.tim1337.lumio.skyblock.misc;

import net.minecraft.network.chat.Component;

/** The look of a cocoon's label: its styles, the rule for what it shows, and the color of its bar. Plain numbers, so it can be tested without the game. */
public final class CocoonLook {
	/** The styles of the label. */
	public enum Style {
		CARD("Card"),
		COMPACT("Compact"),
		MINIMAL("Minimal");

		private final Component displayName;

		Style(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** What the label shows, as bits. */
	static final int NAME = 1;
	static final int TIME = 2;
	static final int BAR = 4;
	/** The bar runs to red in this many last seconds. */
	private static final double TO_RED_SECONDS = 1.0;
	private static final int RED_RGB = 0xFF3B30;

	private CocoonLook() {
	}

	/**
	 * @param before what was shown before a change
	 * @param now    what the change asks for
	 * @return what is shown: as asked, but the last part that is on stays on
	 */
	public static int kept(int before, int now) {
		if (now != 0) {
			return now;
		}
		return before != 0 ? before : NAME;
	}

	/** @return the color of the bar as 0xRRGGBB: the accent color, running to red in the last second */
	static int barRgb(int accentRgb, double secondsLeft) {
		double share = 1.0 - Math.clamp(secondsLeft / TO_RED_SECONDS, 0.0, 1.0);
		return channel(accentRgb >> 16 & 0xFF, RED_RGB >> 16 & 0xFF, share) << 16 | channel(accentRgb >> 8 & 0xFF, RED_RGB >> 8 & 0xFF, share) << 8
			| channel(accentRgb & 0xFF, RED_RGB & 0xFF, share);
	}

	private static int channel(int from, int to, double share) {
		return (int) Math.round(from + (to - from) * share);
	}

	/** @return a number with one decimal, without String.format */
	static String decimal(double value) {
		long tenths = Math.round(value * 10.0);
		return tenths / 10 + "." + tenths % 10;
	}

	/** @return the one line of the compact and minimal styles: the name and the time, as far as they are shown */
	static String line(String name, String seconds, boolean showsName, boolean showsTime) {
		if (showsName && showsTime) {
			return name + " · " + seconds;
		}
		return showsName ? name : showsTime ? seconds : "";
	}
}
