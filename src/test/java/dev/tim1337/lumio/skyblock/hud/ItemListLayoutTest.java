package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.tim1337.lumio.skyblock.hud.ItemListLayout.Places;

/** The bar always shows, and the list and the item's window fit, at every window size and GUI scale. */
class ItemListLayoutTest {
	private static final int[][] WINDOWS = {{1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2000, 1140}, {2560, 1440}, {3440, 1440}, {3840, 2160}};
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int CHEST_HEIGHT = 222;
	private static final int BAR_WIDTH = 220;
	private static final int BAR_HEIGHT = 16;
	/** A wide window: an inventory with the recipe book open. */
	private static final int WIDE_WINDOW = 323;
	private static final int AUTO = 0;

	/** @return the GUI scale the game uses for a chosen one: never so large that less than 320 by 240 is left, and Auto is the largest */
	private static int scaleFor(int chosen, int width, int height) {
		int scale = 1;
		while ((chosen == AUTO || scale < chosen) && scale < width && scale < height && width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
			scale++;
		}
		return scale;
	}

	private static int scaled(int pixels, int scale) {
		return (int) Math.ceil(pixels / (double) scale);
	}

	private static int[] centered(int screenWidth, int screenHeight, int width, int height) {
		return new int[]{(screenWidth - width) / 2, (screenHeight - height) / 2, width, height};
	}

	private interface Check {
		void at(int width, int height, int[] window, String what);
	}

	private static void everywhere(Check check) {
		for (int[] size : WINDOWS) {
			for (int chosen = AUTO; chosen <= 6; chosen++) {
				int scale = scaleFor(chosen, size[0], size[1]);
				int width = scaled(size[0], scale);
				int height = scaled(size[1], scale);
				for (int[] box : new int[][]{{INVENTORY_WIDTH, INVENTORY_HEIGHT}, {INVENTORY_WIDTH, CHEST_HEIGHT}}) {
					check.at(width, height, centered(width, height, box[0], box[1]), size[0] + "x" + size[1] + " gui " + chosen + " window " + box[1]);
				}
				// the inventory with the recipe book open, off center
				check.at(width, height, new int[]{Math.max(0, (width - WIDE_WINDOW) / 2) + 77, (height - INVENTORY_HEIGHT) / 2, INVENTORY_WIDTH, INVENTORY_HEIGHT}, size[0] + "x" + size[1] + " gui " + chosen + " book");
			}
		}
	}

	private static void onScreen(int[] box, int width, int height, String what) {
		assertTrue(box[0] >= 0 && box[1] >= 0 && box[0] + box[2] <= width && box[1] + box[3] <= height, what + ": " + java.util.Arrays.toString(box) + " on " + width + "x" + height);
	}

	/** Six columns of items at the usual size, with the area's padding. */
	private static final int LEAST = 176;

	private static Places at(int width, int height, int[] window) {
		return ItemListLayout.places(width, height, window, BAR_WIDTH, BAR_HEIGHT, LEAST);
	}

	private static boolean overlaps(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	@Test
	void theBarIsAlwaysThereAndOnTheScreen() {
		everywhere((width, height, window, what) -> onScreen(at(width, height, window).bar(), width, height, what));
	}

	@Test
	void theAreaHasRoomEverywhereAndKeepsOffTheWindowAndTheBar() {
		everywhere((width, height, window, what) -> {
			Places places = at(width, height, window);
			assertTrue(places.hasList(), what + ": " + java.util.Arrays.toString(places.list()));
			onScreen(places.list(), width, height, what);
			assertFalse(overlaps(places.list(), window), what);
			assertFalse(overlaps(places.list(), places.bar()), what);
		});
	}

	@Test
	void besideTheInventoryTheAreaReachesTheRightEdge() {
		// 1080p at GUI 2 with Lumio's inventory moved left, as on the player's screen: about 19 columns of 26
		int[] window = {265, 300, INVENTORY_WIDTH, INVENTORY_HEIGHT};
		Places places = at(960, 540, window);
		assertEquals(window[0] + window[2] + ItemListLayout.AREA_GAP, places.list()[0]);
		assertEquals(960 - ItemListLayout.RIGHT_MARGIN, places.list()[0] + places.list()[2]);
		int columns = (places.list()[2] - 2 * ItemSearchPanel.PAD + 6) / 26;
		assertTrue(columns >= 17 && columns <= 20, "columns " + columns);
		// the bar is centered under the area
		assertEquals(places.list()[0] + places.list()[2] / 2, places.bar()[0] + places.bar()[2] / 2, 1);
	}

	@Test
	void aSmallWindowPutsTheAreaBelowOrAboveTheInventory() {
		int width = 320;
		int height = 240;
		int[] window = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT - 60);
		Places places = at(width, height, window);
		assertTrue(places.list()[1] >= window[1] + window[3] || places.list()[1] + places.list()[3] <= window[1], java.util.Arrays.toString(places.list()));
		assertEquals(width - 2 * ItemListLayout.MARGIN, places.list()[2]);
	}

	@Test
	void theItemWindowFitsAndKeepsOffTheList() {
		everywhere((width, height, window, what) -> {
			Places places = at(width, height, window);
			int[] box = ItemListLayout.detail(width, height, window, places.list()[0]);
			onScreen(box, width, height, what);
			assertTrue(box[2] >= ItemListLayout.DETAIL_LEAST_WIDTH, what);
		});
	}

	@Test
	void theItemWindowIsCenteredOnTheInventoryWithRoom() {
		int[] window = centered(1920, 1080, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		int[] box = ItemListLayout.detail(1920, 1080, window, 1920);
		assertEquals(window[0] + window[2] / 2, box[0] + box[2] / 2);
		assertEquals(window[1] + window[3] / 2, box[1] + box[3] / 2);
	}

	@Test
	void theGameScalesAsAssumed() {
		assertEquals(3, scaleFor(AUTO, 1366, 768));
		assertEquals(4, scaleFor(AUTO, 1920, 1080));
		assertEquals(4, scaleFor(6, 1920, 1080));
		assertEquals(6, scaleFor(6, 3840, 2160));
		assertEquals(9, scaleFor(AUTO, 3840, 2160));
	}
}
