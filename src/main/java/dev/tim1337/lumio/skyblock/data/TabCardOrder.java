package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.api.experimental.util.ScoreLines;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The fixed order of the tab list's info cards, by the kind of card: Area, Profile, Pet, Dungeons, Essence, RNG Meter, Party and the others the
 * server writes. A card's place (its slot) is the same whichever other cards there are, so the list can keep every card in its column; a card of
 * a kind not listed has none and comes after them, in the order it was first seen. No card is without a title: lines the server wrote under no
 * heading go into the one "Other" card at the end, exactly as sent, and are written to the debug log (tab) once each. Plain data, so it is unit
 * tested.
 */
public final class TabCardOrder {
	/** The cards in their order, by title in lower case. New kinds go at the end, before Other: a place, once given, stays. */
	private static final List<String> ORDER = List.of("area", "server", "profile", "pet", "dungeons", "essence", "rng meter", "party", "skills", "stats",
		"collection", "minions", "bestiary", "event", "events", "election", "timers", "jacob's contest", "commissions", "powders", "forges", "pickaxe ability",
		"crystals", "fairy souls", "trapper", "pests", "visitors", "crop milestones", "composter", "slayer", "daily quests", "faction quests", "reputation",
		"trophy fish", "frozen corpses", "rain", "thunder", "dragon", "eyes placed", "guests", "coop", "island", "bank", "interest", "fire sale", "cookie buff",
		"god potion", "active effects", "upgrades", "gifts", "good to know", "starborn temple", "moonglade beacon", "agatha's contest", "shards");
	/** The place of the Other card: after every kind listed. */
	static final int OTHER_SLOT = 900;
	private static final Set<String> LOGGED = new HashSet<>();
	private static final int MOST_LOGGED = 200;

	private TabCardOrder() {
	}

	/** @return the fixed place of a card by its title, -1 for a kind not listed; a count in the title ("Party (3)") does not matter */
	public static int slot(String title) {
		if (title == null) {
			return -1;
		}
		String plain = title.strip().toLowerCase(Locale.ROOT);
		int count = plain.indexOf('(');
		if (count > 0) {
			plain = plain.substring(0, count).strip();
		}
		if (plain.equals(TabStats.OTHER.toLowerCase(Locale.ROOT))) {
			return OTHER_SLOT;
		}
		return ORDER.indexOf(plain);
	}

	/**
	 * Gives every card its place and makes sure none is without a title: the rows of title-less cards are moved into the Other card (made if
	 * there is none), which stands last.
	 *
	 * @param log whether the moved lines are written to the debug log
	 */
	public static List<TabCard> placed(List<TabCard> cards, boolean log) {
		List<TabCard> out = new ArrayList<>(cards.size());
		List<TabCard.Row> other = new ArrayList<>();
		List<TabCard.Row> untitled = new ArrayList<>();
		for (TabCard card : cards) {
			String title = card.title() == null ? "" : card.title().strip();
			if (title.isEmpty()) {
				untitled.addAll(card.rows());
				if (log) {
					card.rows().forEach(TabCardOrder::logUntitled);
				}
			} else if (title.equalsIgnoreCase(TabStats.OTHER)) {
				other.addAll(card.rows());
			} else {
				out.add(new TabCard(card.title(), card.rows(), slot(title)));
			}
		}
		other.addAll(untitled);
		if (!other.isEmpty()) {
			out.add(new TabCard(TabStats.OTHER, other, OTHER_SLOT));
		}
		return out;
	}

	private static void logUntitled(TabCard.Row row) {
		String text = row.label().getString() + (row.value() == null ? "" : " | " + row.value().getString());
		if (DebugHooks.ACTIVE && LOGGED.size() < MOST_LOGGED && LOGGED.add(text)) {
			DebugHooks.log(SkyBlockDebug.TAB, "Tab list: a line under no heading, shown under {}: \"{}\" = {}", TabStats.OTHER, text, ScoreLines.codes(text));
		}
	}
}
