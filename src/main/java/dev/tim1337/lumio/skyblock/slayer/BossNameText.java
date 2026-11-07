package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a slayer boss's name tag says, as plain text: its health ("45M❤", "3.6M/10M❤"), the hits left on its shield ("90 Hits") and its tier.
 * Color and format codes are removed first, and the hits are found with any spacing or symbol between the number and the word, so a change in how
 * the server writes them does not lose the shield. No game classes, so it is unit tested with recorded name tags.
 */
final class BossNameText {
	/** Health in the boss name: "45M❤", "4.5M❤", "450k❤", "1,234❤", or current and most: "3.6M/10M❤". */
	static final Pattern HEALTH = Pattern.compile("([\\d.,]+)([kKmMbB]?)(?:/([\\d.,]+)([kKmMbB]?))?❤");
	/** The hits left on a shield: "90 Hits", "1 Hit", "90Hits", "90 ✦ Hits", in any case. */
	static final Pattern HITS = Pattern.compile("(?i)(?<![\\d.,])(\\d{1,4})[^\\p{Alnum}]{0,3}hits?\\b");
	/** The tier at the end of a boss's title: "Voidgloom Seraph IV". */
	private static final Pattern TIER = Pattern.compile("\\b(I{1,3}|IV|V)\\b(?!.*\\b(I{1,3}|IV|V)\\b)");

	private BossNameText() {
	}

	/** @return the text without color and format codes */
	static String plain(String text) {
		return TextColors.noColors(text);
	}

	/** @return the hits left on the boss's shield in a name tag, or -1 when it shows none */
	static int hits(String text) {
		Matcher matcher = HITS.matcher(plain(text));
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
	}

	/** @return the current health in a name tag ("3.6M/10M❤" gives 3.6M), or -1 */
	static double health(String text) {
		Matcher matcher = HEALTH.matcher(plain(text));
		return matcher.find() ? number(matcher.group(1), matcher.group(2)) : -1;
	}

	/** @return the tier of a boss title (1 to 5), or 0 when it has none */
	static int tier(String title) {
		Matcher matcher = TIER.matcher(plain(title));
		if (!matcher.find()) {
			return 0;
		}
		return switch (matcher.group(1)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			default -> 5;
		};
	}

	/** @return a number as the server writes it, with an optional k, M or B ("4.5M"), or -1 when it is none */
	static double number(String digits, String unit) {
		try {
			double value = Double.parseDouble(digits.replace(",", ""));
			return switch (unit.toLowerCase(Locale.ROOT)) {
				case "k" -> value * 1e3;
				case "m" -> value * 1e6;
				case "b" -> value * 1e9;
				default -> value;
			};
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
