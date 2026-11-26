package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.EnchantTiers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the enchants in a line of an item's lore: "Sharpness VII, Smite VI" or "Compact X (12,345)". A line counts only if all of it is enchants, so that a description stays as it is.
 * Plain text, so it is unit tested.
 */
final class EnchantLore {
	/**
	 * One enchant: its name (one word or several), its level in Roman numerals or digits, and what may follow the level. The level is a word of
	 * its own: a name's next word that only starts with such a letter ("Mana Vampire", "Ice Cold", "Strong Vitality") is not one. Read as a
	 * level it ended the entry in the middle of the name, and the whole line, with every other enchant on it, stayed uncolored.
	 */
	private static final Pattern ENTRY = Pattern.compile("([A-Z][A-Za-z'\\-]*(?: [A-Za-z'\\-]+)*?) ([IVXLC]{1,7}|\\d{1,3})(?![A-Za-z'\\-])(?: \\([^)]*\\)| [\\d.,]+[kKmM]?)?");

	/** An enchant's place in the line, from its name's start to its level's end: what follows it stays as it is. */
	record Span(int start, int end, EnchantTiers.Tier tier) {
	}

	private EnchantLore() {
	}

	/** @return the known enchants of a plain line, or nothing when the line is something else */
	static List<Span> parse(String line, EnchantTiers tiers) {
		List<Span> spans = new ArrayList<>();
		int at = skipSpaces(line, 0);
		Matcher matcher = ENTRY.matcher(line);
		while (at < line.length()) {
			matcher.region(at, line.length());
			if (!matcher.lookingAt()) {
				return List.of();
			}
			EnchantTiers.Tier tier = tiers.tier(matcher.group(1), EnchantTiers.level(matcher.group(2)));
			if (tier != null) {
				spans.add(new Span(matcher.start(), matcher.end(2), tier));
			} else if (tiers.firstUnknown(matcher.group(1))) {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "An enchant the item repository does not know: {}", matcher.group(1));
				}
			}
			at = matcher.end();
			if (at < line.length() && line.charAt(at) == ',') {
				at++;
			} else if (at < line.length()) {
				return List.of();
			}
			at = skipSpaces(line, at);
		}
		return spans;
	}

	private static int skipSpaces(String line, int from) {
		int at = from;
		while (at < line.length() && line.charAt(at) == ' ') {
			at++;
		}
		return at;
	}
}
