package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.experimental.util.Debug;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The tab list's Stats card holds only stats: a row whose label is not a known stat (a mob of an event, "Goblin", "Ice Walker", a commission line)
 * goes to a card "Other" after the cards, exactly as the server sent it; nothing is dropped and nothing renamed. A label not known is written to
 * the debug log once per start, so new stats are easy to add.
 */
public final class TabStats {
	/** The title of the card the rows that are no stats go to. */
	public static final String OTHER = "Other";
	private static final String STATS = "stats";
	/** Known stat labels, lower case, without symbols. */
	private static final Set<String> KNOWN = Set.of(
		// combat and general
		"health", "defense", "strength", "speed", "crit chance", "crit damage", "intelligence", "attack speed", "bonus attack speed", "ability damage",
		"true defense", "ferocity", "magic find", "pet luck", "health regen", "vitality", "mending", "swing range", "mana regen", "tracking", "sweep",
		// gathering
		"mining speed", "mining spread", "gemstone spread", "pristine", "breaking power", "heat resistance", "cold resistance", "farming speed",
		"bonus pest chance", "foraging speed", "sea creature chance", "fishing speed", "double hook chance", "treasure chance", "trophy fish chance",
		"hunter fortune", "respiration", "pressure resistance", "rift time", "rift damage", "hearts", "mana",
		// what the island shows among them: currencies and the cold of the tunnels
		"purse", "piggy", "bits", "motes", "copper", "gems", "north stars", "heat", "cold", "pelts", "sowdust");
	/** Logged unknown labels, once per start; bounded. */
	private static final Set<String> LOGGED = new HashSet<>();
	private static final int MOST_LOGGED = 200;

	private TabStats() {
	}

	/** @return the cards with every row of a Stats card that is no stat moved to an Other card at the end; the cards themselves if there is none */
	public static List<TabCard> sorted(List<TabCard> cards, boolean log) {
		List<TabCard.Row> other = null;
		List<TabCard> out = null;
		for (int i = 0; i < cards.size(); i++) {
			TabCard card = cards.get(i);
			if (card.title() == null || !card.title().toLowerCase(Locale.ROOT).equals(STATS)) {
				if (out != null) {
					out.add(card);
				}
				continue;
			}
			List<TabCard.Row> stats = new ArrayList<>(card.rows().size());
			for (TabCard.Row row : card.rows()) {
				if (isStat(row)) {
					stats.add(row);
					continue;
				}
				if (other == null) {
					other = new ArrayList<>();
				}
				other.add(row);
				if (log) {
					logUnknown(row.label().getString());
				}
			}
			if (stats.size() == card.rows().size()) {
				if (out != null) {
					out.add(card);
				}
				continue;
			}
			if (out == null) {
				out = new ArrayList<>(cards.subList(0, i));
			}
			if (!stats.isEmpty()) {
				out.add(new TabCard(card.title(), stats));
			}
		}
		if (out == null) {
			return cards;
		}
		out.add(new TabCard(OTHER, other));
		return out;
	}

	/** @return whether a row is a known stat: a label and a value, the label on the list or a fortune or wisdom */
	static boolean isStat(TabCard.Row row) {
		if (row.value() == null) {
			return false;
		}
		String label = plain(row.label().getString());
		return KNOWN.contains(label) || label.endsWith(" fortune") || label.endsWith(" wisdom");
	}

	/** @return a label without the symbols and colors around it, lower case: "☘ Mining Fortune" is "mining fortune" */
	static String plain(String label) {
		StringBuilder out = new StringBuilder(label.length());
		boolean space = false;
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i);
			if (Character.isLetter(c)) {
				if (space && !out.isEmpty()) {
					out.append(' ');
				}
				space = false;
				out.append(Character.toLowerCase(c));
			} else if (Character.isWhitespace(c)) {
				space = true;
			}
		}
		return out.toString();
	}

	private static void logUnknown(String label) {
		if (LOGGED.size() < MOST_LOGGED && LOGGED.add(label)) {
			Debug.log("Tab list: \"{}\" is not a known stat, shown under {}", label, OTHER);
		}
	}
}
