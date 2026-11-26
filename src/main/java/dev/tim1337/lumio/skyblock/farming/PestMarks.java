package dev.tim1337.lumio.skyblock.farming;

/**
 * How the pests of the Garden are told from everything else, and which color their glow has. A pest is two things in the world: a name tag
 * that carries the pest sign ("ൠ Fly 1,200❤"), and right under or beside it the body the player sees, a stand wearing the pest's head. The
 * body nearest to a pest's name tag is the pest; a stand with a head and no pest name tag near it is something else (a decoration, a visitor's
 * item, a sprinkler). Plain logic without the game, so it is unit tested.
 */
final class PestMarks {
	/** The sign Hypixel puts before a pest's name, in its name tag, in chat and on the scoreboard. */
	static final String SIGN = "ൠ";
	/** How far a pest's body may be from its name tag: across, and up or down. A name tag floats over its pest. */
	static final double BODY_ACROSS = 1.5;
	static final double BODY_UP_DOWN = 3.0;

	private PestMarks() {
	}

	/** @return whether a name tag is a pest's: it carries the pest sign, with or without color codes around it */
	static boolean pestTag(String name) {
		return name != null && name.contains(SIGN);
	}

	/**
	 * @param tagX   the name tag's place
	 * @param bodies the places of the stands that wear a head, as x, y, z one after the other
	 * @param count  how many of them are filled
	 * @return which body is this name tag's pest: the nearest one within reach, or -1 when none is
	 */
	static int body(double tagX, double tagY, double tagZ, double[] bodies, int count) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			double dx = bodies[i * 3] - tagX;
			double dy = bodies[i * 3 + 1] - tagY;
			double dz = bodies[i * 3 + 2] - tagZ;
			if (dx * dx + dz * dz > BODY_ACROSS * BODY_ACROSS || Math.abs(dy) > BODY_UP_DOWN) {
				continue;
			}
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	/** @return the glow's color as 0xRRGGBB: the menu's color while "Use the menu color" is on, else the player's own */
	static int color(boolean useMenuColor, int menuRgb, int ownRgb) {
		return (useMenuColor ? menuRgb : ownRgb) & 0xFFFFFF;
	}
}
