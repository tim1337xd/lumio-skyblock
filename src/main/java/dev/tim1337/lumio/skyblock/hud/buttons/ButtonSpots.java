package dev.tim1337.lumio.skyblock.hud.buttons;

/**
 * The 28 fixed places of the inventory buttons around a container window: a row of nine above and below it, lined up with the slot columns, and a
 * column of five on each side, around the window's middle; and the pencil in the top right corner. Tied to the window, so they follow it at every
 * screen size and GUI scale and move with a taller chest. A spot that would leave the screen is not used. Plain math, so it is unit tested.
 */
final class ButtonSpots {
	static final int COUNT = 28;
	static final int ROW = 9;
	static final int COLUMN = 5;
	static final int TOP_FIRST = 0;
	static final int BOTTOM_FIRST = ROW;
	static final int LEFT_FIRST = 2 * ROW;
	static final int RIGHT_FIRST = 2 * ROW + COLUMN;
	/** The distance between two spots: a slot's, so the rows line up with the slot columns. */
	static final int PITCH = 18;
	/** The room between the window's edge and the spots, in GUI pixels. */
	static final int GAP = 3;
	/** What {@link #place} fills in. */
	static final int X = 0;
	static final int Y = 1;

	private ButtonSpots() {
	}

	/**
	 * Places a spot.
	 *
	 * @param window the container window: x, y, width, height in GUI pixels
	 * @param size   the button's size, at most {@link #PITCH}
	 * @param out    gets the spot's top left corner
	 * @return whether the spot lies wholly on the screen
	 */
	static boolean place(int index, int[] window, int size, int screenWidth, int screenHeight, int[] out) {
		int middleX = window[0] + window[2] / 2;
		int middleY = window[1] + window[3] / 2;
		int x;
		int y;
		if (index < BOTTOM_FIRST) {
			x = middleX + (index - ROW / 2) * PITCH - size / 2;
			y = window[1] - GAP - size;
		} else if (index < LEFT_FIRST) {
			x = middleX + (index - BOTTOM_FIRST - ROW / 2) * PITCH - size / 2;
			y = window[1] + window[3] + GAP;
		} else if (index < RIGHT_FIRST) {
			x = window[0] - GAP - size;
			y = middleY + (index - LEFT_FIRST - COLUMN / 2) * PITCH - size / 2;
		} else {
			x = window[0] + window[2] + GAP;
			y = middleY + (index - RIGHT_FIRST - COLUMN / 2) * PITCH - size / 2;
		}
		out[X] = x;
		out[Y] = y;
		return onScreen(x, y, size, screenWidth, screenHeight);
	}

	/** Places the pencil: in the top right corner, beside the top row and above the right column. */
	static boolean pencil(int[] window, int size, int screenWidth, int screenHeight, int[] out) {
		out[X] = window[0] + window[2] + GAP;
		out[Y] = window[1] - GAP - size;
		return onScreen(out[X], out[Y], size, screenWidth, screenHeight);
	}

	private static boolean onScreen(int x, int y, int size, int screenWidth, int screenHeight) {
		return x >= 0 && y >= 0 && x + size <= screenWidth && y + size <= screenHeight;
	}

	/** @return where a spot is, for the editor: "Top row · spot 2" */
	static String label(int index) {
		if (index < BOTTOM_FIRST) {
			return "Top row · spot " + (index + 1);
		}
		if (index < LEFT_FIRST) {
			return "Bottom row · spot " + (index - BOTTOM_FIRST + 1);
		}
		if (index < RIGHT_FIRST) {
			return "Left · spot " + (index - LEFT_FIRST + 1);
		}
		return "Right · spot " + (index - RIGHT_FIRST + 1);
	}
}
