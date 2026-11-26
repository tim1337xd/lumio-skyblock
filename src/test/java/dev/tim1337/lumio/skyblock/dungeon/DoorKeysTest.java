package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The party's door keys from the game's lines, and the door colors that follow them at once. */
class DoorKeysTest {
	private static final int READY = 0x30D158;
	private static final int NONE = 0xFF453A;

	private static int witherDoor(DoorKeys keys) {
		return DungeonDoorsFeature.doorColor(false, keys.wither(), keys.blood(), READY, NONE);
	}

	private static int bloodDoor(DoorKeys keys) {
		return DungeonDoorsFeature.doorColor(true, keys.wither(), keys.blood(), READY, NONE);
	}

	@Test
	void aWitherKeyGainedAndUsed() {
		DoorKeys keys = new DoorKeys();
		assertEquals(NONE, witherDoor(keys), "no key: red");
		keys.picked(true);
		assertEquals(READY, witherDoor(keys), "picked up: green at once");
		assertEquals(NONE, bloodDoor(keys), "a wither key opens no blood door");
		keys.opened(true);
		assertEquals(NONE, witherDoor(keys), "used: red again at once");
	}

	@Test
	void twoWitherKeys() {
		DoorKeys keys = new DoorKeys();
		keys.picked(true);
		keys.picked(true);
		keys.opened(true);
		assertTrue(keys.wither(), "one still there");
		keys.opened(true);
		keys.opened(true);
		assertFalse(keys.wither(), "never below none");
	}

	@Test
	void theBloodKey() {
		DoorKeys keys = new DoorKeys();
		keys.picked(false);
		assertEquals(READY, bloodDoor(keys));
		assertEquals(NONE, witherDoor(keys));
		keys.opened(false);
		assertEquals(NONE, bloodDoor(keys));
	}

	@Test
	void theScoreboardLineSetsBoth() {
		DoorKeys keys = new DoorKeys();
		assertTrue(keys.scoreboard("Keys: ■ ✓ ■ 1x"));
		assertTrue(keys.blood());
		assertTrue(keys.wither());
		assertTrue(keys.scoreboard("Keys: ■ ✗ ■ 0x"));
		assertFalse(keys.blood());
		assertFalse(keys.wither());
		assertFalse(keys.scoreboard("Cleared: 42% (110)"), "another line changes nothing");
	}

	@Test
	void theChatLinesAsTheGameWritesThem() {
		assertTrue(DungeonParse.KEY_PICKED.matcher("[MAGE] Kiwi has obtained Wither Key!").matches());
		assertTrue(DungeonParse.KEY_PICKED.matcher("A Blood Key was picked up!").matches());
		assertTrue(DungeonParse.DOOR_OPENED.matcher("Kiwi opened a WITHER door!").matches());
		assertTrue(DungeonParse.DOOR_OPENED.matcher("The BLOOD DOOR has been opened!").matches());
	}
}
