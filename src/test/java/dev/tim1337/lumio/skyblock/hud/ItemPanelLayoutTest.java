package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The item search's panels: saved places survive other screens, the fit keeps them on screen and off the GUI, the small-window fallback. */
class ItemPanelLayoutTest {
	private static final int[][] WINDOWS = {{1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}};
	private static final int INVENTORY_WIDTH = 176;
	private static final int INVENTORY_HEIGHT = 166;
	private static final int CHEST_HEIGHT = 222;

	private static int scaleFor(int width, int height) {
		int scale = 1;
		while (width / (scale + 1) >= 320 && height / (scale + 1) >= 240) {
			scale++;
		}
		return scale;
	}

	private static int[] centered(int screenWidth, int screenHeight, int width, int height) {
		return new int[]{(screenWidth - width) / 2, (screenHeight - height) / 2, width, height};
	}

	/** Every window size at every GUI scale from 2 to the largest. */
	private interface Check {
		void check(int screenWidth, int screenHeight, int[] gui);
	}

	private static void everyScreen(int guiHeight, Check check) {
		for (int[] window : WINDOWS) {
			for (int scale = 2; scale <= scaleFor(window[0], window[1]); scale++) {
				int width = (int) Math.ceil(window[0] / (double) scale);
				int height = (int) Math.ceil(window[1] / (double) scale);
				check.check(width, height, centered(width, height, INVENTORY_WIDTH, guiHeight));
			}
		}
	}

	private static void assertOnScreen(int[] box, int screenWidth, int screenHeight, String what) {
		assertTrue(box[0] >= 0 && box[1] >= 0 && box[0] + box[2] <= screenWidth && box[1] + box[3] <= screenHeight,
			what + " off a " + screenWidth + "x" + screenHeight + " screen: " + java.util.Arrays.toString(box));
	}

	@Test
	void placeKeepsItsEdgeOnOtherScreens() {
		int[] gui = centered(960, 540, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		// beside the inventory on its right, a gap away: it stays beside it
		int[] beside = {gui[0] + gui[2] + 6, 100, 120, 200};
		PanelPlace place = PanelPlace.of(beside, 960, 540, gui);
		assertEquals(PanelPlace.GUI_AFTER, place.xAnchor);
		int[] smaller = centered(640, 360, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		assertEquals(smaller[0] + smaller[2] + 6, place.box(640, 360, smaller)[0]);
		// against the screen's right edge: it stays there
		int[] right = {960 - 4 - 150, 4, 150, 500};
		PanelPlace atEdge = PanelPlace.of(right, 960, 540, gui);
		assertEquals(PanelPlace.SCREEN_END, atEdge.xAnchor);
		assertEquals(PanelPlace.SCREEN_START, atEdge.yAnchor);
		assertEquals(1920 - 4 - 150, atEdge.box(1920, 1080, centered(1920, 1080, INVENTORY_WIDTH, INVENTORY_HEIGHT))[0]);
	}

	@Test
	void placeRoundTripsThroughItsAnchors() {
		int[] gui = centered(800, 450, INVENTORY_WIDTH, CHEST_HEIGHT);
		int[][] boxes = {{20, 30, 100, 80}, {gui[0] - 130, gui[1], 120, 90}, {gui[0], gui[1] + gui[3] + 10, gui[2], 40}, {380, 200, 60, 40}, {700, 400, 90, 44}};
		for (int[] box : boxes) {
			PanelPlace place = PanelPlace.of(box, 800, 450, gui);
			assertArrayEquals(box, place.box(800, 450, gui));
			PanelPlace read = PanelPlace.fromJson(place.toJson());
			assertNotNull(read);
			assertArrayEquals(box, read.box(800, 450, gui));
		}
	}

	@Test
	void brokenPlacesAreNotRead() {
		assertNull(PanelPlace.fromJson(JsonParser.parseString("{\"xa\":9,\"x\":0,\"ya\":0,\"y\":0,\"w\":10,\"h\":10}").getAsJsonObject()));
		assertNull(PanelPlace.fromJson(JsonParser.parseString("{\"xa\":0,\"x\":1e9,\"ya\":0,\"y\":0,\"w\":10,\"h\":10}").getAsJsonObject()));
		assertNull(PanelPlace.fromJson(JsonParser.parseString("{\"xa\":0}").getAsJsonObject()));
	}

	@Test
	void layoutsSaveAndLoadPerWindowKind() {
		PanelPlaces places = new PanelPlaces();
		PanelPlace inventory = new PanelPlace(PanelPlace.SCREEN_START, 8, PanelPlace.SCREEN_START, 8, 120, 100);
		places.put(PanelPlaces.INVENTORY, ItemPanelKind.PINNED, inventory);
		// a chest not arranged yet takes the inventory's
		String chest = PanelPlaces.windowKind(false, 6);
		assertEquals("chest6", chest);
		assertEquals(120, places.get(chest, ItemPanelKind.PINNED).width);
		PanelPlace moved = new PanelPlace(PanelPlace.SCREEN_END, 8, PanelPlace.SCREEN_START, 8, 140, 100);
		places.put(chest, ItemPanelKind.RECENT, moved);
		// the chest keeps the inventory's Pinned, and its own Recent; the inventory has no Recent of its own
		assertEquals(120, places.get(chest, ItemPanelKind.PINNED).width);
		assertEquals(140, places.get(chest, ItemPanelKind.RECENT).width);
		assertNull(places.get(PanelPlaces.INVENTORY, ItemPanelKind.RECENT));
		PanelPlaces read = new PanelPlaces();
		read.load(places.toJson());
		assertEquals(140, read.get(chest, ItemPanelKind.RECENT).width);
		assertEquals(120, read.get(PanelPlaces.INVENTORY, ItemPanelKind.PINNED).width);
		read.clear();
		assertTrue(read.isEmpty());
	}

	@Test
	void theSearchBarStartsAtTheBottomCenter() {
		for (int[] screen : new int[][]{{960, 540}, {1280, 720}, {640, 360}}) {
			int width = screen[0];
			int height = screen[1];
			int[] gui = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT);
			int[] search = ItemPanelLayout.starting(width, height, gui, 1f, false).get(ItemPanelKind.SEARCH);
			String what = width + "x" + height;
			// centered on the screen, not on the inventory, and at the bottom with the margin
			assertEquals(width / 2, search[0] + search[2] / 2, what);
			assertEquals(height - ItemPanelLayout.MARGIN, search[1] + search[3], what);
			assertTrue(search[1] >= gui[1] + gui[3], what + ": under the inventory");
			// and it is drawn there: nothing of the layout pushes it away
			ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(), ItemPanelLayout.starting(width, height, gui, 1f, false), 1f);
			assertArrayEquals(search, fit.box(ItemPanelKind.SEARCH), what);
		}
	}

	@Test
	void theStartingLayoutIsPinnedAndRecentLeftItemsRight() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		Map<ItemPanelKind, int[]> start = ItemPanelLayout.starting(width, height, gui, 1f, false);
		int[] pinned = start.get(ItemPanelKind.PINNED);
		int[] recent = start.get(ItemPanelKind.RECENT);
		int[] items = start.get(ItemPanelKind.ITEMS);
		assertEquals(ItemPanelLayout.MARGIN, pinned[0], "Pinned at the left");
		assertEquals(ItemPanelLayout.MARGIN, pinned[1], "at the top");
		assertEquals(pinned[0], recent[0], "Recent under it on the left");
		assertTrue(recent[1] > pinned[1] + pinned[3] - 1);
		assertEquals(width - ItemPanelLayout.MARGIN, items[0] + items[2], "Items at the right");
		assertEquals(ItemPanelLayout.MARGIN, items[1], "at the top");
	}

	@Test
	void resetLayoutPutsTheSearchBarBack() {
		PanelPlaces places = new PanelPlaces();
		places.put(PanelPlaces.INVENTORY, ItemPanelKind.SEARCH, new PanelPlace(PanelPlace.SCREEN_START, 8, PanelPlace.SCREEN_START, 8, 200, 20));
		assertNotNull(places.get(PanelPlaces.INVENTORY, ItemPanelKind.SEARCH));
		// Reset layout forgets every place: the panels follow the starting layout again, the search bar at the bottom center
		places.clear();
		assertNull(places.get(PanelPlaces.INVENTORY, ItemPanelKind.SEARCH));
	}

	@Test
	void anOlderSavedLayoutMovesOnlyTheSearchBar() {
		PanelPlaces places = new PanelPlaces();
		PanelPlace pinned = new PanelPlace(PanelPlace.SCREEN_START, 8, PanelPlace.SCREEN_START, 8, 120, 100);
		PanelPlace search = new PanelPlace(PanelPlace.GUI_START, 0, PanelPlace.GUI_AFTER, 24, 200, 20);
		places.put(PanelPlaces.INVENTORY, ItemPanelKind.PINNED, pinned);
		places.put(PanelPlaces.INVENTORY, ItemPanelKind.SEARCH, search);
		places.put("chest6", ItemPanelKind.SEARCH, search);
		JsonObject saved = new JsonObject();
		saved.add("layout", places.toJson());
		// saved before the bar moved: no revision in the file
		assertEquals(1, ItemListFeature.layoutRevision(saved));
		assertTrue(ItemListFeature.layoutRevision(saved) < ItemListFeature.LAYOUT_REVISION);
		places.forget(ItemPanelKind.SEARCH);
		// the bar follows the starting layout again, in every kind of window; the other panels stay where the player put them
		assertNull(places.get(PanelPlaces.INVENTORY, ItemPanelKind.SEARCH));
		assertNull(places.get("chest6", ItemPanelKind.SEARCH));
		assertEquals(120, places.get(PanelPlaces.INVENTORY, ItemPanelKind.PINNED).width);
		assertEquals(120, places.get("chest6", ItemPanelKind.PINNED).width);
		// once: a layout saved since has the revision, and a bar moved after it stays
		saved.addProperty("layout_revision", ItemListFeature.LAYOUT_REVISION);
		assertEquals(ItemListFeature.LAYOUT_REVISION, ItemListFeature.layoutRevision(saved));
		saved.addProperty("layout_revision", "broken");
		assertEquals(1, ItemListFeature.layoutRevision(saved));
	}

	@Test
	void theStartingLayoutFitsOnEveryScreenOffTheGui() {
		for (int guiHeight : new int[]{INVENTORY_HEIGHT, CHEST_HEIGHT}) {
			everyScreen(guiHeight, (width, height, gui) -> {
				for (float scale : new float[]{0.75f, 1f, 1.5f}) {
					Map<ItemPanelKind, int[]> wanted = ItemPanelLayout.starting(width, height, gui, scale, true);
					ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(), wanted, scale);
					assertNotNull(fit.box(ItemPanelKind.SEARCH), "the search bar always shows: " + width + "x" + height);
					List<int[]> drawn = new ArrayList<>();
					for (ItemPanelKind kind : ItemPanelKind.values()) {
						int[] box = fit.box(kind);
						if (box == null) {
							continue;
						}
						String what = kind + " at " + scale;
						assertOnScreen(box, width, height, what);
						assertFalse(ItemPanelLayout.overlaps(box, gui), what + " covers the GUI on " + width + "x" + height);
						assertTrue(box[2] >= kind.leastWidth && box[3] >= kind.leastHeight, what + " below its least size");
						for (int[] other : drawn) {
							assertFalse(ItemPanelLayout.overlaps(box, other), what + " covers another panel on " + width + "x" + height);
						}
						drawn.add(box);
					}
				}
			});
		}
	}

	@Test
	void theDefaultLayoutHasItsPanelsOnUsualScreens() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(),
			ItemPanelLayout.starting(width, height, gui, 1f, false), 1f);
		assertNotNull(fit.box(ItemPanelKind.ITEMS));
		assertNotNull(fit.box(ItemPanelKind.PINNED));
		assertNotNull(fit.box(ItemPanelKind.RECENT));
		assertFalse(fit.pinnedAsTab);
		// the Items panel is whole items wide and high
		int[] items = fit.box(ItemPanelKind.ITEMS);
		int[] grid = ItemGrid.itemsGrid(items[2], items[3], 1f);
		assertEquals(items[2], ItemGrid.itemsWidth(grid[0], 1f));
		assertEquals(items[3], ItemGrid.itemsHeight(grid[1], 1f));
	}

	@Test
	void savedPlacesNeverEndUpOffScreen() {
		int[] bigGui = centered(1280, 720, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		Map<ItemPanelKind, int[]> bigLayout = ItemPanelLayout.starting(1280, 720, bigGui, 1f, true);
		EnumMap<ItemPanelKind, PanelPlace> saved = new EnumMap<>(ItemPanelKind.class);
		bigLayout.forEach((kind, box) -> saved.put(kind, PanelPlace.of(box, 1280, 720, bigGui)));
		everyScreen(CHEST_HEIGHT, (width, height, gui) -> {
			EnumMap<ItemPanelKind, int[]> wanted = new EnumMap<>(ItemPanelKind.class);
			saved.forEach((kind, place) -> wanted.put(kind, place.box(width, height, gui)));
			ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(), wanted, 1f);
			for (ItemPanelKind kind : ItemPanelKind.values()) {
				int[] box = fit.box(kind);
				if (box != null) {
					assertOnScreen(box, width, height, kind.toString());
					assertFalse(ItemPanelLayout.overlaps(box, gui));
				}
			}
		});
	}

	@Test
	void panelsShrinkFirstThenPinnedBecomesATab() {
		// a narrow screen: little room beside the inventory
		int width = 400;
		int height = 260;
		int[] gui = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		EnumMap<ItemPanelKind, int[]> wanted = new EnumMap<>(ItemPanelKind.class);
		wanted.put(ItemPanelKind.ITEMS, new int[]{gui[0] + gui[2] + 6, 6, 300, 240});
		wanted.put(ItemPanelKind.PINNED, new int[]{6, 6, 200, 150});
		ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(), wanted, 1f);
		int[] items = fit.box(ItemPanelKind.ITEMS);
		assertNotNull(items);
		// shrunk to the room right of the inventory, not moved over it
		assertTrue(items[0] >= gui[0] + gui[2] && items[2] < 300);
		// left of the inventory there are only about 100 pixels: Pinned has no room, and is a tab of Items
		assertNull(fit.box(ItemPanelKind.PINNED));
		assertTrue(fit.pinnedAsTab);
	}

	@Test
	void panelsStepAsideForOtherOverlays() {
		int width = 960;
		int height = 540;
		int[] gui = centered(width, height, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		int[] overlay = {6, 6, 150, 300};
		EnumMap<ItemPanelKind, int[]> wanted = new EnumMap<>(ItemPanelKind.class);
		wanted.put(ItemPanelKind.PINNED, new int[]{6, 6, 180, 280});
		ItemPanelLayout.Fit fit = ItemPanelLayout.fit(width, height, gui, List.of(overlay), wanted, 1f);
		int[] pinned = fit.box(ItemPanelKind.PINNED);
		assertNotNull(pinned);
		assertFalse(ItemPanelLayout.overlaps(pinned, overlay));
		assertFalse(ItemPanelLayout.overlaps(pinned, gui));
	}

	@Test
	void noRoomAtAllLeavesAPanelOut() {
		int[] gui = {0, 0, 320, 240};
		EnumMap<ItemPanelKind, int[]> wanted = new EnumMap<>(ItemPanelKind.class);
		wanted.put(ItemPanelKind.ITEMS, new int[]{10, 10, 200, 200});
		ItemPanelLayout.Fit fit = ItemPanelLayout.fit(320, 240, gui, List.of(), wanted, 1f);
		assertNull(fit.box(ItemPanelKind.ITEMS));
	}
}
