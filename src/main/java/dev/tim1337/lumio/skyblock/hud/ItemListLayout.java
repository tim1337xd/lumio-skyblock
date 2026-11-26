package dev.tim1337.lumio.skyblock.hud;

/**
 * Where an item's details window goes when there is no room beside what was clicked: in the inventory or chest window's place (whose slots are then
 * away), centered on it, on the screen. Plain numbers, so it is unit tested.
 */
final class ItemListLayout {
	/** The item's window: its widest and narrowest, its height, and its margin to the screen and the list. */
	static final int DETAIL_WIDTH = 236;
	static final int DETAIL_LEAST_WIDTH = 176;
	/** The least height beside a click: the recipe grid, a row of ingredients and the action bar still fit. */
	static final int DETAIL_LEAST_HEIGHT = 226;
	static final int DETAIL_HEIGHT = 250;
	static final int DETAIL_MARGIN = 4;

	private ItemListLayout() {
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
