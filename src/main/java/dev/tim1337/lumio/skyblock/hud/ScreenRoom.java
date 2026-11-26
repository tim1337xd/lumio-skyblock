package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * What other overlays draw on the open container screen this frame (the storage and wardrobe overlays, the equipment beside the inventory, a dungeon
 * chest's profit, the trackers shown over menus): they say where, and the item search's panels step aside where they need the room. An overlay that
 * takes the whole screen over also names its main window, which the panels are then placed around instead of the game's window. Read and cleared
 * once per frame by the item search, after everything else on the screen was drawn.
 */
public final class ScreenRoom {
	/** Bounds the list: a screen has only a few overlays. */
	private static final int MOST = 32;
	/** The boxes, kept from frame to frame so nothing is made per frame. */
	private static final int[][] BOXES = new int[MOST][4];
	private static final List<int[]> TAKEN = new ArrayList<>(MOST);
	private static final int[] WINDOW = new int[4];
	private static boolean windowNamed;

	private ScreenRoom() {
	}

	/** Says that an overlay draws in a box this frame: x, y, width, height in GUI pixels. */
	public static void take(int x, int y, int width, int height) {
		if (width > 0 && height > 0 && TAKEN.size() < MOST) {
			int[] box = BOXES[TAKEN.size()];
			set(box, x, y, width, height);
			TAKEN.add(box);
		}
	}

	/** Says that an overlay draws in a box this frame. */
	public static void take(int[] box) {
		if (box != null && box.length >= 4) {
			take(box[0], box[1], box[2], box[3]);
		}
	}

	/** The boxes only the HUD keeps clear of: the item search's own panels, and an overlay's window. */
	private static final int[][] HUD_BOXES = new int[MOST][4];
	private static int hudCount;

	/**
	 * Says that something of ours is drawn in a box this frame that the HUD behind the menu must not show through: a HUD element under it is
	 * faded or steps back for as long as it is there, and only that element. The item search's panels do not move aside for these.
	 */
	public static void hud(int x, int y, int width, int height) {
		if (width > 0 && height > 0 && hudCount < MOST) {
			set(HUD_BOXES[hudCount++], x, y, width, height);
		}
	}

	/** @return whether an overlay said this frame that it draws over any part of the box (or named a window that does) */
	public static boolean covered(int x, int y, int width, int height) {
		if (windowNamed && overlaps(WINDOW, x, y, width, height)) {
			return true;
		}
		for (int[] box : TAKEN) {
			if (overlaps(box, x, y, width, height)) {
				return true;
			}
		}
		return false;
	}

	private static boolean overlaps(int[] box, int x, int y, int width, int height) {
		return x < box[0] + box[2] && x + width > box[0] && y < box[1] + box[3] && y + height > box[1];
	}

	/** An overlay that took the screen over names its main window this frame, which the panels keep around like the game's window. */
	public static void window(int x, int y, int width, int height) {
		set(WINDOW, x, y, width, height);
		windowNamed = true;
	}

	private static void set(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	/** @return the boxes taken this frame */
	static List<int[]> taken() {
		return TAKEN;
	}

	/** @return the main window an overlay named this frame, or null */
	static int[] namedWindow() {
		return windowNamed ? WINDOW : null;
	}

	/** The boxes of the frame before, for the HUD, which is drawn before the screen says its boxes again; and when they were kept. */
	private static final int[][] LAST_BOXES = new int[2 * MOST + 1][4];
	private static int lastCount;
	private static long lastAt;
	/** Boxes older than this are of a screen that is gone. */
	private static final long LAST_NANOS = 150_000_000L;

	/**
	 * Adds the boxes the overlays took in the frame before (inventory buttons, a chest's profit panel, the equipment beside the inventory, the
	 * item search's panels, the storage and wardrobe overlays) to a list: a HUD element behind the menu fades or hides where it lies under one
	 * of them, and only that element. No overlay hides the HUD as a whole.
	 */
	public static void lastTaken(List<int[]> out) {
		if (System.nanoTime() - lastAt > LAST_NANOS) {
			return;
		}
		for (int i = 0; i < lastCount; i++) {
			out.add(LAST_BOXES[i]);
		}
	}

	/** Forgets this frame's boxes; the next frame says them again. */
	static void clear() {
		// kept for the HUD of the next frame
		lastCount = TAKEN.size();
		for (int i = 0; i < lastCount; i++) {
			System.arraycopy(TAKEN.get(i), 0, LAST_BOXES[i], 0, 4);
		}
		for (int i = 0; i < hudCount; i++) {
			System.arraycopy(HUD_BOXES[i], 0, LAST_BOXES[lastCount++], 0, 4);
		}
		if (windowNamed) {
			System.arraycopy(WINDOW, 0, LAST_BOXES[lastCount++], 0, 4);
		}
		hudCount = 0;
		lastAt = System.nanoTime();
		TAKEN.clear();
		windowNamed = false;
	}
}
