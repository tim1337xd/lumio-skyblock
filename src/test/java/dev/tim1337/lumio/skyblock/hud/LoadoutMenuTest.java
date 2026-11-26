package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The Loadouts menu: its title, where its twelve loadouts are, which slots a key may click, the page arrows, key labels and the double-press guard. */
class LoadoutMenuTest {
	@Test
	void titleMatchesOnlyTheLoadoutsMenu() {
		assertTrue(LoadoutMenu.isMenu("(1/2) Loadouts"));
		assertTrue(LoadoutMenu.isMenu("(2/2) Loadouts"));
		assertTrue(LoadoutMenu.isMenu("Loadouts"));
		assertTrue(LoadoutMenu.isMenu("  (1/1) Loadouts "));
		assertFalse(LoadoutMenu.isMenu("(3/2) Loadouts"), "a page past the last is no page");
		assertFalse(LoadoutMenu.isMenu("(0/2) Loadouts"));
		assertFalse(LoadoutMenu.isMenu("(1/3) Armor Sets"), "the wardrobe is another menu");
		assertFalse(LoadoutMenu.isMenu("Loadouts Editor"));
		assertFalse(LoadoutMenu.isMenu("Edit Loadouts"));
		assertFalse(LoadoutMenu.isMenu("SkyBlock Menu"));
		assertFalse(LoadoutMenu.isMenu(""));
		assertFalse(LoadoutMenu.isMenu(null));
	}

	@Test
	void loadoutsAreTheBlockOnTheRight() {
		int[] expected = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
		for (int loadout = 1; loadout <= LoadoutMenu.LOADOUTS; loadout++) {
			assertEquals(expected[loadout - 1], LoadoutMenu.slotOf(loadout), "loadout " + loadout);
			assertEquals(loadout, LoadoutMenu.loadoutAt(expected[loadout - 1]), "back from slot " + expected[loadout - 1]);
		}
		assertEquals(-1, LoadoutMenu.slotOf(0));
		assertEquals(-1, LoadoutMenu.slotOf(13));
		assertEquals(0, LoadoutMenu.loadoutAt(13), "the column left of the block holds no loadout");
		assertEquals(0, LoadoutMenu.loadoutAt(17));
		assertEquals(0, LoadoutMenu.loadoutAt(45));
		for (int loadout = 1; loadout <= LoadoutMenu.LOADOUTS; loadout++) {
			assertTrue(LoadoutMenu.slotOf(loadout) < LoadoutMenu.MENU_SLOTS, "inside the six-row menu");
		}
	}

	@Test
	void onlyLoadoutsThatAreThereAreClicked() {
		assertTrue(LoadoutMenu.usable("Mining Loadout", List.of("Click to equip"), "diamond_pickaxe"));
		assertTrue(LoadoutMenu.usable("Loadout 3", List.of("Unlocked"), "leather_chestplate"), "\"Unlocked\" is no lock");
		assertFalse(LoadoutMenu.usable("", List.of(), "air"), "an empty slot");
		assertFalse(LoadoutMenu.usable("Empty Loadout", List.of(), "gray_dye"));
		assertFalse(LoadoutMenu.usable("Loadout 12", List.of("Locked", "Requires MVP+"), "red_dye"));
		assertFalse(LoadoutMenu.usable("Locked Loadout", List.of(), "barrier"));
		assertFalse(LoadoutMenu.usable("Loadout 9", List.of("Click to unlock"), "gray_dye"));
		assertFalse(LoadoutMenu.usable(" ", List.of(), "black_stained_glass_pane"), "the menu's filler");
		assertFalse(LoadoutMenu.usable("Loadout", List.of(), "barrier"));
	}

	@Test
	void pageArrowsByName() {
		assertEquals(LoadoutMenu.Arrow.NEXT, LoadoutMenu.arrow("Next Page"));
		assertEquals(LoadoutMenu.Arrow.PREVIOUS, LoadoutMenu.arrow("Previous Page"));
		assertEquals(LoadoutMenu.Arrow.NONE, LoadoutMenu.arrow("Go Back"));
		assertEquals(LoadoutMenu.Arrow.NONE, LoadoutMenu.arrow(null));
	}

	@Test
	void labelsFitTheCornerOfASlot() {
		assertEquals("R", LoadoutMenu.label("R"));
		assertEquals("F10", LoadoutMenu.label("F10"));
		assertEquals("MB4", LoadoutMenu.label("Mouse Button 4"));
		assertEquals("LS", LoadoutMenu.label("Left Shift"));
		assertEquals("T+R", LoadoutMenu.label("Tab + R"));
		assertEquals("", LoadoutMenu.label(""));
		assertEquals("", LoadoutMenu.label(null));
		assertTrue(LoadoutMenu.label("Keypad Multiply Something Long").length() <= 3);
	}

	@Test
	void aDoublePressClicksOnce() {
		LoadoutMenu.PressGuard guard = new LoadoutMenu.PressGuard();
		long start = 10_000L;
		assertTrue(guard.press(start), "the first press goes through");
		assertFalse(guard.press(start + 120), "a second press right after is held back");
		assertFalse(guard.press(start + LoadoutMenu.PressGuard.GUARD_MILLIS - 1));
		assertTrue(guard.press(start + LoadoutMenu.PressGuard.GUARD_MILLIS), "a press after the guard goes through");
		assertFalse(guard.press(start + LoadoutMenu.PressGuard.GUARD_MILLIS + 10), "and the guard starts again from it");
		guard.reset();
		assertTrue(guard.press(start + LoadoutMenu.PressGuard.GUARD_MILLIS + 20), "a closed menu forgets the last press");
	}
}
