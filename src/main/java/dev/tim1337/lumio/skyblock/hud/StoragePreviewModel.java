package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** What the storage preview shows from what was seen: which pages, under which tab and search, and how long ago. Plain data, so it is unit tested. */
final class StoragePreviewModel {
	static final int ALL = 0;
	static final int ENDER = 1;
	static final int BACKPACKS = 2;
	private static final long MINUTE_MILLIS = 60_000L;
	private static final long HOUR_MINUTES = 60L;
	private static final long DAY_HOURS = 24L;

	/** A page as the preview knows it: whether it was ever seen, and the names of what was in it, for the search. */
	record Page(String key, boolean seen, List<String> names) {
	}

	private StoragePreviewModel() {
	}

	/** @return whether a card shows items: a page seen before, or the one open now, also if it was never kept */
	static boolean hasItems(boolean seen, boolean live) {
		return seen || live;
	}

	/** @return the keys of all pages, known or not, ender chest first, each in order: pages never seen are in the list too, unless they are not wanted */
	static List<String> keys(Collection<String> known, int enderPages, int backpacks, boolean withUnseen) {
		Set<String> keys = new TreeSet<>((a, b) -> {
			if (a.charAt(0) != b.charAt(0)) {
				return a.charAt(0) == 'e' ? -1 : 1;
			}
			return Integer.compare(number(a), number(b));
		});
		keys.addAll(known);
		if (withUnseen) {
			for (int i = 1; i <= enderPages; i++) {
				keys.add("e" + i);
			}
			for (int i = 1; i <= backpacks; i++) {
				keys.add("b" + i);
			}
		}
		return new ArrayList<>(keys);
	}

	private static int number(String key) {
		try {
			return Integer.parseInt(key.substring(1));
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}

	/** @return whether a page goes with a tab */
	static boolean inTab(String key, int tab) {
		return tab == ALL || (tab == ENDER) == key.startsWith("e");
	}

	/** @return whether a page shows under a search: with a search, only one with something that matches, which a page never seen cannot have */
	static boolean matches(Page page, String search) {
		String wanted = search.strip().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return true;
		}
		for (String name : page.names()) {
			if (name.toLowerCase(Locale.ROOT).contains(wanted)) {
				return true;
			}
		}
		return false;
	}

	/** @return how many things on a page match a search */
	static int count(Page page, String search) {
		String wanted = search.strip().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (String name : page.names()) {
			if (name.toLowerCase(Locale.ROOT).contains(wanted)) {
				count++;
			}
		}
		return count;
	}

	/** @return the pages to show under a tab and a search, in their order */
	static List<Page> shown(List<Page> pages, int tab, String search) {
		List<Page> out = new ArrayList<>();
		for (Page page : pages) {
			if (inTab(page.key(), tab) && matches(page, search)) {
				out.add(page);
			}
		}
		return out;
	}

	/** @return the tag of the preview: "Preview · seen 12 min ago"; nothing seen yet is said too */
	static String tag(long newestSeen, long now) {
		if (newestSeen <= 0L) {
			return "Preview · nothing seen yet";
		}
		long minutes = Math.max(0L, now - newestSeen) / MINUTE_MILLIS;
		if (minutes < 1L) {
			return "Preview · seen just now";
		}
		if (minutes < HOUR_MINUTES) {
			return "Preview · seen " + minutes + " min ago";
		}
		long hours = minutes / HOUR_MINUTES;
		return hours < DAY_HOURS ? "Preview · seen " + hours + " h ago" : "Preview · seen " + hours / DAY_HOURS + " d ago";
	}
}
