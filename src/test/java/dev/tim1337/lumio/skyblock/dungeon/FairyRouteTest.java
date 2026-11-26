package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.tim1337.lumio.skyblock.dungeon.DungeonMapData.Door;
import dev.tim1337.lumio.skyblock.dungeon.DungeonMapData.Kind;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

/** The Fairy room on the route to Blood: which open doorway is outlined, from the map's rooms and doors alone. */
class FairyRouteTest {
	private static final int GRID = DungeonMapData.GRID;

	/** A map of 1x1 rooms on the top row and below, made by hand: every tile its own room. */
	private static DungeonMapData map() {
		DungeonMapData map = new DungeonMapData();
		try {
			Field size = DungeonMapData.class.getDeclaredField("roomSize");
			size.setAccessible(true);
			size.setInt(map, 16);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		java.util.Arrays.fill(map.doorEast, Door.NONE);
		java.util.Arrays.fill(map.doorSouth, Door.NONE);
		return map;
	}

	private static void room(DungeonMapData map, int tile, Kind kind) {
		map.kinds[tile] = kind;
		map.roomOf[tile] = tile;
		map.mainTile[tile] = tile;
	}

	/** Entrance (0), a room (1), the Fairy room (2), a room (3), the Blood room (4) in a row; a side room (8) under the Fairy room. */
	private static DungeonMapData row(Door afterFairy, Door beforeBlood) {
		DungeonMapData map = map();
		room(map, 0, Kind.ENTRANCE);
		room(map, 1, Kind.NORMAL);
		room(map, 2, Kind.FAIRY);
		room(map, 3, Kind.NORMAL);
		room(map, 2 + GRID, Kind.NORMAL);
		// the wither door behind the player is open: the map draws it as a plain doorway
		map.doorEast[0] = Door.NORMAL;
		map.doorEast[1] = Door.FAIRY;
		map.doorEast[2] = afterFairy;
		map.doorSouth[2] = Door.FAIRY;
		if (beforeBlood != Door.NONE) {
			room(map, 4, Kind.BLOOD);
			map.doorEast[3] = beforeBlood;
		}
		return map;
	}

	@Test
	void theFairyRoomIsNext() {
		DungeonMapData map = row(Door.FAIRY, Door.BLOOD);
		// in the room before it: the doorway into the Fairy room
		assertEquals(new FairyRoute.Doorway(1, true), FairyRoute.doorway(map, 1));
		// rooms further away show nothing
		assertNull(FairyRoute.doorway(map, 0));
	}

	@Test
	void standingInTheFairyRoom() {
		DungeonMapData map = row(Door.FAIRY, Door.BLOOD);
		// the doorway that leads on toward Blood: not the one back to the entrance, not the side room's
		assertEquals(new FairyRoute.Doorway(2, true), FairyRoute.doorway(map, 2));
		// the same with a wither door ahead and the Blood room not on the map yet
		DungeonMapData wither = row(Door.FAIRY, Door.NONE);
		room(wither, 4, Kind.UNEXPLORED);
		wither.doorEast[3] = Door.WITHER;
		assertEquals(new FairyRoute.Doorway(2, true), FairyRoute.doorway(wither, 2));
	}

	@Test
	void pastTheFairyRoomNothingPointsBack() {
		DungeonMapData map = row(Door.FAIRY, Door.BLOOD);
		// in the room after it, or in the side room: the Fairy room is not next on the route to Blood from there
		assertNull(FairyRoute.doorway(map, 3));
		assertNull(FairyRoute.doorway(map, 2 + GRID));
	}

	@Test
	void theRoutePastTheFairyRoomIsNotKnownYet() {
		// the rooms after the Fairy room are on the map, but no wither door, blood door or Blood room beyond any doorway: nothing, rather than a guess
		DungeonMapData map = row(Door.FAIRY, Door.NONE);
		assertNull(FairyRoute.doorway(map, 2));
		assertNull(FairyRoute.doorway(map, 1));
		// two doorways with the route's marks beyond them: not known which
		DungeonMapData two = row(Door.FAIRY, Door.BLOOD);
		room(two, 2 + 2 * GRID, Kind.UNEXPLORED);
		two.doorSouth[2 + GRID] = Door.WITHER;
		assertNull(FairyRoute.doorway(two, 2));
	}

	@Test
	void theFairyRoomDirectlyBeforeBlood() {
		// Entrance, a room, the Fairy room, then the Blood room behind the blood door
		DungeonMapData map = map();
		room(map, 0, Kind.ENTRANCE);
		room(map, 1, Kind.NORMAL);
		room(map, 2, Kind.FAIRY);
		room(map, 3, Kind.BLOOD);
		map.doorEast[0] = Door.NORMAL;
		map.doorEast[1] = Door.FAIRY;
		map.doorEast[2] = Door.BLOOD;
		// before it: the doorway into the Fairy room
		assertEquals(new FairyRoute.Doorway(1, true), FairyRoute.doorway(map, 1));
		// in it: the blood door is the door highlight's own, no Fairy doorway on top
		assertEquals(new FairyRoute.Doorway(2, true), FairyRoute.onward(map, 2));
		assertNull(FairyRoute.doorway(map, 2));
	}

	@Test
	void aDoorwayTheMapDoesNotDrawInPinkCountsToo() {
		// the case that failed in real runs, as far as the code can tell: the map draws the Fairy room's doorways as plain ones
		DungeonMapData map = row(Door.NORMAL, Door.BLOOD);
		map.doorEast[1] = Door.NORMAL;
		map.doorSouth[2] = Door.NORMAL;
		assertEquals(new FairyRoute.Doorway(1, true), FairyRoute.doorway(map, 1), "into the Fairy room");
		assertEquals(new FairyRoute.Doorway(2, true), FairyRoute.doorway(map, 2), "from it onward");
		assertEquals("", FairyRoute.why(map, 2));
		// a wither door onward is the door highlight's own: no second outline
		DungeonMapData wither = row(Door.WITHER, Door.BLOOD);
		assertNull(FairyRoute.doorway(wither, 2));
	}

	@Test
	void whyNothingIsOutlinedIsSaid() {
		DungeonMapData unknown = row(Door.FAIRY, Door.NONE);
		org.junit.jupiter.api.Assertions.assertTrue(FairyRoute.why(unknown, 2).contains("toward Blood"), FairyRoute.why(unknown, 2));
		DungeonMapData none = map();
		room(none, 0, Kind.ENTRANCE);
		room(none, 1, Kind.NORMAL);
		none.doorEast[0] = Door.NORMAL;
		org.junit.jupiter.api.Assertions.assertTrue(FairyRoute.why(none, 1).contains("no Fairy room"), FairyRoute.why(none, 1));
	}

	@Test
	void withoutAMapOrARoomThereIsNothing() {
		assertNull(FairyRoute.doorway(new DungeonMapData(), 2));
		DungeonMapData map = row(Door.FAIRY, Door.BLOOD);
		assertNull(FairyRoute.doorway(map, -1));
		assertNull(FairyRoute.doorway(map, 20));
	}
}
