package dev.tim1337.lumio.skyblock.dungeon;

/** How the dungeon features write times. */
public final class DungeonTime {
	private static final int TICKS_PER_SECOND = 20;
	private static final int HUNDREDTHS_PER_TICK = 5;

	private DungeonTime() {
	}

	/** @return server ticks as seconds with two decimals: "1.45 s" */
	public static String seconds(long ticks) {
		long safe = Math.max(0, ticks);
		long hundredths = safe % TICKS_PER_SECOND * HUNDREDTHS_PER_TICK;
		return safe / TICKS_PER_SECOND + "." + (hundredths < 10 ? "0" : "") + hundredths + " s";
	}
}
