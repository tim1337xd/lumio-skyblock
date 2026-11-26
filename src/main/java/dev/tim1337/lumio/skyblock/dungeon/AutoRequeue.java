package dev.tim1337.lumio.skyblock.dungeon;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether and when the one requeue command of a finished run goes out. After the run ends it waits a moment for the end screen's
 * lines, then counts down the player's delay; at its end it says "send" exactly once, and only while every condition still holds: the player
 * leads (or allowed it otherwise), the party is large enough (or solo is allowed), nobody asked for downtime, nobody left, the run was
 * completed (or failed runs are allowed), and the player neither left, requeued by hand, cancelled nor was told to stop. Never more than once
 * per run: only a new run's start makes it ready again. Plain logic with the time given to it, so it is tested without the game.
 */
public final class AutoRequeue {
	/** Where the requeue of this run stands. */
	public enum State {
		/** No run ended yet (or the player left). */
		IDLE,
		/** The run just ended: its last lines are still coming in. */
		SETTLING,
		/** Counting down to the requeue. */
		COUNTING,
		/** The command of this run is out (or the player sent it). */
		SENT,
		/** The run ended, and something speaks against a requeue. */
		BLOCKED
	}

	/** Why no requeue goes out. */
	public enum Reason {
		NONE(""), STOPPED("stopped by the party"), CANCELLED("cancelled"), FAILED_RUN("the run was not completed"), DOWNTIME("downtime was asked for"),
		SOMEONE_LEFT("someone left the party"), NOT_LEADER("you are not the party leader"), SOLO("you are not in a party"),
		PARTY_TOO_SMALL("the party is too small"), ALREADY_SENT("already requeued"), LEFT("you left");

		private final String text;

		Reason(String text) {
			this.text = text;
		}

		public String text() {
			return text;
		}
	}

	/**
	 * The player's settings.
	 *
	 * @param delaySeconds how long after the run's end the command goes out
	 * @param notLeader    whether it also goes out while the player does not lead the party
	 * @param solo         whether it also goes out without a party
	 * @param minParty     the least size of the party, the player counted
	 * @param stopOnLeave  whether someone leaving the party stops it
	 * @param afterFailed  whether it also goes out after a run that was not completed
	 */
	public record Settings(int delaySeconds, boolean notLeader, boolean solo, int minParty, boolean stopOnLeave, boolean afterFailed) {
		public static final Settings DEFAULT = new Settings(5, false, false, 5, true, false);
	}

	/**
	 * What the game says now.
	 *
	 * @param completed  whether the boss was defeated
	 * @param partySize  the party's size with the player; 1 without a party
	 * @param selfLeads  whether the player leads the party
	 */
	public record Facts(boolean completed, int partySize, boolean selfLeads) {
	}

	/** How long the end screen's lines are waited for, so "completed" is known before anything is decided. */
	static final long SETTLE_MILLIS = 500;
	private static final long MILLIS_PER_SECOND = 1000;
	private static final int MOST_NAMES = 16;

	private State state = State.IDLE;
	private Reason reason = Reason.NONE;
	private long endedAt;
	private long sendAt;
	/** Who asked for downtime in this run, with the reason given (or ""). */
	private final Map<String, String> downtime = new LinkedHashMap<>();
	private final Set<String> leftParty = new LinkedHashSet<>();
	private boolean stopped;
	private boolean cancelled;
	private boolean sent;
	private boolean leftSelf;

	/** A new run: everything of the last one is forgotten, and one requeue may go out again. */
	public void runStarted() {
		state = State.IDLE;
		reason = Reason.NONE;
		downtime.clear();
		leftParty.clear();
		stopped = false;
		cancelled = false;
		sent = false;
		leftSelf = false;
	}

	/** The run ended (the end screen came). */
	public void ended(long now) {
		if (state == State.IDLE && !sent && !leftSelf) {
			state = State.SETTLING;
			endedAt = now;
		}
	}

	/** A party member asked for downtime ("!dt food"). */
	public void downtime(String name, String why) {
		if (downtime.size() < MOST_NAMES || downtime.containsKey(key(name))) {
			downtime.put(key(name), why == null ? "" : why);
		}
	}

	/** A party member took the downtime back ("!undt"). */
	public void undowntime(String name) {
		downtime.remove(key(name));
	}

	/** Someone left the party, was kicked or disconnected. */
	public void memberLeft(String name) {
		if (leftParty.size() < MOST_NAMES) {
			leftParty.add(key(name));
		}
	}

	/** A party member said "!stop": no requeue in this run. */
	public void stop() {
		stopped = true;
	}

	/** The player cancelled (the key, or the chat line's Cancel): no requeue in this run. */
	public void cancel() {
		if (state == State.SETTLING || state == State.COUNTING) {
			cancelled = true;
		}
	}

	/** The player sent the requeue by hand: nothing more goes out in this run. */
	public void selfRequeued() {
		sent = true;
		state = State.SENT;
		reason = Reason.ALREADY_SENT;
	}

	/** The player left the dungeon (another world): nothing goes out any more. */
	public void left() {
		leftSelf = true;
		if (state != State.SENT) {
			state = State.IDLE;
			reason = Reason.LEFT;
		}
	}

	/**
	 * Called every tick.
	 *
	 * @return true exactly once per run: the requeue command goes out now
	 */
	public boolean tick(long now, Settings settings, Facts facts) {
		if (state != State.SETTLING && state != State.COUNTING && state != State.BLOCKED) {
			return false;
		}
		if (state == State.SETTLING && now - endedAt < SETTLE_MILLIS) {
			return false;
		}
		Reason against = against(settings, facts);
		if (against != Reason.NONE) {
			state = State.BLOCKED;
			reason = against;
			return false;
		}
		if (state == State.BLOCKED) {
			// what spoke against it is gone (the downtime was taken back): the countdown starts anew, never at once
			sendAt = now + settings.delaySeconds() * MILLIS_PER_SECOND;
		} else if (state == State.SETTLING) {
			sendAt = endedAt + settings.delaySeconds() * MILLIS_PER_SECOND;
		}
		state = State.COUNTING;
		reason = Reason.NONE;
		if (now < sendAt) {
			return false;
		}
		sent = true;
		state = State.SENT;
		return true;
	}

	/**
	 * A party member asked the leader to requeue now ("!rq"): after the run's end, while the player leads and nothing was sent, stopped or
	 * cancelled, the one command of this run goes out without waiting for the countdown or a downtime.
	 *
	 * @return whether the command goes out now
	 */
	public boolean requestNow(Facts facts) {
		boolean runOver = state == State.SETTLING || state == State.COUNTING || state == State.BLOCKED;
		if (!runOver || sent || leftSelf || stopped || cancelled || !facts.selfLeads()) {
			return false;
		}
		sent = true;
		state = State.SENT;
		reason = Reason.NONE;
		return true;
	}

	/** @return what speaks against a requeue now, the weightiest first */
	private Reason against(Settings settings, Facts facts) {
		if (stopped) {
			return Reason.STOPPED;
		}
		if (cancelled) {
			return Reason.CANCELLED;
		}
		if (!facts.completed() && !settings.afterFailed()) {
			return Reason.FAILED_RUN;
		}
		if (!downtime.isEmpty()) {
			return Reason.DOWNTIME;
		}
		if (settings.stopOnLeave() && !leftParty.isEmpty()) {
			return Reason.SOMEONE_LEFT;
		}
		if (facts.partySize() <= 1) {
			return settings.solo() ? Reason.NONE : Reason.SOLO;
		}
		if (!facts.selfLeads() && !settings.notLeader()) {
			return Reason.NOT_LEADER;
		}
		return facts.partySize() < settings.minParty() ? Reason.PARTY_TOO_SMALL : Reason.NONE;
	}

	public State state() {
		return state;
	}

	/** @return why no requeue goes out, {@link Reason#NONE} while one may */
	public Reason reason() {
		return reason;
	}

	/** @return the milliseconds until the command goes out, while counting; 0 otherwise */
	public long millisLeft(long now) {
		return state == State.COUNTING ? Math.max(0, sendAt - now) : 0;
	}

	/** @return why no requeue goes out, in words: "Kiwi asked for downtime (food)", "Rae left the party", "you are not the party leader" */
	public String reasonText() {
		if (reason == Reason.DOWNTIME && !downtime.isEmpty()) {
			Map.Entry<String, String> first = downtime.entrySet().iterator().next();
			String more = downtime.size() > 1 ? " and " + (downtime.size() - 1) + " more" : "";
			return first.getKey() + more + " asked for downtime" + (first.getValue().isEmpty() ? "" : " (" + first.getValue() + ")");
		}
		if (reason == Reason.SOMEONE_LEFT && !leftParty.isEmpty()) {
			return leftParty.iterator().next() + " left the party";
		}
		return reason.text();
	}

	private static String key(String name) {
		return name == null ? "" : name.toLowerCase(Locale.ROOT);
	}
}
