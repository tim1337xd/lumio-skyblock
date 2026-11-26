package dev.tim1337.lumio.skyblock.mining;

/**
 * The Precision Mining target on the block being mined: the server marks a spot on the block with particles, one kind for the target and another
 * while the player aims at it. Only particles of those kinds inside the bounds of the block being mined count; the target follows the newest one,
 * stays for a moment after the last and then fades, and is gone at once when the player stops mining or mines another block. Plain numbers only,
 * so it is tested without the game.
 */
final class PrecisionTarget {
	/** What a particle says: the target, or the target while the player aims at it; anything else is ignored. */
	enum Kind {
		TARGET, AIMED
	}

	/** How far from the block's middle a particle may be and still belong to it, in blocks (the block's half and a little room). */
	static final double REACH = 0.6;
	/** How long the target stays fully after its last particle, then how long it fades: gone about half a second after it. */
	static final long HOLD_MILLIS = 300L;
	static final long FADE_MILLIS = 200L;
	/** How long the server's aimed particle counts as the player being on the target. */
	static final long AIMED_MILLIS = 300L;
	/** The faces of a block: the axis (0 x, 1 y, 2 z) and its side. */
	static final int AXES = 3;

	private boolean mining;
	private int blockX;
	private int blockY;
	private int blockZ;
	private boolean has;
	private double x;
	private double y;
	private double z;
	private long lastAt;
	private long aimedAt = Long.MIN_VALUE / 2;

	/** The player mines this block now: another block than before clears the target at once. */
	void mining(int bx, int by, int bz) {
		if (!mining || bx != blockX || by != blockY || bz != blockZ) {
			mining = true;
			blockX = bx;
			blockY = by;
			blockZ = bz;
			has = false;
		}
	}

	/** The player mines nothing (or nothing the perk works on): the target is gone at once. */
	void stopped() {
		mining = false;
		has = false;
	}

	/** @return whether a point is within the bounds of a block, with a little room around it */
	static boolean inside(double px, double py, double pz, int bx, int by, int bz) {
		return Math.abs(px - (bx + 0.5)) <= REACH && Math.abs(py - (by + 0.5)) <= REACH && Math.abs(pz - (bz + 0.5)) <= REACH;
	}

	/**
	 * A particle of a kind the perk uses arrived (others are never passed here).
	 *
	 * @return whether it was taken: only while mining, and only inside the block being mined
	 */
	boolean particle(Kind kind, double px, double py, double pz, long now) {
		if (!mining || !inside(px, py, pz, blockX, blockY, blockZ)) {
			return false;
		}
		has = true;
		x = px;
		y = py;
		z = pz;
		lastAt = now;
		if (kind == Kind.AIMED) {
			aimedAt = now;
		}
		return true;
	}

	/** @return how visible the target is, from 1 (just seen) to 0 (gone) */
	float alpha(long now) {
		if (!has) {
			return 0f;
		}
		long age = now - lastAt;
		if (age <= HOLD_MILLIS) {
			return 1f;
		}
		return Math.max(0f, 1f - (age - HOLD_MILLIS) / (float) FADE_MILLIS);
	}

	/** @return whether the server says the player aims at the target now */
	boolean aimedByServer(long now) {
		return has && now - aimedAt <= AIMED_MILLIS;
	}

	double x() {
		return x;
	}

	double y() {
		return y;
	}

	double z() {
		return z;
	}

	int blockX() {
		return blockX;
	}

	int blockY() {
		return blockY;
	}

	int blockZ() {
		return blockZ;
	}

	/**
	 * @return the face of the block a point is on, as the axis times two plus the side (0 the low side, 1 the high side): the axis on which the
	 *         point is farthest from the block's middle
	 */
	static int face(double px, double py, double pz, int bx, int by, int bz) {
		double dx = px - (bx + 0.5);
		double dy = py - (by + 0.5);
		double dz = pz - (bz + 0.5);
		double ax = Math.abs(dx);
		double ay = Math.abs(dy);
		double az = Math.abs(dz);
		if (ax >= ay && ax >= az) {
			return dx >= 0 ? 1 : 0;
		}
		if (ay >= az) {
			return 2 + (dy >= 0 ? 1 : 0);
		}
		return 4 + (dz >= 0 ? 1 : 0);
	}

	/** @return whether a point on the face (as the crosshair hits it) is on the target, within the marker's radius */
	static boolean onTarget(double hitX, double hitY, double hitZ, double targetX, double targetY, double targetZ, int face, double radius) {
		int axis = face / 2;
		// the distance along the face only: the particles float a little in front of it
		double du = axis == 0 ? hitY - targetY : hitX - targetX;
		double dv = axis == 2 ? hitY - targetY : hitZ - targetZ;
		return du * du + dv * dv <= radius * radius;
	}
}
