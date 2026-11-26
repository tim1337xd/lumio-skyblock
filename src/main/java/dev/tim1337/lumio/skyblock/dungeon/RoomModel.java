package dev.tim1337.lumio.skyblock.dungeon;

/**
 * Lumio's room model for points kept per room: a place counts from the middle block of the room's footprint, across and down along the room as it
 * is turned ({@link CurrentRoom.Rotation}), with the height as in the world. The middle is the same block however the room lies, so a point stays
 * put in every run. The turn's corner ({@link CurrentRoom}, the room's clay corner or the corner its tiles give) is where counting starts inside
 * the game code; this class moves between the two.
 */
public final class RoomModel {
	/** A tile is 31 blocks wide, with a gap of one to the next: its middle block is 15 from its edge. */
	private static final int TILE_MIDDLE = 15;

	private RoomModel() {
	}

	/**
	 * @param shape the room's shape ("1x1", "1x2", "1x3", "1x4", "2x2" or "L")
	 * @return the middle of the room's footprint, across and down from its corner, in the room's own turn
	 */
	static int[] middle(String shape) {
		int across = switch (shape) {
			case "1x2", "2x2", "L" -> 2;
			case "1x3" -> 3;
			case "1x4" -> 4;
			default -> 1;
		};
		int down = "2x2".equals(shape) || "L".equals(shape) ? 2 : 1;
		return new int[] {(across - 1) * CurrentRoom.TILE / 2 + TILE_MIDDLE, (down - 1) * CurrentRoom.TILE / 2 + TILE_MIDDLE};
	}

	/** @return the middle of a room of a shape, across and down from its corner (so the room reaches twice that, plus one, across and down) */
	public static int[] middleOf(String shape) {
		return middle(shape);
	}

	/** @return a place counted from the room's corner, counted from its middle instead */
	public static int[] fromCorner(String shape, int x, int y, int z) {
		int[] middle = middle(shape);
		return new int[] {x - middle[0], y, z - middle[1]};
	}

	/** @return a place counted from the room's middle, counted from its corner instead */
	public static int[] toCorner(String shape, int x, int y, int z) {
		int[] middle = middle(shape);
		return new int[] {x + middle[0], y, z + middle[1]};
	}
}
