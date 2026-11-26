package dev.tim1337.lumio.skyblock.arrows;

/**
 * The count shown, from the one value the game shows (the quiver): the first value at once, a change only once it has held for a few ticks, so a
 * value the server corrects right after shows no jump.
 */
final class ArrowSettle {
	/** How long a new value must hold before it is shown, in client ticks. */
	static final int SETTLE_TICKS = 3;

	private boolean any;
	private int shown;
	private int pending;
	private long pendingSince;

	/** @return the count to show, given the game's value now at this tick */
	int update(int value, long tick) {
		if (!any) {
			any = true;
			shown = value;
			pending = value;
			return shown;
		}
		if (value != pending) {
			pending = value;
			pendingSince = tick;
		}
		if (pending != shown && tick - pendingSince >= SETTLE_TICKS) {
			shown = pending;
		}
		return shown;
	}

	void clear() {
		any = false;
	}
}
