package dev.tim1337.lumio.skyblock.farming;

import java.util.regex.Pattern;

/**
 * The rules of the mouse lock: when it locks and unlocks, and how much of the mouse's movement still turns the camera. It always unlocks when anything is off, so the
 * player can never get stuck. Besides the key, it can lock by itself while the player farms (see {@link FarmingCondition}: in the Garden, a farming tool held, on the
 * ground, not on the Barn plot, no screen open) and lets go at once when that ends: flying or in the air, the Barn, another item, a screen, leaving the Garden, a
 * teleport. A press of the key always wins: a manual lock stays until the next press, and a manual unlock while farming keeps it unlocked until the item changes or the
 * farming ends. Plain logic without the game, so it is unit tested.
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

	/** What the automatic lock did in a tick. */
	enum Auto {
		LOCKED, UNLOCKED, NONE
	}

	/**
	 * What the automatic lock looks at in a tick.
	 *
	 * @param allowed  "Lock by itself while farming" is on and the feature works
	 * @param farming  the player is farming now
	 * @param heldItem which item is held, to see a switch (its hotbar slot and id)
	 */
	record Farming(boolean allowed, boolean farming, String heldItem) {
	}

	/** What the game says when it takes the player to another plot or the barn. "Warping..." is left out: it is said on the way to anywhere. */
	private static final Pattern TELEPORT = Pattern.compile("^Teleported you to (Plot - .+|The Barn)!$");
	private static final float PERCENT = 100f;

	private boolean on;
	/** Whether the lock now is the automatic one (it lets go by itself), not one of the key. */
	private boolean automatic;
	/** The item held when the automatic lock took hold: another one lets it go. */
	private String lockedItem = "";
	/** After a manual unlock while farming: no automatic lock until the item changes or the farming ends. */
	private boolean keptOff;
	private String keptOffItem = "";

	boolean on() {
		return on;
	}

	/** @return whether the lock now is the automatic one */
	boolean automatic() {
		return on && automatic;
	}

	/** @return what a press of the key or the command does: it works in the Garden only */
	Toggled toggle(boolean inGarden) {
		return toggle(inGarden, "", false);
	}

	/**
	 * A press of the key or the command: it unlocks what is locked (automatic or not), else locks until the next press.
	 *
	 * @param heldItem the item held now
	 * @param farming  whether the player is farming now: an unlock then stays until the item changes or the farming ends
	 */
	Toggled toggle(boolean inGarden, String heldItem, boolean farming) {
		if (on) {
			unlock();
			keptOff = farming;
			keptOffItem = heldItem;
			return Toggled.UNLOCKED;
		}
		if (!inGarden) {
			return Toggled.NOT_HERE;
		}
		on = true;
		automatic = false;
		keptOff = false;
		return Toggled.LOCKED;
	}

	/** One tick of the automatic lock. @return whether it locked or let go now */
	Auto tick(Farming farming) {
		if (keptOff && (!farming.farming() || !farming.heldItem().equals(keptOffItem))) {
			keptOff = false;
		}
		if (on) {
			if (automatic && (!farming.allowed() || !farming.farming() || !farming.heldItem().equals(lockedItem))) {
				unlock();
				return Auto.UNLOCKED;
			}
			return Auto.NONE;
		}
		if (farming.allowed() && farming.farming() && !keptOff) {
			on = true;
			automatic = true;
			lockedItem = farming.heldItem();
			return Auto.LOCKED;
		}
		return Auto.NONE;
	}

	private void unlock() {
		on = false;
		automatic = false;
	}

	/** @return whether a chat line unlocked it: the game's message about a teleport, which always ends an automatic lock */
	boolean onChat(String line, boolean unlockOnTeleport) {
		if (on && (unlockOnTeleport || automatic) && isTeleport(line)) {
			unlock();
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
			unlock();
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
