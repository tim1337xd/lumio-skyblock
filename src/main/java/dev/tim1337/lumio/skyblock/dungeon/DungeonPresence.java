package dev.tim1337.lumio.skyblock.dungeon;

/**
 * Whether the player is in a run of the Catacombs, from what the game shows: a run starts when a scoreboard read after the last world change names a
 * floor while the player list's area is the Catacombs (or shows no area at all), and it ends with the world, or as soon as the area names somewhere
 * else (the Dungeon Hub). A scoreboard still left from before the world change never starts one. Plain logic, so it is tested without the game.
 */
public final class DungeonPresence {
	private static final String CATACOMBS = "Catacombs";
	/** How long the final score of a run stays, and how long it then fades, in milliseconds. */
	public static final long FINAL_MILLIS = 5_000L;
	public static final long FADE_MILLIS = 800L;

	private DungeonPresence() {
	}

	/** @return whether a run starts now: not in one, a fresh scoreboard naming a floor, and the area the Catacombs or not shown */
	public static boolean starts(boolean inRun, DungeonFloor seen, boolean sidebarFresh, String area) {
		return !inRun && seen != null && sidebarFresh && (area.isEmpty() || area.equalsIgnoreCase(CATACOMBS));
	}

	/** @return whether a run ends now: the area names a place other than the Catacombs */
	public static boolean ends(boolean inRun, String area) {
		return inRun && !area.isEmpty() && !area.equalsIgnoreCase(CATACOMBS);
	}

	/** @return how visible the final score of a run that ended at a time still is: 1, then fading, then 0 (also when none ended) */
	public static float finalShare(long endedAt, long now) {
		if (endedAt <= 0) {
			return 0f;
		}
		long since = now - endedAt;
		if (since < 0 || since > FINAL_MILLIS + FADE_MILLIS) {
			return 0f;
		}
		return since <= FINAL_MILLIS ? 1f : 1f - (since - FINAL_MILLIS) / (float) FADE_MILLIS;
	}
}
