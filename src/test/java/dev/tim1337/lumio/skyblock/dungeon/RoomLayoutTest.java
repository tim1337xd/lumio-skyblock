package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lumio's room model: a point kept in a room lands on the same spot of that room however the room lies on the grid, in every shape and turn, and
 * whatever order the room's tiles were found in.
 */
class RoomLayoutTest {
	/** The shapes as tile offsets, as each lies unturned. The L misses the tile right of its top left. */
	private static final Map<String, int[][]> SHAPES = Map.of(
		"1x2", new int[][] {{0, 0}, {1, 0}},
		"1x3", new int[][] {{0, 0}, {1, 0}, {2, 0}},
		"1x4", new int[][] {{0, 0}, {1, 0}, {2, 0}, {3, 0}},
		"2x2", new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}},
		"L", new int[][] {{0, 0}, {0, 1}, {1, 1}});
	/** Points of the model, from the room's middle, inside every shape. */
	private static final int[][] POINTS = {{0, 70, 0}, {-12, 64, 9}, {14, 80, -13}, {-31, 61, 15}};

	/**
	 * Turned by a quarter turn, a room's tiles and its content turn together around the room's middle: the point of the model must be where the
	 * unturned room had it, turned the same way. A row (and a square) turned half around has the same tiles as unturned, so the grid cannot tell
	 * them apart; those turns are where the room's tiles first differ.
	 */
	@Test
	void aPointStaysOnItsSpotInEveryTurn() {
		for (Map.Entry<String, int[][]> shape : SHAPES.entrySet()) {
			int turns = switch (shape.getKey()) {
				case "L" -> 4;
				case "2x2" -> 1;
				default -> 2;
			};
			for (int[] point : POINTS) {
				int[] unturned = worldOf(shape.getKey(), turned(shape.getValue(), 0), point);
				int[] middle = middleOf(turned(shape.getValue(), 0));
				for (int turn = 1; turn < turns; turn++) {
					int[] turnedMiddle = middleOf(turned(shape.getValue(), turn));
					int[] around = turnAround(unturned, middle, turn);
					int[] expected = {around[0] - middle[0] + turnedMiddle[0], around[1], around[2] - middle[1] + turnedMiddle[1]};
					assertArrayEquals(expected, worldOf(shape.getKey(), turned(shape.getValue(), turn), point), shape.getKey() + " turn " + turn);
				}
			}
		}
	}

	@Test
	void oneTileRoomsTurnByTheirClayInAllFourTurns() {
		for (int[] point : POINTS) {
			RoomLayout.Placement unturned = RoomLayout.Placement.onTile(CurrentRoom.Rotation.SOUTH, 2, 3);
			int[] start = CurrentRoom.modelToWorld(point[0], point[1], point[2], "1x1", unturned.clayX(), unturned.clayZ(), unturned.rotation());
			int[] middle = {CurrentRoom.middleOf(2), CurrentRoom.middleOf(3)};
			CurrentRoom.Rotation[] byTurn = {CurrentRoom.Rotation.SOUTH, CurrentRoom.Rotation.WEST, CurrentRoom.Rotation.NORTH, CurrentRoom.Rotation.EAST};
			for (int turn = 1; turn < 4; turn++) {
				RoomLayout.Placement placed = RoomLayout.Placement.onTile(byTurn[turn], 2, 3);
				assertArrayEquals(turnAround(start, middle, turn),
					CurrentRoom.modelToWorld(point[0], point[1], point[2], "1x1", placed.clayX(), placed.clayZ(), placed.rotation()), "turn " + turn);
			}
		}
	}

	@Test
	void theOrderOfTheTilesNeverMatters() {
		for (Map.Entry<String, int[][]> shape : SHAPES.entrySet()) {
			for (int turn = 0; turn < 4; turn++) {
				for (int column = 0; column < 2; column++) {
					List<int[]> tiles = moved(turned(shape.getValue(), turn), column, 1);
					RoomLayout.Placement first = RoomLayout.of(shape.getKey(), tiles);
					assertNotNull(first, shape.getKey());
					for (List<int[]> order : orders(tiles)) {
						assertEquals(first, RoomLayout.of(shape.getKey(), order), shape.getKey() + " turn " + turn);
					}
				}
			}
		}
	}

	@Test
	void anLIsTurnedByItsMissingTile() {
		assertEquals(CurrentRoom.Rotation.SOUTH, RoomLayout.of("L", tiles(0, 0, 0, 1, 1, 1)).rotation());
		assertEquals(CurrentRoom.Rotation.WEST, RoomLayout.of("L", tiles(0, 0, 1, 0, 0, 1)).rotation());
		assertEquals(CurrentRoom.Rotation.NORTH, RoomLayout.of("L", tiles(0, 0, 1, 0, 1, 1)).rotation());
		assertEquals(CurrentRoom.Rotation.EAST, RoomLayout.of("L", tiles(1, 0, 0, 1, 1, 1)).rotation());
		assertNull(RoomLayout.of("L", tiles(0, 0, 1, 0, 2, 0)));
	}

	@Test
	void theModelCountsFromTheRoomsMiddle() {
		assertArrayEquals(new int[] {15, 15}, RoomModel.middle("1x1"));
		assertArrayEquals(new int[] {31, 15}, RoomModel.middle("1x2"));
		assertArrayEquals(new int[] {47, 15}, RoomModel.middle("1x3"));
		assertArrayEquals(new int[] {63, 15}, RoomModel.middle("1x4"));
		assertArrayEquals(new int[] {31, 31}, RoomModel.middle("2x2"));
		assertArrayEquals(new int[] {31, 31}, RoomModel.middle("L"));
		for (Map.Entry<String, int[][]> shape : SHAPES.entrySet()) {
			for (int turn = 0; turn < 4; turn++) {
				int[][] tiles = turned(shape.getValue(), turn);
				int[] middle = middleOf(tiles);
				assertArrayEquals(new int[] {middle[0], 70, middle[1]}, worldOf(shape.getKey(), tiles, new int[] {0, 70, 0}), shape.getKey());
			}
		}
	}

	/** A few places worked out by hand: from the room's corner, turned with the room. */
	@Test
	void handWorkedPlaces() {
		// a row of two at tiles (1,2) and (2,2): unturned, its corner at the first tile's north-west corner (-168, -136)
		RoomLayout.Placement row = RoomLayout.of("1x2", List.of(new int[] {1, 2}, new int[] {2, 2}));
		assertEquals(CurrentRoom.Rotation.SOUTH, row.rotation());
		assertArrayEquals(new int[] {-163, 63, -113}, CurrentRoom.toWorld(5, 63, 23, row.clayX(), row.clayZ(), row.rotation()));
		// a column of two at (1,1) and (1,2): a quarter turn, its corner at the first tile's north-east corner (-138, -168)
		RoomLayout.Placement column = RoomLayout.of("1x2", List.of(new int[] {1, 1}, new int[] {1, 2}));
		assertEquals(CurrentRoom.Rotation.WEST, column.rotation());
		assertArrayEquals(new int[] {-161, 63, -163}, CurrentRoom.toWorld(5, 63, 23, column.clayX(), column.clayZ(), column.rotation()));
		// an L of (0,0), (1,0), (0,1), missing its south-east tile: its corner at (1,0)'s north-east corner (-138, -200)
		RoomLayout.Placement l = RoomLayout.of("L", tiles(0, 0, 1, 0, 0, 1));
		assertEquals(CurrentRoom.Rotation.WEST, l.rotation());
		assertArrayEquals(new int[] {-161, 63, -195}, CurrentRoom.toWorld(5, 63, 23, l.clayX(), l.clayZ(), l.rotation()));
	}

	/** @return where a point of the model lands for a room on these tiles, moved into the grid */
	private static int[] worldOf(String shape, int[][] tiles, int[] point) {
		RoomLayout.Placement placed = RoomLayout.of(shape, moved(tiles, 1, 1));
		return CurrentRoom.modelToWorld(point[0], point[1], point[2], shape, placed.clayX(), placed.clayZ(), placed.rotation());
	}

	/** @return the middle block of a room on these tiles, moved into the grid as {@link #worldOf} moves them */
	private static int[] middleOf(int[][] tiles) {
		List<int[]> placed = moved(tiles, 1, 1);
		int left = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int top = Integer.MAX_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (int[] tile : placed) {
			left = Math.min(left, tile[0]);
			right = Math.max(right, tile[0]);
			top = Math.min(top, tile[1]);
			bottom = Math.max(bottom, tile[1]);
		}
		return new int[] {(CurrentRoom.middleOf(left) + CurrentRoom.middleOf(right)) / 2, (CurrentRoom.middleOf(top) + CurrentRoom.middleOf(bottom)) / 2};
	}

	/** @return a place turned by quarter turns around a middle, the way {@link #turned} turns tiles */
	private static int[] turnAround(int[] place, int[] middle, int turns) {
		int x = place[0] - middle[0];
		int z = place[2] - middle[1];
		for (int t = 0; t < turns; t++) {
			int turnedX = -z;
			z = x;
			x = turnedX;
		}
		return new int[] {middle[0] + x, place[1], middle[1] + z};
	}

	private static int[][] turned(int[][] shape, int turns) {
		int[][] out = new int[shape.length][];
		for (int i = 0; i < shape.length; i++) {
			int x = shape[i][0];
			int z = shape[i][1];
			for (int t = 0; t < turns; t++) {
				int turnedX = -z;
				z = x;
				x = turnedX;
			}
			out[i] = new int[] {x, z};
		}
		return out;
	}

	/** @return the tiles moved so their top left is at a place */
	private static List<int[]> moved(int[][] shape, int toX, int toZ) {
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		for (int[] tile : shape) {
			minX = Math.min(minX, tile[0]);
			minZ = Math.min(minZ, tile[1]);
		}
		List<int[]> tiles = new ArrayList<>();
		for (int[] tile : shape) {
			tiles.add(new int[] {tile[0] - minX + toX, tile[1] - minZ + toZ});
		}
		return tiles;
	}

	private static List<int[]> tiles(int... coordinates) {
		List<int[]> tiles = new ArrayList<>();
		for (int i = 0; i < coordinates.length; i += 2) {
			tiles.add(new int[] {coordinates[i], coordinates[i + 1]});
		}
		return tiles;
	}

	/** @return every order of the tiles */
	private static List<List<int[]>> orders(List<int[]> tiles) {
		if (tiles.size() <= 1) {
			return List.of(new ArrayList<>(tiles));
		}
		List<List<int[]>> all = new ArrayList<>();
		for (int i = 0; i < tiles.size(); i++) {
			List<int[]> rest = new ArrayList<>(tiles);
			int[] first = rest.remove(i);
			for (List<int[]> order : orders(rest)) {
				order.addFirst(first);
				all.add(order);
			}
		}
		return all;
	}
}
