package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.draw.Canvas;

/**
 * Fits a room's name inside its room on the map: at its size if it fits, smaller down to a limit, then over two lines at a space, and not at all
 * where even that is too big, so a name never overflows its room. Worked out once per room until the name or the room's size changes.
 */
final class RoomNameFit {
	/** How a name is shown: its lines and their text scale. */
	record Fit(String[] lines, float scale) {
	}

	private static final int ROOMS = DungeonMapData.GRID * DungeonMapData.GRID;
	private static final float STEP = 0.05f;
	private static final float LINE = 9f;

	private final String[] names = new String[ROOMS];
	private final float[] widths = new float[ROOMS];
	private final float[] heights = new float[ROOMS];
	private final Fit[] fits = new Fit[ROOMS];

	/** @return the fit of a room's name in a box, or null where it does not fit */
	Fit fit(Canvas ui, int room, String name, float width, float height, float largest, float smallest) {
		if (name.equals(names[room]) && widths[room] == width && heights[room] == height) {
			return fits[room];
		}
		names[room] = name;
		widths[room] = width;
		heights[room] = height;
		fits[room] = work(ui, name, width, height, largest, smallest);
		return fits[room];
	}

	private static Fit work(Canvas ui, String name, float width, float height, float largest, float smallest) {
		for (float scale = largest; scale >= smallest - 1e-4f; scale -= STEP) {
			if (ui.boldWidthScaled(name, scale) <= width && LINE * scale <= height) {
				return new Fit(new String[] {name}, scale);
			}
		}
		// two lines, split at the space that makes the longer line shortest
		int best = -1;
		float bestWidth = Float.MAX_VALUE;
		for (int i = name.indexOf(' '); i > 0; i = name.indexOf(' ', i + 1)) {
			float longer = Math.max(ui.boldWidthScaled(name.substring(0, i), 1f), ui.boldWidthScaled(name.substring(i + 1), 1f));
			if (longer < bestWidth) {
				bestWidth = longer;
				best = i;
			}
		}
		if (best < 0) {
			return null;
		}
		String[] lines = {name.substring(0, best), name.substring(best + 1)};
		for (float scale = largest; scale >= smallest - 1e-4f; scale -= STEP) {
			if (bestWidth * scale <= width && 2 * LINE * scale <= height) {
				return new Fit(lines, scale);
			}
		}
		return null;
	}
}
