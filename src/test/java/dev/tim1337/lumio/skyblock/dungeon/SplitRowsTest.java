package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.dungeon.f7.BossInfoSplit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The rows of the Splits panel: the boss's phases per floor from sample chat lines, the running phase, the sections of Goldor's phase, and the split-up of Boss info. */
class SplitRowsTest {
	private static final String BLOOD = "The BLOOD DOOR has been opened!";
	private static final String PORTAL = "[BOSS] The Watcher: You have proven yourself. You may pass.";
	private static final String MAXOR = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!";
	private static final String STORM = "[BOSS] Storm: Pathetic Maxor, just like expected.";
	private static final String GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?";
	private static final String CORE = "The Core entrance is opening!";
	private static final String NECRON = "[BOSS] Necron: You went further than any human before, congratulations.";

	private static List<String> names(List<DungeonSplits.Row> rows) {
		List<String> names = new ArrayList<>();
		for (DungeonSplits.Row row : rows) {
			names.add(row.name());
		}
		return names;
	}

	/** A run of a floor after these lines, a second apart. */
	private static DungeonSplits run(DungeonFloor floor, String... lines) {
		DungeonSplits splits = new DungeonSplits(floor, 0, 0);
		long at = 1_000;
		for (String line : lines) {
			splits.line(line, at, at / 50);
			at += 1_000;
		}
		return splits;
	}

	@Test
	void theBossPhasesOfEachFloorFollowTheClear() {
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Maxor", "Storm", "Terminals", "Goldor", "Necron", "Wither King", "Total"),
			names(run(DungeonFloor.F7).rows(0, 0)));
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Livid", "Total"), names(run(DungeonFloor.F5).rows(0, 0)));
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Terracottas", "Giants", "Sadan", "Total"), names(run(DungeonFloor.F6).rows(0, 0)));
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Guardians", "Professor", "Cleared", "Total"), names(run(DungeonFloor.F3).rows(0, 0)));
	}

	@Test
	void thePhaseRunningIsTheOneMarked() {
		DungeonSplits splits = run(DungeonFloor.F7, BLOOD, PORTAL, MAXOR, STORM);
		List<DungeonSplits.Row> rows = splits.rows(9_000, 180);
		for (DungeonSplits.Row row : rows) {
			assertEquals(row.name().equals("Storm"), row.current(), row.name());
		}
		// Maxor took from its line to Storm's, one second; Storm runs on with the clock
		assertEquals(1_000, rows.get(3).millis());
		assertTrue(rows.get(3).done());
		assertEquals(5_000, rows.get(4).millis());
		assertFalse(rows.get(5).current() || rows.get(5).done());
	}

	@Test
	void withoutTheBossPhasesOnlyTheClearAndTheTotalShow() {
		DungeonSplits splits = run(DungeonFloor.F7, BLOOD, PORTAL, MAXOR);
		SplitRows.Shown shown = SplitRows.shown(splits.rows(5_000, 100), false, null, 5_000, 100);
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Total"), names(shown.rows()));
		// each row keeps the place of its best among the run's stored splits
		assertArrayEquals(new int[] {0, 1, 2, 9}, shown.slots());
		SplitRows.Shown all = SplitRows.shown(splits.rows(5_000, 100), true, null, 5_000, 100);
		assertEquals(10, all.rows().size());
		assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, all.slots());
	}

	@Test
	void terminalsAsSectionsShowFourRowsWithTheirOwnBests() {
		DungeonSplits splits = run(DungeonFloor.F7, BLOOD, PORTAL, MAXOR, STORM, GOLDOR);
		SplitRows.Sections sections = new SplitRows.Sections();
		// Goldor's line at 5 s starts S1; S2 at 7 s; now is 8 s
		sections.see(1, false, 5_000, 100);
		sections.see(2, false, 7_000, 140);
		SplitRows.Shown shown = SplitRows.shown(splits.rows(8_000, 160), true, sections, 8_000, 160);
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Maxor", "Storm", "S1", "S2", "S3", "S4", "Goldor", "Necron", "Wither King", "Total"),
			names(shown.rows()));
		assertArrayEquals(new int[] {0, 1, 2, 3, 4, -1, -2, -3, -4, 6, 7, 8, 9}, shown.slots());
		DungeonSplits.Row first = shown.rows().get(5);
		assertTrue(first.done());
		assertEquals(2_000, first.millis());
		assertEquals(40, first.ticks());
		DungeonSplits.Row second = shown.rows().get(6);
		assertTrue(second.current());
		assertEquals(1_000, second.millis());
		assertFalse(shown.rows().get(7).current() || shown.rows().get(7).done());
		// on a floor without that row nothing changes
		DungeonSplits five = run(DungeonFloor.F5, BLOOD, PORTAL);
		assertEquals(List.of("Blood open", "Blood clear", "Portal", "Livid", "Total"), names(SplitRows.shown(five.rows(3_000, 60), true, sections, 3_000, 60).rows()));
	}

	@Test
	void theSectionsTimesAreKeptForTheBests() {
		SplitRows.Sections sections = new SplitRows.Sections();
		sections.see(0, false, 1_000, 20);
		assertFalse(sections.over());
		sections.see(1, false, 5_000, 100);
		sections.see(2, false, 7_000, 140);
		// the lines skipped S3: it begins and ends at once
		sections.see(4, false, 12_000, 240);
		sections.see(0, true, 15_500, 310);
		assertTrue(sections.over());
		assertArrayEquals(new long[] {2_000, 5_000, 0, 3_500}, sections.spent(false));
		assertArrayEquals(new long[] {40, 100, 0, 70}, sections.spent(true));
		sections.reset();
		assertFalse(sections.over());
		assertArrayEquals(new long[] {0, 0, 0, 0}, sections.spent(false));
	}

	@Test
	void theRunningPhaseWithoutASectionSeenYetMarksItsFirstSection() {
		DungeonSplits splits = run(DungeonFloor.F7, BLOOD, PORTAL, MAXOR, STORM, GOLDOR);
		SplitRows.Shown shown = SplitRows.shown(splits.rows(6_000, 120), true, new SplitRows.Sections(), 6_000, 120);
		assertTrue(shown.rows().get(5).current());
		assertEquals("S1", shown.rows().get(5).name());
		assertEquals(1_000, shown.rows().get(5).millis());
	}

	@Test
	void bossInfoSplitUp() {
		// Boss info on: the timers on, the Terminal status as it was, the Splits on (they show the phases now)
		assertEquals(new BossInfoSplit.After(true, true, true), BossInfoSplit.after(true, true, false));
		assertEquals(new BossInfoSplit.After(true, false, true), BossInfoSplit.after(true, false, true));
		// Boss info off: nothing of it shows, as before; the Splits as they were
		assertEquals(new BossInfoSplit.After(false, false, false), BossInfoSplit.after(false, true, false));
		assertEquals(new BossInfoSplit.After(false, false, true), BossInfoSplit.after(false, false, true));
	}
}
