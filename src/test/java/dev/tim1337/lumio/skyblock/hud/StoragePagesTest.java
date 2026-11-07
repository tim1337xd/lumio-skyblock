package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The pages of the storage, and what each looks like on the panel. */
class StoragePagesTest {
	@Test
	void thePagesAreInOrderWithTheirNames() {
		assertEquals("e1", StoragePages.key(0));
		assertEquals("e9", StoragePages.key(8));
		assertEquals("b1", StoragePages.key(9));
		assertEquals("b18", StoragePages.key(26));
		assertEquals("Ender chest 9", StoragePages.shortName(8));
		assertEquals("Backpack 18", StoragePages.shortName(26));
		assertEquals(1, StoragePages.number(9));
		assertEquals(18, StoragePages.number(26));
	}

	@Test
	void theyHaveTheirPlacesInTheGrid() {
		assertEquals(0, StoragePages.row(8));
		assertEquals(8, StoragePages.column(8));
		assertEquals(1, StoragePages.row(9));
		assertEquals(0, StoragePages.column(9));
		assertEquals(1, StoragePages.row(17));
		assertEquals(8, StoragePages.column(17));
		assertEquals(2, StoragePages.row(18));
		assertEquals(2, StoragePages.row(26));
		assertEquals(8, StoragePages.column(26));
	}

	@Test
	void theOpenPageGoesFirst() {
		assertEquals(StoragePages.State.OPEN, StoragePages.state(true, false, "Locked Page"));
	}

	@Test
	void whatTheMenuSaysDecides() {
		assertEquals(StoragePages.State.LOCKED, StoragePages.state(false, true, "Locked Backpack Slot 4"));
		assertEquals(StoragePages.State.LOCKED, StoragePages.state(false, false, "Empty Backpack Slot 4"));
		assertEquals(StoragePages.State.READY, StoragePages.state(false, false, "Ender Chest Page 3"));
	}

	@Test
	void withoutTheMenuAPageSeenCanBeOpened() {
		assertEquals(StoragePages.State.READY, StoragePages.state(false, true, null));
		assertEquals(StoragePages.State.UNSEEN, StoragePages.state(false, false, null));
	}

	@Test
	void aBackpackHasTheColorOfItsSize() {
		assertNotEquals(StoragePages.backpackRgb("Jumbo Backpack (Slot #1)"), StoragePages.backpackRgb("Small Backpack (Slot #2)"));
		assertNotEquals(StoragePages.backpackRgb("Greater Backpack"), StoragePages.backpackRgb("Large Backpack"));
		assertNotEquals(StoragePages.backpackRgb("Medium Backpack"), StoragePages.UNKNOWN_RGB);
		assertEquals(StoragePages.UNKNOWN_RGB, StoragePages.backpackRgb(null));
		assertEquals(StoragePages.UNKNOWN_RGB, StoragePages.backpackRgb("Backpack"));
	}
}
