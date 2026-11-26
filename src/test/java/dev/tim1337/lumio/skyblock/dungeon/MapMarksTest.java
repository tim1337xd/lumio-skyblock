package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * The map's marks: centered on the room's rectangle, or on the largest rectangle of an L; and the player names setting of configs from before.
 */
class MapMarksTest {
	private static final int GRID = 6;

	/** A grid with one room on these tiles (column, row pairs), its main tile the first. */
	private static int[] room(int... tiles) {
		int[] roomOf = new int[GRID * GRID];
		Arrays.fill(roomOf, -1);
		int main = tiles[0] + tiles[1] * GRID;
		for (int i = 0; i < tiles.length; i += 2) {
			roomOf[tiles[i] + tiles[i + 1] * GRID] = main;
		}
		return roomOf;
	}

	private static int[] rect(int[] roomOf, int... firstTile) {
		int[] out = new int[4];
		MapMarks.markRect(roomOf, firstTile[0] + firstTile[1] * GRID, GRID, out);
		return out;
	}

	@Test
	void rectangles() {
		assertArrayEquals(new int[] {2, 3, 2, 3}, rect(room(2, 3), 2, 3), "1x1: its tile");
		assertArrayEquals(new int[] {1, 0, 4, 0}, rect(room(1, 0, 2, 0, 3, 0, 4, 0), 1, 0), "1x4: the whole row");
		assertArrayEquals(new int[] {5, 1, 5, 3}, rect(room(5, 1, 5, 2, 5, 3), 5, 1), "1x3 upright: the whole column");
		assertArrayEquals(new int[] {0, 0, 1, 1}, rect(room(0, 0, 1, 0, 0, 1, 1, 1), 0, 0), "2x2: the square");
	}

	@Test
	void anLsLargestRectangle() {
		// three in a row and one below its end
		assertArrayEquals(new int[] {0, 0, 2, 0}, rect(room(0, 0, 1, 0, 2, 0, 2, 1), 0, 0));
		// the small L of three tiles: the first of the two pairs
		assertArrayEquals(new int[] {3, 3, 4, 3}, rect(room(3, 3, 4, 3, 3, 4), 3, 3));
		// a tall L: three down and one to the side at the bottom
		assertArrayEquals(new int[] {1, 1, 1, 3}, rect(room(1, 1, 1, 2, 1, 3, 2, 3), 1, 1));
	}

	@Test
	void noTilesNoMark() {
		int[] roomOf = new int[GRID * GRID];
		Arrays.fill(roomOf, -1);
		assertFalse(MapMarks.markRect(roomOf, 7, GRID, new int[4]));
	}

	@Test
	void oldPlayerNamesBecomeTheNewOnes() {
		for (String old : new String[] {"TEAM", "ALL"}) {
			JsonObject json = new JsonObject();
			json.addProperty("player_names", old);
			DungeonMapFeature.migrateNames(json);
			assertEquals("ALWAYS", json.get("player_names").getAsString());
		}
		JsonObject off = new JsonObject();
		off.addProperty("player_names", "OFF");
		DungeonMapFeature.migrateNames(off);
		assertEquals("OFF", off.get("player_names").getAsString());
		JsonObject none = new JsonObject();
		DungeonMapFeature.migrateNames(none);
		assertFalse(none.has("player_names"), "a fresh config starts at Only in leap menu");
	}

	private static final int G = DungeonMapData.GRID;

	/** A room of these tiles, its first tile as its number, and its main tile (where the game's map has the checkmark). */
	private static int anchorOf(int main, int... tiles) {
		int[] roomOf = new int[G * G];
		int[] mainTile = new int[G * G];
		Arrays.fill(roomOf, -1);
		int room = tiles[0];
		for (int tile : tiles) {
			roomOf[tile] = room;
		}
		mainTile[room] = main;
		return MapMarks.anchorTile(roomOf, mainTile, room);
	}

	@Test
	void theAnchorTileOfEveryRoomShape() {
		// 1x1: its only tile
		assertEquals(7, anchorOf(7, 7));
		// 1x2, 1x3, 1x4, across and down: the main tile, wherever the game's checkmark is, never between two tiles
		assertEquals(1, anchorOf(1, 0, 1));
		assertEquals(0, anchorOf(0, 0, G));
		assertEquals(2, anchorOf(2, 0, 1, 2));
		assertEquals(3 * G, anchorOf(3 * G, 0, G, 2 * G, 3 * G));
		// 2x2: one of its four tiles, not the middle of the four
		assertEquals(G + 1, anchorOf(G + 1, 0, 1, G, G + 1));
		// L (0, 6, 7): its bounding box's corner tile 1 is not part of the room; the anchor always is
		int l = anchorOf(G, 0, G, G + 1);
		assertEquals(G, l);
		assertTrue(l == 0 || l == G || l == G + 1);
		// before the game has a checkmark, or with a main tile that is not the room's: the room's first tile
		assertEquals(0, anchorOf(0, 0, G, G + 1));
		assertEquals(0, anchorOf(1, 0, G, G + 1));
	}

	private static MapMarks.Stack stack(float tile, boolean mark, boolean count, boolean preferCount) {
		// a count "9/9" about 14 px wide at scale 1; the mark 62 % of the tile, 40 % at least; the count's scale follows the tile
		return MapMarks.stack(tile, mark, count, 0.62f, 0.4f, 14f, 0.45f * tile / 20f, 0.3f, 1f, 0.5f, preferCount);
	}

	@Test
	void theCountStandsUnderTheMarkInsideTheTile() {
		for (float tile : new float[] {16f, 20f, 26f, 40f}) {
			MapMarks.Stack stack = stack(tile, true, true, false);
			assertTrue(stack.mark() && stack.count(), "both at " + tile);
			float countHeight = MapMarks.LINE * stack.countScale();
			// the mark above, the count below it, both within the tile's margin
			assertTrue(stack.markTop() >= 1f - 0.01f, "the mark's top at " + tile);
			assertTrue(stack.countTop() >= stack.markTop() + stack.markSize(), "the count under the mark at " + tile);
			assertTrue(stack.countTop() + countHeight <= tile - 1f + 0.01f, "the count's bottom inside the tile at " + tile);
			assertTrue(14f * stack.countScale() <= tile - 2f + 0.01f, "the count's width inside the tile at " + tile);
			// a little smaller than alone, never below its least size
			assertTrue(stack.markSize() <= tile * 0.62f + 0.01f && stack.markSize() >= tile * 0.4f);
		}
	}

	@Test
	void theCountScalesWithTheMap() {
		assertTrue(stack(40f, true, true, false).countScale() > stack(20f, true, true, false).countScale());
	}

	@Test
	void aTileTooSmallKeepsTheCheckUnlessTheCountIsAskedFor() {
		// 10 px: the count would have to be smaller than readable, or the mark smaller than 40 %
		MapMarks.Stack check = stack(10f, true, true, false);
		assertTrue(check.mark());
		assertFalse(check.count());
		assertEquals(10f * 0.62f, check.markSize(), 0.001f);
		// a tile where the count fits alone but not with the mark: the setting picks which stays
		MapMarks.Stack either = MapMarks.stack(12f, true, true, 0.62f, 0.5f, 14f, 0.6f, 0.36f, 1f, 0.5f, false);
		assertTrue(either.mark() && !either.count());
		MapMarks.Stack count = MapMarks.stack(12f, true, true, 0.62f, 0.5f, 14f, 0.6f, 0.36f, 1f, 0.5f, true);
		assertTrue(count.count() && !count.mark());
		assertTrue(count.countTop() >= 1f && count.countTop() + MapMarks.LINE * count.countScale() <= 11f + 0.01f);
	}

	@Test
	void withoutAMarkTheCountTakesTheMiddleAndWithoutACountTheMarkDoes() {
		MapMarks.Stack count = stack(20f, false, true, false);
		assertFalse(count.mark());
		assertEquals(10f, count.countTop() + MapMarks.LINE * count.countScale() / 2f, 0.001f);
		MapMarks.Stack mark = stack(20f, true, false, false);
		assertFalse(mark.count());
		assertEquals(10f, mark.markTop() + mark.markSize() / 2f, 0.001f);
	}
}
