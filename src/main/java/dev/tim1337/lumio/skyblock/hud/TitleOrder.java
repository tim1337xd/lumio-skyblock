package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The order of Lumio's on-screen titles, without the game: one title at a time. A title that comes while another shows waits for it instead
 * of taking its place, and while titles wait each one shows only briefly, so several at once (three drops in a second) are all seen. An
 * urgent title (a dungeon alert that is about this very moment) shows at once, as it always did; what waits follows it.
 *
 * @param <T> what a title is, to the caller
 */
public final class TitleOrder<T> {
	/** How long a title shows while others wait behind it, and how many may wait; older ones give way. */
	public static final long BRIEF_MILLIS = 1_200;
	public static final int MOST_WAITING = 6;

	/** A title to show now, and for how long. */
	public record Showing<T>(T title, long millis) {
	}

	private record Waiting<T>(T title, long millis) {
	}

	private final Deque<Waiting<T>> waiting = new ArrayDeque<>();
	private long shownAt = Long.MIN_VALUE / 2;
	private long busyUntil = Long.MIN_VALUE / 2;

	/**
	 * A new title.
	 *
	 * @param millis how long it shows when nothing waits behind it
	 * @param urgent whether it shows at once, also over a title that shows
	 * @return the title to show now, or null when it waits for its turn
	 */
	public Showing<T> offer(T title, long millis, boolean urgent, long now) {
		if (urgent || now >= busyUntil && waiting.isEmpty()) {
			return show(title, urgent || waiting.isEmpty() ? millis : Math.min(millis, BRIEF_MILLIS), now);
		}
		for (Waiting<T> each : waiting) {
			if (each.title().equals(title)) {
				return null;
			}
		}
		if (waiting.size() >= MOST_WAITING) {
			waiting.removeFirst();
		}
		waiting.addLast(new Waiting<>(title, millis));
		// the one showing makes room once it was seen for a moment
		busyUntil = Math.min(busyUntil, Math.max(now, shownAt + BRIEF_MILLIS));
		return null;
	}

	/** Once per tick. @return the next waiting title when its turn has come, or null */
	public Showing<T> next(long now) {
		if (waiting.isEmpty() || now < busyUntil) {
			return null;
		}
		Waiting<T> next = waiting.removeFirst();
		return show(next.title(), waiting.isEmpty() ? next.millis() : Math.min(next.millis(), BRIEF_MILLIS), now);
	}

	private Showing<T> show(T title, long millis, long now) {
		shownAt = now;
		busyUntil = now + millis;
		return new Showing<>(title, millis);
	}

	/** Nothing shows and nothing waits: another world. */
	public void clear() {
		waiting.clear();
		shownAt = Long.MIN_VALUE / 2;
		busyUntil = Long.MIN_VALUE / 2;
	}

	public int waiting() {
		return waiting.size();
	}
}
