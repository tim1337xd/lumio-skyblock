package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Works out the item search's results in small steps, a slice of the items per frame, so a search over every item of SkyBlock never holds a frame
 * up: the matches of the words, pinned items first if wanted, then the name matches, sorted; items that
 * only mention the words in their lore come only when no name matches. The last results stay shown until the new ones are done. Prices come from the
 * lists the mod already has; nothing is asked of anybody.
 */
final class ItemSearchJob {
	/** How long a frame may spend on the search. */
	static final long BUDGET_NANOS = 1_500_000L;
	/** How many items are looked at between checks of the clock. */
	private static final int CHECK_EVERY = 64;
	private static final Pattern SPACES = Pattern.compile("\\s+");

	/** What the results depend on: when any of it changes, the search starts again. */
	record Query(String text, boolean lore, ItemListFeature.Sort sort, int pinnedVersion, boolean pinnedFirst, boolean onlyWhileSearching, int itemCount, int sourceCount) {
	}

	private Query wanted;
	private Query running;
	private String[] words = new String[0];
	private List<ItemRepo.Entry> source = List.of();
	private int at;
	private final List<ItemRepo.Entry> pinnedMatches = new ArrayList<>();
	private final List<ItemRepo.Entry> names = new ArrayList<>();
	private final List<ItemRepo.Entry> lore = new ArrayList<>();
	private List<ItemRepo.Entry> results = List.of();
	private boolean everDone;
	private int doneVersion;

	/** @return the results as last worked out */
	List<ItemRepo.Entry> results() {
		return results;
	}

	/** @return whether new results are being worked out */
	boolean busy() {
		return running != null;
	}

	/** @return whether results were ever worked out (else the panel says it is loading) */
	boolean everDone() {
		return everDone;
	}

	/** @return a number that changes with every new set of results */
	int version() {
		return doneVersion;
	}

	/**
	 * Goes on with the search for a frame: starts it again when the query changed, and works through items until the frame's time is used.
	 *
	 * @param pinned the pinned items, for "Pinned first"
	 */
	void step(Query query, Set<String> pinned) {
		if (!query.equals(wanted)) {
			wanted = query;
			start(query);
		}
		if (running == null) {
			return;
		}
		long until = System.nanoTime() + BUDGET_NANOS;
		while (at < source.size()) {
			for (int i = 0; i < CHECK_EVERY && at < source.size(); i++) {
				look(source.get(at++), pinned);
			}
			if (System.nanoTime() > until) {
				return;
			}
		}
		finish();
	}

	private void start(Query query) {
		running = query;
		at = 0;
		pinnedMatches.clear();
		names.clear();
		lore.clear();
		String text = query.text().strip().toLowerCase(Locale.ROOT);
		words = text.isEmpty() ? new String[0] : SPACES.split(text);
		if (words.length == 0 && query.onlyWhileSearching()) {
			source = List.of();
			return;
		}
		// mobs and NPCs are found by a search, for their drops and shops, and are not listed otherwise
		List<ItemRepo.Entry> all = ItemRepo.items();
		if (words.length > 0) {
			all = new ArrayList<>(all);
			all.addAll(ItemRepo.sources());
		}
		source = all;
	}

	private void look(ItemRepo.Entry entry, Set<String> pinned) {
		int match = words.length == 0 ? 2 : ItemRepo.matches(entry, words, running.lore());
		if (match == 0) {
			return;
		}
		if (running.pinnedFirst() && pinned.contains(entry.id)) {
			pinnedMatches.add(entry);
		} else if (match == 2) {
			names.add(entry);
		} else {
			lore.add(entry);
		}
	}

	private void finish() {
		Comparator<ItemRepo.Entry> order = order(running.sort());
		pinnedMatches.sort(order);
		names.sort(order);
		lore.sort(order);
		List<ItemRepo.Entry> done = new ArrayList<>(pinnedMatches.size() + names.size() + lore.size());
		done.addAll(pinnedMatches);
		done.addAll(names);
		// items that only mention the words in their lore ("aspect of the dragons" in a pet's drops) are only shown when no name matches: a search
		// for an item finds the item, not everything mentioning it
		if (names.isEmpty() && pinnedMatches.isEmpty()) {
			done.addAll(lore);
		}
		results = done;
		running = null;
		everDone = ItemRepo.ready();
		doneVersion++;
	}

	private static Comparator<ItemRepo.Entry> order(ItemListFeature.Sort sort) {
		Comparator<ItemRepo.Entry> byName = Comparator.comparing(ItemRepo.Entry::plainName, String.CASE_INSENSITIVE_ORDER);
		return switch (sort) {
			case RARITY -> Comparator.<ItemRepo.Entry>comparingInt(entry -> -ItemTypes.of(entry).rank()).thenComparing(byName);
			case NAME -> byName;
			case PRICE -> Comparator.<ItemRepo.Entry>comparingDouble(entry -> -SkyBlockPrices.listedPrice(entry.id)).thenComparing(byName);
		};
	}
}
