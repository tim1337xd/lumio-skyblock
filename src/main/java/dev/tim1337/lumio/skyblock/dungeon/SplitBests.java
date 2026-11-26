package dev.tim1337.lumio.skyblock.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The personal bests of the dungeon splits, per floor (F5 and M5 apart), and what a run is compared with. A run is compared with the bests as
 * they were stored before it started: they are copied when the run starts and only updated when it ends, so a split that sets a new best shows
 * by how much it beat the old one, not a difference to itself. The real time and the tick time (server ticks) of every split are kept side by
 * side, each with its own best; one of them is compared, the same for every row. Plain data, so it is unit tested.
 */
final class SplitBests {
	/** How a split stands to its best. */
	enum Kind {
		/** Nothing to show: the split has not started, or runs and is not slower than the best yet. */
		NOTHING,
		/** No best is known for this split yet: a grey dash. */
		NO_BEST,
		/** Faster than the best: green, with a minus. */
		FASTER,
		/** Slower than the best: red, with a plus. */
		SLOWER,
		/** The same to the tenth of a second. */
		SAME
	}

	/** What the "vs. best" column shows in a row. */
	record Cell(Kind kind, String text) {
		static final Cell NOTHING = new Cell(Kind.NOTHING, "");
		static final Cell NO_BEST = new Cell(Kind.NO_BEST, "–");
	}

	static final long MILLIS_PER_TICK = 50L;
	/** A difference below this many milliseconds rounds to 0.0. */
	private static final long SAME_BELOW = 50L;
	private static final int MOST_SPLITS = 16;
	private static final long MOST_MILLIS = 24L * 3_600_000L;

	/** The best and the last time of each split, by floor: real time in milliseconds and tick time in server ticks; 0 where none is known. */
	private final Map<DungeonFloor, long[]> bestMillis = new EnumMap<>(DungeonFloor.class);
	private final Map<DungeonFloor, long[]> bestTicks = new EnumMap<>(DungeonFloor.class);
	private final Map<DungeonFloor, long[]> lastMillis = new EnumMap<>(DungeonFloor.class);
	private final Map<DungeonFloor, long[]> lastTicks = new EnumMap<>(DungeonFloor.class);
	/** What the run going on is compared with: the bests and the last run as they were when it started; null without a run or without any. */
	private long[] runBestMillis;
	private long[] runBestTicks;
	private long[] runLastMillis;
	private long[] runLastTicks;

	/** What its values are saved under comes after this: "" for a run's splits, "section_" for the sections of Goldor's phase. */
	private final String prefix;

	SplitBests() {
		this("");
	}

	SplitBests(String prefix) {
		this.prefix = prefix;
	}

	/** A run starts on a floor: what is stored for it now is what the run is compared with, until the next run starts. */
	void runStarted(DungeonFloor floor) {
		runBestMillis = copy(bestMillis.get(floor));
		runBestTicks = copy(bestTicks.get(floor));
		runLastMillis = copy(lastMillis.get(floor));
		runLastTicks = copy(lastTicks.get(floor));
	}

	/** No run any more: nothing to compare with. */
	void runLeft() {
		runBestMillis = null;
		runBestTicks = null;
		runLastMillis = null;
		runLastTicks = null;
	}

	/**
	 * The run is over: its splits become the last run of the floor, and the best where they are faster. What the run itself is compared with
	 * stays as it was when it started.
	 *
	 * @param millis the real time of each split and of the total, in milliseconds; 0 for a split that was not reached
	 * @param ticks  the same in server ticks
	 */
	void runFinished(DungeonFloor floor, long[] millis, long[] ticks) {
		lastMillis.put(floor, millis.clone());
		lastTicks.put(floor, ticks.clone());
		bestMillis.put(floor, faster(bestMillis.get(floor), millis));
		bestTicks.put(floor, faster(bestTicks.get(floor), ticks));
	}

	private static long[] faster(long[] before, long[] now) {
		long[] merged = before == null || before.length != now.length ? now.clone() : before.clone();
		for (int i = 0; i < now.length; i++) {
			if (now[i] > 0 && (merged[i] <= 0 || now[i] < merged[i])) {
				merged[i] = now[i];
			}
		}
		return merged;
	}

	/** Forgets the bests of a floor (its last run stays), also for the run going on there. */
	void reset(DungeonFloor floor) {
		bestMillis.remove(floor);
		bestTicks.remove(floor);
		runBestMillis = null;
		runBestTicks = null;
	}

	/** @return whether a best is stored for a floor */
	boolean has(DungeonFloor floor) {
		return bestMillis.containsKey(floor);
	}

	/** @return the stored best of a split of a floor in milliseconds (the tick time as ticks × 50), 0 for none */
	long stored(DungeonFloor floor, int split, boolean tickTime) {
		long[] values = (tickTime ? bestTicks : bestMillis).get(floor);
		return values == null || split >= values.length ? 0 : values[split] * (tickTime ? MILLIS_PER_TICK : 1);
	}

	/**
	 * @param last     whether the run is compared with the last run of the floor instead of the best
	 * @param tickTime whether the tick time is compared instead of the real time
	 * @return what split number {@code split} of the run going on is compared with, in milliseconds; 0 for none
	 */
	long against(int split, boolean last, boolean tickTime) {
		long[] values = last ? (tickTime ? runLastTicks : runLastMillis) : (tickTime ? runBestTicks : runBestMillis);
		return values == null || split >= values.length ? 0 : values[split] * (tickTime ? MILLIS_PER_TICK : 1);
	}

	/**
	 * What the column shows for a split.
	 *
	 * @param value   the split's time so far or in the end, in milliseconds (the tick time as ticks × 50)
	 * @param best    what it is compared with, in milliseconds; 0 for none
	 * @param started whether the split has started
	 * @param done    whether it is finished
	 */
	static Cell cell(long value, long best, boolean started, boolean done) {
		if (!started) {
			return Cell.NOTHING;
		}
		if (best <= 0) {
			return done ? Cell.NO_BEST : Cell.NOTHING;
		}
		long difference = value - best;
		if (!done) {
			// while it runs: nothing, until it is slower than the best; then the live difference
			return difference >= SAME_BELOW ? new Cell(Kind.SLOWER, DungeonSplits.delta(difference)) : Cell.NOTHING;
		}
		if (Math.abs(difference) < SAME_BELOW) {
			return new Cell(Kind.SAME, "0.0");
		}
		return new Cell(difference < 0 ? Kind.FASTER : Kind.SLOWER, DungeonSplits.delta(difference));
	}

	private static long[] copy(long[] values) {
		return values == null ? null : values.clone();
	}

	// ------------------------------------------------------------------------------------------------ the file

	void save(JsonObject json) {
		json.add(prefix + "best", times(bestMillis));
		json.add(prefix + "last", times(lastMillis));
		json.add(prefix + "best_ticks", times(bestTicks));
		json.add(prefix + "last_ticks", times(lastTicks));
	}

	void load(JsonObject json) {
		read(json.get(prefix + "best"), bestMillis);
		read(json.get(prefix + "last"), lastMillis);
		read(json.get(prefix + "best_ticks"), bestTicks);
		read(json.get(prefix + "last_ticks"), lastTicks);
	}

	private static JsonObject times(Map<DungeonFloor, long[]> byFloor) {
		JsonObject json = new JsonObject();
		byFloor.forEach((floor, values) -> {
			JsonArray array = new JsonArray();
			for (long value : values) {
				array.add(value);
			}
			json.add(floor.name(), array);
		});
		return json;
	}

	private static void read(JsonElement element, Map<DungeonFloor, long[]> into) {
		into.clear();
		if (!(element instanceof JsonObject json)) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			DungeonFloor floor = DungeonFloor.of(entry.getKey());
			if (floor == null || !(entry.getValue() instanceof JsonArray array) || array.size() > MOST_SPLITS) {
				continue;
			}
			long[] values = new long[array.size()];
			for (int i = 0; i < values.length; i++) {
				try {
					values[i] = Math.clamp(array.get(i).getAsLong(), 0L, MOST_MILLIS);
				} catch (RuntimeException e) {
					values[i] = 0;
				}
			}
			into.put(floor, values);
		}
	}
}
