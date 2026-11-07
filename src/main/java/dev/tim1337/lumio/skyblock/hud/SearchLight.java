package dev.tim1337.lumio.skyblock.hud;

/**
 * Whether the item search lights up the slots: typing only filters; double-clicking the search bar turns the slots' light on, and again off. It stays on from one container to the
 * next, and goes off with Esc or once no screen has been open for a moment (a server changing containers closes one a little before it opens the next). Plain numbers, so it is
 * unit tested.
 */
final class SearchLight {
	/** How close two clicks are to count as a double click. */
	static final long DOUBLE_CLICK_MILLIS = 400L;
	/** How long no screen may be open before it counts as all closed. */
	static final int CLOSED_TICKS = 10;

	private boolean on;
	private long lastClick = -DOUBLE_CLICK_MILLIS - 1;
	private int closedFor;

	/**
	 * The search bar was clicked.
	 *
	 * @return whether this was a double click, which has turned the light on or off
	 */
	boolean clicked(long millis) {
		if (millis - lastClick <= DOUBLE_CLICK_MILLIS) {
			on = !on;
			// a third click is not another double click
			lastClick = -DOUBLE_CLICK_MILLIS - 1;
			return true;
		}
		lastClick = millis;
		return false;
	}

	/** Esc: turns it off. @return whether it was on */
	boolean escape() {
		boolean was = on;
		on = false;
		return was;
	}

	/** Called every tick with whether a screen is open. */
	void tick(boolean screenOpen) {
		closedFor = screenOpen ? 0 : closedFor + 1;
		if (closedFor >= CLOSED_TICKS) {
			on = false;
		}
	}

	boolean on() {
		return on;
	}

	/** @return whether the slots light up for a search: an empty search never does */
	boolean lights(String search) {
		return on && !search.isBlank();
	}
}
