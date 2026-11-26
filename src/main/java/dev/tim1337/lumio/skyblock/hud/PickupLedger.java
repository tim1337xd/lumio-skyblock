package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What just entered or left the inventory, for the pickup log: the inventory's counts by SkyBlock id are compared with the last ones each time the
 * inventory changes, so an item that only changed its data (durability, fuel, a counter in its lore) or moved to another slot is no change. Changes of
 * the same item within a few seconds add up on one line. Nothing is counted while a menu is open (unless wanted), for a moment after one closes (an
 * item on the cursor goes back), or for the first seconds in a new world (the server sends the inventory again). Plain data, so it is unit tested.
 */
final class PickupLedger {
	/** Changes of the same item this close together add up on one line. */
	static final long MERGE_MILLIS = 3_000L;
	/** How long after joining a world or switching profiles nothing is counted. */
	static final long WORLD_GRACE_MILLIS = 4_000L;
	/** How long after a menu closes nothing is counted: an item held on the cursor goes back into the inventory then. */
	static final long MENU_GRACE_MILLIS = 1_000L;
	/** How long a line takes to fade out at the end of its time. */
	static final long FADE_MILLIS = 400L;
	/** The id of the purse's coins. */
	static final String COINS = "SKYBLOCK_COIN";
	/** Lines kept at most, whatever is shown. */
	static final int MOST_LINES = 32;

	/** A line: the item, how many came in (negative: left), whether through the sacks, and when it started and last changed. */
	static final class Line {
		final String id;
		final boolean sack;
		long count;
		long since;
		long at;

		Line(String id, boolean sack, long count, long now) {
			this.id = id;
			this.sack = sack;
			this.count = count;
			this.since = now;
			this.at = now;
		}

		boolean coins() {
			return COINS.equals(id);
		}
	}

	private final Map<String, Long> last = new HashMap<>();
	private boolean known;
	private long quietUntil;
	private boolean menuWasOpen;
	private long lastPurse = -1;
	private final List<Line> lines = new ArrayList<>();
	private final Set<String> seen = new HashSet<>();
	private int version;

	/** @return the lines, newest last */
	List<Line> lines() {
		return lines;
	}

	/** @return a number that changes with every change of the lines */
	int version() {
		return version;
	}

	/** A new world or another profile: the next inventory is taken as it is, after a short wait. */
	void worldChanged(long now) {
		known = false;
		lastPurse = -1;
		quietUntil = now + WORLD_GRACE_MILLIS;
	}

	/**
	 * The inventory changed: its counts by SkyBlock id now.
	 *
	 * @param menuOpen      whether a menu is open (chests, storage, trades, the inventory screen)
	 * @param whileMenuOpen whether changes count while one is open
	 * @param hidden        ids that never show
	 */
	void inventory(Map<String, Long> counts, long now, boolean menuOpen, boolean whileMenuOpen, Set<String> hidden) {
		boolean ignore = !known || now < quietUntil || menuOpen && !whileMenuOpen;
		if (menuWasOpen && !menuOpen && !whileMenuOpen) {
			quietUntil = Math.max(quietUntil, now + MENU_GRACE_MILLIS);
			ignore = true;
		}
		menuWasOpen = menuOpen;
		if (!ignore) {
			seen.clear();
			for (Map.Entry<String, Long> entry : counts.entrySet()) {
				seen.add(entry.getKey());
				long change = entry.getValue() - last.getOrDefault(entry.getKey(), 0L);
				if (change != 0 && !hidden.contains(entry.getKey())) {
					add(entry.getKey(), change, false, now);
				}
			}
			for (Map.Entry<String, Long> entry : last.entrySet()) {
				if (!seen.contains(entry.getKey()) && !hidden.contains(entry.getKey())) {
					add(entry.getKey(), -entry.getValue(), false, now);
				}
			}
		}
		last.clear();
		last.putAll(counts);
		known = true;
	}

	/** Items that went into the sacks (negative: taken out), from the chat's sack summary. */
	void sack(String id, long count, long now) {
		if (count != 0 && now >= quietUntil) {
			add(id, count, true, now);
		}
	}

	/** The purse as the scoreboard shows it: its change is a coins line. */
	void purse(long purse, long now) {
		if (purse < 0) {
			return;
		}
		if (lastPurse >= 0 && purse != lastPurse && now >= quietUntil) {
			add(COINS, purse - lastPurse, false, now);
		}
		lastPurse = purse;
	}

	/** Adds a change: onto the item's line when it changed within the last seconds, else as a new line. A line whose changes add up to nothing goes. */
	void add(String id, long count, boolean sack, long now) {
		for (int i = lines.size() - 1; i >= 0; i--) {
			Line line = lines.get(i);
			if (line.id.equals(id) && line.sack == sack && now - line.at <= MERGE_MILLIS) {
				line.count += count;
				line.at = now;
				if (line.count == 0) {
					lines.remove(i);
				} else {
					// the line moves to the end, the newest
					lines.remove(i);
					lines.add(line);
				}
				version++;
				return;
			}
		}
		lines.add(new Line(id, sack, count, now));
		while (lines.size() > MOST_LINES) {
			lines.removeFirst();
		}
		version++;
	}

	/** Lets lines go once they were shown long enough (and have faded out). */
	void expire(long now, long showMillis) {
		if (lines.removeIf(line -> now - line.at > showMillis + FADE_MILLIS)) {
			version++;
		}
	}

	/** @return how visible a line is now: fading in when new, out at the end of its time */
	static float shown(Line line, long now, long showMillis, long fadeInMillis) {
		float in = fadeInMillis <= 0 ? 1f : Math.min(1f, (now - line.since) / (float) fadeInMillis);
		long left = showMillis + FADE_MILLIS - (now - line.at);
		float out = Math.min(1f, Math.max(0f, left / (float) FADE_MILLIS));
		return Math.max(0f, Math.min(in, out));
	}

	/** @return whether an item is on the hide list: its id, or a part of its name ("*" stands for anything), ignoring case */
	static boolean hiddenBy(List<String> patterns, String id, String name) {
		String lowerId = id.toLowerCase(Locale.ROOT);
		String lowerName = name.toLowerCase(Locale.ROOT);
		for (String pattern : patterns) {
			String lower = pattern.toLowerCase(Locale.ROOT).strip();
			if (lower.isEmpty()) {
				continue;
			}
			if (lower.equals(lowerId) || wildcard(lower, lowerName) || wildcard(lower, lowerId)) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a text matches a pattern where "*" stands for anything; without "*", whether the text contains it */
	private static boolean wildcard(String pattern, String text) {
		if (pattern.indexOf('*') < 0) {
			return text.contains(pattern);
		}
		String[] parts = pattern.split("\\*", -1);
		int at = 0;
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.isEmpty()) {
				continue;
			}
			int found = text.indexOf(part, at);
			if (found < 0 || i == 0 && found != 0) {
				return false;
			}
			at = found + part.length();
		}
		return parts[parts.length - 1].isEmpty() || text.endsWith(parts[parts.length - 1]);
	}
}
