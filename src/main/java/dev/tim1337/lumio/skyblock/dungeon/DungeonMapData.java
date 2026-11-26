package dev.tim1337.lumio.skyblock.dungeon;

/**
 * What the game's dungeon map item shows, read from its 128 by 128 colors: which of the 6 by 6 tiles are rooms and of which kind, which tiles of a
 * room are joined, the doors between rooms, and each room's checkmark. Only the map's own pixels are read, never the world, so nothing shows that
 * the map does not. How the map is laid out (the room size, where the grid starts) was learned
 * from how Odin reads it; the reading here is our own.
 */
public final class DungeonMapData {
	public static final int SIZE = 128;
	public static final int GRID = 6;
	private static final int SPACING = 4;
	/** The sizes a room can have on the map, in pixels, and where the grid starts when the entrance sits at the edge. */
	private static final java.util.Set<Integer> ROOM_SIZES = java.util.Set.of(16, 18);
	private static final int ALIGNED_START = 22;

	/** The kinds of rooms, by the map color the game draws them in. */
	public enum Kind {
		ENTRANCE(30), FAIRY(82), NORMAL(63), BLOOD(18), MINIBOSS(74), PUZZLE(66), TRAP(62), UNEXPLORED(85);

		final byte color;

		Kind(int color) {
			this.color = (byte) color;
		}

		static Kind of(byte color) {
			for (Kind kind : values()) {
				if (kind.color == color) {
					return kind;
				}
			}
			return null;
		}
	}

	/** A room's checkmark, by the color in its middle. */
	public enum Check {
		NONE, WHITE, GREEN, FAILED, QUESTION
	}

	/** A door: none, a normal opening, a wither door, the blood door, or the fairy door. */
	public enum Door {
		NONE, NORMAL, WITHER, BLOOD, FAIRY
	}

	private static final byte EMPTY = 0;
	private static final byte WITHER_DOOR = 119;
	private static final byte CHECK_WHITE = 34;
	private static final byte CHECK_GREEN = 30;
	private static final byte CHECK_RED = 18;
	private static final byte CHECK_QUESTION = 119;

	private int roomSize = -1;
	private int startX;
	private int startY;
	public final Kind[] kinds = new Kind[GRID * GRID];
	public final Check[] checks = new Check[GRID * GRID];
	/** Whether a tile is joined with the tile east of it, and south of it, as one room. */
	public final boolean[] joinedEast = new boolean[GRID * GRID];
	public final boolean[] joinedSouth = new boolean[GRID * GRID];
	/** The door east of a tile, and south of it. */
	public final Door[] doorEast = new Door[GRID * GRID];
	public final Door[] doorSouth = new Door[GRID * GRID];
	/** The room each tile belongs to: the first of its tiles (left to right, top to bottom); -1 for no room. */
	public final int[] roomOf = new int[GRID * GRID];
	/** The tile of a room that holds its checkmark (or its first tile), by the room's first tile. */
	public final int[] mainTile = new int[GRID * GRID];
	/** How many tiles a room has, by its first tile. */
	public final int[] roomTiles = new int[GRID * GRID];

	{
		java.util.Arrays.fill(roomOf, -1);
	}

	/** @return whether the layout is known: the entrance was found on the map */
	public boolean known() {
		return roomSize > 0;
	}

	public int roomSize() {
		return roomSize;
	}

	/** @return where a tile's top left is on the map, across and down */
	public int tileX(int tile) {
		return startX + tile % GRID * (roomSize + SPACING);
	}

	public int tileY(int tile) {
		return startY + tile / GRID * (roomSize + SPACING);
	}

	/** Reads the map's colors; a map that is not a dungeon's leaves it as it was. */
	public void read(byte[] colors) {
		if (colors == null || colors.length < SIZE * SIZE || colors[0] != EMPTY) {
			return;
		}
		if (!known() && !layout(colors)) {
			return;
		}
		int half = roomSize / 2;
		int connection = roomSize + SPACING / 2;
		for (int tile = 0; tile < GRID * GRID; tile++) {
			int x = tileX(tile);
			int y = tileY(tile);
			byte corner = pixel(colors, x, y);
			Kind kind = corner == EMPTY ? null : Kind.of(corner);
			kinds[tile] = kind;
			byte middle = pixel(colors, x + half, y + half);
			checks[tile] = kind == null || middle == corner ? Check.NONE : switch (middle) {
				case CHECK_WHITE -> Check.WHITE;
				case CHECK_GREEN -> Check.GREEN;
				case CHECK_RED -> Check.FAILED;
				case CHECK_QUESTION -> Check.QUESTION;
				default -> Check.NONE;
			};
			int column = tile % GRID;
			int row = tile / GRID;
			joinedEast[tile] = column < GRID - 1 && kind != null && pixel(colors, x + connection, y) != EMPTY;
			joinedSouth[tile] = row < GRID - 1 && kind != null && pixel(colors, x, y + connection) != EMPTY;
			doorEast[tile] = column < GRID - 1 ? door(pixel(colors, x + connection, y + half), pixel(colors, x + connection, y + half - SPACING)) : Door.NONE;
			doorSouth[tile] = row < GRID - 1 ? door(pixel(colors, x + half, y + connection), pixel(colors, x + half - SPACING, y + connection)) : Door.NONE;
			if (joinedEast[tile]) {
				doorEast[tile] = Door.NONE;
			}
			if (joinedSouth[tile]) {
				doorSouth[tile] = Door.NONE;
			}
		}
		groupRooms();
	}

	/** Joins the tiles into rooms along their joins: one connected shape each. */
	private void groupRooms() {
		java.util.Arrays.fill(roomOf, -1);
		int[] queue = new int[GRID * GRID];
		for (int first = 0; first < GRID * GRID; first++) {
			if (kinds[first] == null || roomOf[first] >= 0) {
				continue;
			}
			int head = 0;
			int tail = 0;
			queue[tail++] = first;
			roomOf[first] = first;
			mainTile[first] = first;
			while (head < tail) {
				int tile = queue[head++];
				if (checks[tile] != Check.NONE) {
					mainTile[first] = tile;
				}
				int column = tile % GRID;
				tail = join(queue, tail, first, joinedEast[tile] ? tile + 1 : -1);
				tail = join(queue, tail, first, joinedSouth[tile] ? tile + GRID : -1);
				tail = join(queue, tail, first, column > 0 && joinedEast[tile - 1] ? tile - 1 : -1);
				tail = join(queue, tail, first, tile >= GRID && joinedSouth[tile - GRID] ? tile - GRID : -1);
			}
			roomTiles[first] = tail;
		}
	}

	private int join(int[] queue, int tail, int room, int next) {
		if (next < 0 || roomOf[next] >= 0 || kinds[next] == null) {
			return tail;
		}
		roomOf[next] = room;
		queue[tail] = next;
		return tail + 1;
	}

	/**
	 * A room the player walked into before the game's map has it: its tile is drawn as that kind of room. The game's map wins: this only fills
	 * a tile the map has nothing for, or still marks as unexplored, and the next read of the map takes over as soon as it shows the room.
	 *
	 * @return whether the tile was filled
	 */
	public boolean reveal(int tile, Kind kind) {
		if (!known() || kind == null || kinds[tile] != null && kinds[tile] != Kind.UNEXPLORED) {
			return false;
		}
		kinds[tile] = kind;
		checks[tile] = Check.NONE;
		return true;
	}

	/** Joins two revealed tiles of one room, east or south of each other, and groups the rooms anew; after {@link #reveal}. */
	public void joinRevealed(int tile, boolean east) {
		if (east) {
			joinedEast[tile] = true;
			doorEast[tile] = Door.NONE;
		} else {
			joinedSouth[tile] = true;
			doorSouth[tile] = Door.NONE;
		}
	}

	/** Groups the tiles into rooms again, after tiles were revealed. */
	public void regroup() {
		groupRooms();
	}

	/** @return the kind a room of the room data is drawn as: its type, a room without a kind of its own as a normal one */
	public static Kind kindOf(String type) {
		return switch (type == null ? "" : type) {
			case "ENTRANCE" -> Kind.ENTRANCE;
			case "FAIRY" -> Kind.FAIRY;
			case "BLOOD" -> Kind.BLOOD;
			case "PUZZLE" -> Kind.PUZZLE;
			case "TRAP" -> Kind.TRAP;
			case "CHAMPION" -> Kind.MINIBOSS;
			default -> Kind.NORMAL;
		};
	}

	/** A door is a short colored line between two rooms, empty beside it (a joined room is colored there too). */
	private static Door door(byte color, byte beside) {
		if (color == EMPTY || beside != EMPTY) {
			return Door.NONE;
		}
		if (color == WITHER_DOOR) {
			return Door.WITHER;
		}
		if (color == Kind.BLOOD.color) {
			return Door.BLOOD;
		}
		return color == Kind.FAIRY.color ? Door.FAIRY : Door.NORMAL;
	}

	/**
	 * Finds the grid from the entrance: the game draws it first, as a run of 16 or 18 pixels, which is the size of a room; the grid starts where
	 * that run's place repeats, one room and a gap at a time.
	 */
	private boolean layout(byte[] colors) {
		for (int row = 0; row < SIZE; row++) {
			int column = 0;
			while (column < SIZE) {
				int run = runOf(colors, row, column, Kind.ENTRANCE.color);
				if (run == 0) {
					column++;
					continue;
				}
				if (ROOM_SIZES.contains(run)) {
					roomSize = run;
					int step = run + SPACING;
					startX = gridStart(column, step);
					startY = gridStart(row, step);
					return true;
				}
				column += run;
			}
		}
		return false;
	}

	/** @return how many pixels of a color follow from a place, along its row */
	private static int runOf(byte[] colors, int row, int column, byte color) {
		int length = 0;
		while (column + length < SIZE && colors[row * SIZE + column + length] == color) {
			length++;
		}
		return length;
	}

	/** @return where the grid starts along one side, from a room's place on it; a room at the very edge means the grid starts a step in */
	private static int gridStart(int place, int step) {
		int start = place % step;
		return start == 0 ? ALIGNED_START : start;
	}

	/** Forgets the layout, for a new run. */
	public void reset() {
		roomSize = -1;
		java.util.Arrays.fill(kinds, null);
		java.util.Arrays.fill(checks, Check.NONE);
		java.util.Arrays.fill(joinedEast, false);
		java.util.Arrays.fill(joinedSouth, false);
		java.util.Arrays.fill(doorEast, Door.NONE);
		java.util.Arrays.fill(doorSouth, Door.NONE);
		java.util.Arrays.fill(roomOf, -1);
	}

	private static byte pixel(byte[] colors, int x, int y) {
		return x < 0 || y < 0 || x >= SIZE || y >= SIZE ? EMPTY : colors[x + y * SIZE];
	}

	/** @return the map pixel of a place in the world, across and down; -1 while the layout is unknown */
	public float worldToMap(double coordinate, boolean down) {
		if (!known()) {
			return -1;
		}
		double tiles = (coordinate + 200.0) / CurrentRoom.TILE;
		return (float) ((down ? startY : startX) + tiles * (roomSize + SPACING));
	}
}
