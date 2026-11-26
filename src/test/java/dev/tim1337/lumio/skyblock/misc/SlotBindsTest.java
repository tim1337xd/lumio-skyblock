package dev.tim1337.lumio.skyblock.misc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Slot binds: binding over two presses, the slots that can be bound, the menu's numbers both ways, the one swap click, the named sets with their
 * areas, and the config with the six numbered sets of before taken over.
 */
class SlotBindsTest {
	/** Inventory slots: hotbar 0–8, inventory 9–35. */
	private static final int HOTBAR_3 = 2;
	private static final int INVENTORY_12 = 20;
	private static final int INVENTORY_1 = 9;

	@Test
	void bindOverTwoPressesAndUnbind() {
		SlotBinds binds = new SlotBinds();
		assertEquals(SlotBinds.Result.STARTED, binds.press(INVENTORY_12, false));
		assertEquals(INVENTORY_12, binds.pending(), "the first slot is kept for the second press");
		assertEquals(SlotBinds.Result.BOUND, binds.press(HOTBAR_3, false));
		assertEquals(HOTBAR_3, binds.partner(INVENTORY_12));
		assertEquals(INVENTORY_12, binds.partner(HOTBAR_3));
		assertEquals(SlotBinds.Result.UNBOUND, binds.press(HOTBAR_3, false), "the key on a bound slot removes its bind");
		assertEquals(-1, binds.partner(INVENTORY_12));
	}

	@Test
	void theRules() {
		SlotBinds binds = new SlotBinds();
		binds.press(INVENTORY_12, false);
		assertEquals(SlotBinds.Result.SAME_SLOT, binds.press(INVENTORY_12, false));
		binds.press(INVENTORY_12, false);
		assertEquals(SlotBinds.Result.NO_HOTBAR, binds.press(INVENTORY_1, false));
		binds.press(INVENTORY_12, false);
		assertEquals(SlotBinds.Result.NOT_BINDABLE, binds.press(8, true), "the SkyBlock Menu cannot be bound");
		assertEquals(-1, binds.pending(), "and the bind being made is dropped");
		assertEquals(SlotBinds.Result.NOT_BINDABLE, binds.press(36, false), "armor and the off hand cannot be bound");
		assertEquals(SlotBinds.Result.NOT_BINDABLE, binds.press(-1, false));
	}

	@Test
	void menuSlotsBothWays() {
		// the inventory screen: 0 result, 1–4 crafting, 5–8 armor, 9–35 inventory, 36–44 hotbar, 45 off hand
		for (int menu = 0; menu <= 45; menu++) {
			int slot = SlotBinds.fromMenuSlot(menu);
			if (menu >= 9 && menu <= 44) {
				assertEquals(menu, SlotBinds.menuSlot(slot), "menu slot " + menu + " back and forth");
			} else {
				assertEquals(-1, slot, "menu slot " + menu + " cannot be bound");
			}
		}
		assertEquals(36, SlotBinds.menuSlot(0));
		assertEquals(44, SlotBinds.menuSlot(8));
		assertEquals(9, SlotBinds.menuSlot(9));
		assertEquals(35, SlotBinds.menuSlot(35));
	}

	@Test
	void oneSwapClickFromEitherSide() {
		SlotBinds binds = new SlotBinds();
		binds.press(INVENTORY_12, false);
		binds.press(HOTBAR_3, false);
		// one click: the inventory slot, swapped with hotbar number 2 (the third), whichever of the two is shift-clicked
		assertArrayEquals(new int[] {INVENTORY_12, HOTBAR_3}, binds.swap(INVENTORY_12));
		assertArrayEquals(new int[] {INVENTORY_12, HOTBAR_3}, binds.swap(HOTBAR_3));
		assertNull(binds.swap(INVENTORY_1), "no bind: the game's own shift-click");
	}

	@Test
	void namedSetsAndAreas() {
		SlotBinds binds = new SlotBinds();
		assertEquals("Default", binds.name(0));
		int dungeons = binds.add("Dungeons");
		binds.setArea(dungeons, SlotBinds.Area.DUNGEONS);
		binds.press(INVENTORY_12, false);
		binds.press(HOTBAR_3, false);
		assertTrue(binds.areaNow(SlotBinds.Area.DUNGEONS), "entering a dungeon uses its set");
		assertEquals(dungeons, binds.active());
		assertEquals(-1, binds.partner(INVENTORY_12), "the Default set's bind is not in the dungeon set");
		binds.choose(0);
		assertFalse(binds.areaNow(SlotBinds.Area.DUNGEONS), "the player's choice stays while the area is the same");
		assertEquals(0, binds.active());
		binds.areaNow(SlotBinds.Area.EVERYWHERE);
		binds.areaNow(SlotBinds.Area.DUNGEONS);
		assertEquals(dungeons, binds.active(), "until the area changes");
		binds.rename(dungeons, "  Catacombs  ");
		assertEquals("Catacombs", binds.name(dungeons));
		assertTrue(binds.delete(dungeons));
		assertFalse(binds.delete(0), "the last set stays");
	}

	@Test
	void savedAndLoaded() {
		SlotBinds binds = new SlotBinds();
		binds.press(INVENTORY_12, false);
		binds.press(HOTBAR_3, false);
		int mining = binds.add("Mining");
		binds.setArea(mining, SlotBinds.Area.MINING);
		binds.choose(mining);
		JsonObject json = new JsonObject();
		SlotBindsJson.write(binds, json);
		SlotBinds again = new SlotBinds();
		SlotBindsJson.read(json, again);
		assertEquals(2, again.count());
		assertEquals(HOTBAR_3, again.partner(0, INVENTORY_12));
		assertEquals(SlotBinds.Area.MINING, again.area(1));
		assertEquals(mining, again.active());
	}

	@Test
	void theSixNumberedSetsOfBefore() {
		// set 2 had binds (one of them of the helmet, which can no longer be bound), set 4 too; the others were empty. Set 4 was in use.
		JsonObject json = JsonParser.parseString("{\"bind_set\":\"FOUR\",\"binds\":[[],[[2,20],[0,39]],[],[[5,30]],[],[]]}").getAsJsonObject();
		SlotBindsJson.migrate(json);
		SlotBinds binds = new SlotBinds();
		SlotBindsJson.read(json, binds);
		assertEquals(2, binds.count(), "empty sets are dropped");
		assertEquals("Set 2", binds.name(0));
		assertEquals("Set 4", binds.name(1));
		assertEquals(20, binds.partner(0, 2));
		assertEquals(-1, binds.partner(0, 0), "the helmet's bind is left out");
		assertEquals(1, binds.active(), "the set in use stays in use");
		assertFalse(json.has("binds"));
	}
}
