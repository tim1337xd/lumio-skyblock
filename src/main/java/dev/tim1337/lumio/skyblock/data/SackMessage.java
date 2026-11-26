package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel's sack summary in chat, "[Sacks] +12 items, -3 items. (Last 30s.)": its hover text lists each item, "+12 Enchanted Mithril (Mining
 * Sack)". Read here line by line, both what went in and what was taken out. Plain text, so it is unit tested.
 */
public final class SackMessage {
	private static final Pattern MESSAGE = Pattern.compile("^\\[Sacks]\\s+[+-]");
	private static final Pattern LINE = Pattern.compile("^([+-][\\d,]+)\\s+(.+?)\\s+\\((.+?)\\)\\.?$");
	/** Bounds a count read from chat. */
	private static final long MOST_COUNT = 1_000_000_000L;

	/** One item of the summary: its name as written and how many went in (negative: taken out). */
	public record Entry(String name, long count) {
	}

	private SackMessage() {
	}

	/** @return whether a chat line (without colors) is the sack summary */
	public static boolean is(String text) {
		return MESSAGE.matcher(text).find();
	}

	/** @return the items of the summary's hover lines (without colors); lines that are not an item are left out */
	public static List<Entry> entries(List<String> hoverLines) {
		List<Entry> entries = new ArrayList<>();
		for (String line : hoverLines) {
			Matcher matcher = LINE.matcher(line);
			if (!matcher.matches()) {
				continue;
			}
			String signed = matcher.group(1);
			try {
				long count = Long.parseLong(signed.substring(1).replace(",", ""));
				if (count > 0 && count <= MOST_COUNT) {
					entries.add(new Entry(matcher.group(2), signed.startsWith("-") ? -count : count));
				}
			} catch (NumberFormatException tooLong) {
				// not a count: left out
			}
		}
		return entries;
	}
}
