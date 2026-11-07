package dev.tim1337.lumio.skyblock.hud;

/**
 * Where the item search field goes: at the very bottom of the screen with a small margin to the edge, in the middle of the free space right of the inventory or chest window. Where that space is too narrow for it (a high GUI scale) it is
 * centered on the screen instead; where it would cover the window there, it moves just below the window; with no room below it either, it gets narrower beside the window, and then above the window. It always has a place, as the last resort at
 * the bottom over the window's lower edge: a bar that is not there looks like a broken mod. Plain numbers, so it is unit tested without the game.
 */
final class SearchBarPlace {
	/** The room between the field and the screen's bottom edge, and between the window and the field when it moves below it. */
	static final int BOTTOM_MARGIN = 6;
	static final int BELOW_WINDOW = 4;
	/** The least room kept on each side of the field in the free space beside the window. */
	static final int BESIDE_MARGIN = 6;
	/** It reads down to this width: the icon, and a few letters. */
	static final int LEAST_WIDTH = 56;

	private SearchBarPlace() {
	}

	/**
	 * @param screenWidth  the screen's width, in GUI pixels
	 * @param screenHeight the screen's height
	 * @param window       the inventory or chest window: x, y, width, height
	 * @param width        the field's width
	 * @param height       the field's height
	 * @return the field's box (x, y, width, height), never null and always on the screen
	 */
	static int[] place(int screenWidth, int screenHeight, int[] window, int width, int height) {
		int y = screenHeight - BOTTOM_MARGIN - height;
		int windowRight = window[0] + window[2];
		int free = screenWidth - windowRight;
		if (free >= width + 2 * BESIDE_MARGIN) {
			return new int[]{windowRight + (free - width) / 2, y, width, height};
		}
		int x = Math.max(0, (screenWidth - width) / 2);
		if (!covers(x, y, width, height, window)) {
			return new int[]{x, y, width, height};
		}
		int below = window[1] + window[3] + BELOW_WINDOW;
		if (below + height <= screenHeight) {
			return new int[]{x, below, width, height};
		}
		int narrow = free - 2 * BESIDE_MARGIN;
		if (narrow >= LEAST_WIDTH) {
			return new int[]{windowRight + BESIDE_MARGIN, y, narrow, height};
		}
		int leftFree = window[0] - 2 * BESIDE_MARGIN;
		if (leftFree >= LEAST_WIDTH) {
			return new int[]{BESIDE_MARGIN, y, Math.min(width, leftFree), height};
		}
		int above = window[1] - BELOW_WINDOW - height;
		if (above >= 0) {
			return new int[]{x, above, width, height};
		}
		return new int[]{x, y, width, height};
	}

	/** @return whether a box overlaps the window */
	static boolean covers(int x, int y, int width, int height, int[] window) {
		return x < window[0] + window[2] && x + width > window[0] && y < window[1] + window[3] && y + height > window[1];
	}
}
