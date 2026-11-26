package dev.tim1337.lumio.skyblock.mining;

import java.util.function.Consumer;

/**
 * The pickaxe ability's cooldown as a clock. Its length is fixed when the ability is used: the base cooldown (from the pickaxe's lore, or learned from
 * use to ready), less Sky Mall's 20 % when that buff is on and the player is where it counts. Then it runs in real time, and nothing about a world,
 * server or area change touches it: entering or leaving a Glacite Mineshaft keeps it. Whenever the server says how it is, it is set to that: the player
 * list's widget (which wins over the estimate), "This ability is on cooldown for Xs." and "... is now available!". Each of those is written to the debug
 * log with what it set. Plain, so it is unit tested.
 */
final class AbilityClock {
	/** Sky Mall's "-20% Pickaxe Ability cooldowns". */
	static final float SKY_MALL = 0.8f;
	/** Right after a use the player list still shows the time from before for a moment: it is not read then. */
	static final long TAB_LATE_MILLIS = 2000L;
	/** The list refreshes every few seconds, so it is always a little behind: a difference below this is not a correction. */
	static final long TAB_TOLERANCE_MILLIS = 3000L;
	/** After the ability became ready the list can still show a few seconds left: that does not start it again. */
	static final long STALE_AFTER_READY_MILLIS = 10_000L;
	static final long STALE_LEFT_MILLIS = 8000L;
	private static final long SHORTEST_LEARNED = 5_000L;
	private static final long LONGEST_LEARNED = 400_000L;

	/** When it is ready again, or 0 when it is ready. */
	long readyAt;
	/** The whole cooldown of the running use, what a bar fills over. */
	long totalMillis = 60_000L;
	/** When it was used, 0 when not running. */
	long usedAt;
	/** The base cooldown, without reductions: from the lore, or learned. */
	long baseMillis = 60_000L;
	/** Whether the base came from the lore (exact), and whether one was learned (saved). */
	boolean loreBase;
	boolean learned;
	/** Whether this use's whole cooldown was taken from the player list already. */
	private boolean totalFromList;
	/** The reduction at the use, and when it last became ready. */
	private float factorAtUse = 1f;
	private long readySince;
	private final Consumer<String> log;

	AbilityClock(Consumer<String> log) {
		this.log = log;
	}

	/** @return the time left, 0 when ready */
	long left(long now) {
		return readyAt == 0 ? 0 : Math.max(0, readyAt - now);
	}

	/** The pickaxe's lore states the base cooldown. */
	void lore(long baseFromLore) {
		if (baseFromLore > 0) {
			baseMillis = baseFromLore;
			loreBase = true;
		}
	}

	/** "You used your ... Pickaxe Ability!": the cooldown starts, its length fixed now. */
	void used(long now, boolean skyMallHere) {
		factorAtUse = skyMallHere ? SKY_MALL : 1f;
		totalMillis = Math.round(baseMillis * factorAtUse);
		readyAt = now + totalMillis;
		usedAt = now;
		totalFromList = false;
		log.accept("used: cooldown " + totalMillis / 1000.0 + " s" + (skyMallHere ? " (Sky Mall -20%)" : ""));
	}

	/** "... is now available!": ready; the time since the use, without the reduction then, is the base when the lore does not say it. */
	void available(long now) {
		if (!loreBase && usedAt > 0 && now - usedAt > SHORTEST_LEARNED && now - usedAt < LONGEST_LEARNED) {
			baseMillis = Math.round((now - usedAt) / factorAtUse);
			learned = true;
		}
		usedAt = 0;
		readyAt = 0;
		readySince = now;
		log.accept("ready message: 0 s left");
	}

	/** "This ability is on cooldown for Xs.": the server's own count, higher or lower. */
	void onCooldownFor(long now, long seconds) {
		readyAt = now + seconds * 1000L;
		totalMillis = Math.max(totalMillis, readyAt - now);
		log.accept("on cooldown message: " + seconds + " s left");
	}

	/**
	 * The player list's widget: the time left, or -1 for "Available". It wins over the estimate, except right after a use and while the ability is
	 * active (the list still shows the time before), and for the few seconds it still shows after the ability became ready.
	 */
	void tab(long now, long leftMillis, boolean activePhase) {
		if (usedAt > 0 && (now - usedAt < TAB_LATE_MILLIS || activePhase)) {
			return;
		}
		if (leftMillis < 0) {
			if (readyAt != 0 && left(now) > TAB_TOLERANCE_MILLIS) {
				log.accept("player list: available, was " + left(now) / 1000 + " s");
			}
			if (readyAt != 0) {
				readySince = now;
			}
			readyAt = 0;
			usedAt = 0;
			return;
		}
		long counted = left(now);
		boolean justReady = counted == 0 && now - readySince < STALE_AFTER_READY_MILLIS && leftMillis <= STALE_LEFT_MILLIS;
		if (!justReady && Math.abs(leftMillis - counted) > TAB_TOLERANCE_MILLIS) {
			readyAt = now + leftMillis;
			log.accept("player list: " + leftMillis / 1000 + " s left, was " + counted / 1000 + " s");
		}
		if (!loreBase && !totalFromList && leftMillis > 0 && usedAt > 0) {
			// the first time the list gives after a use, with the time since, is the whole cooldown of this use
			totalFromList = true;
			totalMillis = Math.max(1000L, leftMillis + (now - usedAt));
			baseMillis = Math.round(totalMillis / factorAtUse);
			learned = true;
		}
		totalMillis = Math.max(totalMillis, left(now));
	}

	/** Forgets the running cooldown: another profile, or the game left for longer than it had left. */
	void reset(String why) {
		if (readyAt != 0) {
			log.accept("reset (" + why + ")");
		}
		readyAt = 0;
		usedAt = 0;
	}
}
