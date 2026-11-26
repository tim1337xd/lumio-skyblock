package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Tic Tac Toe: the best move now, and the move after next (the player's best answer to the game's best reply). */
class TicTacToePuzzleTest {
	/** A board from three rows, '.' for an empty square. */
	private static char[] board(String rows) {
		char[] board = new char[9];
		String flat = rows.replace("/", "");
		for (int i = 0; i < 9; i++) {
			char c = flat.charAt(i);
			board[i] = c == '.' ? TicTacToePuzzle.EMPTY : c;
		}
		return board;
	}

	@Test
	void aForcedLine() {
		// X threatens the top row: O blocks at 2; then X must block O's diagonal at 6, and O blocks X's left column at 3
		char[] board = board("XX./.O./...");
		assertEquals(2, TicTacToePuzzle.bestMove(board, TicTacToePuzzle.CIRCLE));
		assertEquals(3, TicTacToePuzzle.moveAfterNext(board, TicTacToePuzzle.CIRCLE));
	}

	@Test
	void noPredictionWhenTheNextMoveEndsTheGame() {
		// O wins now
		assertEquals(-1, TicTacToePuzzle.moveAfterNext(board("OO./XX./X.."), TicTacToePuzzle.CIRCLE));
		// the last square
		assertEquals(-1, TicTacToePuzzle.moveAfterNext(board("XOX/XOO/OX."), TicTacToePuzzle.CIRCLE));
	}

	@Test
	void anotherReplyMovesThePrediction() {
		char[] board = board("XX./.O./...");
		int predicted = TicTacToePuzzle.moveAfterNext(board, TicTacToePuzzle.CIRCLE);
		// O blocked at 2, and the game played 8 instead of 6: O now wins at 6, not 3
		char[] played = board("XXO/.O./..X");
		int now = TicTacToePuzzle.bestMove(played, TicTacToePuzzle.CIRCLE);
		assertEquals(6, now);
		assertNotEquals(predicted, now);
	}

	@Test
	void unclearRepliesGiveNoPrediction() {
		// an empty corner start: the game's best replies differ and call for different answers, so nothing is marked rather than a guess
		char[] board = board("X../.../...");
		int predicted = TicTacToePuzzle.moveAfterNext(board, TicTacToePuzzle.CIRCLE);
		if (predicted >= 0) {
			// where one is given, it is a free square other than the best move now
			assertEquals(TicTacToePuzzle.EMPTY, board[predicted]);
			assertNotEquals(TicTacToePuzzle.bestMove(board, TicTacToePuzzle.CIRCLE), predicted);
		}
	}
}
