package dev.tim1337.lumio.skyblock.hud;

/**
 * Where the search bar, the item area and an item's window go, at any window size and GUI scale. The area starts a gap right of the inventory or chest
 * window and reaches to the right edge of the screen, so its columns follow from the room; the bar sits under it at the bottom of the screen, centered
 * on it, and the rows fill the height down to above the bar. With room for fewer than a few columns beside the window (a small window, a high GUI
 * scale) the area goes below the window, or above it where there is more room there. The HUD is not drawn under it (see {@link ItemListFeature}), so
 * no panel takes room from it. Plain numbers, so it is unit tested.
 */
final class ItemListLayout {
	/** Narrower or lower than this, the area is left out and only the bar stays. */
	static final int LEAST_LIST_WIDTH = 48;
	static final int LEAST_LIST_HEIGHT = 40;
	/** The room kept to the screen's edges, and between the area and the bar. */
	static final int MARGIN = 8;
	static final int GAP = 10;
	/** The gap between the window and the area beside it, and the area's margin to the screen's right edge. */
	static final int AREA_GAP = 24;
	static final int RIGHT_MARGIN = 16;
	/** The room between the bar and the screen's bottom edge. */
	static final int BAR_BOTTOM = 6;
	/** The item's window: its widest and narrowest, its height, and its margin to the screen and the list. */
	static final int DETAIL_WIDTH = 236;
	static final int DETAIL_LEAST_WIDTH = 176;
	static final int DETAIL_HEIGHT = 226;
	static final int DETAIL_MARGIN = 4;

	/** Where things go: the bar, and the area's box (its width below the least means no area). */
	record Places(int[] bar, int[] list) {
		boolean hasList() {
			return list[2] >= LEAST_LIST_WIDTH && list[3] >= LEAST_LIST_HEIGHT;
		}
	}

	private ItemListLayout() {
	}

	/**
	 * @param window     the inventory or chest window: x, y, width, height
	 * @param barHeight  the bar's height
	 * @param leastWidth the least width beside the window (a few columns); with less, the area goes below or above the window
	 */
	static Places places(int screenWidth, int screenHeight, int[] window, int mostBarWidth, int barHeight, int leastWidth) {
		int barY = screenHeight - BAR_BOTTOM - barHeight;
		int left = window[0] + window[2] + AREA_GAP;
		int width = screenWidth - RIGHT_MARGIN - left;
		if (width >= leastWidth) {
			return beside(left, width, barY, mostBarWidth, barHeight);
		}
		Places stacked = stacked(screenWidth, screenHeight, window, mostBarWidth, barHeight);
		// no room below or above either: a narrow column beside the window is better than none
		return stacked.hasList() || width < LEAST_LIST_WIDTH ? stacked : beside(left, width, barY, mostBarWidth, barHeight);
	}

	/** The area beside the window, from its gap to the right edge, with the bar centered under it. */
	private static Places beside(int left, int width, int barY, int mostBarWidth, int barHeight) {
		int barWidth = Math.min(mostBarWidth, width);
		int[] bar = {left + (width - barWidth) / 2, barY, barWidth, barHeight};
		return new Places(bar, new int[]{left, MARGIN, width, Math.max(0, barY - GAP - MARGIN)});
	}

	/** The area across the screen below the window, or above it where there is more room there. */
	private static Places stacked(int screenWidth, int screenHeight, int[] window, int mostBarWidth, int barHeight) {
		int[] bar = SearchBarPlace.place(screenWidth, screenHeight, window, Math.min(mostBarWidth, screenWidth - 2 * MARGIN), barHeight);
		int across = screenWidth - 2 * MARGIN;
		int belowTop = window[1] + window[3] + GAP;
		int belowBottom = Math.min(bar[1], screenHeight) - GAP;
		int aboveBottom = window[1] - GAP;
		boolean below = belowBottom - belowTop >= aboveBottom - MARGIN;
		int top = below ? belowTop : MARGIN;
		int bottom = below ? belowBottom : Math.min(aboveBottom, bar[1] - GAP);
		return new Places(bar, new int[]{MARGIN, top, across, Math.max(0, bottom - top)});
	}

	/**
	 * @param window  the inventory or chest window, whose place it takes
	 * @param listLeft where the list starts on its right, or the screen's width without a list
	 * @return an item's window: centered on the inventory, clear of the list and on the screen
	 */
	static int[] detail(int screenWidth, int screenHeight, int[] window, int listLeft) {
		int right = Math.min(listLeft - DETAIL_MARGIN, screenWidth - DETAIL_MARGIN);
		int room = right - DETAIL_MARGIN;
		if (room < DETAIL_LEAST_WIDTH) {
			// a tiny screen: the list's room is given up for it
			right = screenWidth - DETAIL_MARGIN;
			room = right - DETAIL_MARGIN;
		}
		int width = Math.min(DETAIL_WIDTH, room);
		int height = Math.min(DETAIL_HEIGHT, screenHeight - 2 * DETAIL_MARGIN);
		int x = Math.max(DETAIL_MARGIN, Math.min(window[0] + (window[2] - width) / 2, right - width));
		int y = Math.max(DETAIL_MARGIN, Math.min(window[1] + (window[3] - height) / 2, screenHeight - DETAIL_MARGIN - height));
		return new int[]{x, y, width, height};
	}
}
