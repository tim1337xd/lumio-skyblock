package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.util.ServerTicks;

/**
 * Server ticks of the dungeon: counted from the server's tick pings ({@link ServerTicks}) only while in a dungeon, and back to 0 on every world
 * change. A run's start and its splits read this count at the same moments as their real times.
 */
public final class DungeonTicks {
	private static long count;

	private DungeonTicks() {
	}

	/** Starts counting; once, while the add-on starts. */
	public static void register() {
		ServerTicks.listen(DungeonTicks::serverTick);
	}

	private static void serverTick() {
		if (Dungeon.in()) {
			count++;
		}
	}

	/** @return the server ticks counted in the dungeon of this world so far */
	public static long count() {
		return count;
	}

	/** A new world: the count starts again. */
	static void reset() {
		count = 0;
	}
}
