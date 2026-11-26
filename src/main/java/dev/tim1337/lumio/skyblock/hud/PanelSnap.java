package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.hud.GridSnap;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapping of the item search's panels while they are moved or pulled: only to the grid and the screen's center lines, with the HUD editor's own
 * grid and logic ({@link GridSnap}); never to the inventory, the screen's edges or another panel. Each axis snaps on its own; the guides are the
 * lines the panel sits on, drawn as accent lines across the screen. The Items panel is brought to whole items after it (see {@link #wholeItems}).
 * Plain numbers on top of the snapper, so it is unit tested.
 */
final class PanelSnap {
	static final String ON_GRID = "Snaps to the grid";
	static final String ON_CENTER = "Centered";

	/** A guide line across the screen: vertical at x = at or horizontal at y = at, and whether it is the screen's center line (else a grid line). */
	record Guide(boolean vertical, int at, boolean center) {
		String label() {
			return center ? ON_CENTER : ON_GRID;
		}
	}

	/** The result: the snapped box and the guides to draw. */
	record Result(int[] box, List<Guide> guides) {
	}

	private PanelSnap() {
	}

	/**
	 * Snaps a moved box: its position changes, its size stays.
	 *
	 * @param free whether it is placed freely (Shift held, or snapping switched off)
	 */
	static Result move(GridSnap snap, int[] box, boolean free) {
		int[] snapped = box.clone();
		List<Guide> guides = new ArrayList<>(2);
		if (snap.move(box[0], box[1], box[2], box[3], free)) {
			snapped[0] = snap.x();
			snapped[1] = snap.y();
			addGuide(guides, snap, GridSnap.ACROSS);
			addGuide(guides, snap, GridSnap.DOWN);
		}
		return new Result(snapped, guides);
	}

	/**
	 * Snaps a pulled box: only the edges being pulled move, the others stay where they are.
	 *
	 * @param left   whether its left edge is pulled
	 * @param top    whether its top edge is pulled
	 * @param right  whether its right edge is pulled
	 * @param bottom whether its bottom edge is pulled
	 */
	static Result resize(GridSnap snap, int[] box, boolean left, boolean top, boolean right, boolean bottom, boolean free) {
		int[] snapped = box.clone();
		List<Guide> guides = new ArrayList<>(2);
		if (left || right) {
			int edge = left ? box[0] : box[0] + box[2];
			int to = snap.edge(GridSnap.ACROSS, edge, free);
			if (left) {
				snapped[0] = to;
				snapped[2] = box[0] + box[2] - to;
			} else {
				snapped[2] = to - box[0];
			}
			if (!free) {
				addGuide(guides, snap, GridSnap.ACROSS);
			}
		}
		if (top || bottom) {
			int edge = top ? box[1] : box[1] + box[3];
			int to = snap.edge(GridSnap.DOWN, edge, free);
			if (top) {
				snapped[1] = to;
				snapped[3] = box[1] + box[3] - to;
			} else {
				snapped[3] = to - box[1];
			}
			if (!free) {
				addGuide(guides, snap, GridSnap.DOWN);
			}
		}
		return new Result(snapped, guides);
	}

	private static void addGuide(List<Guide> guides, GridSnap snap, int axis) {
		boolean center = snap.onCenter(axis);
		if (center || snap.onGrid(axis)) {
			guides.add(new Guide(axis == GridSnap.ACROSS, snap.heldAt(axis), center));
		}
	}

	/**
	 * Brings a pulled Items panel to whole items, from the edge that stays: its grid never cuts an item off, also after snapping to the grid.
	 *
	 * @return the box, and the columns and rows it shows: x, y, width, height, columns, rows
	 */
	static int[] wholeItems(int[] box, boolean left, boolean top, float itemScale) {
		int[] grid = ItemGrid.snapItems(box[2], box[3], itemScale);
		int x = left ? box[0] + box[2] - grid[0] : box[0];
		int y = top ? box[1] + box[3] - grid[1] : box[1];
		return new int[]{x, y, grid[0], grid[1], grid[2], grid[3]};
	}
}
