package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * What one profit tracker counted lately, so a gain taken back later (a sack summary that shows the items came from the player's own sacks, a craft
 * seen late, a Personal Deletor) leaves only the tracker that counted it, and only as much as it counted. Every tracker hears every take-back; a
 * tracker that was not active when the gain came, or counted it under another activity, takes nothing out. Plain logic, tested without the game.
 */
public final class CountedGains {
	/** How long a counted gain can still be taken back: the gain flow keeps it 40 seconds, and a sack summary comes a little late. */
	static final long KEEP_MILLIS = 60_000L;
	/** The most gains kept; the oldest go first. Fast farming counts a few a second. */
	private static final int MOST_KEPT = 512;

	private static final class Gain {
		final String id;
		long count;
		final long at;

		Gain(String id, long count, long at) {
			this.id = id;
			this.count = count;
			this.at = at;
		}
	}

	private final Deque<Gain> gains = new ArrayDeque<>();

	/** A gain this tracker counted. */
	public void counted(String id, long count, long now) {
		if (id == null || count <= 0) {
			return;
		}
		forget(now);
		if (gains.size() >= MOST_KEPT) {
			gains.removeFirst();
		}
		gains.addLast(new Gain(id, count, now));
	}

	/** @return how much of a take-back this tracker counted lately, now taken out of what it remembers: what it should remove */
	public long takeBack(String id, long count, long now) {
		forget(now);
		long rest = count;
		for (Iterator<Gain> it = gains.descendingIterator(); it.hasNext() && rest > 0; ) {
			Gain gain = it.next();
			if (gain.id.equals(id)) {
				long taken = Math.min(rest, gain.count);
				gain.count -= taken;
				rest -= taken;
				if (gain.count <= 0) {
					it.remove();
				}
			}
		}
		return count - rest;
	}

	/** Forgets everything, as when the tracker is reset. */
	public void clear() {
		gains.clear();
	}

	private void forget(long now) {
		while (!gains.isEmpty() && now - gains.getFirst().at > KEEP_MILLIS) {
			gains.removeFirst();
		}
	}
}
