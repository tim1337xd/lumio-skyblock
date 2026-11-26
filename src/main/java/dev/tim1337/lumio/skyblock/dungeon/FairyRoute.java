package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The Fairy room on the route to the Blood room. The route runs through the Fairy room, but its doorways are open (no wither door), so the door
 * highlight had nothing to show there. From the dungeon map's data alone (the rooms, and the doors between them as the map draws them: wither,
 * blood, fairy, normal) this picks the one open doorway to outline for the room the player is in:
 * <ul>
 * <li>in the Fairy room: the doorway that leads on toward Blood, not the other exits;</li>
 * <li>in a room before it: the doorway into the Fairy room, when the Fairy room is next on the route.</li>
 * </ul>
 * "Toward Blood" is only said where the map shows it: beyond that doorway lies the Blood room, the blood door or a wither door, and the entrance
 * does not. Where that is not known yet, or more than one doorway fits, nothing is picked rather than guessed. Plain data, so it is unit tested.
 */
final class FairyRoute {
	private static final int GRID = DungeonMapData.GRID;
	private static final int TILES = GRID * GRID;

	/** An open doorway between two tiles: the tile it lies east or south of. */
	record Doorway(int tile, boolean east) {
		/** @return the tile on its other side */
		int other() {
			return east ? tile + 1 : tile + GRID;
		}
	}

	private FairyRoute() {
	}

	/**
	 * @param tile the tile the player stands on, or -1
	 * @return the doorway of the player's room to outline, or null for none
	 */
	static Doorway doorway(DungeonMapData map, int tile) {
		if (tile < 0 || tile >= TILES || !map.known()) {
			return null;
		}
		int room = map.roomOf[tile];
		if (room < 0) {
			return null;
		}
		if (map.kinds[room] == DungeonMapData.Kind.FAIRY) {
			Doorway onward = onward(map, room);
			// a wither or blood door there is the door highlight's own to show; any open doorway counts, whatever color the map draws it in
			return onward != null && open(doorOf(map, onward)) ? onward : null;
		}
		// in a room before the Fairy room: its doorway into it, when the route goes on from there
		for (int t = 0; t < TILES; t++) {
			for (int side = 0; side < 2; side++) {
				Doorway door = new Doorway(t, side == 0);
				if (!open(doorOf(map, door))) {
					continue;
				}
				int a = map.roomOf[t];
				int b = map.roomOf[door.other()];
				int fairy = a == room ? b : b == room ? a : -1;
				if (fairy < 0 || map.kinds[fairy] != DungeonMapData.Kind.FAIRY) {
					continue;
				}
				Doorway onward = onward(map, fairy);
				// the player comes from the entrance's side, and the route leaves the Fairy room through another doorway
				if (onward != null && !onward.equals(door) && beyond(map, fairy, room)[ENTRANCE]) {
					return door;
				}
			}
		}
		return null;
	}

	/** @return whether a door of the map is an open doorway: there is one, and it is no wither or blood door */
	private static boolean open(DungeonMapData.Door kind) {
		return kind != DungeonMapData.Door.NONE && kind != DungeonMapData.Door.WITHER && kind != DungeonMapData.Door.BLOOD;
	}

	/**
	 * Why no doorway is picked, in words for the debug log; "" when one is.
	 *
	 * @param tile the tile the player stands on, or -1
	 */
	static String why(DungeonMapData map, int tile) {
		if (doorway(map, tile) != null) {
			return "";
		}
		if (!map.known()) {
			return "the dungeon map is not read yet";
		}
		if (tile < 0 || tile >= TILES || map.roomOf[tile] < 0) {
			return "the player's tile is no room of the map";
		}
		int fairy = -1;
		for (int room = 0; room < map.kinds.length; room++) {
			if (map.kinds[room] == DungeonMapData.Kind.FAIRY) {
				fairy = room;
			}
		}
		if (fairy < 0) {
			return "the map shows no Fairy room yet";
		}
		Doorway onward = onward(map, fairy);
		if (onward == null) {
			return "no doorway of the Fairy room is known to lead toward Blood (the Blood room, the blood door or a wither door is not on the map beyond exactly one of its doorways)";
		}
		if (map.roomOf[tile] == fairy) {
			return "the Fairy room's way on is a wither or blood door, which the door highlight shows itself";
		}
		return "the player's room has no open doorway into the Fairy room on the entrance's side of it";
	}

	/** @return the doorway of the Fairy room that leads on toward Blood: the only one with the route's marks beyond it and no entrance; else null */
	static Doorway onward(DungeonMapData map, int fairy) {
		Doorway found = null;
		for (int t = 0; t < TILES; t++) {
			for (int side = 0; side < 2; side++) {
				Doorway door = new Doorway(t, side == 0);
				DungeonMapData.Door kind = doorOf(map, door);
				if (kind == DungeonMapData.Door.NONE) {
					continue;
				}
				int a = map.roomOf[t];
				int b = map.roomOf[door.other()];
				int next = a == fairy ? b : b == fairy ? a : -1;
				if (next < 0 || next == fairy) {
					continue;
				}
				boolean[] seen = beyond(map, fairy, next);
				boolean route = kind == DungeonMapData.Door.BLOOD || seen[BLOOD] || seen[ROUTE_DOOR];
				if (!route || seen[ENTRANCE]) {
					continue;
				}
				if (found != null) {
					// two doorways fit: not known which, so none
					return null;
				}
				found = door;
			}
		}
		return found;
	}

	private static final int ENTRANCE = 0;
	private static final int BLOOD = 1;
	private static final int ROUTE_DOOR = 2;

	/**
	 * What lies beyond a doorway of a room: every room reached from the room on its other side without going back through the room itself.
	 *
	 * @return whether the entrance is among them, the Blood room, and a wither or blood door between them
	 */
	private static boolean[] beyond(DungeonMapData map, int without, int from) {
		boolean[] result = new boolean[3];
		boolean[] reached = new boolean[TILES];
		int[] queue = new int[TILES];
		int head = 0;
		int tail = 0;
		queue[tail++] = from;
		reached[from] = true;
		while (head < tail) {
			int room = queue[head++];
			if (map.kinds[room] == DungeonMapData.Kind.ENTRANCE) {
				result[ENTRANCE] = true;
			}
			if (map.kinds[room] == DungeonMapData.Kind.BLOOD) {
				result[BLOOD] = true;
			}
			for (int t = 0; t < TILES; t++) {
				for (int side = 0; side < 2; side++) {
					Doorway door = new Doorway(t, side == 0);
					DungeonMapData.Door kind = doorOf(map, door);
					if (kind == DungeonMapData.Door.NONE) {
						continue;
					}
					int a = map.roomOf[t];
					int b = map.roomOf[door.other()];
					int next = a == room ? b : b == room ? a : -1;
					if (next < 0 || next == without) {
						continue;
					}
					if (kind == DungeonMapData.Door.WITHER || kind == DungeonMapData.Door.BLOOD) {
						result[ROUTE_DOOR] = true;
					}
					if (!reached[next]) {
						reached[next] = true;
						queue[tail++] = next;
					}
				}
			}
		}
		return result;
	}

	/** @return the door the map draws at a doorway; none at the grid's edge or where a side is no room */
	static DungeonMapData.Door doorOf(DungeonMapData map, Doorway door) {
		int tile = door.tile();
		if (door.east() ? tile % GRID >= GRID - 1 : tile / GRID >= GRID - 1) {
			return DungeonMapData.Door.NONE;
		}
		if (map.roomOf[tile] < 0 || map.roomOf[door.other()] < 0) {
			return DungeonMapData.Door.NONE;
		}
		DungeonMapData.Door kind = door.east() ? map.doorEast[tile] : map.doorSouth[tile];
		return kind == null ? DungeonMapData.Door.NONE : kind;
	}
}
