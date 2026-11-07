package dev.tim1337.lumio.skyblock.slayer;

/**
 * The beacon of a watched Voidgloom Seraph through its short life: it is thrown as an entity (an armor stand wearing it, or a falling block), lands, and then is a
 * real block until it is destroyed. Only the thrown entity can be told apart by its owner, so the block is looked for around that entity, while it is there and for a moment
 * after it disappeared. Plain numbers, so it is unit tested.
 */
final class BeaconWatch {
	/** How long after the thrown beacon disappears its block is looked for, in ticks. */
	static final int LAND_TICKS = 40;
	/** How far from where the thrown beacon was last seen its block can be, in blocks: across, and up and down, since it falls. */
	static final int LAND_ACROSS = 5;
	static final int LAND_BELOW = 10;
	static final int LAND_ABOVE = 3;
	private static final int NO_CARRIER = Integer.MIN_VALUE;

	enum State {
		NONE, THROWN, LANDING, PLACED
	}

	private State state = State.NONE;
	private double x;
	private double y;
	private double z;
	private int lostAt;
	private int blockX;
	private int blockY;
	private int blockZ;
	private int carrier = NO_CARRIER;
	private int spentCarrier = NO_CARRIER;

	/**
	 * A watched boss's thrown beacon is seen at a place, carried by an entity; a new throw ends what was watched before. The carrier of one that has landed can stay around, and does not
	 * throw it again.
	 */
	void thrown(int entity, double atX, double atY, double atZ) {
		if (entity == spentCarrier) {
			return;
		}
		state = State.THROWN;
		carrier = entity;
		x = atX;
		y = atY;
		z = atZ;
	}

	/** No thrown beacon of a watched boss is up in this tick. */
	void noneThrown(int tick) {
		if (state == State.THROWN) {
			state = State.LANDING;
			lostAt = tick;
		} else if (state == State.LANDING && tick - lostAt > LAND_TICKS) {
			state = State.NONE;
		}
	}

	/** Its block was found. */
	void landed(int atX, int atY, int atZ) {
		state = State.PLACED;
		spentCarrier = carrier;
		blockX = atX;
		blockY = atY;
		blockZ = atZ;
		x = atX + 0.5;
		y = atY + 0.5;
		z = atZ + 0.5;
	}

	/** Its block is gone: destroyed by the player or by the server. */
	void clear() {
		state = State.NONE;
	}

	State state() {
		return state;
	}

	/** @return whether the beacon needs attention now */
	boolean up() {
		return state != State.NONE;
	}

	boolean placed() {
		return state == State.PLACED;
	}

	/** @return whether its block may be there by now: while it flies, and for a moment after */
	boolean searching() {
		return state == State.THROWN || state == State.LANDING;
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
}
