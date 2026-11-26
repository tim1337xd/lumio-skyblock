package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A split's tick time is never above its real time: equal at 20 ticks a second, lower in lag; the tick count and the real time are taken at the
 * same moments, and the debug check catches more ticks than passed.
 */
class SplitTicksTest {
	private static final long MILLIS_PER_TICK = 50;
	private static final long MINUTE_MILLIS = 60_000;
	private static final String BLOOD_OPEN = "The BLOOD DOOR has been opened!";

	@Test
	void twentyTicksASecondIsTheRealTime() {
		DungeonSplits splits = run(MINUTE_MILLIS / MILLIS_PER_TICK);
		DungeonSplits.Check blood = splits.check(0);
		assertEquals(blood.millis(), blood.ticks() * MILLIS_PER_TICK);
		assertTrue(DungeonSplits.ticksPlausible(blood.ticks(), blood.millis(), MILLIS_PER_TICK));
	}

	@Test
	void inLagTheTickTimeIsLower() {
		// 15 ticks a second for a minute
		DungeonSplits.Check blood = run(15 * MINUTE_MILLIS / 1000).check(0);
		assertTrue(blood.ticks() * MILLIS_PER_TICK < blood.millis());
		assertTrue(DungeonSplits.ticksPlausible(blood.ticks(), blood.millis(), MILLIS_PER_TICK));
	}

	@Test
	void theCrossCheckHasTheGameTimeOverTheSplit() {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F7, 10_000, 40, 5_000);
		assertEquals(1, splits.line(BLOOD_OPEN, 10_000 + MINUTE_MILLIS, 40 + 1_200, 6_200));
		assertEquals(1_200, splits.check(0).gameTicks());
		DungeonSplits unknown = new DungeonSplits(DungeonFloor.F7, 0, 0, -1);
		unknown.line(BLOOD_OPEN, MINUTE_MILLIS, 1_200, 6_200);
		assertEquals(-1, unknown.check(0).gameTicks(), "no game time at the start: unknown, not a wrong number");
	}

	@Test
	void otherLinesFinishNoSplit() {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F7, 0, 0, 0);
		assertEquals(0, splits.line("Some other line", 1_000, 20, 20));
		assertEquals(0, splits.finished());
	}

	@Test
	void moreTicksThanPassedAreCaughtByTheDebugCheck() {
		// the M3 run of the report: 11:43.80 of ticks in 11:25.87 of real time
		assertFalse(DungeonSplits.ticksPlausible(14_076, 685_870, MILLIS_PER_TICK));
		// a tick of rounding is fine
		assertTrue(DungeonSplits.ticksPlausible(MINUTE_MILLIS / MILLIS_PER_TICK + 1, MINUTE_MILLIS, MILLIS_PER_TICK));
	}

	/** A run whose blood door opens a minute after the start, with this many ticks counted in that minute. */
	private static DungeonSplits run(long ticksInAMinute) {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F7, 1_000, 100, 2_000);
		splits.line(BLOOD_OPEN, 1_000 + MINUTE_MILLIS, 100 + ticksInAMinute, 2_000 + ticksInAMinute);
		return splits;
	}
}
