package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/** The splits' personal bests: what a run is compared with, when the bests change, per floor and across restarts. */
class SplitBestsTest {
	private static final long[] FIRST = {30_000, 70_000, 100_000};
	private static final long[] FIRST_TICKS = {600, 1_400, 2_000};

	private static SplitBests afterOneRun(DungeonFloor floor) {
		SplitBests bests = new SplitBests();
		bests.runStarted(floor);
		bests.runFinished(floor, FIRST, FIRST_TICKS);
		return bests;
	}

	@Test
	void theFirstRunHasNoBestsAndShowsADash() {
		SplitBests bests = new SplitBests();
		bests.runStarted(DungeonFloor.F5);
		assertEquals(0, bests.against(0, false, false));
		// a finished split without a best: a grey dash, never "+0.0"
		SplitBests.Cell cell = SplitBests.cell(30_000, bests.against(0, false, false), true, true);
		assertEquals(SplitBests.Kind.NO_BEST, cell.kind());
		assertEquals("–", cell.text());
		// while it runs, and before it starts: nothing
		assertEquals(SplitBests.Kind.NOTHING, SplitBests.cell(12_000, 0, true, false).kind());
		assertEquals(SplitBests.Kind.NOTHING, SplitBests.cell(0, 0, false, false).kind());
	}

	@Test
	void theRunIsComparedWithTheBestsFromBeforeItStarted() {
		SplitBests bests = afterOneRun(DungeonFloor.F5);
		bests.runStarted(DungeonFloor.F5);
		// the run ends with a faster first split: the stored best changes, what this run is compared with does not
		bests.runFinished(DungeonFloor.F5, new long[] {28_700, 72_400, 101_100}, new long[] {574, 1_448, 2_022});
		assertEquals(28_700, bests.stored(DungeonFloor.F5, 0, false));
		assertEquals(30_000, bests.against(0, false, false));
		// the bug: compared with the best it had just set itself, every new best read "+0.0"
		SplitBests.Cell faster = SplitBests.cell(28_700, bests.against(0, false, false), true, true);
		assertEquals(SplitBests.Kind.FASTER, faster.kind());
		assertEquals("−1.3", faster.text());
		SplitBests.Cell slower = SplitBests.cell(72_400, bests.against(1, false, false), true, true);
		assertEquals(SplitBests.Kind.SLOWER, slower.kind());
		assertEquals("+2.4", slower.text());
		// the total follows the same rules, against the best total
		assertEquals("+1.1", SplitBests.cell(101_100, bests.against(2, false, false), true, true).text());
	}

	@Test
	void aRunningSplitShowsNothingUntilItIsSlowerThanTheBest() {
		assertEquals(SplitBests.Kind.NOTHING, SplitBests.cell(12_000, 30_000, true, false).kind());
		SplitBests.Cell late = SplitBests.cell(31_500, 30_000, true, false);
		assertEquals(SplitBests.Kind.SLOWER, late.kind());
		assertEquals("+1.5", late.text());
	}

	@Test
	void bestsKeepTheFasterSplitOfEveryRun() {
		SplitBests bests = afterOneRun(DungeonFloor.F7);
		bests.runStarted(DungeonFloor.F7);
		bests.runFinished(DungeonFloor.F7, new long[] {28_000, 75_000, 103_000}, new long[] {560, 1_500, 2_060});
		assertEquals(28_000, bests.stored(DungeonFloor.F7, 0, false));
		assertEquals(70_000, bests.stored(DungeonFloor.F7, 1, false));
		assertEquals(100_000, bests.stored(DungeonFloor.F7, 2, false));
		// the third run is compared with those
		bests.runStarted(DungeonFloor.F7);
		assertEquals(28_000, bests.against(0, false, false));
		assertEquals(70_000, bests.against(1, false, false));
		// and with the last run, if asked
		assertEquals(75_000, bests.against(1, true, false));
		// a split that was never reached (0) is no best
		bests.runFinished(DungeonFloor.F7, new long[] {27_000, 0, 0}, new long[] {540, 0, 0});
		assertEquals(27_000, bests.stored(DungeonFloor.F7, 0, false));
		assertEquals(70_000, bests.stored(DungeonFloor.F7, 1, false));
	}

	@Test
	void everyFloorHasItsOwnBests() {
		SplitBests bests = afterOneRun(DungeonFloor.F5);
		// M5 has none of F5's
		bests.runStarted(DungeonFloor.M5);
		assertEquals(0, bests.against(0, false, false));
		assertFalse(bests.has(DungeonFloor.M5));
		bests.runFinished(DungeonFloor.M5, new long[] {50_000, 90_000, 140_000}, new long[] {1_000, 1_800, 2_800});
		assertEquals(30_000, bests.stored(DungeonFloor.F5, 0, false));
		assertEquals(50_000, bests.stored(DungeonFloor.M5, 0, false));
	}

	@Test
	void tickTimeAndRealTimeAreComparedApart() {
		SplitBests bests = afterOneRun(DungeonFloor.F7);
		bests.runStarted(DungeonFloor.F7);
		// the tick time in the same unit as the real time: ticks × 50 ms
		assertEquals(600 * 50, bests.against(0, false, true));
		assertEquals(30_000, bests.against(0, false, false));
		// a laggy run: slower in real time, the same in ticks
		assertEquals(SplitBests.Kind.SLOWER, SplitBests.cell(33_000, bests.against(0, false, false), true, true).kind());
		assertEquals(SplitBests.Kind.SAME, SplitBests.cell(600 * 50, bests.against(0, false, true), true, true).kind());
	}

	@Test
	void bestsSurviveARestart() {
		SplitBests bests = afterOneRun(DungeonFloor.F5);
		bests.runStarted(DungeonFloor.M5);
		bests.runFinished(DungeonFloor.M5, new long[] {50_000, 90_000, 140_000}, new long[] {1_000, 1_800, 2_800});
		JsonObject file = new JsonObject();
		bests.save(file);
		SplitBests read = new SplitBests();
		read.load(file);
		assertEquals(30_000, read.stored(DungeonFloor.F5, 0, false));
		assertEquals(600 * 50, read.stored(DungeonFloor.F5, 0, true));
		assertEquals(140_000, read.stored(DungeonFloor.M5, 2, false));
		// the next run after the restart is compared with them
		read.runStarted(DungeonFloor.F5);
		assertEquals(70_000, read.against(1, false, false));
	}

	@Test
	void resettingAFloorLeavesTheOthers() {
		SplitBests bests = afterOneRun(DungeonFloor.F5);
		bests.runStarted(DungeonFloor.M5);
		bests.runFinished(DungeonFloor.M5, new long[] {50_000, 90_000, 140_000}, new long[] {1_000, 1_800, 2_800});
		bests.reset(DungeonFloor.M5);
		assertFalse(bests.has(DungeonFloor.M5));
		assertTrue(bests.has(DungeonFloor.F5));
		bests.runStarted(DungeonFloor.M5);
		assertEquals(SplitBests.Kind.NO_BEST, SplitBests.cell(50_000, bests.against(0, false, false), true, true).kind());
	}
}
