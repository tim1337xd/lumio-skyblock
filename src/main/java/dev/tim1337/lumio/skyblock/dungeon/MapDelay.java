package dev.tim1337.lumio.skyblock.dungeon;

/**
 * For the debug log: how long a room takes to appear on the dungeon map after the player walked into it, and where the time goes: waiting for
 * the game's map item to change (the server's map), our own reading of it (every few frames), and when the room's name is known. It only
 * measures while a debug tool listens; nothing is drawn or decided from it. Plain logic, tested without the game.
 */
final class MapDelay {
	private static final int NONE = -1;

	private int tile = NONE;
	private int entered = NONE;
	private long enteredAt;
	private long lastChangeAt = Long.MIN_VALUE;
	private long nameAt = Long.MIN_VALUE;

	/**
	 * Every frame: the tile the player stands on (-1 outside the grid) and whether its room shows on our map.
	 *
	 * @return a line for the log when the player walked onto a tile whose room was on the map already, else null
	 */
	String frame(int nowTile, boolean shown, long now) {
		if (nowTile == tile) {
			return null;
		}
		tile = nowTile;
		entered = NONE;
		if (nowTile < 0) {
			return null;
		}
		if (shown) {
			return "Dungeon map: the room at tile " + nowTile + " was on the map before it was entered";
		}
		entered = nowTile;
		enteredAt = now;
		nameAt = Long.MIN_VALUE;
		return null;
	}

	/** The game's map item changed (its colors are not what they were at our last read). */
	void mapItemChanged(long now) {
		lastChangeAt = now;
	}

	/** The room's name became known (the room the player stands in was told from its blocks). */
	void named(long now) {
		if (entered != NONE && nameAt == Long.MIN_VALUE) {
			nameAt = now;
		}
	}

	/**
	 * After each of our reads of the map item: whether the room of the tile entered shows now.
	 *
	 * @param readEveryFrames how many frames lie between two reads
	 * @return the line for the log once the room shows, else null
	 */
	String read(boolean shown, long now, int readEveryFrames) {
		if (entered == NONE || !shown) {
			return null;
		}
		long total = now - enteredAt;
		long waited = lastChangeAt < enteredAt ? 0 : lastChangeAt - enteredAt;
		String line = "Dungeon map: the room at tile " + entered + " appeared " + total + " ms after it was entered: " + waited
			+ " ms until the game's map item changed, " + (total - waited) + " ms until our read of it (every " + readEveryFrames
			+ " frames), drawn in the same frame; its name " + (nameAt == Long.MIN_VALUE ? "not known yet" : "known after " + (nameAt - enteredAt) + " ms");
		entered = NONE;
		return line;
	}

	void reset() {
		tile = NONE;
		entered = NONE;
		lastChangeAt = Long.MIN_VALUE;
		nameAt = Long.MIN_VALUE;
	}
}
