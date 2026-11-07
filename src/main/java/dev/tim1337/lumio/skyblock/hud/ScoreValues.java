package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.ShortNumber;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * The numbers of a currency line written short: "Purse: 100,081,263 (+1,500)" is "Purse: 100M (+1.5K)", each part with its color. The server splits long lines into parts, so a number can run across parts and color codes.
 */
final class ScoreValues {
	private static final String AFTER_LABEL = ": ";
	private static final char COLOR_CODE = '§';

	private ScoreValues() {
	}

	/** @return a line with its numbers written short; the line itself when nothing changes */
	static Component shortened(Component line) {
		String whole = line.getString();
		int label = whole.indexOf(AFTER_LABEL);
		if (label < 0) {
			return line;
		}
		// for each place: its replacement, and whether it gives way to one; color codes in a number stay
		String[] written = new String[whole.length()];
		boolean[] gone = new boolean[whole.length()];
		boolean changed = false;
		int at = label + AFTER_LABEL.length();
		while (at < whole.length()) {
			char c = whole.charAt(at);
			if (c == COLOR_CODE) {
				at += 2;
			} else if (Character.isDigit(c)) {
				int start = at;
				at = number(whole, start, written, gone);
				changed |= written[start] != null;
			} else {
				at++;
			}
		}
		if (!changed) {
			return line;
		}
		MutableComponent out = Component.empty();
		int[] place = {0};
		line.visit((Style style, String part) -> {
			StringBuilder text = new StringBuilder();
			for (int i = 0; i < part.length(); i++) {
				int index = place[0] + i;
				if (written[index] != null) {
					text.append(written[index]);
				}
				if (!gone[index]) {
					text.append(part.charAt(i));
				}
			}
			place[0] += part.length();
			out.append(Component.literal(text.toString()).withStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	/** Reads the number at a place and marks what replaces it. @return where to go on */
	private static int number(String whole, int start, String[] written, boolean[] gone) {
		StringBuilder digits = new StringBuilder();
		int end = start;
		int at = start;
		while (at < whole.length()) {
			char c = whole.charAt(at);
			if (c == COLOR_CODE) {
				at += 2;
			} else if (Character.isDigit(c) || (c == ',' || c == '.') && digitAfter(whole, at + 1)) {
				digits.append(c);
				at++;
				end = at;
			} else {
				break;
			}
		}
		// a unit or a word right after it, or before it: not a plain number ("1.2B", "T3")
		boolean plain = (end >= whole.length() || !Character.isLetter(visibleAt(whole, end))) && !Character.isLetterOrDigit(visibleBefore(whole, start));
		String shorter = plain ? ShortNumber.of(digits.toString()) : digits.toString();
		if (!shorter.contentEquals(digits)) {
			written[start] = shorter;
			for (int i = start; i < end; i++) {
				if (whole.charAt(i) == COLOR_CODE) {
					i++;
				} else {
					gone[i] = true;
				}
			}
		}
		return end;
	}

	/** @return the last letter shown before a place, before color codes; a space at the start */
	private static char visibleBefore(String whole, int place) {
		int at = place - 1;
		while (at >= 1 && whole.charAt(at - 1) == COLOR_CODE) {
			at -= 2;
		}
		return at >= 0 ? whole.charAt(at) : ' ';
	}

	private static boolean digitAfter(String whole, int from) {
		char next = visibleAt(whole, from);
		return Character.isDigit(next);
	}

	/** @return the next letter shown from a place on, after color codes; a space at the end */
	private static char visibleAt(String whole, int from) {
		int at = from;
		while (at < whole.length() && whole.charAt(at) == COLOR_CODE) {
			at += 2;
		}
		return at < whole.length() ? whole.charAt(at) : ' ';
	}
}
