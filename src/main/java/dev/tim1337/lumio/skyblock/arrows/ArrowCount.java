package dev.tim1337.lumio.skyblock.arrows;

/**
 * How many arrows are in the quiver: the server's value (or the arrows just made), the most since the last refill, and when to warn. A warning comes once per drop below the level, and
 * one more at none; there is no other until the quiver is refilled above the level. Being low from the start, or after changing the arrow, is no drop. Plain numbers, so it is unit tested.
 */
public final class ArrowCount {
	/** What a change has to tell the player. */
	public enum Alarm {
		NONE, LOW, OUT
	}

	/** How far the server's value has to rise to be a refill: less is only a small correction. */
	static final int REFILL_RISE = 20;

	private String name = "";
	private boolean known;
	private int count;
	private int most;
	private boolean lowTold;
	private boolean outTold;
	private int toldAt;

	public boolean known() {
		return known;
	}

	public String name() {
		return name;
	}

	public int count() {
		return count;
	}

	public int most() {
		return Math.max(1, most);
	}

	/** Forgets everything, as when leaving the server. */
	public void clear() {
		known = false;
		name = "";
		count = 0;
		most = 0;
		lowTold = false;
		outTold = false;
	}

	/** The server's value: it replaces what was counted. */
	public Alarm real(String arrow, int value, int warnAt) {
		if (!known || !arrow.equals(name)) {
			start(arrow, value, warnAt);
			return Alarm.NONE;
		}
		if (value >= count + REFILL_RISE || value > most) {
			most = value;
		}
		return moved(value, warnAt);
	}

	/** Arrows just made and added to the quiver; of another arrow than the selected one they change nothing. */
	public Alarm forged(String arrow, int added, int warnAt) {
		if (!known || !arrow.equals(name)) {
			return Alarm.NONE;
		}
		long sum = (long) count + added;
		int value = (int) Math.min(Integer.MAX_VALUE, sum);
		most = Math.max(value, added >= REFILL_RISE ? value : most);
		return moved(value, warnAt);
	}

	private void start(String arrow, int value, int warnAt) {
		known = true;
		name = arrow;
		count = value;
		most = value;
		lowTold = value < warnAt;
		outTold = value == 0;
		toldAt = value;
	}

	private Alarm moved(int value, int warnAt) {
		int before = count;
		count = value;
		if (value >= warnAt && value >= toldAt + REFILL_RISE) {
			lowTold = false;
			outTold = false;
		}
		if (value == 0 && before > 0 && !outTold) {
			outTold = true;
			lowTold = true;
			toldAt = 0;
			return Alarm.OUT;
		}
		if (before >= warnAt && value < warnAt && !lowTold) {
			lowTold = true;
			toldAt = value;
			return Alarm.LOW;
		}
		return Alarm.NONE;
	}
}
