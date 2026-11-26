package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom.RoomData;

import org.junit.jupiter.api.Test;

/**
 * The background scan's limits: nothing of a room is given out before it is discovered (the one way to it that the map, the highlights, the
 * waypoints and the API have), walking in discovers, and the game's map wins for a room's state while the scan gives what does not change.
 */
class DungeonScanTest {
	private static final RoomData CRYPT = new RoomData("Redstone Crypt", "NORMAL", "1x1", 1, 4, 2);
	private static final RoomData QUIZ = new RoomData("Quiz", "PUZZLE", "1x1", 1, 0, 0);
	private static final RoomData LONG = new RoomData("Long Hall", "NORMAL", "1x2", 2, 6, 1);

	/** A room of 16 pixels on the map, as the game draws it. */
	private static void room(byte[] colors, int tile, int color) {
		int left = 5 + tile % 6 * 20;
		int top = 5 + tile / 6 * 20;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				colors[left + x + (top + y) * 128] = (byte) color;
			}
		}
	}

	@Test
	void nothingOfARoomIsGivenOutBeforeItIsDiscovered() {
		DungeonScan.Rooms rooms = new DungeonScan.Rooms();
		rooms.scanned(7, CRYPT);
		rooms.scanned(8, QUIZ);
		// told by the scan, not discovered: nobody gets it, whatever asks (the map's renderer, the highlights, the waypoints and the API all ask here)
		assertNull(rooms.room(7));
		assertNull(rooms.room(8));
		assertFalse(rooms.discovered(7));
		// the game's map shows the room: now it is given out, the other one still is not
		rooms.mapShows(7);
		assertSame(CRYPT, rooms.room(7));
		assertNull(rooms.room(8));
		// a tile outside the grid is never discovered
		assertFalse(rooms.discovered(-1));
		assertFalse(rooms.discovered(36));
	}

	@Test
	void walkingIntoARoomDiscoversItWithoutTheMap() {
		DungeonScan.Rooms rooms = new DungeonScan.Rooms();
		rooms.scanned(8, QUIZ);
		assertFalse(rooms.walked(8));
		rooms.walkedInto(8);
		assertTrue(rooms.discovered(8));
		assertTrue(rooms.walked(8));
		assertSame(QUIZ, rooms.room(8));
		// a room the map shows but the player was not in is discovered, not walked: it is not drawn ahead of the map
		rooms.scanned(9, CRYPT);
		rooms.mapShows(9);
		assertFalse(rooms.walked(9));
		// discovered before the scan told it: given out as soon as it is told
		rooms.walkedInto(10);
		assertNull(rooms.room(10));
		rooms.scanned(10, CRYPT);
		assertSame(CRYPT, rooms.room(10));
	}

	@Test
	void theTotalOfTheSecretsIsKnownOnlyOnceEveryTileWasToldAndCountsEachRoomOnce() {
		DungeonScan.Rooms rooms = new DungeonScan.Rooms();
		rooms.scanned(0, CRYPT);
		rooms.scanned(1, LONG);
		rooms.scanned(2, LONG);
		assertEquals(-1, rooms.totalSecrets());
		assertFalse(rooms.complete());
		for (int tile = 3; tile < 36; tile++) {
			rooms.scanned(tile, null);
		}
		assertTrue(rooms.complete());
		// 4 and 6: the room of two tiles once; the total is there though no room is discovered, and no room is given out for it
		assertEquals(10, rooms.totalSecrets());
		assertNull(rooms.room(0));
		assertNull(rooms.room(1));
		rooms.reset();
		assertEquals(-1, rooms.totalSecrets());
		assertFalse(rooms.done(0));
	}

	@Test
	void theGamesMapWinsForARoomsStateAndTheScanGivesItsKind() {
		// the entrance, an open room and a room the map still has as unexplored
		byte[] colors = new byte[128 * 128];
		room(colors, 0, 30);
		room(colors, 1, 63);
		room(colors, 2, 85);
		DungeonMapData map = new DungeonMapData();
		map.read(colors);
		int unexplored = -1;
		int open = -1;
		int empty = -1;
		for (int tile = 0; tile < 36; tile++) {
			if (map.kinds[tile] == DungeonMapData.Kind.UNEXPLORED && unexplored < 0) {
				unexplored = tile;
			} else if (map.kinds[tile] == DungeonMapData.Kind.NORMAL && open < 0) {
				open = tile;
			} else if (map.kinds[tile] == null && empty < 0) {
				empty = tile;
			}
		}
		assertTrue(unexplored >= 0 && open >= 0 && empty >= 0, "the sample map has an unexplored room, an open one and an empty tile");
		// a room the map has as discovered keeps the map's kind and check
		DungeonMapData.Check check = map.checks[open];
		assertFalse(map.reveal(open, DungeonMapData.Kind.PUZZLE));
		assertEquals(DungeonMapData.Kind.NORMAL, map.kinds[open]);
		assertEquals(check, map.checks[open]);
		// a room the player walked into that the map still has as unexplored, or does not have at all, is drawn as its kind
		assertTrue(map.reveal(unexplored, DungeonMapData.Kind.PUZZLE));
		assertEquals(DungeonMapData.Kind.PUZZLE, map.kinds[unexplored]);
		assertTrue(map.reveal(empty, DungeonMapData.Kind.TRAP));
		map.regroup();
		assertEquals(empty, map.roomOf[empty]);
		// the kinds of the room data's types
		assertEquals(DungeonMapData.Kind.MINIBOSS, DungeonMapData.kindOf("CHAMPION"));
		assertEquals(DungeonMapData.Kind.NORMAL, DungeonMapData.kindOf("RARE"));
		assertEquals(DungeonMapData.Kind.PUZZLE, DungeonMapData.kindOf("PUZZLE"));
		assertEquals(DungeonMapData.Kind.NORMAL, DungeonMapData.kindOf(null));
	}
}
