package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * When the Watcher starts sending his mobs ("Let's see how you can handle this."), counted from his first line of the run: an estimate per floor at
 * first, then learned from the player's own runs, the median of the last ten once there are three. Kept locally with the settings. Plain logic, so
 * it is tested without the game.
 */
public final class WatcherTimes {
	/** How many runs per floor are kept, and how many make the learned value count. */
	static final int KEPT = 10;
	static final int LEARNED_FROM = 3;
	/** A measured time outside this is not a real run (a reconnect, a party leaving), in seconds. */
	private static final double LEAST_SECONDS = 5;
	private static final double MOST_SECONDS = 120;

	private final Map<DungeonFloor, Deque<Double>> runs = new EnumMap<>(DungeonFloor.class);

	/** @return the estimate for a floor at first, in seconds: E 24, F1 30, F2 to F4 25, F5 and F6 23.5, F7 21; Master floors as their number */
	static double startValue(DungeonFloor floor) {
		if (floor == null) {
			return 21;
		}
		return switch (floor.number()) {
			case 0 -> 24;
			case 1 -> 30;
			case 2, 3, 4 -> 25;
			case 5, 6 -> 23.5;
			default -> 21;
		};
	}

	/** One run's real time from the first Watcher line to his mobs, in seconds; times that cannot be real are left out. */
	public void measured(DungeonFloor floor, double seconds) {
		if (floor == null || seconds < LEAST_SECONDS || seconds > MOST_SECONDS) {
			return;
		}
		Deque<Double> kept = runs.computeIfAbsent(floor, key -> new ArrayDeque<>());
		kept.addLast(seconds);
		while (kept.size() > KEPT) {
			kept.removeFirst();
		}
	}

	/** @return the time for a floor in seconds: the median of its runs once there are enough, else the estimate */
	public double seconds(DungeonFloor floor) {
		Deque<Double> kept = floor == null ? null : runs.get(floor);
		if (kept == null || kept.size() < LEARNED_FROM) {
			return startValue(floor);
		}
		List<Double> sorted = new ArrayList<>(kept);
		Collections.sort(sorted);
		int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
	}

	/** @return how many runs a floor's time was learned from (0 while it is still the estimate) */
	public int learnedFrom(DungeonFloor floor) {
		Deque<Double> kept = floor == null ? null : runs.get(floor);
		return kept == null || kept.size() < LEARNED_FROM ? 0 : kept.size();
	}

	/** @return every floor's runs, for saving */
	public Map<DungeonFloor, List<Double>> runs() {
		Map<DungeonFloor, List<Double>> copy = new EnumMap<>(DungeonFloor.class);
		runs.forEach((floor, kept) -> copy.put(floor, List.copyOf(kept)));
		return copy;
	}

	/** Sets a floor's runs, as saved. */
	public void load(DungeonFloor floor, List<Double> seconds) {
		runs.remove(floor);
		for (double each : seconds) {
			measured(floor, each);
		}
	}
}
