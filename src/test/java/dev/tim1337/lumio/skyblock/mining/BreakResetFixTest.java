package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When an update of a slot counts as one of the held item. */
class BreakResetFixTest {
	private static final int HELD_SLOT = 2;
	private static final int ITS_MENU_SLOT = 38;

	@Test
	void anUpdateOfTheHeldSlotCounts() {
		assertTrue(BreakResetFixFeature.applies(true, true, false, ITS_MENU_SLOT, HELD_SLOT));
		assertTrue(BreakResetFixFeature.applies(true, true, false, 36, 0), "the first of the hotbar");
		assertTrue(BreakResetFixFeature.applies(true, true, false, 44, 8), "the last of the hotbar");
	}

	@Test
	void anotherSlotDoesNot() {
		assertFalse(BreakResetFixFeature.applies(true, true, false, 39, HELD_SLOT), "another of the hotbar");
		assertFalse(BreakResetFixFeature.applies(true, true, false, 9, HELD_SLOT), "the inventory");
		assertFalse(BreakResetFixFeature.applies(true, true, false, 45, HELD_SLOT), "the off hand");
		assertFalse(BreakResetFixFeature.applies(true, true, false, HELD_SLOT, HELD_SLOT), "the slot's number in the hotbar is not the menu's");
	}

	@Test
	void aScreenOpenDoesNot() {
		assertFalse(BreakResetFixFeature.applies(true, true, true, ITS_MENU_SLOT, HELD_SLOT));
	}

	@Test
	void theInventoryPacketCountsByItsOwnSlots() {
		assertTrue(BreakResetFixFeature.appliesToInventory(true, true, false, HELD_SLOT, HELD_SLOT));
		assertFalse(BreakResetFixFeature.appliesToInventory(true, true, false, ITS_MENU_SLOT, HELD_SLOT), "not the menu's numbers");
		assertFalse(BreakResetFixFeature.appliesToInventory(true, true, false, 3, HELD_SLOT), "another of the hotbar");
	}

	@Test
	void theSwitchOffDoesNot() {
		assertFalse(BreakResetFixFeature.applies(false, true, false, ITS_MENU_SLOT, HELD_SLOT));
	}

	@Test
	void offSkyBlockItDoesNot() {
		assertFalse(BreakResetFixFeature.applies(true, false, false, ITS_MENU_SLOT, HELD_SLOT));
	}
}
