package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The solvers' own logic, without the game: the best Tic Tac Toe move, the way out of Ice Path, the Teleport Maze's view, the lever order. */
class RoomPuzzlesTest {
	private static char[] board(String rows) {
		return rows.replace('.', TicTacToePuzzle.EMPTY).toCharArray();
	}

	@Test
	void ticTacToeWinsWhenItCan() {
		// X to move with two in the top row
		assertEquals(2, TicTacToePuzzle.bestMove(board("XX.OO...."), 'X'));
	}

	@Test
	void ticTacToeBlocksALine() {
		// O has two in the middle column; X must take the bottom of it
		assertEquals(7, TicTacToePuzzle.bestMove(board(".OX.O.X.."), 'X'));
	}

	@Test
	void ticTacToeKnowsTheWinner() {
		assertEquals('O', TicTacToePuzzle.winner(board("O..O..O..")));
		assertEquals(TicTacToePuzzle.EMPTY, TicTacToePuzzle.winner(board("XO.......")));
		assertEquals(-1, TicTacToePuzzle.bestMove(board("XOXXOOOXX"), 'X'));
	}

	@Test
	void icePathSlidesToTheWayOut() {
		boolean[][] blocked = new boolean[IcePathPuzzle.SIZE][IcePathPuzzle.SIZE];
		// a wall in the first row everywhere but the way out, and one block to stop at
		for (int column = 0; column < IcePathPuzzle.SIZE; column++) {
			blocked[0][column] = column < 7 || column > 9;
		}
		blocked[16][8] = true;
		// from the bottom left: right along the bottom, stopped by nothing until the edge, then up ... the search finds a way
		List<int[]> way = IcePathPuzzle.wayOut(blocked, 16, 0);
		assertFalse(way.isEmpty());
		assertArrayEquals(new int[] {16, 0}, way.getFirst());
		int[] last = way.getLast();
		assertEquals(0, last[0]);
		assertTrue(last[1] >= 7 && last[1] <= 9);
		// every step is a slide along one line
		for (int i = 0; i + 1 < way.size(); i++) {
			int[] from = way.get(i);
			int[] to = way.get(i + 1);
			assertTrue(from[0] == to[0] || from[1] == to[1]);
		}
	}

	@Test
	void icePathWithoutAWayOut() {
		boolean[][] blocked = new boolean[IcePathPuzzle.SIZE][IcePathPuzzle.SIZE];
		for (int column = 0; column < IcePathPuzzle.SIZE; column++) {
			blocked[0][column] = true;
		}
		assertTrue(IcePathPuzzle.wayOut(blocked, 8, 8).isEmpty());
	}

	@Test
	void teleportMazeSeesThePadItFaces() {
		// yaw 0 looks along +z
		assertTrue(TeleportMazePuzzle.inView(0.5, 10.5, 0.5, 0.5, 0f));
		assertFalse(TeleportMazePuzzle.inView(0.5, -10.5, 0.5, 0.5, 0f));
		assertFalse(TeleportMazePuzzle.inView(5.5, 10.5, 0.5, 0.5, 0f));
		// yaw 90 looks along -x
		assertTrue(TeleportMazePuzzle.inView(-8.5, 0.5, 0.5, 0.5, 90f));
		assertFalse(TeleportMazePuzzle.inView(0.5, 60.5, 0.5, 0.5, 0f));
	}

	@Test
	void waterBoardFlicksWhatIsDueFirst() {
		Map<WaterBoardPuzzle.Lever, List<Double>> left = new EnumMap<>(WaterBoardPuzzle.Lever.class);
		left.put(WaterBoardPuzzle.Lever.QUARTZ, new ArrayList<>(List.of(0.0, 10.0)));
		left.put(WaterBoardPuzzle.Lever.GOLD, new ArrayList<>(List.of(0.0)));
		left.put(WaterBoardPuzzle.Lever.EMERALD, new ArrayList<>(List.of(7.9)));
		left.put(WaterBoardPuzzle.Lever.WATER, new ArrayList<>(List.of(0.0)));
		assertEquals(WaterBoardPuzzle.Lever.GOLD, WaterBoardPuzzle.nextLever(left, -1));
		left.get(WaterBoardPuzzle.Lever.GOLD).clear();
		left.get(WaterBoardPuzzle.Lever.QUARTZ).removeFirst();
		left.get(WaterBoardPuzzle.Lever.WATER).clear();
		// after the water: emerald at 7.9 s comes before quartz at 10 s
		assertEquals(WaterBoardPuzzle.Lever.EMERALD, WaterBoardPuzzle.nextLever(left, 1.0));
	}

	@Test
	void theBundledSolutionsLoad() {
		Map<WaterBoardPuzzle.Lever, List<Double>> water = WaterBoardPuzzle.solution("0", "012");
		assertNotNull(water);
		assertEquals(List.of(7.9), water.get(WaterBoardPuzzle.Lever.EMERALD));
		assertFalse(BoulderPuzzle.solutions().isEmpty());
		assertFalse(CreeperBeamsPuzzle.pairs().isEmpty());
		assertEquals(7, RoomPuzzles.create(null).size());
	}
}
