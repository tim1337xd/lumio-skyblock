package dev.tim1337.lumio.skyblock.dungeon.f7;

/**
 * "Block wrong clicks" of the Floor 7 terminal and device solvers, without the game: whether a click of the player's is one the solver knows
 * to be wrong, so it is simply not sent. Only ever a "no" to the player's own click: nothing is clicked for the player and no click is turned
 * into another one. Where the solver is not sure (a menu not loaded, no solution, an unknown layout), nothing is blocked, and while the
 * player holds the override key every click goes through.
 */
public final class WrongClicks {
	/** How long a click that went through counts as on its way while the game has not shown it yet, in client ticks. */
	public static final int AHEAD_TICKS = 20;

	/** How many clicks were blocked since the game started, for the debug log. */
	private static int blocked;

	private WrongClicks() {
	}

	/** Counts a blocked click. @return the line for the debug log */
	public static String note(String where, String reason) {
		blocked++;
		return "blocked click #" + blocked + " (" + where + "): " + reason;
	}

	public static int blocked() {
		return blocked;
	}

	/**
	 * A click on a slot of a terminal's menu.
	 *
	 * @param loaded   whether the menu's contents were read and are a terminal's (for the melody: its marked column is known)
	 * @param slot     the slot clicked
	 * @param chest    the slots of the terminal itself; a click outside of them (the player's inventory) is never blocked
	 * @param ahead    the right clicks sent that the menu has not shown yet (the order terminal lets the number after those through)
	 * @return whether the click is known to be wrong
	 */
	public static boolean terminal(boolean on, boolean holding, Terminals.Kind kind, Terminals.Solution solution, boolean loaded, int slot, int chest,
		int ahead) {
		if (!on || holding || kind == null || solution == null || !loaded || slot < 0 || slot >= chest) {
			return false;
		}
		int place = solution.place(slot);
		if (kind == Terminals.Kind.MELODY) {
			// the one button of the row whose pane stands in the marked column, and only while it stands there
			return place < 0;
		}
		if (solution.isEmpty()) {
			// nothing left to click, or nothing known: not ours to judge
			return false;
		}
		if (kind == Terminals.Kind.NUMBERS) {
			return place != ahead;
		}
		return place < 0;
	}

	/**
	 * A click on a button of the Simon Says grid.
	 *
	 * @param known how many buttons the order has (0: nothing shown yet, nothing is blocked)
	 * @param place where the button stands from the next press (0 is next, -1 not to be pressed), in the corrected order
	 * @param ahead the right presses sent that the world has not shown yet
	 */
	public static boolean simonButton(boolean on, boolean holding, int known, int place, int ahead) {
		return on && !holding && known > 0 && place != ahead;
	}

	/**
	 * A click on the Simon Says start button: limited only in the first phase (after its first press), where more presses skip more rounds.
	 *
	 * @param presses how often it was pressed in this first phase, the player's own clicks on their way included
	 */
	public static boolean simonStart(boolean on, boolean holding, boolean firstPhase, int presses, int most) {
		return on && !holding && firstPhase && presses >= most;
	}

	/**
	 * A click on a frame of Arrow Align: wrong when the arrow already points the right way, or would with the clicks on their way.
	 *
	 * @param clicks  the clicks each frame still needs, or null when no layout is known (nothing is blocked)
	 * @param pending the player's clicks on that frame the world has not shown yet
	 */
	public static boolean arrow(boolean on, boolean holding, int[] clicks, int frame, int pending) {
		return on && !holding && clicks != null && frame >= 0 && frame < clicks.length && clicks[frame] - pending <= 0;
	}

	/** A click on a lever of the Lights device: wrong when the lever is already on. */
	public static boolean lever(boolean on, boolean holding, boolean powered) {
		return on && !holding && powered;
	}

	/** @return the clicks still on their way after the world showed {@code shown} of them */
	public static int settle(int ahead, int shown) {
		return Math.max(0, ahead - Math.max(0, shown));
	}
}
