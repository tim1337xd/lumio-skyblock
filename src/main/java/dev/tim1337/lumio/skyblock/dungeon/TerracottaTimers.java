package dev.tim1337.lumio.skyblock.dungeon;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Sadan's terracotta army (F6, M6), kept simple: a block that turns into a flower pot where no timer runs starts one there, 15 seconds (12 in
 * Master Mode) counted down in server ticks, 0.05 s each; at 0 it is gone. Every timer goes on a world change, when the player leaves the boss, and
 * half a second after Sadan's "ENOUGH!". Places are block positions packed into a long. Plain logic, tested without the game.
 */
public final class TerracottaTimers {
	static final int RESPAWN_TICKS = 300;
	static final int MASTER_RESPAWN_TICKS = 240;
	/** How long after Sadan's "ENOUGH!" every timer goes, in server ticks (half a second). */
	static final int CLEAR_AFTER_TICKS = 10;
	/** Above this many ticks left the text is green, above the second orange, below red (5 s and 2 s). */
	static final long GREEN_ABOVE_TICKS = 100;
	static final long ORANGE_ABOVE_TICKS = 40;
	/** How far the text shows, in blocks: across the whole arena. */
	static final double MOST_DISTANCE = 64.0;
	/** The text's size in the world, in blocks per font pixel: twice a name tag's. */
	private static final float BASE_SCALE = 0.05f;
	/** Beyond this distance, in blocks, the text grows with the distance, so it keeps its size on the screen and stays readable. */
	private static final double STEADY_FROM_BLOCKS = 8.0;
	private static final int MOST_POTS = 64;
	/** The most texts drawn in a frame: one per pot. */
	static final int MOST_TEXTS = MOST_POTS;

	/** What a timer did, for the Debug log. */
	public interface Log {
		void removed(long place);
	}

	/** How high above a pot the text may climb looking for air, in blocks. */
	static final int MOST_CLIMB = 8;
	/** Texts closer together than this, in blocks, are pushed apart sideways, and how far each goes. */
	static final double CLOSE_BLOCKS = 1.0;
	static final double SIDEWAYS_BLOCKS = 0.45;

	/**
	 * @param air whether the block at a height of the pot's column is air
	 * @return the height of the first air block above a pot (not the pot's own); the text stands half a block above that block's bottom
	 */
	static int firstAirAbove(int potY, java.util.function.IntPredicate air) {
		for (int y = potY + 1; y <= potY + MOST_CLIMB; y++) {
			if (air.test(y)) {
				return y;
			}
		}
		return potY + MOST_CLIMB;
	}

	/**
	 * Pushes texts that would stand closer than a block apart sideways, along the camera's right: the first of each close pair left, the second right.
	 *
	 * @param x  the texts' places across, changed where pushed
	 * @param y  their heights
	 * @param z  their places along
	 * @param rightX the camera's right, across
	 * @param rightZ the camera's right, along
	 */
	static void spread(double[] x, double[] y, double[] z, int count, double rightX, double rightZ) {
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				double dx = x[i] - x[j];
				double dy = y[i] - y[j];
				double dz = z[i] - z[j];
				if (dx * dx + dy * dy + dz * dz < CLOSE_BLOCKS * CLOSE_BLOCKS) {
					x[i] -= rightX * SIDEWAYS_BLOCKS;
					z[i] -= rightZ * SIDEWAYS_BLOCKS;
					x[j] += rightX * SIDEWAYS_BLOCKS;
					z[j] += rightZ * SIDEWAYS_BLOCKS;
				}
			}
		}
	}

	/** The color stage of a timer. */
	enum Stage {
		GREEN, ORANGE, RED
	}

	private final Long2LongLinkedOpenHashMap endsAt = new Long2LongLinkedOpenHashMap();
	private long clearAt = -1;
	private Object world;

	/**
	 * A block turned into a flower pot: starts a timer there, unless one runs there already.
	 *
	 * @return whether a timer started
	 */
	public boolean potAppeared(long place, long tick, boolean master) {
		if (endsAt.containsKey(place)) {
			return false;
		}
		if (endsAt.size() >= MOST_POTS) {
			endsAt.removeFirstLong();
		}
		endsAt.put(place, tick + (master ? MASTER_RESPAWN_TICKS : RESPAWN_TICKS));
		return true;
	}

	/** The terracotta came back at a pot: its timer ends now (the real moment). @return the ticks it still had left, or -1 where none ran */
	public long back(long place, long tick) {
		if (!endsAt.containsKey(place)) {
			return -1;
		}
		long left = endsAt.get(place) - tick;
		endsAt.remove(place);
		return left;
	}

	/** Sadan's "ENOUGH!": every timer goes half a second later. */
	public void enough(long tick) {
		clearAt = tick + CLEAR_AFTER_TICKS;
	}

	/**
	 * Once a client tick: everything goes on a world change or half a second after "ENOUGH!", and each timer that reached 0 is removed.
	 *
	 * @param currentWorld the world now (only compared by identity)
	 */
	public void tick(long tick, Object currentWorld, Log log) {
		if (currentWorld != world) {
			world = currentWorld;
			clear();
			return;
		}
		if (clearAt >= 0 && tick >= clearAt) {
			clear();
			return;
		}
		for (LongIterator places = endsAt.keySet().iterator(); places.hasNext(); ) {
			long place = places.nextLong();
			if (endsAt.get(place) <= tick) {
				places.remove();
				log.removed(place);
			}
		}
	}

	/** @return the ticks left of a place's timer, or -1 where none runs */
	public long ticksLeft(long place, long tick) {
		if (!endsAt.containsKey(place)) {
			return -1;
		}
		return Math.max(0, endsAt.get(place) - tick);
	}

	/** @return the places with a timer, in the order they started */
	public LongIterator places() {
		return endsAt.keySet().iterator();
	}

	public boolean isEmpty() {
		return endsAt.isEmpty();
	}

	/** Every timer goes, and a pending "ENOUGH!" with them. */
	public void clear() {
		endsAt.clear();
		clearAt = -1;
	}

	/** @return the tenths of a second shown for some ticks left, rounded up so the text reaches 0.0 only when the timer goes */
	static int tenths(long ticks) {
		return (int) Math.max(0, (ticks + 1) / 2);
	}

	/** @return the text for some tenths of a second: "7.4s" */
	static String text(int tenths) {
		return tenths / 10 + "." + tenths % 10 + "s";
	}

	static Stage stage(long ticks) {
		return ticks > GREEN_ABOVE_TICKS ? Stage.GREEN : ticks > ORANGE_ABOVE_TICKS ? Stage.ORANGE : Stage.RED;
	}

	/** @return whether a pot this far away shows its text */
	static boolean inReach(double distance) {
		return distance <= MOST_DISTANCE;
	}

	/** @return the text's size in the world this far away, in blocks per font pixel: fixed near, growing with the distance beyond */
	static float worldScale(double distance) {
		return (float) (BASE_SCALE * Math.max(1.0, distance / STEADY_FROM_BLOCKS));
	}
}
