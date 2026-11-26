package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The probe that times a room's way onto the dungeon map. */
class MapDelayTest {
	@Test
	void aRoomEnteredIsTimedUntilItShowsWithWhereTheTimeWent() {
		MapDelay delay = new MapDelay();
		assertNull(delay.frame(7, false, 1_000));
		// our reads before the game's map item has the room: nothing yet
		assertNull(delay.read(false, 1_100, 5));
		delay.named(1_150);
		delay.mapItemChanged(1_400);
		String line = delay.read(true, 1_430, 5);
		assertEquals("Dungeon map: the room at tile 7 appeared 430 ms after it was entered: 400 ms until the game's map item changed, 30 ms until our read of it "
			+ "(every 5 frames), drawn in the same frame; its name known after 150 ms", line);
		// told once
		assertNull(delay.read(true, 1_500, 5));
	}

	@Test
	void aRoomAlreadyOnTheMapIsSaidSoAndNotTimed() {
		MapDelay delay = new MapDelay();
		assertTrue(delay.frame(3, true, 500).contains("was on the map before"));
		assertNull(delay.read(true, 600, 5));
		// standing on the same tile says nothing more; leaving the grid neither
		assertNull(delay.frame(3, true, 700));
		assertNull(delay.frame(-1, false, 800));
	}

	@Test
	void aMapItemChangeFromBeforeTheRoomWasEnteredCountsAsNoWait() {
		MapDelay delay = new MapDelay();
		delay.mapItemChanged(900);
		delay.frame(2, false, 1_000);
		String line = delay.read(true, 1_040, 5);
		assertTrue(line.contains("appeared 40 ms after it was entered: 0 ms until the game's map item changed, 40 ms until our read"), line);
		assertTrue(line.endsWith("its name not known yet"), line);
		delay.frame(4, false, 2_000);
		delay.reset();
		assertNull(delay.read(true, 2_100, 5));
	}
}
