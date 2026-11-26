package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.mining.PowderTab.Powder;

import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the player made of each powder: gained, the time farmed, and the treasure chests opened, for the session, today and all time (a
 * {@link ProfitTracker} for each powder, kept with the profit trackers' values). Gains come from two sources that often report the same powder:
 * the chat lines of a powder chest, at once, and the tab list's value rising, a moment later. A gain from chat is remembered for a short while and
 * taken off the next rise of the tab value, so it counts once. A value that falls (powder spent in the Heart of the Mountain) is never a loss:
 * it only moves the value the next rise is measured from. Time counts between gains that are less than a minute apart; a longer stop is a pause.
 * Plain logic with the times given to it, so it is unit tested.
 */
final class PowderGains {
	/** A stop longer than this is a pause: its time is not farmed time. */
	static final long PAUSE_AFTER_MS = 60_000L;
	/** A gain read in chat is expected in the tab value within this time; after it the tab's rise is a gain of its own. */
	static final long CREDIT_MS = 15_000L;
	/** A powder line this soon after a chest was opened is that chest's. */
	static final long CHEST_LINES_MS = 5_000L;
	static final String GAINED = "powder";
	static final String CHESTS = "chests";
	static final String FROM_CHESTS = "chest_powder";
	/** "You received +1,234 Gemstone Powder." (with or without a symbol before the name) and a reward list's "Gemstone Powder x1,234". */
	private static final Pattern RECEIVED = Pattern.compile("^You received \\+?([0-9][0-9,]*) (?:\\S{1,2} )?(Mithril|Gemstone|Glacite) Powder\\.?$");
	private static final Pattern LISTED = Pattern.compile("^(?:\\S{1,2} )?(Mithril|Gemstone|Glacite) Powder x([0-9][0-9,]*)$");

	private final ProfitTracker[] trackers = new ProfitTracker[Powder.values().length];
	private final long[] tab = new long[trackers.length];
	private final long[] credit = new long[trackers.length];
	private final long[] creditAt = new long[trackers.length];
	private final long[] lastGainAt = new long[trackers.length];
	private final boolean[] chestCounted = new boolean[trackers.length];
	private long chestOpenedAt = Long.MIN_VALUE;
	private Powder lastGained;

	PowderGains() {
		for (int i = 0; i < trackers.length; i++) {
			trackers[i] = new ProfitTracker();
			tab[i] = PowderTab.UNKNOWN;
			lastGainAt[i] = Long.MIN_VALUE;
		}
	}

	/** A gain a chat line names: a powder and its amount. */
	record Line(Powder powder, long amount) {
	}

	/** @return the powder gain a server line names, or null: "You received +1,234 Gemstone Powder." or a reward list's "Gemstone Powder x1,234" */
	static Line parse(String line) {
		String text = line.strip();
		Matcher received = RECEIVED.matcher(text);
		if (received.matches()) {
			return line(received.group(2), received.group(1));
		}
		Matcher listed = LISTED.matcher(text);
		return listed.matches() ? line(listed.group(1), listed.group(2)) : null;
	}

	private static Line line(String name, String amount) {
		long value = PowderTab.amount(amount);
		if (value <= 0) {
			return null;
		}
		for (Powder powder : Powder.values()) {
			if (powder.label.equals(name)) {
				return new Line(powder, value);
			}
		}
		return null;
	}

	/** A treasure chest was opened: the powder lines that follow shortly are its loot. */
	void chestOpened(long nowMs) {
		chestOpenedAt = nowMs;
		java.util.Arrays.fill(chestCounted, false);
	}

	/** A powder gain read in chat. It is taken off the tab value's next rise, so it counts once. */
	void fromChat(Powder powder, long amount, long nowMs) {
		if (amount <= 0) {
			return;
		}
		int i = powder.ordinal();
		expireCredit(i, nowMs);
		credit[i] += amount;
		creditAt[i] = nowMs;
		gained(powder, amount, nowMs);
		if (chestOpenedAt != Long.MIN_VALUE && nowMs - chestOpenedAt <= CHEST_LINES_MS) {
			trackers[i].count(FROM_CHESTS, amount);
			if (!chestCounted[i]) {
				chestCounted[i] = true;
				trackers[i].count(CHESTS, 1);
			}
		}
	}

	/**
	 * The tab list's value of a powder, as read now.
	 *
	 * @return what of its rise was counted as a gain: none of the first value read, of a fall, or of what chat reported already
	 */
	long fromTab(Powder powder, long amount, long nowMs) {
		int i = powder.ordinal();
		long before = tab[i];
		if (amount == PowderTab.UNKNOWN) {
			return 0;
		}
		tab[i] = amount;
		if (before == PowderTab.UNKNOWN || amount <= before) {
			return 0;
		}
		long rise = amount - before;
		expireCredit(i, nowMs);
		long counted = Math.max(0, rise - credit[i]);
		credit[i] = Math.max(0, credit[i] - rise);
		if (counted > 0) {
			gained(powder, counted, nowMs);
		}
		return counted;
	}

	private void expireCredit(int i, long nowMs) {
		if (credit[i] > 0 && nowMs - creditAt[i] > CREDIT_MS) {
			credit[i] = 0;
		}
	}

	private void gained(Powder powder, long amount, long nowMs) {
		int i = powder.ordinal();
		trackers[i].count(GAINED, amount);
		// the time since the gain before is farmed time, unless it was a pause
		if (lastGainAt[i] != Long.MIN_VALUE && nowMs - lastGainAt[i] <= PAUSE_AFTER_MS && nowMs > lastGainAt[i]) {
			trackers[i].addTime(nowMs - lastGainAt[i]);
		}
		lastGainAt[i] = nowMs;
		lastGained = powder;
	}

	/** @return the powder gained last in this session, or null before any gain */
	Powder lastGained() {
		return lastGained;
	}

	/** @return whether a powder's timer stands still: no gain for a while */
	boolean paused(Powder powder, long nowMs) {
		long last = lastGainAt[powder.ordinal()];
		return last == Long.MIN_VALUE || nowMs - last > PAUSE_AFTER_MS;
	}

	ProfitTracker tracker(Powder powder) {
		return trackers[powder.ordinal()];
	}

	private static long counter(ProfitTracker.Bucket bucket, String key) {
		Long value = bucket.counters.get(key);
		return value == null ? 0 : value;
	}

	long gained(Powder powder, ProfitTracker.Range range) {
		return counter(tracker(powder).bucket(range), GAINED);
	}

	long chests(Powder powder, ProfitTracker.Range range) {
		return counter(tracker(powder).bucket(range), CHESTS);
	}

	long timeMillis(Powder powder, ProfitTracker.Range range) {
		return tracker(powder).bucket(range).activeMillis;
	}

	/** @return a count per hour of farmed time; 0 while too little time was farmed to judge */
	private static double perHour(long count, long millis) {
		return millis < 10_000L ? 0 : count / (millis / 3_600_000.0);
	}

	double gainedPerHour(Powder powder, ProfitTracker.Range range) {
		return perHour(gained(powder, range), timeMillis(powder, range));
	}

	double chestsPerHour(Powder powder, ProfitTracker.Range range) {
		return perHour(chests(powder, range), timeMillis(powder, range));
	}

	/** @return the average powder of a chest, 0 before any chest */
	double perChest(Powder powder, ProfitTracker.Range range) {
		long opened = chests(powder, range);
		return opened == 0 ? 0 : counter(tracker(powder).bucket(range), FROM_CHESTS) / (double) opened;
	}

	void reset(ProfitTracker.Range range) {
		for (ProfitTracker tracker : trackers) {
			tracker.reset(range);
		}
	}

	/** @return a number that changes with every gain, for saving */
	int changes() {
		int changes = 0;
		for (ProfitTracker tracker : trackers) {
			changes = changes * 31 + tracker.changes();
		}
		return changes;
	}

	JsonObject save() {
		JsonObject json = new JsonObject();
		for (Powder powder : Powder.values()) {
			json.add(powder.name().toLowerCase(java.util.Locale.ROOT), tracker(powder).toJson());
		}
		return json;
	}

	/** Takes a profile's saved values in place of its own (today and all time); the session goes on. */
	void load(JsonObject json) {
		for (Powder powder : Powder.values()) {
			String key = powder.name().toLowerCase(java.util.Locale.ROOT);
			tracker(powder).replaceSaved(json != null && json.has(key) ? json.getAsJsonObject(key) : null);
		}
	}

	/** Adds saved values to its own. */
	void merge(JsonObject json) {
		for (Powder powder : Powder.values()) {
			String key = powder.name().toLowerCase(java.util.Locale.ROOT);
			if (json != null && json.has(key)) {
				tracker(powder).addSaved(json.getAsJsonObject(key));
			}
		}
	}
}
