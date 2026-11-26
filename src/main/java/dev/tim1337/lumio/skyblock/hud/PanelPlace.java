package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.hud.HudAnchors;

import com.google.gson.JsonObject;

/**
 * Where the player put a panel of the item search, kept so it survives other window sizes and GUI scales: per axis an anchor and an offset in GUI
 * units, and the panel's size. The anchor is a screen edge or its middle (the HUD's anchors, {@link HudAnchors}), or an edge of the inventory or chest
 * window (the GUI): just before it, along its start or end, or just after it. A panel beside the inventory stays beside it, one along the screen's edge
 * stays there. Plain numbers, so it is unit tested.
 */
final class PanelPlace {
	static final int SCREEN_START = HudAnchors.START;
	static final int SCREEN_END = HudAnchors.END;
	/** Its end is this far before the GUI's start. */
	static final int GUI_BEFORE = 3;
	/** Its start is this far after the GUI's start. */
	static final int GUI_START = 4;
	/** Its end is this far before the GUI's end. */
	static final int GUI_END = 5;
	/** Its start is this far after the GUI's end. */
	static final int GUI_AFTER = 6;
	private static final int MOST_ANCHOR = GUI_AFTER;
	/** Within this of a screen edge, a panel is kept to that edge. */
	static final int EDGE_NEAR = 14;
	/** The biggest saved size or offset that is read; a broken file can't push a panel anywhere. */
	private static final int MOST_UNITS = 10_000;

	final int xAnchor;
	final float xOffset;
	final int yAnchor;
	final float yOffset;
	final int width;
	final int height;

	PanelPlace(int xAnchor, float xOffset, int yAnchor, float yOffset, int width, int height) {
		this.xAnchor = xAnchor;
		this.xOffset = xOffset;
		this.yAnchor = yAnchor;
		this.yOffset = yOffset;
		this.width = width;
		this.height = height;
	}

	/** @return the panel's box on a screen with the GUI at a place: x, y, width, height */
	int[] box(int screenWidth, int screenHeight, int[] gui) {
		int x = Math.round(start(xAnchor, xOffset, width, screenWidth, gui[0], gui[2]));
		int y = Math.round(start(yAnchor, yOffset, height, screenHeight, gui[1], gui[3]));
		return new int[]{x, y, width, height};
	}

	/** @return the place that puts a panel at a box on this screen, with the anchors that fit where it is */
	static PanelPlace of(int[] box, int screenWidth, int screenHeight, int[] gui) {
		int xAnchor = anchor(box[0], box[2], screenWidth, gui[0], gui[2]);
		int yAnchor = anchor(box[1], box[3], screenHeight, gui[1], gui[3]);
		return new PanelPlace(xAnchor, offset(xAnchor, box[0], box[2], screenWidth, gui[0], gui[2]), yAnchor,
			offset(yAnchor, box[1], box[3], screenHeight, gui[1], gui[3]), box[2], box[3]);
	}

	/** @return the same place with another size, its anchored edge kept */
	PanelPlace resized(int newWidth, int newHeight, int screenWidth, int screenHeight, int[] gui) {
		int[] box = box(screenWidth, screenHeight, gui);
		return of(new int[]{box[0], box[1], newWidth, newHeight}, screenWidth, screenHeight, gui);
	}

	/** @return the start (left or top) along one axis */
	static float start(int anchor, float offset, float size, float screen, float guiStart, float guiSize) {
		return switch (anchor) {
			case GUI_BEFORE -> guiStart - offset - size;
			case GUI_START -> guiStart + offset;
			case GUI_END -> guiStart + guiSize - offset - size;
			case GUI_AFTER -> guiStart + guiSize + offset;
			default -> HudAnchors.start(anchor, offset, size, screen);
		};
	}

	/** @return the offset along one axis that puts the start at a place: the inverse of {@link #start} */
	static float offset(int anchor, float start, float size, float screen, float guiStart, float guiSize) {
		return switch (anchor) {
			case GUI_BEFORE -> guiStart - start - size;
			case GUI_START -> start - guiStart;
			case GUI_END -> guiStart + guiSize - start - size;
			case GUI_AFTER -> start - guiStart - guiSize;
			default -> HudAnchors.offset(anchor, start, size, screen);
		};
	}

	/**
	 * @return the anchor along one axis: a screen edge the panel lies against; else the GUI when the panel lies beside it and nearer to it than to the
	 *         screen's edge on that side, or along it when its middle is within the GUI; else the nearest third of the screen
	 */
	static int anchor(float start, float size, float screen, float guiStart, float guiSize) {
		float end = start + size;
		float guiEnd = guiStart + guiSize;
		if (start <= EDGE_NEAR) {
			return SCREEN_START;
		}
		if (screen - end <= EDGE_NEAR) {
			return SCREEN_END;
		}
		if (end <= guiStart) {
			return guiStart - end <= start ? GUI_BEFORE : SCREEN_START;
		}
		if (start >= guiEnd) {
			return start - guiEnd <= screen - end ? GUI_AFTER : SCREEN_END;
		}
		float middle = start + size / 2f;
		if (middle >= guiStart && middle <= guiEnd) {
			return GUI_START;
		}
		return HudAnchors.nearest(start, size, screen);
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("xa", xAnchor);
		json.addProperty("x", xOffset);
		json.addProperty("ya", yAnchor);
		json.addProperty("y", yOffset);
		json.addProperty("w", width);
		json.addProperty("h", height);
		return json;
	}

	/** @return the place read from the file, or null where it is broken */
	static PanelPlace fromJson(JsonObject json) {
		try {
			int xa = json.get("xa").getAsInt();
			int ya = json.get("ya").getAsInt();
			float x = json.get("x").getAsFloat();
			float y = json.get("y").getAsFloat();
			int w = json.get("w").getAsInt();
			int h = json.get("h").getAsInt();
			if (xa < 0 || xa > MOST_ANCHOR || ya < 0 || ya > MOST_ANCHOR || !Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > MOST_UNITS
				|| Math.abs(y) > MOST_UNITS || w <= 0 || h <= 0 || w > MOST_UNITS || h > MOST_UNITS) {
				return null;
			}
			return new PanelPlace(xa, x, ya, y, w, h);
		} catch (RuntimeException broken) {
			return null;
		}
	}
}
