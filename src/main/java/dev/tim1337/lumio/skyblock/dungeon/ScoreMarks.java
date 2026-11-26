package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The bonus marks of the score panels: M for the Mimic, P for the Prince, B for the bat that gives bonus score, each got (✓), not yet (–) or no
 * longer possible (✗, once the clearing is over). M only on the floors that have a Mimic; B not in the Entrance.
 */
final class ScoreMarks {
	/** What a mark says. */
	enum State {
		GOT("✓"), NOT_YET("–"), LOST("✗");

		final String sign;

		State(String sign) {
			this.sign = sign;
		}
	}

	private ScoreMarks() {
	}

	static boolean showsMimic(DungeonFloor floor) {
		return floor != null && floor.hasMimic();
	}

	static boolean showsPrince(DungeonFloor floor) {
		return floor != null;
	}

	static boolean showsBat(DungeonFloor floor) {
		return floor != null && floor != DungeonFloor.E;
	}

	/** @return a mark's state: got, else lost once the clearing is over (the boss is on or the run ended), else not yet */
	static State state(boolean got, boolean clearingOver) {
		return got ? State.GOT : clearingOver ? State.LOST : State.NOT_YET;
	}
}
