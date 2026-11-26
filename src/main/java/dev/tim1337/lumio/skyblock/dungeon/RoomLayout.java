package dev.tim1337.lumio.skyblock.dungeon;

import java.util.List;

/**
 * How a room of more than one tile is turned, from the way its tiles lie on the grid alone, the same whatever order the tiles were found in:
 * <ul>
 * <li>an L by the tile its 2×2 is missing: in the room's own turn that tile is always the one right of the top left;</li>
 * <li>a column of tiles a quarter turn, a row or a square as it lies (the grid cannot tell these apart when turned half around).</li>
 * </ul>
 * A place in the room counts from the room's middle ({@link RoomModel}); the corner the game code counts from lies where that middle puts it. Rooms
 * of one tile are turned by their blue clay instead ({@link Placement#onTile}). Plain logic over tile numbers, tested without the game.
 */
final class RoomLayout {
	/** How far a room's corner is from the middle of a tile, across and down. */
	static final int CORNER = 15;

	/** The turn of a room and the place of the corner its places count from, in the world. */
	record Placement(CurrentRoom.Rotation rotation, int clayX, int clayZ) {
		/** @return the placement of a room of one tile, turned so its corner is the given corner of the tile */
		static Placement onTile(CurrentRoom.Rotation rotation, int tileAcross, int tileDown) {
			return new Placement(rotation, CurrentRoom.middleOf(tileAcross) + rotation.cornerX * CORNER,
				CurrentRoom.middleOf(tileDown) + rotation.cornerZ * CORNER);
		}
	}

	private RoomLayout() {
	}

	/**
	 * @param shape the room's shape ("1x2", "1x3", "1x4", "2x2" or "L")
	 * @param tiles the room's tiles as {across, down}
	 * @return the room's turn and corner, or null where the tiles do not make the shape
	 */
	static Placement of(String shape, List<int[]> tiles) {
		if (tiles.isEmpty()) {
			return null;
		}
		int left = Integer.MAX_VALUE;
		int top = Integer.MAX_VALUE;
		int right = Integer.MIN_VALUE;
		int bottom = Integer.MIN_VALUE;
		for (int[] tile : tiles) {
			left = Math.min(left, tile[0]);
			right = Math.max(right, tile[0]);
			top = Math.min(top, tile[1]);
			bottom = Math.max(bottom, tile[1]);
		}
		CurrentRoom.Rotation rotation;
		if ("L".equals(shape)) {
			rotation = lTurn(tiles, left, top, right, bottom);
			if (rotation == null) {
				return null;
			}
		} else {
			rotation = left == right && top != bottom ? CurrentRoom.Rotation.WEST : CurrentRoom.Rotation.SOUTH;
		}
		// the room's middle block is the middle of its tiles; its corner lies where the room's own turn puts it from there
		int middleX = (CurrentRoom.middleOf(left) + CurrentRoom.middleOf(right)) / 2;
		int middleZ = (CurrentRoom.middleOf(top) + CurrentRoom.middleOf(bottom)) / 2;
		int[] middle = RoomModel.middle(shape);
		int[] toMiddle = rotation.intoWorld(middle[0], middle[1]);
		return new Placement(rotation, middleX - toMiddle[0], middleZ - toMiddle[1]);
	}

	/** @return the turn of an L: which corner of its 2×2 is empty (right top as it lies, then right bottom, left bottom, left top), or null */
	private static CurrentRoom.Rotation lTurn(List<int[]> tiles, int left, int top, int right, int bottom) {
		if (tiles.size() != 3 || right - left != 1 || bottom - top != 1) {
			return null;
		}
		if (!contains(tiles, right, top)) {
			return CurrentRoom.Rotation.SOUTH;
		}
		if (!contains(tiles, right, bottom)) {
			return CurrentRoom.Rotation.WEST;
		}
		if (!contains(tiles, left, bottom)) {
			return CurrentRoom.Rotation.NORTH;
		}
		return contains(tiles, left, top) ? null : CurrentRoom.Rotation.EAST;
	}

	private static boolean contains(List<int[]> tiles, int x, int z) {
		for (int[] tile : tiles) {
			if (tile[0] == x && tile[1] == z) {
				return true;
			}
		}
		return false;
	}
}
