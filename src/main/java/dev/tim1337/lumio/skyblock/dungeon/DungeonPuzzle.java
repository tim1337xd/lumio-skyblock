package dev.tim1337.lumio.skyblock.dungeon;

/** The puzzles of the Catacombs, by the names the player list gives them. */
public enum DungeonPuzzle {
	BLAZE("Higher Or Lower"), BEAMS("Creeper Beams"), WEIRDOS("Three Weirdos"), TIC_TAC_TOE("Tic Tac Toe"), WATER_BOARD("Water Board"),
	TELEPORT_MAZE("Teleport Maze"), BOULDER("Boulder"), ICE_FILL("Ice Fill"), ICE_PATH("Ice Path"), QUIZ("Quiz");

	/** How far a puzzle is, as the player list marks it. */
	public enum Status {
		OPEN, DONE, FAILED
	}

	private final String label;

	DungeonPuzzle(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** @return the puzzle of a name in the player list, or null (also for "???", one not found yet) */
	public static DungeonPuzzle of(String name) {
		for (DungeonPuzzle puzzle : values()) {
			if (puzzle.label.equalsIgnoreCase(name)) {
				return puzzle;
			}
		}
		return null;
	}
}
