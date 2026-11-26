package dev.tim1337.lumio.skyblock.dungeon;

/**
 * Where a room's marks sit on the map: its checkmark (or "?") and its secrets count share one tile, the room's main tile, the count under the
 * mark and both inside that tile; the room's name uses the room's rectangle (1x1, 1x2 to 1x4, 2x2; for an L the largest rectangle inside it). Plain tile numbers, so it is tested without the game.
 */
final class MapMarks {
	/** The values of a rectangle of tiles: its left and top column and row, and its right and bottom ones (included). */
	static final int LEFT = 0;
	static final int TOP = 1;
	static final int RIGHT = 2;
	static final int BOTTOM = 3;

	private MapMarks() {
	}

	/**
	 * Writes the rectangle of tiles a room's mark is centered on into {@code out}: the room itself when it is a rectangle, else the largest
	 * rectangle of its tiles (the first found from the top left when two are as large).
	 *
	 * @param roomOf each tile's room (the room's main tile), -1 for none
	 * @return false when the room has no tile
	 */
	static boolean markRect(int[] roomOf, int room, int grid, int[] out) {
		int best = 0;
		for (int top = 0; top < grid; top++) {
			for (int left = 0; left < grid; left++) {
				if (roomOf[left + top * grid] != room) {
					continue;
				}
				for (int bottom = top; bottom < grid && roomOf[left + bottom * grid] == room; bottom++) {
					for (int right = left; right < grid && filled(roomOf, room, grid, left, top, right, bottom); right++) {
						int area = (right - left + 1) * (bottom - top + 1);
						if (area > best) {
							best = area;
							out[LEFT] = left;
							out[TOP] = top;
							out[RIGHT] = right;
							out[BOTTOM] = bottom;
						}
					}
				}
			}
		}
		return best > 0;
	}

	/**
	 * The tile a room's checkmark and its secrets count stand on, one for both: the room's main tile (the one the game's map has its checkmark on, or
	 * its first tile), whatever the room's shape. Never a corner of the room's bounding box, which for an L is not even part of the room.
	 *
	 * @param roomOf   each tile's room, -1 for none
	 * @param mainTile each room's main tile, by the room
	 * @return the tile, or -1 when the room has none
	 */
	static int anchorTile(int[] roomOf, int[] mainTile, int room) {
		if (room < 0 || room >= roomOf.length) {
			return -1;
		}
		int main = mainTile[room];
		if (main >= 0 && main < roomOf.length && roomOf[main] == room) {
			return main;
		}
		return roomOf[room] == room ? room : -1;
	}

	/** How the checkmark and the count share one tile: what shows, the mark's size and top, the count's text scale and top (from the tile's top). */
	record Stack(boolean mark, boolean count, float markSize, float markTop, float countScale, float countTop) {
	}

	/** The height of a line of text at scale 1, in GUI pixels. */
	static final float LINE = 9f;

	/**
	 * Lays the checkmark and the secrets count out inside one tile: the count centered under the mark, both within the tile with a small margin.
	 * The count's text follows the tile's size and shrinks to the tile's width; the mark shrinks a little so both fit. Where the tile is too small
	 * for both, one of them stays: the mark, or the count when that is asked for.
	 *
	 * @param tile         the tile's size on the screen
	 * @param hasMark      whether the room has a checkmark (or "?")
	 * @param hasCount     whether its secrets are known
	 * @param markShare    the mark's usual size, as a share of the tile
	 * @param leastShare   the smallest the mark may shrink to, as a share of the tile
	 * @param countWidth   the count's width at text scale 1
	 * @param wantedScale  the count's text scale at this tile size
	 * @param leastScale   the smallest scale the count is still readable at
	 * @param margin       the room kept to the tile's edges
	 * @param gap          the room between the mark and the count
	 * @param preferCount  whether the count stays instead of the mark where only one fits
	 */
	static Stack stack(float tile, boolean hasMark, boolean hasCount, float markShare, float leastShare, float countWidth, float wantedScale, float leastScale,
		float margin, float gap, boolean preferCount) {
		float inner = tile - 2 * margin;
		float fullMark = tile * markShare;
		float scale = hasCount && countWidth > 0 ? Math.min(wantedScale, inner / countWidth) : 0f;
		boolean countFits = hasCount && scale >= leastScale && LINE * scale <= inner;
		if (!countFits) {
			return new Stack(hasMark, false, fullMark, (tile - fullMark) / 2f, 0f, 0f);
		}
		float countHeight = LINE * scale;
		if (!hasMark) {
			return new Stack(false, true, 0f, 0f, scale, (tile - countHeight) / 2f);
		}
		float mark = Math.min(fullMark, inner - countHeight - gap);
		if (mark >= tile * leastShare) {
			float top = (tile - (mark + gap + countHeight)) / 2f;
			return new Stack(true, true, mark, top, scale, top + mark + gap);
		}
		// too small for both
		if (preferCount) {
			return new Stack(false, true, 0f, 0f, scale, (tile - countHeight) / 2f);
		}
		return new Stack(true, false, fullMark, (tile - fullMark) / 2f, 0f, 0f);
	}

	/** @return whether every tile of a rectangle belongs to the room */
	private static boolean filled(int[] roomOf, int room, int grid, int left, int top, int right, int bottom) {
		for (int row = top; row <= bottom; row++) {
			if (roomOf[right + row * grid] != room) {
				return false;
			}
		}
		for (int column = left; column <= right; column++) {
			if (roomOf[column + bottom * grid] != room) {
				return false;
			}
		}
		return true;
	}
}
