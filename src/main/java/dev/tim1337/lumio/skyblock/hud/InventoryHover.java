package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.motion.Spring;

/**
 * The one rule for a HUD panel drawn again over an open screen: the panel itself stays under the screen's dimming like every other, and comes up bright only while the mouse is over it, to be clicked. Every panel that draws over a screen goes through
 * this.
 */
public final class InventoryHover {
	private static final float INVISIBLE = 0.01f;

	private final Spring spring = Spring.smooth(Spring.SNAPPY);

	/** @return whether a point lies on a panel's box of x, y, width and height */
	public static boolean over(int[] box, double mouseX, double mouseY) {
		return mouseX >= box[0] && mouseY >= box[1] && mouseX < box[0] + box[2] && mouseY < box[1] + box[3];
	}

	/** @return how strongly to draw a panel over the screen this frame, from 0 to 1; at 0 it is not drawn there */
	public float lit(int[] box, double mouseX, double mouseY) {
		return Math.max(0f, Math.min(1f, spring.follow(over(box, mouseX, mouseY) ? 1f : 0f)));
	}

	/** @return whether a panel at this strength is drawn at all */
	public static boolean drawn(float lit) {
		return lit >= INVISIBLE;
	}
}
