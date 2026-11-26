package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the item window opens beside a click: never over a panel of the item search (Items, Pinned, Recent, the search bar, the settings window)
 * or another overlay. In this order: at its full size in free room, nearest to what was clicked; else smaller (down to its least size) in free
 * room; else over the inventory or chest window (whose slots are then away), still clear of the panels; else nowhere (the caller then puts it
 * in the inventory's place). Worked out once when it opens; it keeps that place afterwards. Plain numbers, so it is unit tested.
 */
final class ItemWindowPlace {
	/** How much smaller each try is, as a share of the room between the full and the least size. */
	private static final int SHRINK_STEPS = 6;

	/** A place: its box, and whether it lies over the inventory or chest window. */
	record Place(int[] box, boolean overGui) {
	}

	private ItemWindowPlace() {
	}

	/**
	 * @param anchor what was clicked (an item's tile), or the mouse as a box of 1 × 1
	 * @param gui    the inventory or chest window
	 * @param panels every panel and overlay on the screen it must stay clear of
	 * @return its place, or null when there is none clear of the panels
	 */
	static Place place(int[] anchor, int width, int height, int leastWidth, int leastHeight, int screenWidth, int screenHeight, int[] gui,
		List<int[]> panels) {
		List<int[]> withGui = new ArrayList<>(panels);
		withGui.add(gui);
		for (int step = 0; step <= SHRINK_STEPS; step++) {
			int w = size(width, leastWidth, step);
			int h = size(height, leastHeight, step);
			int[] box = nearestFree(anchor, w, h, screenWidth, screenHeight, withGui);
			if (box != null) {
				return new Place(box, false);
			}
		}
		for (int step = 0; step <= SHRINK_STEPS; step++) {
			int[] box = nearestFree(anchor, size(width, leastWidth, step), size(height, leastHeight, step), screenWidth, screenHeight, panels);
			if (box != null) {
				return new Place(box, ItemPanelLayout.overlaps(box, gui));
			}
		}
		return null;
	}

	/** @return a size at a step from the full one (0) to the least one ({@link #SHRINK_STEPS}) */
	static int size(int full, int least, int step) {
		int smallest = Math.min(full, least);
		return full - (full - smallest) * step / SHRINK_STEPS;
	}

	/**
	 * @return the free box of a size nearest to the anchor: tried right and left of it, above and below it, and against every edge of what is in
	 *         the way and of the screen; null when none is free
	 */
	static int[] nearestFree(int[] anchor, int width, int height, int screenWidth, int screenHeight, List<int[]> blocked) {
		int margin = ItemPanelLayout.MARGIN;
		int gap = ItemPanelLayout.GAP;
		if (width > screenWidth - 2 * margin || height > screenHeight - 2 * margin) {
			return null;
		}
		List<Integer> xs = new ArrayList<>(List.of(anchor[0] + anchor[2] + gap, anchor[0] - gap - width, anchor[0] + anchor[2] / 2 - width / 2, margin,
			screenWidth - margin - width));
		List<Integer> ys = new ArrayList<>(List.of(anchor[1] - height / 3, anchor[1] + anchor[3] + gap, anchor[1] - gap - height, margin,
			screenHeight - margin - height));
		for (int[] other : blocked) {
			xs.add(other[0] + other[2] + gap);
			xs.add(other[0] - gap - width);
			ys.add(other[1] + other[3] + gap);
			ys.add(other[1] - gap - height);
		}
		double anchorX = anchor[0] + anchor[2] / 2.0;
		double anchorY = anchor[1] + anchor[3] / 2.0;
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int x : xs) {
			for (int y : ys) {
				int[] box = {Math.max(margin, Math.min(x, screenWidth - margin - width)), Math.max(margin, Math.min(y, screenHeight - margin - height)), width,
					height};
				if (ItemPanelLayout.overlaps(box, anchor) || !ItemPanelLayout.free(box, blocked)) {
					continue;
				}
				double dx = Math.max(0, Math.max(box[0] - anchorX, anchorX - (box[0] + width)));
				double dy = Math.max(0, Math.max(box[1] - anchorY, anchorY - (box[1] + height)));
				double distance = dx * dx + dy * dy;
				if (distance < bestDistance) {
					bestDistance = distance;
					best = box;
				}
			}
		}
		return best;
	}
}
