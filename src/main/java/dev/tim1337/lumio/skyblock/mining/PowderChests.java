package dev.tim1337.lumio.skyblock.mining;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Crystal Hollows treasure chests the player uncovered, by block (a packed block position) and the server tick it appeared at. Every chest
 * block that appears near the player through a block change is a treasure chest, at once, with or without the server's "You uncovered a
 * treasure chest!" line: the line only confirms it, in either order, one chest for each line. (Before, a chest needed its line within a second,
 * and a line made one chest only: two chests uncovered at once, a chest whose line came late or not at all, and a chest that appeared while the
 * line of the one before was still waiting were all missed.) A chest leaves when it is opened, when its block is gone, when its time is up, or
 * on a world change. Plain logic with the ticks given to it, so it is unit tested.
 */
final class PowderChests {
	/** How long a chest stays, in server ticks: believed to be 60 seconds; the debug probe logs the real times to measure it. */
	static final long LIFETIME_TICKS = 60 * 20;
	/** How many ticks apart the chest block and the chat line may come and still belong together: for the log only, a chest counts without. */
	static final long PAIR_WINDOW_TICKS = 40;
	/** Under these seconds left the timer turns orange, and red. */
	static final int ORANGE_SECONDS = 20;
	static final int RED_SECONDS = 10;
	static final int WHITE = 0xFFFFFFFF;
	static final int ORANGE = 0xFFFFAA00;
	static final int RED = 0xFFFF5555;
	/** No block: what {@link #mostRecent} gives while no chest is active. */
	static final long NONE = Long.MIN_VALUE;
	private static final int TICKS_PER_SECOND = 20;
	/** At most this many "uncovered" lines wait for their chest. */
	private static final int MOST_LINES = 8;

	/** The active chests: block → the tick it appeared at, in the order they appeared. */
	private final Map<Long, Long> active = new LinkedHashMap<>();
	/** The active chests a chat line confirmed. */
	private final java.util.Set<Long> confirmed = new java.util.HashSet<>();
	/** The ticks of the "uncovered" lines no chest took yet, oldest first. */
	private final java.util.ArrayDeque<Long> lines = new java.util.ArrayDeque<>();

	/**
	 * A chest block appeared near the player: it is a chest of the player's from now on.
	 *
	 * @return whether it is new (not one that is active already)
	 */
	boolean chestAppeared(long block, long tick) {
		if (active.containsKey(block)) {
			return false;
		}
		active.put(block, tick);
		dropOldLines(tick);
		if (!lines.isEmpty()) {
			// a line that came shortly before was this chest's
			lines.pollFirst();
			confirmed.add(block);
		}
		return true;
	}

	/**
	 * The server said a treasure chest was uncovered: the oldest chest that appeared shortly before and has no line yet is confirmed, or the
	 * next one to appear is.
	 *
	 * @return the block of the chest it confirmed, or {@link #NONE} when it waits for the block
	 */
	long uncovered(long tick) {
		for (Map.Entry<Long, Long> entry : active.entrySet()) {
			if (tick - entry.getValue() <= PAIR_WINDOW_TICKS && !confirmed.contains(entry.getKey())) {
				confirmed.add(entry.getKey());
				return entry.getKey();
			}
		}
		dropOldLines(tick);
		if (lines.size() >= MOST_LINES) {
			lines.pollFirst();
		}
		lines.addLast(tick);
		return NONE;
	}

	/** @return whether a chat line confirmed an active chest */
	boolean confirmed(long block) {
		return confirmed.contains(block);
	}

	private void dropOldLines(long tick) {
		while (!lines.isEmpty() && tick - lines.peekFirst() > PAIR_WINDOW_TICKS) {
			lines.pollFirst();
		}
	}

	/** @return whether the chest was active and is gone now: opened, or its block changed */
	boolean remove(long block) {
		confirmed.remove(block);
		return active.remove(block) != null;
	}

	/** @return the tick an active chest appeared at, or {@link #NONE} */
	long appearedAt(long block) {
		Long at = active.get(block);
		return at == null ? NONE : at;
	}

	/** @return the chests whose time is up, removed */
	List<Long> expire(long tick) {
		List<Long> ended = null;
		for (Map.Entry<Long, Long> entry : active.entrySet()) {
			if (tick - entry.getValue() >= LIFETIME_TICKS) {
				if (ended == null) {
					ended = new ArrayList<>();
				}
				ended.add(entry.getKey());
			}
		}
		if (ended == null) {
			return List.of();
		}
		for (Long block : ended) {
			active.remove(block);
			confirmed.remove(block);
		}
		return ended;
	}

	/** A world change: every chest and every waiting line is gone. */
	void clear() {
		active.clear();
		confirmed.clear();
		lines.clear();
	}

	/** @return the active chests' blocks, in the order they appeared */
	Iterable<Long> blocks() {
		return active.keySet();
	}

	boolean contains(long block) {
		return active.containsKey(block);
	}

	boolean isEmpty() {
		return active.isEmpty();
	}

	int size() {
		return active.size();
	}

	/** @return the block of the chest uncovered last that is still active, or {@link #NONE} */
	long mostRecent() {
		long found = NONE;
		long foundTick = Long.MIN_VALUE;
		for (Map.Entry<Long, Long> entry : active.entrySet()) {
			if (entry.getValue() >= foundTick) {
				found = entry.getKey();
				foundTick = entry.getValue();
			}
		}
		return found;
	}

	/** @return the whole seconds a chest has left, rounded up (0 when its time is up), or -1 for a block that is no active chest */
	int secondsLeft(long block, long tick) {
		Long at = active.get(block);
		if (at == null) {
			return -1;
		}
		long left = LIFETIME_TICKS - (tick - at);
		return left <= 0 ? 0 : (int) ((left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
	}

	/** @return the timer's color for the seconds left: white, orange under 20 s, red under 10 s */
	static int timerColor(int seconds) {
		if (seconds < RED_SECONDS) {
			return RED;
		}
		return seconds < ORANGE_SECONDS ? ORANGE : WHITE;
	}
}
