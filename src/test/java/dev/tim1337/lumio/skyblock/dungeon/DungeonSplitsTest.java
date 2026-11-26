package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The phases of an F7 run, timed from recorded lines, in real time and server ticks. */
class DungeonSplitsTest {
	@Test
	void phasesOfAnF7Run() {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F7, 1_000, 100);
		assertEquals(9, splits.phases());
		splits.line("The BLOOD DOOR has been opened!", 32_400, 728);
		splits.line("[BOSS] The Watcher: You have proven yourself. You may pass.", 104_450, 2_169);
		List<DungeonSplits.Row> rows = splits.rows(110_000, 2_280);
		assertEquals("Blood open", rows.get(0).name());
		assertEquals(31_400, rows.get(0).millis());
		assertEquals(628, rows.get(0).ticks());
		assertTrue(rows.get(0).done());
		assertEquals(72_050, rows.get(1).millis());
		assertEquals("Portal", rows.get(2).name());
		assertTrue(rows.get(2).current());
		assertEquals(5_550, rows.get(2).millis());
		assertFalse(rows.get(3).done());
		assertEquals(109_000, rows.getLast().millis());
		assertFalse(splits.over());
	}

	@Test
	void aLaterLineSkipsThePhasesInBetween() {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F5, 0, 0);
		splits.line("[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.", 200_000, 4_000);
		splits.line("                ☠ Defeated Livid in 04m 10s", 250_000, 5_000);
		assertTrue(splits.over());
		List<DungeonSplits.Row> rows = splits.rows(999_999, 99_999);
		assertEquals("Total", rows.getLast().name());
		assertEquals(250_000, rows.getLast().millis());
		assertEquals(5_000, rows.getLast().ticks());
		assertEquals(50_000, rows.get(rows.size() - 2).millis());
	}

	@Test
	void linesOutOfOrderAreLeftAlone() {
		DungeonSplits splits = new DungeonSplits(DungeonFloor.F7, 0, 0);
		splits.line("[BOSS] Storm: Pathetic Maxor, just like expected.", 5_000, 100);
		splits.line("The BLOOD DOOR has been opened!", 6_000, 120);
		// Storm's line skipped the clear: the blood line no longer counts
		assertEquals(5_000, splits.rows(7_000, 140).get(0).millis());
	}

	@Test
	void timesAndDeltas() {
		assertEquals("0:31.40", DungeonSplits.time(31_400));
		assertEquals("7:42.25", DungeonSplits.time(462_250));
		assertEquals("−1.2", DungeonSplits.delta(-1_200));
		assertEquals("+0.8", DungeonSplits.delta(800));
	}
}
