package dev.tim1337.lumio.skyblock.hud.buttons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The spots around the inventory (176 x 166) and chests (176 x 222 for six rows, 176 x 168 for three) on several screens, as the GUI scale makes
 * them (1920 x 1080 at scales 2, 3 and 4; 1280 x 720 at 2; 854 x 480 at 2): never over the window, never off the screen, rows lined up with the
 * slot columns.
 */
class ButtonSpotsTest {
	private static final int[][] SCREENS = {{960, 540}, {640, 360}, {480, 270}, {640, 360}, {427, 240}};
	private static final int[][] WINDOW_SIZES = {{176, 166}, {176, 222}, {176, 168}};
	private static final int[] SIZES = {14, 16, 17};

	private static int[] centered(int[] screen, int[] size) {
		return new int[]{(screen[0] - size[0]) / 2, (screen[1] - size[1]) / 2, size[0], size[1]};
	}

	@Test
	void neverOverTheWindowAndOnTheScreenWhenTheyFit() {
		int[] out = new int[2];
		for (int[] screen : SCREENS) {
			for (int[] windowSize : WINDOW_SIZES) {
				int[] window = centered(screen, windowSize);
				for (int size : SIZES) {
					for (int i = 0; i < ButtonSpots.COUNT; i++) {
						boolean fits = ButtonSpots.place(i, window, size, screen[0], screen[1], out);
						boolean overlaps = out[0] < window[0] + window[2] && out[0] + size > window[0] && out[1] < window[1] + window[3]
							&& out[1] + size > window[1];
						assertFalse(overlaps, "spot " + i + " over the window");
						if (fits) {
							assertTrue(out[0] >= 0 && out[1] >= 0 && out[0] + size <= screen[0] && out[1] + size <= screen[1], "spot " + i + " off screen");
						}
					}
				}
			}
		}
	}

	@Test
	void allFitAroundTheInventoryOnUsualScreens() {
		int[] out = new int[2];
		for (int s = 0; s < 4; s++) {
			int[] window = centered(SCREENS[s], WINDOW_SIZES[0]);
			for (int i = 0; i < ButtonSpots.COUNT; i++) {
				assertTrue(ButtonSpots.place(i, window, 16, SCREENS[s][0], SCREENS[s][1], out), "spot " + i + " on screen " + s);
			}
		}
	}

	@Test
	void aTallChestOnASmallScreenLosesTheRowsThatDoNotFit() {
		int[] out = new int[2];
		int[] window = centered(SCREENS[4], WINDOW_SIZES[1]);
		assertFalse(ButtonSpots.place(ButtonSpots.TOP_FIRST, window, 16, SCREENS[4][0], SCREENS[4][1], out), "no room above a 222 tall chest in 240");
		assertTrue(ButtonSpots.place(ButtonSpots.LEFT_FIRST + 2, window, 16, SCREENS[4][0], SCREENS[4][1], out), "the sides still fit");
	}

	@Test
	void theRowsLineUpWithTheSlotColumns() {
		int[] out = new int[2];
		int[] window = {100, 100, 176, 166};
		for (int i = 0; i < ButtonSpots.ROW; i++) {
			ButtonSpots.place(ButtonSpots.TOP_FIRST + i, window, 16, 1000, 1000, out);
			// the game's inventory slots start 8 pixels in, 18 apart
			assertEquals(window[0] + 8 + 18 * i, out[0], "top spot " + i);
			ButtonSpots.place(ButtonSpots.BOTTOM_FIRST + i, window, 16, 1000, 1000, out);
			assertEquals(window[0] + 8 + 18 * i, out[0], "bottom spot " + i);
		}
	}

	@Test
	void theyMoveWithATallerWindow() {
		int[] inventory = new int[2];
		int[] chest = new int[2];
		ButtonSpots.place(ButtonSpots.BOTTOM_FIRST, new int[]{100, 100, 176, 166}, 16, 1000, 1000, inventory);
		ButtonSpots.place(ButtonSpots.BOTTOM_FIRST, new int[]{100, 72, 176, 222}, 16, 1000, 1000, chest);
		assertEquals(inventory[1] + 28, chest[1]);
	}

	@Test
	void thePencilIsInTheCornerAndFree() {
		int[] pencil = new int[2];
		int[] spot = new int[2];
		int[] window = {100, 100, 176, 166};
		assertTrue(ButtonSpots.pencil(window, 16, 1000, 1000, pencil));
		for (int i = 0; i < ButtonSpots.COUNT; i++) {
			ButtonSpots.place(i, window, 16, 1000, 1000, spot);
			assertFalse(Math.abs(spot[0] - pencil[0]) < 16 && Math.abs(spot[1] - pencil[1]) < 16, "the pencil covers spot " + i);
		}
	}
}
