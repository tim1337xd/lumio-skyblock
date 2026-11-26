package dev.tim1337.lumio.skyblock.mining.hollows;

/**
 * The Crystal Hollows as a square: its bounds, its four quadrants and the Crystal Nucleus in the middle, and the way from a place in the world to a
 * point on the map. Plain math, so it is unit tested.
 */
final class HollowsGeometry {
	/** The island's square on x and z, and the heights it has, in blocks (all included). */
	static final int LOW = 202;
	static final int HIGH = 823;
	static final int LOWEST_Y = 31;
	static final int HIGHEST_Y = 188;
	/** The middle, where the quadrants meet. */
	static final double MIDDLE = (LOW + HIGH + 1) / 2.0;
	/** The Crystal Nucleus: a square around the middle, this far out each way, in blocks. */
	static final int NUCLEUS_HALF = 50;
	private static final double SPAN = HIGH + 1 - LOW;

	/** The parts of the island, each in its color. North is up (lower z), west is left (lower x). */
	enum Quadrant {
		JUNGLE("Jungle", 0x34C759),
		MITHRIL_DEPOSITS("Mithril Deposits", 0x30C8B4),
		GOBLIN_HOLDOUT("Goblin Holdout", 0xFF9F0A),
		PRECURSOR_REMNANTS("Precursor Remnants", 0x4A90FF),
		NUCLEUS("Crystal Nucleus", 0xE8E8EE);

		final String label;
		final int rgb;

		Quadrant(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
		}
	}

	private HollowsGeometry() {
	}

	/** @return whether a place is inside the island, height included */
	static boolean inside(int x, int y, int z) {
		return x >= LOW && x <= HIGH && z >= LOW && z <= HIGH && y >= LOWEST_Y && y <= HIGHEST_Y;
	}

	/** @return the part of the island at a place, or null outside the island's square */
	static Quadrant quadrantAt(double x, double z) {
		if (x < LOW || x >= HIGH + 1 || z < LOW || z >= HIGH + 1) {
			return null;
		}
		if (Math.abs(x - MIDDLE) <= NUCLEUS_HALF && Math.abs(z - MIDDLE) <= NUCLEUS_HALF) {
			return Quadrant.NUCLEUS;
		}
		boolean west = x < MIDDLE;
		boolean north = z < MIDDLE;
		if (north) {
			return west ? Quadrant.JUNGLE : Quadrant.MITHRIL_DEPOSITS;
		}
		return west ? Quadrant.GOBLIN_HOLDOUT : Quadrant.PRECURSOR_REMNANTS;
	}

	/** @return a place along x or z on a map this many pixels across, kept on the map (a place outside lies on its edge) */
	static float toMap(double world, float size) {
		double share = (world - LOW) / SPAN;
		return (float) (Math.clamp(share, 0.0, 1.0) * size);
	}

	/** @return how wide the Crystal Nucleus is on a map this many pixels across */
	static float nucleusOnMap(float size) {
		return (float) (2 * NUCLEUS_HALF / SPAN * size);
	}
}
