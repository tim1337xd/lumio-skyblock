package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the item search's panels go on a screen: the three starting layouts (the search bar at the bottom center of the screen in all three), and the fit that keeps every panel on the screen and off the inventory
 * or chest window (the GUI), off the other overlays and off each other. A panel that has no room where it was put shrinks first, toward the room it has;
 * only then it moves beside what is in the way; with no room at all it is left out, and Pinned becomes a tab of the Items panel. The fit is never
 * saved: the player's own places stay theirs, and once there is room again the panels are back there. Plain numbers, so it is unit tested.
 */
final class ItemPanelLayout {
	/** The room kept to the screen's edges. */
	static final int MARGIN = 6;
	/** The gap between panels, and between a panel and the GUI: about 12 pixels at GUI scale 2. */
	static final int GAP = 6;
	/** The starting layouts keep a little more room to the GUI. */
	static final int PRESET_GAP = 12;
	/** The search bar's widest. */
	static final int MOST_SEARCH_WIDTH = 260;
	/** The side panels' widest in the starting layouts. */
	static final int MOST_SIDE_WIDTH = 200;
	/** Pinned takes this share of the height on the left, Recent comes under it. */
	private static final float PINNED_SHARE = 0.55f;
	/** The most columns of items a starting layout gives the Items panel. */
	static final int MOST_PRESET_COLUMNS = 9;
	/** The Recent panel's height in the starting layouts: its header and one row of items. */
	static final int RECENT_HEIGHT = PanelFrame.HEADER + ItemGrid.BASE_TILE + ItemGrid.TILE_GAP + PanelFrame.PAD;
	/** How many candidates the shrinking keeps per step; the screen has few things on it. */
	private static final int MOST_CANDIDATES = 16;

	/** Where the panels are drawn this frame; null for one that is off or has no room. */
	static final class Fit {
		final EnumMap<ItemPanelKind, int[]> boxes = new EnumMap<>(ItemPanelKind.class);
		/** Whether Pinned had no room of its own and is a tab of the Items panel. */
		boolean pinnedAsTab;

		int[] box(ItemPanelKind kind) {
			return boxes.get(kind);
		}
	}

	private ItemPanelLayout() {
	}

	// ------------------------------------------------------------------------------------------------ the fit

	/**
	 * @param gui       the inventory or chest window, which no panel covers
	 * @param obstacles what the other overlays draw this frame, which the panels step aside from
	 * @param wanted    where each shown panel was put; the panels not in it are off
	 * @param itemScale the Items panel's item size, so it is trimmed to whole items
	 */
	static Fit fit(int screenWidth, int screenHeight, int[] gui, List<int[]> obstacles, Map<ItemPanelKind, int[]> wanted, float itemScale) {
		Fit fit = new Fit();
		List<int[]> blocked = new ArrayList<>(obstacles.size() + ItemPanelKind.values().length + 1);
		blocked.add(gui);
		blocked.addAll(obstacles);
		for (ItemPanelKind kind : ItemPanelKind.values()) {
			int[] want = wanted.get(kind);
			if (want == null) {
				continue;
			}
			// a panel put smaller than its least size (a layout on a small screen) gets its least size first
			int[] box = onScreen(new int[]{want[0], want[1], Math.max(want[2], kind.leastWidth), Math.max(want[3], kind.leastHeight)}, screenWidth, screenHeight);
			box = box == null || box[2] < kind.leastWidth || box[3] < kind.leastHeight ? null : box;
			box = box == null ? null : placed(box, blocked, kind.leastWidth, kind.leastHeight, screenWidth, screenHeight);
			if (box != null && kind == ItemPanelKind.ITEMS) {
				int[] trimmed = ItemGrid.trimItems(box, itemScale);
				// at a small item size whole items can be less than the least size: then it keeps the room it has
				box = trimmed[2] >= kind.leastWidth && trimmed[3] >= kind.leastHeight ? trimmed : box;
			}
			if (box == null) {
				if (kind == ItemPanelKind.PINNED && fit.boxes.containsKey(ItemPanelKind.ITEMS)) {
					fit.pinnedAsTab = true;
				}
				continue;
			}
			fit.boxes.put(kind, box);
			blocked.add(box);
		}
		return fit;
	}

	/** @return the box no larger than the screen without its margins and moved onto it, or null when the screen is too small for anything */
	static int[] onScreen(int[] box, int screenWidth, int screenHeight) {
		int roomWidth = screenWidth - 2 * MARGIN;
		int roomHeight = screenHeight - 2 * MARGIN;
		if (roomWidth <= 0 || roomHeight <= 0) {
			return null;
		}
		int width = Math.min(box[2], roomWidth);
		int height = Math.min(box[3], roomHeight);
		int x = Math.max(MARGIN, Math.min(box[0], screenWidth - MARGIN - width));
		int y = Math.max(MARGIN, Math.min(box[1], screenHeight - MARGIN - height));
		return new int[]{x, y, width, height};
	}

	/** @return the box where it is when nothing is in the way; else shrunk toward its free room; else moved beside what is in the way; else null */
	static int[] placed(int[] box, List<int[]> blocked, int leastWidth, int leastHeight, int screenWidth, int screenHeight) {
		if (free(box, blocked)) {
			return box.clone();
		}
		int[] shrunk = largestFreePart(box, blocked, leastWidth, leastHeight);
		if (shrunk != null) {
			return shrunk;
		}
		int[] moved = nearestFreeSpot(box, blocked, screenWidth, screenHeight);
		if (moved == null && (box[2] > leastWidth || box[3] > leastHeight)) {
			moved = nearestFreeSpot(new int[]{box[0], box[1], leastWidth, leastHeight}, blocked, screenWidth, screenHeight);
		}
		return moved;
	}

	static boolean overlaps(int[] a, int[] b) {
		return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
	}

	static boolean free(int[] box, List<int[]> blocked) {
		for (int[] other : blocked) {
			if (overlaps(box, other)) {
				return false;
			}
		}
		return true;
	}

	/** @return the largest part of the box clear of everything in the way, a gap away from it, at least the least size; null without one */
	private static int[] largestFreePart(int[] box, List<int[]> blocked, int leastWidth, int leastHeight) {
		List<int[]> parts = new ArrayList<>();
		parts.add(box);
		for (int[] other : blocked) {
			List<int[]> next = new ArrayList<>();
			for (int[] part : parts) {
				if (!overlaps(part, other)) {
					next.add(part);
					continue;
				}
				addIfLarge(next, new int[]{part[0], part[1], other[0] - GAP - part[0], part[3]}, leastWidth, leastHeight);
				int right = other[0] + other[2] + GAP;
				addIfLarge(next, new int[]{right, part[1], part[0] + part[2] - right, part[3]}, leastWidth, leastHeight);
				addIfLarge(next, new int[]{part[0], part[1], part[2], other[1] - GAP - part[1]}, leastWidth, leastHeight);
				int bottom = other[1] + other[3] + GAP;
				addIfLarge(next, new int[]{part[0], bottom, part[2], part[1] + part[3] - bottom}, leastWidth, leastHeight);
			}
			next.sort((a, b) -> Long.compare((long) b[2] * b[3], (long) a[2] * a[3]));
			parts = next.size() > MOST_CANDIDATES ? new ArrayList<>(next.subList(0, MOST_CANDIDATES)) : next;
			if (parts.isEmpty()) {
				return null;
			}
		}
		return parts.getFirst();
	}

	private static void addIfLarge(List<int[]> parts, int[] part, int leastWidth, int leastHeight) {
		if (part[2] >= leastWidth && part[3] >= leastHeight) {
			parts.add(part);
		}
	}

	/** @return the box moved the shortest way to a free spot beside something in the way, on the screen; null without one */
	private static int[] nearestFreeSpot(int[] box, List<int[]> blocked, int screenWidth, int screenHeight) {
		int[] best = null;
		long bestDistance = Long.MAX_VALUE;
		for (int[] other : blocked) {
			int[][] spots = {
				{other[0] - GAP - box[2], box[1]}, {other[0] + other[2] + GAP, box[1]},
				{box[0], other[1] - GAP - box[3]}, {box[0], other[1] + other[3] + GAP}};
			for (int[] spot : spots) {
				int[] candidate = onScreen(new int[]{spot[0], spot[1], box[2], box[3]}, screenWidth, screenHeight);
				if (candidate == null || candidate[2] < box[2] || candidate[3] < box[3] || !free(candidate, blocked)) {
					continue;
				}
				long dx = candidate[0] - box[0];
				long dy = candidate[1] - box[1];
				long distance = dx * dx + dy * dy;
				if (distance < bestDistance) {
					bestDistance = distance;
					best = candidate;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------------------------------------ the starting layouts

	/**
	 * @param gui           the inventory or chest window
	 * @param itemScale     the item size, for the Items panel's width in whole items
	 * @param detailsDocked whether the details window is a panel of its own
	 * @return where each panel starts on this screen: Pinned at the top left with Recent under it, Items at the top right, the search bar at the
	 *         bottom center (and the details window, docked, under Recent)
	 */
	static EnumMap<ItemPanelKind, int[]> starting(int screenWidth, int screenHeight, int[] gui, float itemScale, boolean detailsDocked) {
		EnumMap<ItemPanelKind, int[]> boxes = new EnumMap<>(ItemPanelKind.class);
		int leftRoom = gui[0] - PRESET_GAP - MARGIN;
		int rightRoom = screenWidth - MARGIN - (gui[0] + gui[2]) - PRESET_GAP;
		int fullHeight = screenHeight - 2 * MARGIN;
		int[] search = searchBar(screenWidth, screenHeight);
		int itemsWidth = itemsWidthIn(rightRoom, itemScale);
		boxes.put(ItemPanelKind.ITEMS, new int[]{screenWidth - MARGIN - itemsWidth, MARGIN, itemsWidth, fullHeight});
		int sideWidth = Math.max(0, Math.min(MOST_SIDE_WIDTH, leftRoom));
		int pinnedHeight = Math.round(fullHeight * PINNED_SHARE);
		boxes.put(ItemPanelKind.PINNED, new int[]{MARGIN, MARGIN, sideWidth, pinnedHeight});
		int recentY = MARGIN + pinnedHeight + GAP;
		boxes.put(ItemPanelKind.RECENT, new int[]{MARGIN, recentY, sideWidth, RECENT_HEIGHT});
		if (detailsDocked) {
			int detailsY = recentY + RECENT_HEIGHT + GAP;
			boxes.put(ItemPanelKind.DETAILS, new int[]{MARGIN, detailsY, sideWidth, screenHeight - MARGIN - detailsY});
		}
		boxes.put(ItemPanelKind.SEARCH, search);
		return boxes;
	}

	/** @return the search bar where the starting layout has it: at the bottom center of the screen */
	static int[] searchBar(int screenWidth, int screenHeight) {
		int width = Math.min(MOST_SEARCH_WIDTH, screenWidth - 2 * MARGIN);
		return new int[]{(screenWidth - width) / 2, screenHeight - MARGIN - ItemPanelKind.SEARCH.leastHeight, width, ItemPanelKind.SEARCH.leastHeight};
	}

	/** @return the Items panel's width in whole items in a room, at most the starting layouts' most columns */
	private static int itemsWidthIn(int room, float itemScale) {
		int columns = Math.min(MOST_PRESET_COLUMNS, ItemGrid.columns(room - 2 * PanelFrame.PAD - ItemGrid.SCROLLBAR_ROOM, itemScale));
		return Math.max(ItemGrid.itemsWidth(ItemGrid.LEAST_COLUMNS, itemScale), ItemGrid.itemsWidth(columns, itemScale));
	}
}
