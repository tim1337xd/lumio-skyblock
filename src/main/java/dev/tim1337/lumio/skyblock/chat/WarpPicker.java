package dev.tim1337.lumio.skyblock.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which warps match what is typed, and their order: those that start with it first, then unlocked ones, then those used most. Plain text, so it is unit tested. */
final class WarpPicker {
	private static final Pattern WARP_IN_LORE = Pattern.compile("/warp ([a-zA-Z0-9_-]{1,24})");
	private static final int STARTS = 0;
	private static final int A_WORD_STARTS = 1;
	private static final int NO_MATCH = -1;

	/**
	 * A warp.
	 *
	 * @param island   its island, or ""
	 * @param unlocked false only when the game's menu showed it as locked
	 */
	record Warp(String name, String island, boolean unlocked) {
	}

	private WarpPicker() {
	}

	/** @return the warps that match, in their order */
	static List<Warp> order(String typed, Collection<Warp> warps, Map<String, Integer> uses) {
		String lower = typed.toLowerCase(Locale.ROOT);
		List<Warp> out = new ArrayList<>();
		for (Warp warp : warps) {
			if (rank(warp, lower) != NO_MATCH) {
				out.add(warp);
			}
		}
		out.sort(Comparator.<Warp>comparingInt(warp -> rank(warp, lower))
			.thenComparing(warp -> !warp.unlocked())
			.thenComparingInt(warp -> -uses.getOrDefault(warp.name(), 0))
			.thenComparing(Warp::name));
		return out;
	}

	private static int rank(Warp warp, String lower) {
		if (lower.isEmpty() || warp.name().toLowerCase(Locale.ROOT).startsWith(lower)) {
			return STARTS;
		}
		return anyWordStarts(warp.name(), lower) || anyWordStarts(warp.island(), lower) ? A_WORD_STARTS : NO_MATCH;
	}

	private static boolean anyWordStarts(String text, String lower) {
		for (String word : text.toLowerCase(Locale.ROOT).split("[ _-]+")) {
			if (word.startsWith(lower)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reads an item of the game's Fast Travel menu.
	 *
	 * @param itemName the item's name, without colors
	 * @param lore     the lines under it, without colors
	 * @return the warp it shows, or null when it shows none
	 */
	static Warp ofMenuItem(String itemName, List<String> lore) {
		String name = null;
		boolean unlocked = true;
		for (String line : lore) {
			Matcher matcher = WARP_IN_LORE.matcher(line);
			if (name == null && matcher.find()) {
				name = matcher.group(1).toLowerCase(Locale.ROOT);
			}
			String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("not unlocked") || lower.contains("locked!") || lower.contains("requires")) {
				unlocked = false;
			}
		}
		return name == null ? null : new Warp(name, itemName.strip(), unlocked);
	}
}
