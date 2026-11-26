package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A dungeon map of 16-pixel rooms read as the game draws it: kinds, joined tiles, doors and checkmarks. */
class DungeonMapDataTest {
	private static final int ROOM = 16;
	private static final int GAP = 20;
	private static final int START = 5;

	private static void room(byte[] colors, int tile, int color) {
		int left = START + tile % 6 * GAP;
		int top = START + tile / 6 * GAP;
		for (int y = 0; y < ROOM; y++) {
			for (int x = 0; x < ROOM; x++) {
				colors[left + x + (top + y) * 128] = (byte) color;
			}
		}
	}

	@Test
	void readsRoomsJoinsDoorsAndChecks() {
		byte[] colors = new byte[128 * 128];
		room(colors, 0, 30);
		room(colors, 1, 63);
		room(colors, 2, 63);
		room(colors, 8, 66);
		// tiles 1 and 2 are one room: the space between them is filled across its whole side
		for (int y = 0; y < ROOM; y++) {
			for (int x = ROOM; x < GAP; x++) {
				colors[START + GAP + x + (START + y) * 128] = 63;
			}
		}
		// a wither door from the entrance east to tile 1: a short line in the middle of the gap
		for (int y = 6; y < 10; y++) {
			for (int x = ROOM; x < GAP; x++) {
				colors[START + x + (START + y) * 128] = 119;
			}
		}
		// a white checkmark in the middle of the puzzle room
		int middle = START + 2 * GAP + 8 + (START + GAP + 8) * 128;
		colors[middle] = 34;
		DungeonMapData map = new DungeonMapData();
		map.read(colors);
		assertTrue(map.known());
		assertEquals(16, map.roomSize());
		assertEquals(DungeonMapData.Kind.ENTRANCE, map.kinds[0]);
		assertEquals(DungeonMapData.Kind.NORMAL, map.kinds[1]);
		assertEquals(DungeonMapData.Kind.PUZZLE, map.kinds[8]);
		assertNull(map.kinds[3]);
		assertTrue(map.joinedEast[1]);
		assertFalse(map.joinedEast[0]);
		assertEquals(DungeonMapData.Door.WITHER, map.doorEast[0]);
		assertEquals(DungeonMapData.Check.WHITE, map.checks[8]);
		assertEquals(DungeonMapData.Check.NONE, map.checks[1]);
	}

	@Test
	void aMapWithoutTheEntranceStaysUnknown() {
		DungeonMapData map = new DungeonMapData();
		map.read(new byte[128 * 128]);
		assertFalse(map.known());
	}
}
