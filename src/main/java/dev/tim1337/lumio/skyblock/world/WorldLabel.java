package dev.tim1337.lumio.skyblock.world;

import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import org.joml.Vector3fc;

/**
 * Labels that stand at a place in the world, like the game's name tags: they have a fixed size in the world, so they get smaller as the player moves away, and fade out
 * where their text would be too small to read. The place is worked out on the screen and the label is drawn with the HUD, so it always faces the player. What a label
 * shows, and whether the player can see it, is up to its feature: a label is never shown through a wall.
 */
public final class WorldLabel {
	/** What {@link #project} fills in: x and y in GUI pixels, how large a GUI pixel of the label is there, and how much of it shows. */
	public static final int PLACE_VALUES = 4;
	public static final int X = 0;
	public static final int Y = 1;
	public static final int SIZE = 2;
	public static final int FADE = 3;
	/** A point closer than this in front of the camera, in blocks, counts as behind it. */
	private static final double NEAR = 0.2;
	/** How large a GUI pixel of a label is in the world, in blocks: a card reads well from 3 to 8 blocks away, and a name tag's pixel is 0.025. */
	private static final double BLOCKS_PER_PIXEL = 0.02;
	/** It stops growing this close, in blocks, so that standing in a mob does not fill the screen with its label. */
	private static final double NEAREST_BLOCKS = 2.0;
	/** How tall small text is, in the label's GUI pixels. */
	private static final float TEXT_PIXELS = TextScale.SMALL * 8f;
	/** How tall the text is on the screen, in screen pixels, where the label starts to fade, and where it has gone. */
	private static final float FADE_FROM_PIXELS = 8f;
	private static final float GONE_AT_PIXELS = 6f;
	private static final float SHOWN = 0.01f;

	private WorldLabel() {
	}

	/**
	 * Finds where a place in the world is on the screen, and how large a label is there.
	 *
	 * @param out receives {@link #PLACE_VALUES} numbers: {@link #X}, {@link #Y}, {@link #SIZE} and {@link #FADE}
	 * @return false when it is behind the camera, or too far away for its label to show
	 */
	public static boolean project(Minecraft client, double x, double y, double z, float[] out) {
		return project(client, x, y, z, 1f, out);
	}

	/** Same, for a label with a size of its own on top of the distance. */
	public static boolean project(Minecraft client, double x, double y, double z, float ownSize, float[] out) {
		Camera camera = client.gameRenderer.mainCamera();
		var eye = camera.position();
		double dx = x - eye.x;
		double dy = y - eye.y;
		double dz = z - eye.z;
		Vector3fc forward = camera.forwardVector();
		Vector3fc up = camera.upVector();
		Vector3fc left = camera.leftVector();
		double ahead = dx * forward.x() + dy * forward.y() + dz * forward.z();
		if (ahead < NEAR) {
			return false;
		}
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		double fov = client.options.fov().get();
		double tan = Math.tan(Math.toRadians(fov) / 2.0);
		double aspect = width / (double) Math.max(1, height);
		double across = -(dx * left.x() + dy * left.y() + dz * left.z()) / ahead / (tan * aspect);
		double upward = (dx * up.x() + dy * up.y() + dz * up.z()) / ahead / tan;
		out[X] = (float) (width / 2.0 * (1.0 + across));
		out[Y] = (float) (height / 2.0 * (1.0 - upward));
		out[SIZE] = size(ahead, height, fov) * ownSize;
		out[FADE] = fade(out[SIZE], client.getWindow().getGuiScale());
		return out[FADE] >= SHOWN;
	}

	/**
	 * @param ahead  how far the place is in front of the camera, in blocks
	 * @param height the height of the screen, in GUI pixels
	 * @param fov    the camera's field of view, in degrees
	 * @return how large a label's GUI pixel is drawn there, in GUI pixels
	 */
	static float size(double ahead, int height, double fov) {
		double blocksOnScreen = 2.0 * Math.max(ahead, NEAREST_BLOCKS) * Math.tan(Math.toRadians(fov) / 2.0);
		return (float) (BLOCKS_PER_PIXEL * height / blocksOnScreen);
	}

	/**
	 * @param size     how large a label's GUI pixel is drawn, in GUI pixels
	 * @param guiScale screen pixels per GUI pixel
	 * @return how much of a label shows: all of it while its text can be read, and soft towards where it cannot
	 */
	static float fade(float size, double guiScale) {
		float textOnScreen = TEXT_PIXELS * size * (float) guiScale;
		float share = Mth.clamp((textOnScreen - GONE_AT_PIXELS) / (FADE_FROM_PIXELS - GONE_AT_PIXELS), 0f, 1f);
		return share * share * (3f - 2f * share);
	}

	/**
	 * Draws a label at a place from {@link #project}, standing on it and centered: scaled, with its text built for that size so it stays sharp, and faded.
	 *
	 * @param width   the label's width, in its GUI pixels
	 * @param height  its height, up to where it stands on the place
	 * @param opacity its own opacity, from 0 to 1, besides the fade with distance
	 * @param content draws the label with its top left at 0, 0
	 */
	public static void draw(GuiGraphicsExtractor graphics, float[] place, float width, float height, float opacity, Runnable content) {
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(place[X], place[Y]);
		pose.scale(place[SIZE], place[SIZE]);
		pose.translate(-width / 2f, -height);
		float alphaBefore = Draw.alpha();
		float zoomBefore = Draw.zoom();
		Draw.setAlpha(alphaBefore * opacity * place[FADE]);
		Draw.setZoom(zoomBefore * place[SIZE]);
		try {
			content.run();
		} finally {
			Draw.setZoom(zoomBefore);
			Draw.setAlpha(alphaBefore);
			pose.popMatrix();
		}
	}
}
