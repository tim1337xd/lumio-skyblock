package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.hud.GridSnap;

import org.junit.jupiter.api.Test;

/**
 * Snapping while moving and pulling panels (only to the grid and the screen's center, through the HUD editor's snapper), and the grid sizes of the
 * Items panel. The snapper here is a plain stand-in with the same targets; the editor's own logic is tested in Lumio.
 */
class PanelSnapTest {
	private static final int W = 960;
	private static final int H = 540;
	private static final int STEP = 8;
	private static final int REACH = 3;
	/** The inventory window: once a target, now none. */
	private static final int[] GUI = {392, 187, 176, 166};

	/** A snapper with the grid's lines and the screen's center lines as its only targets, as the HUD editor's is for the item search. */
	private static final class Grid implements GridSnap {
		private final int[] held = new int[2];
		private final int[] kind = new int[2];
		private final int[] snapped = new int[2];
		private int width;
		private int height;

		@Override
		public void begin(int screenWidth, int screenHeight) {
			width = screenWidth;
			height = screenHeight;
			kind[0] = 0;
			kind[1] = 0;
		}

		/** @return how far a point is moved to its nearest line within reach, noting the line; 0 and no line otherwise */
		private int pull(int axis, int[] points, boolean free) {
			kind[axis] = 0;
			if (free) {
				return 0;
			}
			int center = (axis == ACROSS ? width : height) / 2;
			int best = REACH + 1;
			int delta = 0;
			for (int point : points) {
				if (Math.abs(center - point) < best) {
					best = Math.abs(center - point);
					delta = center - point;
					held[axis] = center;
					kind[axis] = 2;
				}
			}
			for (int point : points) {
				int line = Math.round(point / (float) STEP) * STEP;
				if (Math.abs(line - point) < best) {
					best = Math.abs(line - point);
					delta = line - point;
					held[axis] = line;
					kind[axis] = 1;
				}
			}
			return delta;
		}

		@Override
		public boolean move(int x, int y, int boxWidth, int boxHeight, boolean free) {
			snapped[0] = x + pull(ACROSS, new int[]{x, x + boxWidth / 2, x + boxWidth}, free);
			snapped[1] = y + pull(DOWN, new int[]{y, y + boxHeight / 2, y + boxHeight}, free);
			return kind[0] != 0 || kind[1] != 0;
		}

		@Override
		public int x() {
			return snapped[0];
		}

		@Override
		public int y() {
			return snapped[1];
		}

		@Override
		public int edge(int axis, int edge, boolean free) {
			return edge + pull(axis, new int[]{edge}, free);
		}

		@Override
		public boolean onGrid(int axis) {
			return kind[axis] == 1;
		}

		@Override
		public boolean onCenter(int axis) {
			return kind[axis] == 2;
		}

		@Override
		public int heldAt(int axis) {
			return held[axis];
		}

		@Override
		public int step() {
			return STEP;
		}
	}

	private static Grid grid() {
		Grid grid = new Grid();
		grid.begin(W, H);
		return grid;
	}

	@Test
	void aMovedPanelSnapsToTheGridOnBothAxes() {
		PanelSnap.Result result = PanelSnap.move(grid(), new int[]{98, 121, 121, 68}, false);
		assertArrayEquals(new int[]{96, 120, 121, 68}, result.box());
		assertEquals(2, result.guides().size());
		PanelSnap.Guide across = result.guides().getFirst();
		assertTrue(across.vertical());
		assertEquals(96, across.at());
		assertFalse(across.center());
		assertEquals("Snaps to the grid", across.label());
		assertFalse(result.guides().get(1).vertical());
		assertEquals(120, result.guides().get(1).at());
	}

	@Test
	void aPanelsCenterSnapsToTheScreensCenter() {
		PanelSnap.Result result = PanelSnap.move(grid(), new int[]{434, 301, 95, 37}, false);
		assertEquals(433, result.box()[0]);
		PanelSnap.Guide guide = result.guides().getFirst();
		assertTrue(guide.center());
		assertEquals(W / 2, guide.at());
		assertEquals("Centered", guide.label());
	}

	@Test
	void theInventoryAndTheScreensEdgesAreNoTargets() {
		// its right edge 4 before the inventory: once it snapped beside it, a gap away. Its edges and center lie midway between grid lines, so
		// nothing holds it now
		int[] besideGui = {GUI[0] - 4 - 144, 204, 144, 96};
		PanelSnap.Result result = PanelSnap.move(grid(), besideGui, false);
		assertArrayEquals(besideGui, result.box());
		assertTrue(result.guides().isEmpty());
		// 4 from the left edge: once it snapped to the edge's margin
		int[] nearEdge = {4, 204, 96, 96};
		assertArrayEquals(nearEdge, PanelSnap.move(grid(), nearEdge, false).box());
	}

	@Test
	void shiftPlacesFreely() {
		int[] box = {98, 121, 121, 68};
		PanelSnap.Result result = PanelSnap.move(grid(), box, true);
		assertArrayEquals(box, result.box());
		assertTrue(result.guides().isEmpty());
		PanelSnap.Result pulled = PanelSnap.resize(grid(), box, false, false, true, true, true);
		assertArrayEquals(box, pulled.box());
		assertTrue(pulled.guides().isEmpty());
	}

	@Test
	void pullingOnlyMovesThePulledEdge() {
		// the right edge at 218 goes to the line at 216; left, top and bottom stay
		PanelSnap.Result right = PanelSnap.resize(grid(), new int[]{101, 121, 117, 67}, false, false, true, false, false);
		assertArrayEquals(new int[]{101, 121, 115, 67}, right.box());
		assertEquals(1, right.guides().size());
		assertEquals(216, right.guides().getFirst().at());
		// the left and top edges: the right and bottom ones stay where they are
		PanelSnap.Result corner = PanelSnap.resize(grid(), new int[]{98, 122, 120, 66}, true, true, false, false, false);
		assertArrayEquals(new int[]{96, 120, 122, 68}, corner.box());
		assertEquals(98 + 120, corner.box()[0] + corner.box()[2]);
		assertEquals(122 + 66, corner.box()[1] + corner.box()[3]);
	}

	@Test
	void theItemsPanelStaysWholeItemsAfterSnapping() {
		float scale = 1f;
		int cell = ItemGrid.cell(scale);
		int[] pulled = {300, 100, ItemGrid.itemsWidth(7, scale) + cell / 3, ItemGrid.itemsHeight(8, scale) - cell / 3};
		// pulled by its right and bottom edges: the left and top stay
		int[] whole = PanelSnap.wholeItems(pulled, false, false, scale);
		assertEquals(300, whole[0]);
		assertEquals(100, whole[1]);
		assertEquals(ItemGrid.itemsWidth(7, scale), whole[2]);
		assertEquals(ItemGrid.itemsHeight(8, scale), whole[3]);
		assertEquals(7, whole[4]);
		assertEquals(8, whole[5]);
		// pulled by its left and top edges: the right and bottom stay
		int[] fromEnd = PanelSnap.wholeItems(pulled, true, true, scale);
		assertEquals(pulled[0] + pulled[2], fromEnd[0] + fromEnd[2]);
		assertEquals(pulled[1] + pulled[3], fromEnd[1] + fromEnd[3]);
	}

	@Test
	void theItemsGridSnapsToWholeItems() {
		float scale = 1f;
		int cell = ItemGrid.cell(scale);
		int width = ItemGrid.itemsWidth(7, scale) + cell / 3;
		int height = ItemGrid.itemsHeight(8, scale) - cell / 3;
		int[] snapped = ItemGrid.snapItems(width, height, scale);
		assertEquals(7, snapped[2]);
		assertEquals(8, snapped[3]);
		assertEquals(ItemGrid.itemsWidth(7, scale), snapped[0]);
		assertEquals(ItemGrid.itemsHeight(8, scale), snapped[1]);
		assertEquals("7 × 8 · 56 shown", ItemGrid.badge(7, 8));
		// never below the least grid
		int[] tiny = ItemGrid.snapItems(10, 10, scale);
		assertEquals(ItemGrid.LEAST_COLUMNS, tiny[2]);
		assertEquals(ItemGrid.LEAST_ROWS, tiny[3]);
	}

	@Test
	void gridSizeFollowsThePanelAndTheItemSize() {
		int[] grid = ItemGrid.itemsGrid(ItemGrid.itemsWidth(6, 1f), ItemGrid.itemsHeight(5, 1f), 1f);
		assertArrayEquals(new int[]{6, 5}, grid);
		int[] larger = ItemGrid.itemsGrid(ItemGrid.itemsWidth(6, 1f), ItemGrid.itemsHeight(5, 1f), 1.5f);
		assertTrue(larger[0] < 6 && larger[1] < 5);
		int[] trimmed = ItemGrid.trimItems(new int[]{0, 0, ItemGrid.itemsWidth(6, 1f) + 10, ItemGrid.itemsHeight(5, 1f) + 10}, 1f);
		assertEquals(ItemGrid.itemsWidth(6, 1f), trimmed[2]);
		assertEquals(ItemGrid.itemsHeight(5, 1f), trimmed[3]);
	}

	@Test
	void edgesOfAPanel() {
		int[] box = {100, 100, 200, 150};
		assertEquals(PanelFrame.RIGHT | PanelFrame.BOTTOM, PanelFrame.edgesAt(box, 298, 248, true));
		assertEquals(PanelFrame.LEFT, PanelFrame.edgesAt(box, 100, 180, true));
		assertEquals(PanelFrame.RIGHT, PanelFrame.edgesAt(box, 301, 180, false));
		assertEquals(0, PanelFrame.edgesAt(box, 200, 180, true));
		// only the grip at the header's start picks it up, not the rest of the header
		assertTrue(PanelFrame.onGrip(box, 110, 105));
		assertFalse(PanelFrame.onGrip(box, 150, 105));
	}
}
