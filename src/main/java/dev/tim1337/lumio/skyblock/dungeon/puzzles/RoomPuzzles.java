package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import java.util.List;

/** The puzzle solvers that work from their room alone, each with the key and the name of its switch. */
public final class RoomPuzzles {
	/** A solver and its switch. */
	public record Entry(String key, String label, RoomPuzzle solver) {
	}

	private RoomPuzzles() {
	}

	/** @return new solvers, one of each, in the order of their switches */
	public static List<Entry> create(TicTacToePuzzle.Look ticTacToe) {
		return List.of(new Entry("water_board", "Water Board", new WaterBoardPuzzle()), new Entry("teleport_maze", "Teleport Maze", new TeleportMazePuzzle()),
			new Entry("tic_tac_toe", "Tic Tac Toe", new TicTacToePuzzle(ticTacToe)), new Entry("creeper_beams", "Creeper Beams", new CreeperBeamsPuzzle()),
			new Entry("boulder", "Boulder", new BoulderPuzzle()), new Entry("ice_fill", "Ice Fill", new IceFillPuzzle()),
			new Entry("ice_path", "Ice Path", new IcePathPuzzle()));
	}
}
