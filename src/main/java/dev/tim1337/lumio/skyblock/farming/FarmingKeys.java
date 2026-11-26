package dev.tim1337.lumio.skyblock.farming;

/**
 * The rules of the Farming Keys: while they are active, a key of the player's own stands for one action of the game. Holding the key holds the
 * action, letting go lets it go: one key, one action, nothing pressed by itself, nothing repeated, no timing. An action without a key of its
 * own keeps the game's key. When the keys stop being active, every action they were holding is let go. Plain logic without the game, so it is
 * unit tested.
 */
final class FarmingKeys {
	/** The actions of the game a farming key can stand for. */
	enum Action {
		BREAK, USE, LEFT, RIGHT, FORWARD, BACK, JUMP, SNEAK
	}

	/** What happens to the game's key of an action in a tick: left as the game has it, held, or let go. */
	enum Hold {
		LEAVE, DOWN, UP
	}

	static final int ACTIONS = Action.values().length;

	/** The actions held by a farming key now: only these are ever let go by it. */
	private final boolean[] holding = new boolean[ACTIONS];

	/**
	 * @param farming the player wants to farm: the one automatic mode shared with the Mouse lock (see {@link FarmingCondition})
	 * @param focused the game's window has the keyboard
	 * @return whether the keys act now
	 */
	static boolean active(boolean farming, boolean focused) {
		return farming && focused;
	}

	/**
	 * One tick: what each action's key of the game is set to.
	 *
	 * @param active    whether the keys act now
	 * @param bound     for each action, whether it has a farming key
	 * @param down      for each action, whether its farming key is held
	 * @param normalOff "Turn off the normal key for remapped actions"
	 * @param out       for each action, what happens to the game's key
	 */
	void tick(boolean active, boolean[] bound, boolean[] down, boolean normalOff, Hold[] out) {
		for (int i = 0; i < ACTIONS; i++) {
			if (active && bound[i] && down[i]) {
				out[i] = Hold.DOWN;
				holding[i] = true;
				continue;
			}
			// let go what a farming key held; with the normal key off, the action of a key that is not held is up whatever the normal key does
			out[i] = holding[i] || normalKeyOff(active, bound[i], normalOff) ? Hold.UP : Hold.LEAVE;
			holding[i] = false;
		}
	}

	/** @return whether the game's own key of an action does nothing now: the action has a farming key and the normal key is turned off */
	static boolean normalKeyOff(boolean active, boolean bound, boolean normalOff) {
		return active && bound && normalOff;
	}

	/** @return whether a farming key holds this action now */
	boolean holding(Action action) {
		return holding[action.ordinal()];
	}
}
