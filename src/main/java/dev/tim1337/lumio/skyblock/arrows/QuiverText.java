package dev.tim1337.lumio.skyblock.arrows;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What Hypixel writes about the quiver, in the forms seen in real play: the preview item in the last hotbar slot, named after the selected arrow with the line "Arrows Remaining: 2,864", and the chat messages
 * "QUIVER! You only have 50 Flint Arrows left!", "QUIVER! You have run out of Explosive Arrows!" and "Jax forged Flint Arrow x2842 for 14k Coins!". Plain text, so it is unit tested.
 */
public final class QuiverText {
	/** What a line says: which arrow, and a number. */
	public enum Kind {
		/** The number of arrows left. */
		LEFT,
		/** None are left. */
		OUT,
		/** The number of arrows just made and added. */
		FORGED
	}

	/** A message about arrows. */
	public record Message(Kind kind, String name, int count) {
	}

	/** The arrow selected in the quiver, and how many are left. */
	public record Selected(String name, int count) {
	}

	private static final Pattern REMAINING = Pattern.compile("^Arrows Remaining: ([0-9][0-9,]{0,12})$");
	private static final Pattern LEFT = Pattern.compile("^QUIVER! You only have ([0-9][0-9,]{0,12}) (.{1,40}? Arrow)s? left!$");
	private static final Pattern OUT = Pattern.compile("^QUIVER! You have run out of (.{1,40}? Arrow)s?!$");
	private static final Pattern FORGED = Pattern.compile("^Jax forged (.{1,40}? Arrow) x([0-9][0-9,]{0,12})(?: for .{1,20})?!$");
	private static final int MOST = 1_000_000_000;

	private QuiverText() {
	}

	/** @return the selected arrow from the preview item's name and lore lines, without their colors, or null for another item */
	public static Selected ofItem(String name, List<String> lore) {
		for (String line : lore) {
			Matcher matcher = REMAINING.matcher(line.strip());
			if (matcher.matches()) {
				int count = number(matcher.group(1));
				return count < 0 || name.isBlank() ? null : new Selected(name.strip(), count);
			}
		}
		return null;
	}

	/** @return what a server message says about arrows, without its colors, or null for any other message */
	public static Message ofMessage(String plain) {
		String text = plain.strip();
		if (text.startsWith("QUIVER!")) {
			Matcher left = LEFT.matcher(text);
			if (left.matches()) {
				int count = number(left.group(1));
				return count < 0 ? null : new Message(Kind.LEFT, left.group(2), count);
			}
			Matcher out = OUT.matcher(text);
			return out.matches() ? new Message(Kind.OUT, out.group(1), 0) : null;
		}
		if (text.startsWith("Jax forged")) {
			Matcher forged = FORGED.matcher(text);
			if (forged.matches()) {
				int count = number(forged.group(2));
				return count < 0 ? null : new Message(Kind.FORGED, forged.group(1), count);
			}
		}
		return null;
	}

	/** @return a number written with commas, or -1 if it is not one a quiver can hold */
	private static int number(String text) {
		long value = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c != ',') {
				value = value * 10 + (c - '0');
				if (value > MOST) {
					return -1;
				}
			}
		}
		return (int) value;
	}
}
