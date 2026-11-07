package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The storage window is as wide as its columns and as tall as its content. */
class StorageLayoutTest {
	@Test
	void theSettingIsTheColumnsWhileTheyFit() {
		assertEquals(3, StorageLayout.columns(3, 1912));
		assertEquals(8, StorageLayout.columns(8, 3832));
	}

	@Test
	void aNarrowScreenTakesFewer() {
		assertEquals(2, StorageLayout.columns(5, 400));
		assertEquals(1, StorageLayout.columns(5, 150));
	}

	@Test
	void unsetFitsUpToTheUsualMost() {
		assertEquals(3, StorageLayout.columns(0, 3832));
		assertEquals(2, StorageLayout.columns(0, 400));
	}

	@Test
	void theWindowIsOnlyAsWideAsItsCards() {
		assertEquals(178 + 20, StorageLayout.windowWidth(1));
		assertEquals(3 * 178 + 2 * 8 + 20, StorageLayout.windowWidth(3));
	}

	@Test
	void theHeightFitsTheContentUpToTheMost() {
		assertEquals(150, StorageLayout.windowHeight(60, 90, 100, 400));
		assertEquals(400, StorageLayout.windowHeight(60, 900, 100, 400));
		assertEquals(100, StorageLayout.windowHeight(60, 10, 100, 400));
	}

	private static final int INVENTORY_WIDTH = 178;
	private static final int INVENTORY_HEIGHT = 105;
	private static final int GAP_BESIDE = 8;
	private static final int EDGE = 4;

	@Test
	void theInventoryIsInTheMiddle() {
		assertEquals((960 - 178) / 2, StorageLayout.inventoryX(960, 178));
		assertEquals(StorageLayout.roomBeside(960, 178, 8, 4), 960 - (960 - 178) / 2 - 178 - 8 - 4);
	}

	@Test
	void thePanelFitsBesideTheInventoryAtEveryWidth() {
		for (int width = 320; width <= 3840; width++) {
			int room = StorageLayout.roomBeside(width, INVENTORY_WIDTH, GAP_BESIDE, EDGE);
			StorageLayout.ShortcutGrid grid = StorageLayout.shortcutGrid(room);
			assertNotNull(grid, "the smallest window the game allows has a panel too: " + width);
			int inventoryRight = StorageLayout.inventoryX(width, INVENTORY_WIDTH) + INVENTORY_WIDTH;
			assertTrue(inventoryRight + GAP_BESIDE + grid.width() <= width - EDGE, "off the screen at " + width + ": " + grid);
			assertTrue(grid.height() + SettingsButton.SIZE < INVENTORY_HEIGHT, "the settings button has its own room under the panel: " + grid);
			assertTrue(SettingsButton.fits(inventoryRight, width), "the settings button fits at " + width);
		}
	}

	@Test
	void slotsAreAsInTheInventoryWhereThereIsRoomAndSmallerBeforeOverlapping() {
		assertEquals(18, StorageLayout.shortcutGrid(StorageLayout.roomBeside(960, INVENTORY_WIDTH, GAP_BESIDE, EDGE)).slot(), "1080p at GUI scale 2");
		assertEquals(13, StorageLayout.shortcutGrid(StorageLayout.roomBeside(480, INVENTORY_WIDTH, GAP_BESIDE, EDGE)).slot(), "1080p at Auto");
		assertEquals(18, StorageLayout.shortcutGrid(StorageLayout.roomBeside(1920, INVENTORY_WIDTH, GAP_BESIDE, EDGE)).slot(), "4K at GUI scale 2");
		assertEquals(10, StorageLayout.shortcutGrid(StorageLayout.roomBeside(427, INVENTORY_WIDTH, GAP_BESIDE, EDGE)).slot(), "4K at Auto");
		assertNull(StorageLayout.shortcutGrid(2 * StorageLayout.SHORTCUT_PAD + 9 * 4 - 1), "no room at all: no panel, rather than over something");
	}

	@Test
	void everyRowHasItsPlaceAndTheBackpacksStandApart() {
		StorageLayout.ShortcutGrid grid = new StorageLayout.ShortcutGrid(18);
		assertEquals(0, grid.rowY(0));
		assertTrue(grid.rowY(1) > 18, "a little apart from the ender chest's row");
		assertEquals(grid.rowY(1) + 18, grid.rowY(2));
		assertTrue(StorageLayout.SHORTCUT_TOP + grid.rowY(2) + 18 <= grid.height(), "inside the panel");
	}
}
