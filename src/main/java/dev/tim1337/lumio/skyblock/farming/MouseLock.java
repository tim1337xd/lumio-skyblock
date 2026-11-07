package dev.tim1337.lumio.skyblock.farming;

import java.util.regex.Pattern;

/**
 * The rules of the mouse lock: when it locks and unlocks, and how much of the mouse's movement still turns the camera. It always unlocks when anything is off, so the
 * player can never get stuck. Plain logic without the game, so it is unit tested.
 */
final class MouseLock {
	/** What the lock does. */
	enum Mode {
		LOCK, SLOW
	}

	/** What a press of the key or the command did. */
	enum Toggled {
		LOCKED, UNLOCKED, NOT_HERE
	}

	/** What the game says when it takes the player to another plot or the barn. "Warping..." is left out: it is said on the way to anywhere. */
	private static final Pattern TELEPORT = Pattern.compile("^Teleported you to (Plot - .+|The Barn)!$");
	private static final float PERCENT = 100f;

	private boolean on;

	boolean on() {
		return on;
	}

	/** @return what a press of the key or the command does: it works in the Garden only */
	Toggled toggle(boolean inGarden) {
		if (on) {
			on = false;
			return Toggled.UNLOCKED;
		}
		if (!inGarden) {
			return Toggled.NOT_HERE;
		}
		on = true;
		return Toggled.LOCKED;
	}

	/** @return whether a chat line unlocked it: the game's message about a teleport */
	boolean onChat(String line, boolean unlockOnTeleport) {
		if (on && unlockOnTeleport && isTeleport(line)) {
			on = false;
			return true;
		}
		return false;
	}

	static boolean isTeleport(String line) {
		return TELEPORT.matcher(line).matches();
	}

	/**
	 * Checks what must always unlock it.
	 *
	 * @return whether it unlocked now
	 */
	boolean check(boolean enabled, boolean connected, boolean inGarden, boolean focused, boolean alive) {
		if (on && !(enabled && connected && inGarden && focused && alive)) {
			on = false;
			return true;
		}
		return false;
	}

	/** @return how much of the mouse's movement turns the camera: all of it unless it is on, then none, or the set percent */
	float scale(Mode mode, float percent) {
		if (!on) {
			return 1f;
		}
		return mode == Mode.LOCK ? 0f : Math.max(0f, Math.min(PERCENT, percent)) / PERCENT;
	}
}
