package dev.tim1337.lumio.skyblock.dungeon.f7;

import java.util.List;

/**
 * The logic of the Floor 7 device hints, without the game: the clicks each frame of Arrow Align still needs to match the known layouts (the
 * order of the Simon Says buttons is in {@link SimonSays}). The places in the world are in {@link F7DevicesFeature}.
 */
public final class Devices {
	/** The rotations of an item frame: eight steps for a full turn. */
	static final int FRAME_STEPS = 8;
	/** Arrow Align's grid: five by five frames. */
	public static final int FRAMES = 25;

	private Devices() {
	}

	/**
	 * Arrow Align: the layout whose frames are where the frames are, and the clicks each frame needs to point the right way.
	 *
	 * @param rotations each frame's rotation (0 to 7), or -1 where there is no frame
	 * @return the clicks per frame (0 for none), or null when no layout matches
	 */
	public static int[] arrowClicks(int[] rotations, List<int[]> layouts) {
		for (int[] layout : layouts) {
			if (layout.length != FRAMES || !sameFrames(rotations, layout)) {
				continue;
			}
			int[] clicks = new int[FRAMES];
			for (int i = 0; i < FRAMES; i++) {
				clicks[i] = layout[i] < 0 ? 0 : Math.floorMod(layout[i] - rotations[i], FRAME_STEPS);
			}
			return clicks;
		}
		return null;
	}

	private static boolean sameFrames(int[] rotations, int[] layout) {
		for (int i = 0; i < FRAMES; i++) {
			if (rotations[i] < 0 != layout[i] < 0) {
				return false;
			}
		}
		return true;
	}
}
