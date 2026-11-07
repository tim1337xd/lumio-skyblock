package dev.tim1337.lumio.skyblock.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

class ProtectionRulesTest {
	private static final ProtectionRules.Switches ALL_ON = new ProtectionRules.Switches(true, true, true, true, true, true, false);

	private static ProtectionRules.Click onItem(boolean inMenuPart) {
		return new ProtectionRules.Click(true, false, false, false, false, false, inMenuPart);
	}

	private static ProtectionRules.Click carried(boolean inMenuPart) {
		return new ProtectionRules.Click(false, true, false, false, false, false, inMenuPart);
	}

	@Test
	void titlesNameTheirMenus() {
		assertEquals(ProtectionRules.Menu.TRADE, ProtectionRules.menuOf("You                  Someone", false));
		assertEquals(ProtectionRules.Menu.SALVAGE, ProtectionRules.menuOf("Salvage Items", false));
		assertEquals(ProtectionRules.Menu.AUCTION, ProtectionRules.menuOf("Co-op Auction House", false));
		assertEquals(ProtectionRules.Menu.AUCTION, ProtectionRules.menuOf("Create BIN Auction", false));
		assertEquals(ProtectionRules.Menu.DUNGEON_CHEST, ProtectionRules.menuOf("Chest", true));
		assertEquals(ProtectionRules.Menu.OTHER, ProtectionRules.menuOf("Chest", false), "a chest outside of a dungeon is the player's own");
		assertEquals(ProtectionRules.Menu.OTHER, ProtectionRules.menuOf("Storage", false));
	}

	@Test
	void movingItemsAroundIsNeverStopped() {
		for (boolean part : new boolean[]{true, false}) {
			assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(onItem(part), ProtectionRules.Menu.OTHER, ALL_ON, false));
			assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(carried(part), ProtectionRules.Menu.OTHER, ALL_ON, false));
		}
	}

	@Test
	void droppingIsStoppedInEveryWay() {
		ProtectionRules.Click key = new ProtectionRules.Click(true, false, false, false, true, false, false);
		ProtectionRules.Click outside = new ProtectionRules.Click(false, true, false, true, false, false, false);
		assertEquals(ProtectionRules.Blocked.DROP, ProtectionRules.check(key, ProtectionRules.Menu.OTHER, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.DROP, ProtectionRules.check(outside, ProtectionRules.Menu.OTHER, ALL_ON, false));
		ProtectionRules.Switches inDungeons = new ProtectionRules.Switches(true, true, true, true, true, true, true);
		assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(key, ProtectionRules.Menu.OTHER, inDungeons, true), "its own switch allows it there");
		assertEquals(ProtectionRules.Blocked.DROP, ProtectionRules.check(key, ProtectionRules.Menu.OTHER, inDungeons, false), "and only there");
	}

	@Test
	void menusThatTakeItemsStopEveryTouch() {
		assertEquals(ProtectionRules.Blocked.SELL, ProtectionRules.check(onItem(false), ProtectionRules.Menu.SHOP, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.SALVAGE, ProtectionRules.check(onItem(false), ProtectionRules.Menu.SALVAGE, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.TRADE, ProtectionRules.check(carried(true), ProtectionRules.Menu.TRADE, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.AUCTION, ProtectionRules.check(new ProtectionRules.Click(false, false, true, false, false, false, true), ProtectionRules.Menu.AUCTION, ALL_ON, false));
		ProtectionRules.Switches none = new ProtectionRules.Switches(true, false, false, false, false, false, false);
		assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(onItem(false), ProtectionRules.Menu.SHOP, none, false), "each has its switch");
	}

	@Test
	void aDungeonChestOnlyStopsWhatGoesIntoIt() {
		assertEquals(ProtectionRules.Blocked.SWAP, ProtectionRules.check(carried(true), ProtectionRules.Menu.DUNGEON_CHEST, ALL_ON, true));
		assertEquals(ProtectionRules.Blocked.SWAP, ProtectionRules.check(new ProtectionRules.Click(true, false, false, false, false, true, false), ProtectionRules.Menu.DUNGEON_CHEST, ALL_ON, true));
		assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(onItem(false), ProtectionRules.Menu.DUNGEON_CHEST, ALL_ON, true), "picked up in the inventory");
		assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(carried(false), ProtectionRules.Menu.DUNGEON_CHEST, ALL_ON, true), "put down in the inventory");
	}

	@Test
	void onlyTheFormsYetToEvolve() {
		assertTrue(ProtectionRules.evolving("DARK_CACAO_TRUFFLE"));
		assertFalse(ProtectionRules.evolving("REFINED_DARK_CACAO_TRUFFLE"));
		assertFalse(ProtectionRules.evolving(null));
	}

	@Test
	void theSecondPressHasItsTime() {
		assertTrue(ProtectionRules.secondPress(1000L, 3000L));
		assertFalse(ProtectionRules.secondPress(1000L, 3001L));
		assertFalse(ProtectionRules.secondPress(0L, 1000L));
	}

	@Test
	void itemsAreKeptPerProfileAndFoundOverAll() {
		ProtectedItems items = new ProtectedItems();
		assertTrue(items.isEmpty());
		items.add("apple", "one");
		items.add(null, "two");
		assertTrue(items.has("one") && items.has("two"));
		ProtectedItems read = ProtectedItems.fromJson(JsonParser.parseString(items.toJson().toString()).getAsJsonObject());
		assertTrue(read.has("one") && read.has("two"));
		read.remove("one");
		assertFalse(read.has("one"));
		assertTrue(read.has("two"));
	}

	@Test
	void aBrokenFileGivesWhatIsRight() {
		ProtectedItems read = ProtectedItems.fromJson(JsonParser.parseString("{\"profiles\":{\"a\":[\"good\",3,\"\",null],\"b\":\"no\"}}").getAsJsonObject());
		assertTrue(read.has("good"));
		assertFalse(read.has("3"));
		assertFalse(ProtectedItems.fromJson(JsonParser.parseString("{\"profiles\":1}").getAsJsonObject()).has("good"));
	}
}
