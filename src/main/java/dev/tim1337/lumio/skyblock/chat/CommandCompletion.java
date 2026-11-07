package dev.tim1337.lumio.skyblock.chat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How the entries for a command argument are matched and ordered: prefix matches first, then matches at the
 * start of a word, then anywhere, each ignoring case; entries the server suggests are kept and never doubled. Plain strings, so it is unit tested.
 */
public final class CommandCompletion {
	/** Which names a command argument takes. */
	public enum Source {
		WARPS,
		/** Any player: party members first, then friends, guild members and the players on the island, then the rest of the tab list. */
		PLAYERS,
		/** Only the members of the party. */
		PARTY,
		/** Only the guild members seen. */
		GUILD,
		/** Only the friends seen. */
		FRIENDS,
		RECIPES,
		SACKS
	}

	/** Characters after which a word starts inside an entry: "ENCHANTED_DIAMOND" has "DIAMOND" as a word. */
	private static final String WORD_BREAKS = " _-:.";
	/** No match at all. */
	public static final int NO_MATCH = -1;

	private CommandCompletion() {
	}

	/** @return 0 when an entry starts with the typed text, 1 when one of its words does (or its words start with the typed parts in a
	 *         row, "ench_dia" for "ENCHANTED_DIAMOND"), 2 when it contains it elsewhere, or {@link #NO_MATCH} */
	public static int rank(String entry, String typed) {
		if (typed.isEmpty()) {
			return 0;
		}
		String lowerEntry = entry.toLowerCase(Locale.ROOT);
		String lowerTyped = typed.toLowerCase(Locale.ROOT);
		if (lowerEntry.startsWith(lowerTyped)) {
			return 0;
		}
		int at = lowerEntry.indexOf(lowerTyped, 1);
		boolean found = at >= 0;
		while (at >= 0) {
			if (WORD_BREAKS.indexOf(lowerEntry.charAt(at - 1)) >= 0) {
				return 1;
			}
			at = lowerEntry.indexOf(lowerTyped, at + 1);
		}
		if (found) {
			return 2;
		}
		return partsMatch(lowerEntry, lowerTyped) ? 1 : NO_MATCH;
	}

	/** Splits typed text and entries into words: "ench_dia" into "ench" and "dia". */
	private static final Pattern WORDS = Pattern.compile("[ _\\-:.]+");

	/** @return whether every typed part starts a word of the entry, in a row: "ench_dia" matches "ENCHANTED_DIAMOND" */
	private static boolean partsMatch(String lowerEntry, String lowerTyped) {
		String[] parts = WORDS.split(lowerTyped);
		if (parts.length < 2) {
			return false;
		}
		String[] words = WORDS.split(lowerEntry);
		for (int start = 0; start + parts.length <= words.length; start++) {
			boolean all = true;
			for (int k = 0; k < parts.length && all; k++) {
				all = words[start + k].startsWith(parts[k]);
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Orders the entries that match: by how they match, then by their group (the first group first), keeping each group's own order; each entry
	 * once, ignoring case.
	 *
	 * @param groups the entries in groups of priority, such as party members before the other players
	 * @param most   the most entries returned
	 */
	public static List<String> order(String typed, List<List<String>> groups, int most) {
		// each entry is matched once, into the list of its rank
		List<List<String>> byRank = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		Set<String> seen = new HashSet<>();
		for (List<String> group : groups) {
			for (String entry : group) {
				int rank = rank(entry, typed);
				if (rank != NO_MATCH && seen.add(entry.toLowerCase(Locale.ROOT))) {
					byRank.get(rank).add(entry);
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (List<String> ranked : byRank) {
			for (String entry : ranked) {
				if (out.size() >= most) {
					return out;
				}
				out.add(entry);
			}
		}
		return out;
	}

	/**
	 * Merges the server's suggestions with the mod's: the server's come first among entries that match equally well, the mod's never double one
	 * (ignoring case), and server entries that do not match the typed text at all stay at the end, as the game shows them.
	 */
	public static List<String> merge(String typed, List<String> server, List<String> ours) {
		List<String> out = new ArrayList<>(order(typed, List.of(server, ours), Integer.MAX_VALUE));
		Set<String> seen = new HashSet<>();
		for (String entry : out) {
			seen.add(entry.toLowerCase(Locale.ROOT));
		}
		for (String entry : server) {
			if (seen.add(entry.toLowerCase(Locale.ROOT))) {
				out.add(entry);
			}
		}
		return out;
	}

	/**
	 * Merges what comes first (subcommands, warps, already in their order) with the server's suggestions and the names: subcommands, then the server's, then the names by how they match; the server's that
	 * do not match the typed text at all stay at the end. Each entry once, ignoring case.
	 */
	public static List<String> merge(String typed, List<String> first, List<String> server, List<String> names) {
		List<String> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String entry : first) {
			if (seen.add(entry.toLowerCase(Locale.ROOT))) {
				out.add(entry);
			}
		}
		for (String entry : merge(typed, server, names)) {
			if (seen.add(entry.toLowerCase(Locale.ROOT))) {
				out.add(entry);
			}
		}
		return out;
	}
}
