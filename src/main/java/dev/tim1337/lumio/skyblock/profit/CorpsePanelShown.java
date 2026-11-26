package dev.tim1337.lumio.skyblock.profit;

/**
 * When the corpse panel shows: only inside a Glacite Mineshaft, for a while after entering one and after each corpse opened there. Leaving the
 * mineshaft (a warp, a world change, back to the Glacite Tunnels or the Dwarven Mines) hides it at once. Only the showing: the panel's numbers are
 * never touched here, so the next mineshaft shows the session as it was.
 */
final class CorpsePanelShown {
	private boolean inside;
	private long until;

	/** Where the player is now: entering a mineshaft shows the panel for a while, leaving it hides the panel. */
	void at(boolean inMineshaft, long now, long showMillis) {
		if (inMineshaft && !inside) {
			until = now + showMillis;
		} else if (!inMineshaft) {
			until = 0;
		}
		inside = inMineshaft;
	}

	/** A corpse was opened: the panel shows for a while with the new numbers. */
	void corpse(long now, long showMillis) {
		until = now + showMillis;
	}

	/** @return whether the panel shows now */
	boolean shown(long now) {
		return inside && now < until;
	}
}
