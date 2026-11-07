package dev.tim1337.lumio.skyblock.dragons;

import java.util.Locale;

/**
 * Whether the player has anything to do with a dragon fight: they placed an eye for it, were in the Dragon's Nest during it, or its summary lists them. A dragon that spawns while the player is elsewhere is ignored completely. Plain state, so it is unit
 * tested.
 */
final class DragonFight {
	/** The area as the scoreboard shows it, in lower case. */
	private static final String NEST = "dragon's nest";

	private int eyes;
	private boolean fighting;
	private boolean inNest;
	private boolean listed;

	/** @return whether the scoreboard's area is the Dragon's Nest */
	static boolean isNest(String zone) {
		return zone != null && zone.strip().toLowerCase(Locale.ROOT).equals(NEST);
	}

	void eyePlaced() {
		eyes++;
	}

	void eyeRecovered() {
		eyes = Math.max(0, eyes - 1);
	}

	void spawned() {
		fighting = true;
		inNest = false;
		listed = false;
	}

	/** Looked at while the fight goes on: being in the Nest at any moment of it is enough. */
	void seeArea(String zone) {
		if (fighting && isNest(zone)) {
			inNest = true;
		}
	}

	void down() {
		fighting = false;
	}

	/** The summary names the player's place or damage. */
	void listed() {
		listed = true;
	}

	/** The fight's record is done. */
	void finished() {
		eyes = 0;
		inNest = false;
		listed = false;
		fighting = false;
	}

	int eyes() {
		return eyes;
	}

	boolean fighting() {
		return fighting;
	}

	/** @return whether the player has anything to do with this fight */
	boolean involved() {
		return eyes > 0 || inNest || listed;
	}

	/**
	 * @param countOthers the setting to count dragons the player placed no eyes for
	 * @return whether the fight counts: with an eye of the player's, or by that setting when the player was in the Nest and its summary lists them
	 */
	boolean counts(boolean countOthers) {
		return eyes > 0 || countOthers && inNest && listed;
	}
}
