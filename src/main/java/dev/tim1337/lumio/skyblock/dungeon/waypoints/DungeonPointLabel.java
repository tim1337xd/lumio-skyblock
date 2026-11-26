package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import dev.tim1337.lumio.api.experimental.util.TextColors;

/**
 * What the label above a dungeon point says: its number (its place in the room's list, from 1), its title, or both. A point without a title shows
 * its number, so every label says something. The manager and the point's card show the same number and title.
 */
final class DungeonPointLabel {
	/** What the labels show. */
	enum Mode {
		NUMBER("Number"), TITLE("Title"), BOTH("Both"), OFF("Off");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		String label() {
			return label;
		}
	}

	private DungeonPointLabel() {
	}

	/**
	 * @param number the point's place in its room's list, from 1
	 * @return the label's text, or "" where labels are off
	 */
	static String text(Mode mode, String title, int number) {
		String plain = plain(title);
		return switch (mode) {
			case OFF -> "";
			case NUMBER -> String.valueOf(number);
			case TITLE -> plain.isEmpty() ? String.valueOf(number) : plain;
			case BOTH -> plain.isEmpty() ? String.valueOf(number) : number + " · " + plain;
		};
	}

	/** @return a title without the game's color codes (Odin's titles may carry them), stripped */
	static String plain(String title) {
		if (title == null || title.isEmpty()) {
			return "";
		}
		return TextColors.noColors(title).strip();
	}
}
