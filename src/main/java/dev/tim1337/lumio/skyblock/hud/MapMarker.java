package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.world.entity.player.PlayerSkin;

/** The players' markers on Lumio's maps (the dungeon map, the Crystal Hollows map): an arrow the way a player looks, or their face with a ring. */
public final class MapMarker {
	/** The arrow's size, in GUI pixels. */
	public static final float ARROW = 9f;
	/** A face on the map, in GUI pixels, and the ring in its color around it. */
	private static final int HEAD = 7;
	private static final float HEAD_RING = 1f;
	private static final float HEAD_RADIUS = 1.5f;
	/** The arrow: Lumio's arrowhead icon, its dark outline in GUI pixels, and the soft shadow under every marker. */
	private static final String ARROW_ICON = "navigate";
	private static final int ARROW_OUTLINE = 1;
	private static final int ARROW_EDGE = 0xCC111114;
	private static final int SHADOW = 0x66000000;
	private static final float SHADOW_SHARE = 0.9f;
	private static final float SKINLESS_OPACITY = 0.6f;

	private MapMarker() {
	}

	/**
	 * An arrow in the direction a player looks (yaw 0 looks south, down on the map): a clean arrowhead with a thin dark outline and a soft shadow.
	 *
	 * @param scale the arrow's size against {@link #ARROW}
	 */
	public static void arrow(Canvas ui, GuiGraphicsExtractor graphics, float x, float y, float yaw, int color, float scale) {
		ui.softLight(x, y + 1f, ARROW * scale * SHADOW_SHARE, SHADOW);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate((float) Math.toRadians(yaw + 180f));
		int size = Math.round(ARROW * scale);
		int outlined = size + 2 * ARROW_OUTLINE;
		// the outline: the same arrow a little larger in dark under it
		ui.icon(ARROW_ICON, -outlined / 2, -outlined / 2, outlined, ARROW_EDGE);
		ui.icon(ARROW_ICON, -size / 2, -size / 2, size, color);
		pose.popMatrix();
	}

	/** A player's own face (8x8 with the hat layer), small, with a thin ring in their color; their color alone while the skin is unknown. */
	public static void head(Canvas ui, GuiGraphicsExtractor graphics, PlayerSkin skin, float x, float y, int color) {
		int left = Math.round(x - HEAD / 2f);
		int top = Math.round(y - HEAD / 2f);
		ui.softLight(x, y + 1f, HEAD * SHADOW_SHARE, SHADOW);
		ui.roundedSmooth(left - HEAD_RING, top - HEAD_RING, HEAD + 2 * HEAD_RING, HEAD + 2 * HEAD_RING, HEAD_RADIUS + HEAD_RING, color);
		if (skin != null) {
			PlayerFaceExtractor.extractRenderState(graphics, skin, left, top, HEAD);
		} else {
			ui.roundedSmooth(left, top, HEAD, HEAD, HEAD_RADIUS, Draw.withOpacity(color, SKINLESS_OPACITY));
		}
	}
}
