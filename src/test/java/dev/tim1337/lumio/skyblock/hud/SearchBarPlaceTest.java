package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item search field at the bottom of the screen: in the middle of the free space right of the window, centered on the screen where that space
 * is too narrow, just below the window where it would cover it there, and back beside the window when there is no room below either. Checked with
 * the inventory and the large chest at GUI scale 2 to 4, in 1920x1080 and 1280x720, and with a wide window.
 */
class SearchBarPlaceTest {
	private static final int WIDTH = 220;
	private static final int HEIGHT = 20;
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int CHEST_HEIGHT = 222;

	/** @return a window centered on the screen, as the game places it */
	private static int[] centered(int screenWidth, int screenHeight, int height) {
		return new int[]{(screenWidth - INVENTORY_WIDTH) / 2, (screenHeight - height) / 2, INVENTORY_WIDTH, height};
	}

	private static int[] place(int screenWidth, int screenHeight, int[] window) {
		return SearchBarPlace.place(screenWidth, screenHeight, window, Math.min(WIDTH, screenWidth - 16), HEIGHT);
	}

	@Test
	void inTheMiddleOfTheFreeSpaceBesideTheWindow() {
		// 1920x1080 at GUI scale 2 (960x540), inventory and large chest: the window ends at 568, the free space is 568 to 960
		int y = 540 - SearchBarPlace.BOTTOM_MARGIN - HEIGHT;
		for (int height : new int[]{INVENTORY_HEIGHT, CHEST_HEIGHT}) {
			int[] bar = place(960, 540, centered(960, 540, height));
			assertArrayEquals(new int[]{568 + (392 - WIDTH) / 2, y, WIDTH, HEIGHT}, bar);
		}
		// 1920x1080 at GUI scale 3 (640x360): just enough room beside the inventory
		int[] bar = place(640, 360, centered(640, 360, INVENTORY_HEIGHT));
		assertArrayEquals(new int[]{408 + (232 - WIDTH) / 2, 360 - SearchBarPlace.BOTTOM_MARGIN - HEIGHT, WIDTH, HEIGHT}, bar);
	}

	@Test
	void neverOverAWideWindow() {
		// a wide window, off center like the recipe book moves it: the field keeps to what is free of it
		int[] window = {100, 40, 520, 400};
		int[] bar = place(960, 540, window);
		assertArrayEquals(new int[]{620 + (340 - WIDTH) / 2, 540 - SearchBarPlace.BOTTOM_MARGIN - HEIGHT, WIDTH, HEIGHT}, bar);
		assertFalse(SearchBarPlace.covers(bar[0], bar[1], bar[2], bar[3], window));
		assertTrue(bar[0] >= window[0] + window[2] + SearchBarPlace.BESIDE_MARGIN);
		assertTrue(bar[0] + bar[2] <= 960 - SearchBarPlace.BESIDE_MARGIN);
	}

	@Test
	void centeredOnTheScreenWhenTheSpaceIsTooNarrow() {
		// 1920x1080 at GUI scale 4 (480x270): only 152 free beside the inventory, so as before: centered, below the window
		int[] bar = place(480, 270, centered(480, 270, INVENTORY_HEIGHT));
		assertEquals((480 - WIDTH) / 2, bar[0]);
		// one pixel too little beside the window
		int[] window = {0, 0, 960 - WIDTH - 2 * SearchBarPlace.BESIDE_MARGIN + 1, 100};
		assertEquals((960 - WIDTH) / 2, place(960, 540, window)[0]);
	}

	@Test
	void justBelowTheWindowWhenItWouldCoverIt() {
		// 1920x1080 at GUI scale 4 (480x270): the inventory (52 to 218) leaves the bottom place free
		int[] window = centered(480, 270, INVENTORY_HEIGHT);
		int[] bar = place(480, 270, window);
		assertNotNull(bar);
		assertFalse(SearchBarPlace.covers(bar[0], bar[1], bar[2], bar[3], window), "never over the window");
		assertTrue(bar[1] + HEIGHT <= 270);
		// a window reaching into the bottom place (244 to 264): the field moves just below it
		int[] low = {152, 40, INVENTORY_WIDTH, 205};
		bar = place(480, 270, low);
		assertArrayEquals(new int[]{130, 40 + 205 + SearchBarPlace.BELOW_WINDOW, WIDTH, HEIGHT}, bar);
		assertFalse(SearchBarPlace.covers(bar[0], bar[1], bar[2], bar[3], low));
	}

	@Test
	void besideTheWindowWhenThereIsNoRoomBelow() {
		// 1280x720 at GUI scale 3 (427x240) and 4 (320x180) with the large chest: it fills the screen's height
		int[] narrow = place(427, 240, centered(427, 240, CHEST_HEIGHT));
		assertTrue(narrow[2] >= SearchBarPlace.LEAST_WIDTH && narrow[0] + narrow[2] <= 427, "narrower beside the window");
		int[] last = place(320, 180, centered(320, 180, CHEST_HEIGHT));
		assertTrue(last[0] >= 0 && last[1] + last[3] <= 180, "there is always a place");
		// the inventory at 1280x720 scale 3 leaves room below it
		int[] window = centered(427, 240, INVENTORY_HEIGHT);
		int[] bar = place(427, 240, window);
		assertNotNull(bar);
		assertFalse(SearchBarPlace.covers(bar[0], bar[1], bar[2], bar[3], window));
	}

	@Test
	void besideTheWindowIsNoCover() {
		int[] window = {100, 50, INVENTORY_WIDTH, INVENTORY_HEIGHT};
		assertFalse(SearchBarPlace.covers(100 + INVENTORY_WIDTH, 50, 40, 20, window), "touching the edge is no overlap");
		assertTrue(SearchBarPlace.covers(100 + INVENTORY_WIDTH - 1, 50, 40, 20, window));
	}
}
