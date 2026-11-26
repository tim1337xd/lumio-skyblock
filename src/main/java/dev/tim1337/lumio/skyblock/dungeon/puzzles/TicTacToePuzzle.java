package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tic Tac Toe: the best move for the player, worked out by looking ahead to the end of the game, marked on the button to press (only the button), and
 * weaker in its own color the move after next. The board is read
 * from the maps in the item frames on the wall (a cross or a circle in the middle of each map); it is solved only when it is the player's turn.
 */
public final class TicTacToePuzzle implements RoomPuzzle {
	/** The colors and switch from the Puzzles sheet. */
	public interface Look {
		/** @return the color of the move to click now, as 0xRRGGBB */
		int nowRgb();

		/** @return whether the move after next is marked too */
		boolean showNext();

		/** @return the color of the move after next, as 0xRRGGBB */
		int nextRgb();
	}

	/** How strongly the move after next is marked, against the move now. */
	private static final float NEXT_WEIGHT = 0.45f;
	/** The room kept around a button's own shape, in blocks. */
	private static final double BUTTON_PAD = 1.0 / 32.0;

	private final Look look;

	TicTacToePuzzle(Look look) {
		this.look = look;
	}
	static final char EMPTY = ' ';
	static final char CROSS = 'X';
	static final char CIRCLE = 'O';
	/** The score of a square that is taken. */
	private static final int NO_MOVE = Integer.MIN_VALUE;
	/** The middle pixel of a framed map, and the map colors of a cross and a circle. */
	private static final int MIDDLE_PIXEL = 64 * 128 + 64;
	private static final int CROSS_COLOR = 114;
	private static final int CIRCLE_COLOR = 33;
	/** The board on the wall, in the room: the frames' rows by height, their columns by place along the wall, and the buttons before them. */
	private static final int TOP_ROW_Y = 72;
	private static final int FIRST_COLUMN_Z = 17;
	private static final int BUTTON_X = 8;
	private static final double SEARCH_BLOCKS = 24;

	@Override
	public String room() {
		return "Tic Tac Toe";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		char[] board = new char[9];
		java.util.Arrays.fill(board, EMPTY);
		int marked = 0;
		AABB around = client.player.getBoundingBox().inflate(SEARCH_BLOCKS);
		for (ItemFrame frame : client.level.getEntitiesOfClass(ItemFrame.class, around, ItemFrame::hasFramedMap)) {
			MapItemSavedData map = client.level.getMapData(frame.getFramedMapId(frame.getItem()));
			if (map == null) {
				continue;
			}
			int[] inRoom = CurrentRoom.toRoom(frame.blockPosition().getX(), frame.blockPosition().getY(), frame.blockPosition().getZ());
			int row = TOP_ROW_Y - inRoom[1];
			int column = FIRST_COLUMN_Z - inRoom[2];
			if (row < 0 || row > 2 || column < 0 || column > 2) {
				continue;
			}
			int color = map.colors[MIDDLE_PIXEL] & 0xFF;
			char mark = color == CROSS_COLOR ? CROSS : color == CIRCLE_COLOR ? CIRCLE : EMPTY;
			if (mark != EMPTY) {
				board[row * 3 + column] = mark;
				marked++;
			}
		}
		// the game moves first: the player's turn is when an odd number of marks is on the board, and not after the end
		if (marked % 2 == 0 || winner(board) != EMPTY) {
			return;
		}
		char player = count(board, CROSS) < count(board, CIRCLE) ? CROSS : CIRCLE;
		int move = bestMove(board, player);
		if (move >= 0) {
			markButton(client, marks, move, look.nowRgb(), 1f);
		}
		int next = look.showNext() ? moveAfterNext(board, player) : -1;
		if (next >= 0) {
			markButton(client, marks, next, look.nextRgb(), NEXT_WEIGHT);
		}
	}

	/** Marks the button of a square: its own small shape with a little room around it, nothing else of the block. */
	private static void markButton(Minecraft client, PuzzleMarks marks, int square, int rgb, float weight) {
		int[] at = CurrentRoom.toWorld(BUTTON_X, TOP_ROW_Y - square / 3, FIRST_COLUMN_Z - square % 3);
		BlockPos pos = new BlockPos(at[0], at[1], at[2]);
		VoxelShape shape = client.level.getBlockState(pos).getShape(client.level, pos);
		if (shape.isEmpty()) {
			// no button there (yet): the block as before
			marks.box(new PuzzleMarks.Box(at[0], at[1], at[2], at[0] + 1, at[1] + 1, at[2] + 1, rgb, true, weight));
			return;
		}
		AABB button = shape.bounds().move(pos).inflate(BUTTON_PAD);
		marks.box(new PuzzleMarks.Box(button.minX, button.minY, button.minZ, button.maxX, button.maxY, button.maxZ, rgb, true, weight));
	}

	/** @return the best square for the player to take (0 to 8, row by row), or -1 when the board is full */
	static int bestMove(char[] board, char player) {
		int[] scores = moveScores(board, player);
		int best = -1;
		for (int square = 0; square < 9; square++) {
			if (scores[square] != NO_MOVE && (best < 0 || scores[square] > scores[best])) {
				best = square;
			}
		}
		return best;
	}

	/** @return how good each square is for the one to move (as {@link #bestMove} weighs it), NO_MOVE for a taken square */
	static int[] moveScores(char[] board, char toMove) {
		int[] scores = new int[9];
		for (int square = 0; square < 9; square++) {
			if (board[square] != EMPTY) {
				scores[square] = NO_MOVE;
				continue;
			}
			board[square] = toMove;
			scores[square] = -lookAhead(board, other(toMove), 1);
			board[square] = EMPTY;
		}
		return scores;
	}

	/**
	 * The move after next: the player takes the best move, the game answers with its best reply, and this is the player's best answer to that. When
	 * the game has several equally good replies that call for different answers, it is not clear which comes, and there is none.
	 *
	 * @return the square (0 to 8), or -1: none clear, or the game ends with the player's next move or the game's reply
	 */
	static int moveAfterNext(char[] board, char player) {
		int move = bestMove(board, player);
		if (move < 0) {
			return -1;
		}
		char[] after = board.clone();
		after[move] = player;
		if (over(after)) {
			return -1;
		}
		char game = other(player);
		int[] replies = moveScores(after, game);
		int bestReply = Integer.MIN_VALUE;
		for (int score : replies) {
			if (score != NO_MOVE) {
				bestReply = Math.max(bestReply, score);
			}
		}
		int answer = -1;
		for (int reply = 0; reply < 9; reply++) {
			if (replies[reply] == NO_MOVE || replies[reply] != bestReply) {
				continue;
			}
			after[reply] = game;
			int mine = over(after) ? -1 : bestMove(after, player);
			after[reply] = EMPTY;
			if (mine < 0 || answer >= 0 && mine != answer) {
				return -1;
			}
			answer = mine;
		}
		return answer;
	}

	/** @return whether the game is over: three in a line, or no square left */
	static boolean over(char[] board) {
		return winner(board) != EMPTY || count(board, EMPTY) == 0;
	}

	/** @return how good the board is for the one to move: a win sooner is better, a loss later is less bad */
	private static int lookAhead(char[] board, char toMove, int depth) {
		char won = winner(board);
		if (won != EMPTY) {
			return won == toMove ? 10 - depth : depth - 10;
		}
		int best = Integer.MIN_VALUE;
		for (int square = 0; square < 9; square++) {
			if (board[square] == EMPTY) {
				board[square] = toMove;
				best = Math.max(best, -lookAhead(board, other(toMove), depth + 1));
				board[square] = EMPTY;
			}
		}
		return best == Integer.MIN_VALUE ? 0 : best;
	}

	private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

	/** @return the mark that has three in a line, or EMPTY */
	static char winner(char[] board) {
		for (int[] line : LINES) {
			char first = board[line[0]];
			if (first != EMPTY && first == board[line[1]] && first == board[line[2]]) {
				return first;
			}
		}
		return EMPTY;
	}

	private static char other(char mark) {
		return mark == CROSS ? CIRCLE : CROSS;
	}

	private static int count(char[] board, char mark) {
		int count = 0;
		for (char square : board) {
			if (square == mark) {
				count++;
			}
		}
		return count;
	}

	@Override
	public void reset() {
	}
}
