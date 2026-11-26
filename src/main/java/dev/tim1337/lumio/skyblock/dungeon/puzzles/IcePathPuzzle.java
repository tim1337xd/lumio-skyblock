package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ice Path: the shortest way to slide the silverfish out, drawn as a line over the ice. The silverfish slides until a block stops it, so the way
 * is found by trying each of the four pushes from every place it can stop at, nearest first. The board and the silverfish are read again as they
 * change; nothing is pushed for the player.
 */
final class IcePathPuzzle implements RoomPuzzle {
	static final int SIZE = 17;
	/** The board in the room: its first square across and down, and its height; squares count back from there. */
	private static final int BOARD_X = 23;
	private static final int BOARD_Z = 24;
	private static final int BOARD_Y = 67;
	/** The way out: the squares of the first row the silverfish must reach. */
	private static final int EXIT_FROM = 7;
	private static final int EXIT_TO = 9;
	private static final int SEARCH_BLOCKS = 16;
	private static final int LINE_RGB = 0xFF453A;
	private static final int[][] PUSHES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final boolean[][] blocked = new boolean[SIZE][SIZE];
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	@Override
	public String room() {
		return "Ice Path";
	}

	@Override
	public void tick(Minecraft client, PuzzleMarks marks) {
		for (int row = 0; row < SIZE; row++) {
			for (int column = 0; column < SIZE; column++) {
				int[] world = CurrentRoom.toWorld(BOARD_X - column, BOARD_Y, BOARD_Z - row);
				at.set(world[0], world[1], world[2]);
				blocked[row][column] = !client.level.getBlockState(at).isAir();
			}
		}
		int[] middle = CurrentRoom.toWorld(BOARD_X - SIZE / 2, BOARD_Y, BOARD_Z - SIZE / 2);
		AABB board = new AABB(middle[0] - SEARCH_BLOCKS, middle[1] - SEARCH_BLOCKS, middle[2] - SEARCH_BLOCKS, middle[0] + SEARCH_BLOCKS,
			middle[1] + SEARCH_BLOCKS, middle[2] + SEARCH_BLOCKS);
		List<Silverfish> found = client.level.getEntitiesOfClass(Silverfish.class, board, fish -> true);
		if (found.isEmpty()) {
			return;
		}
		BlockPos fish = found.getFirst().blockPosition();
		int[] inRoom = CurrentRoom.toRoom(fish.getX(), fish.getY(), fish.getZ());
		int row = BOARD_Z - inRoom[2];
		int column = BOARD_X - inRoom[0];
		if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) {
			return;
		}
		List<int[]> way = wayOut(blocked, row, column);
		for (int i = 0; i + 1 < way.size(); i++) {
			marks.line(squareInWorld(way.get(i)), squareInWorld(way.get(i + 1)), 0.5, LINE_RGB);
		}
	}

	private static int[] squareInWorld(int[] square) {
		return CurrentRoom.toWorld(BOARD_X - square[1], BOARD_Y, BOARD_Z - square[0]);
	}

	/**
	 * @return the squares the silverfish stops at on the shortest way out, from where it is (row, column) to the exit; empty where there is none
	 */
	static List<int[]> wayOut(boolean[][] blocked, int row, int column) {
		int[][] cameFrom = new int[SIZE * SIZE][];
		boolean[] seen = new boolean[SIZE * SIZE];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {row, column});
		seen[row * SIZE + column] = true;
		while (!queue.isEmpty()) {
			int[] square = queue.poll();
			if (square[0] == 0 && square[1] >= EXIT_FROM && square[1] <= EXIT_TO) {
				List<int[]> way = new ArrayList<>();
				for (int[] step = square; step != null; step = cameFrom[step[0] * SIZE + step[1]]) {
					way.add(step);
				}
				Collections.reverse(way);
				return way;
			}
			for (int[] push : PUSHES) {
				int[] stop = slide(blocked, square, push);
				int index = stop[0] * SIZE + stop[1];
				if (!seen[index]) {
					seen[index] = true;
					cameFrom[index] = square;
					queue.add(stop);
				}
			}
		}
		return List.of();
	}

	/** @return where the silverfish stops when pushed one way: the last free square before a block or the edge */
	private static int[] slide(boolean[][] blocked, int[] from, int[] push) {
		int row = from[0];
		int column = from[1];
		while (true) {
			int nextRow = row + push[0];
			int nextColumn = column + push[1];
			if (nextRow < 0 || nextRow >= SIZE || nextColumn < 0 || nextColumn >= SIZE || blocked[nextRow][nextColumn]) {
				return new int[] {row, column};
			}
			row = nextRow;
			column = nextColumn;
		}
	}

	@Override
	public void reset() {
	}
}
