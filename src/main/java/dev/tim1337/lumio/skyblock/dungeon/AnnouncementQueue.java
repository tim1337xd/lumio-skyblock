package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The one send queue of the messages Lumio sends to the server (the dungeon announcements, the replies to party commands, the mask announce): a
 * message goes out right away when its event happens, unless one went out shortly before; then it waits until a gap since the last one has passed,
 * rolled for each message between 1 and 2 seconds. Each event at most once per run, the same text never twice in the queue; the player's own
 * messages never hold it back. Cleared when the run ends or the world changes. Plain logic with a clock given to it, so it is tested
 * without the game.
 */
public final class AnnouncementQueue {
	/** The texts Odin sends, so other players' mods (Odin, Skytils and others) count them for their score. */
	public static final String MIMIC = "Mimic Killed!";
	public static final String PRINCE = "Prince Killed!";
	public static final String BAT = "Bat Killed!";
	/** The defaults of the texts the player can change. */
	public static final String DEFAULT_270 = "270 score reached! ({time})";
	public static final String DEFAULT_300 = "300 score reached! ({time})";
	public static final String DEFAULT_LEAP = "Leaped to {name}!";
	public static final int MOST_TEXT = 100;
	/** The gap after the last message sent, rolled for each message from the least to the most. */
	static final long GAP_LEAST_MILLIS = 1_000;
	static final long GAP_MOST_MILLIS = 2_000;
	private static final int MOST_WAITING = 8;

	/** When the last message went out, shared by the queues that send for the same player, so together they keep the gap. */
	public static final class Gap {
		private long lastSent = Long.MIN_VALUE / 2;
	}

	/** The gap of the game's queues: the dungeon announcements and the replies to party commands. */
	public static final Gap SHARED_GAP = new Gap();

	/** A message waiting to go out: its event, its text, and the gap it keeps after the last message sent. */
	public record Waiting(String event, String text, long gapMillis) {
	}

	private final LongSupplier clock;
	private final java.util.function.LongUnaryOperator roll;
	private final Deque<Waiting> waiting = new ArrayDeque<>();
	private final Set<String> sentThisRun = new HashSet<>();
	private final Gap gap;

	/**
	 * @param clock the time now, in milliseconds
	 * @param roll  gives a number from 0 to the given one, for the gap of a message (random in the game, fixed in tests)
	 */
	public AnnouncementQueue(LongSupplier clock, java.util.function.LongUnaryOperator roll) {
		this(clock, roll, new Gap());
	}

	/** @param gap the gap shared with the other queues whose messages go out from the same player */
	public AnnouncementQueue(LongSupplier clock, java.util.function.LongUnaryOperator roll, Gap gap) {
		this.clock = clock;
		this.roll = roll;
		this.gap = gap;
	}

	/**
	 * An event of the run with its message. Each event goes out once per run; a text already waiting is not added again.
	 *
	 * @return whether it was taken
	 */
	public boolean add(String event, String text) {
		if (text == null || text.isBlank() || sentThisRun.contains(event) || waiting.size() >= MOST_WAITING) {
			return false;
		}
		for (Waiting each : waiting) {
			if (each.event().equals(event) || each.text().equals(text)) {
				return false;
			}
		}
		sentThisRun.add(event);
		waiting.add(new Waiting(event, text, GAP_LEAST_MILLIS + roll.applyAsLong(GAP_MOST_MILLIS - GAP_LEAST_MILLIS)));
		return true;
	}

	/** @return the message that may go out now, taken from the queue, or null */
	public Waiting poll() {
		Waiting first = waiting.peekFirst();
		long now = clock.getAsLong();
		if (first == null || now - gap.lastSent < first.gapMillis()) {
			return null;
		}
		waiting.pollFirst();
		gap.lastSent = now;
		return first;
	}

	/** The run is over or the world changed: nothing waits, and every event may come again in the next run. */
	public void clear() {
		waiting.clear();
		sentThisRun.clear();
	}

	/** Every event may come again (for queues whose events are not tied to a run, such as the replies to party commands). */
	public void forgetEvents() {
		sentThisRun.clear();
	}

	public int waiting() {
		return waiting.size();
	}

	/** @return a text with its placeholders ("{name}") filled in, cut to the most a message may be; the default where the text is empty */
	public static String fill(String text, String fallback, String... pairs) {
		String result = text == null || text.isBlank() ? fallback : text.strip();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			result = result.replace("{" + pairs[i] + "}", pairs[i + 1]);
		}
		return result.length() > MOST_TEXT ? result.substring(0, MOST_TEXT) : result;
	}
}
