package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class ItemWindowPlaceTest {
	private static final int WIDTH = ItemListLayout.DETAIL_WIDTH;
	private static final int HEIGHT = ItemListLayout.DETAIL_HEIGHT;
	private static final int LEAST_WIDTH = ItemListLayout.DETAIL_LEAST_WIDTH;
	private static final int LEAST_HEIGHT = ItemListLayout.DETAIL_LEAST_HEIGHT;

	private static int[] centered(int screenWidth, int screenHeight, int width, int height) {
		return new int[]{(screenWidth - width) / 2, (screenHeight - height) / 2, width, height};
	}

	private static void assertClear(ItemWindowPlace.Place place, List<int[]> panels, int screenWidth, int screenHeight) {
		assertNotNull(place);
		int[] box = place.box();
		for (int[] panel : panels) {
			assertFalse(ItemPanelLayout.overlaps(box, panel), "over a panel");
		}
		assertTrue(box[0] >= ItemPanelLayout.MARGIN && box[1] >= ItemPanelLayout.MARGIN, "on the screen");
		assertTrue(box[0] + box[2] <= screenWidth - ItemPanelLayout.MARGIN && box[1] + box[3] <= screenHeight - ItemPanelLayout.MARGIN, "on the screen");
	}

	/** The starting layout at 1080p and GUI 2: Pinned and Recent on the left, Items on the right, the bar at the bottom. */
	@Test
	void besideThePanelsAtFullSize() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, 176, 166);
		int[] pinned = {6, 6, 200, 290};
		int[] recent = {6, 302, 200, 55};
		int[] items = {704, 6, 250, 460};
		int[] bar = {350, 512, 260, 22};
		List<int[]> panels = List.of(pinned, recent, items, bar);
		int[] tile = {items[0] + 8, items[1] + 18, 20, 20};
		ItemWindowPlace.Place place = ItemWindowPlace.place(tile, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui, panels);
		assertClear(place, panels, width, height);
		assertFalse(place.overGui());
		assertFalse(ItemPanelLayout.overlaps(place.box(), gui));
		assertTrue(place.box()[2] >= LEAST_WIDTH && place.box()[3] >= LEAST_HEIGHT, "at least its least size");
	}

	@Test
	void atFullSizeBetweenTheInventoryAndTheItemsWhereThereIsRoom() {
		int width = 1280;
		int height = 720;
		int[] gui = centered(width, height, 176, 166);
		int[] items = {1024, 6, 250, 600};
		int[] bar = {510, 692, 260, 22};
		List<int[]> panels = List.of(items, bar);
		ItemWindowPlace.Place place = ItemWindowPlace.place(new int[]{items[0] + 8, 30, 20, 20}, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui,
			panels);
		assertClear(place, panels, width, height);
		assertEquals(WIDTH, place.box()[2]);
		assertEquals(HEIGHT, place.box()[3]);
		assertTrue(place.box()[0] + place.box()[2] <= items[0] && place.box()[0] >= gui[0] + gui[2], "between the inventory and the Items panel");
	}

	@Test
	void aClickInPinnedOpensOnTheOtherSideOfIt() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, 176, 166);
		int[] pinned = {6, 6, 200, 290};
		int[] items = {704, 6, 250, 460};
		List<int[]> panels = List.of(pinned, items);
		int[] tile = {pinned[0] + 8, pinned[1] + 40, 18, 18};
		ItemWindowPlace.Place place = ItemWindowPlace.place(tile, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui, panels);
		assertClear(place, panels, width, height);
		assertFalse(place.overGui());
	}

	@Test
	void smallerWhereTheFullSizeHasNoRoom() {
		// 1080p at GUI 4: 480 × 270, the inventory in the middle and panels at both sides
		int width = 480;
		int height = 270;
		int[] gui = centered(width, height, 176, 166);
		int[] left = {6, 6, 140, 258};
		int[] right = {334, 6, 140, 258};
		List<int[]> panels = List.of(left, right);
		ItemWindowPlace.Place place = ItemWindowPlace.place(new int[]{20, 40, 18, 18}, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui, panels);
		// no free room beside the inventory: over it, still clear of the panels
		assertClear(place, panels, width, height);
		assertTrue(place.overGui());
	}

	@Test
	void shrinksBeforeGoingOverTheInventory() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, 176, 166);
		int[] items = {6, 6, 200, 528};
		// free room on the right only 220 high
		int[] lower = {600, 232, 354, 302};
		List<int[]> panels = List.of(items, lower);
		ItemWindowPlace.Place place = ItemWindowPlace.place(new int[]{20, 40, 20, 20}, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui, panels);
		assertClear(place, panels, width, height);
		assertFalse(place.overGui());
		assertFalse(ItemPanelLayout.overlaps(place.box(), gui));
		assertTrue(place.box()[3] < HEIGHT && place.box()[3] >= LEAST_HEIGHT, "smaller, at least its least size");
	}

	@Test
	void noRoomAtAll() {
		int width = 320;
		int height = 240;
		int[] gui = centered(width, height, 176, 222);
		int[] panel = {6, 6, 308, 228};
		assertNull(ItemWindowPlace.place(new int[]{100, 100, 16, 16}, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, width, height, gui, List.of(panel)));
		// a screen smaller than the least size
		assertNull(ItemWindowPlace.place(new int[]{10, 10, 16, 16}, WIDTH, HEIGHT, LEAST_WIDTH, LEAST_HEIGHT, 150, 150, new int[]{0, 0, 10, 10}, List.of()));
	}

	@Test
	void sizesStepFromFullToLeast() {
		assertEquals(236, ItemWindowPlace.size(236, 176, 0));
		assertEquals(176, ItemWindowPlace.size(236, 176, 6));
		assertEquals(100, ItemWindowPlace.size(100, 176, 3), "never larger than asked");
	}
}
