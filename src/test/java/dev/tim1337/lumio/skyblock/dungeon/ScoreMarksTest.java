package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The bonus marks of the score panels: which floors show them, their three states, and the bat's bonus counted once. */
class ScoreMarksTest {
	@Test
	void mimicOnlyWhereThereIsOne() {
		for (DungeonFloor floor : DungeonFloor.values()) {
			assertEquals(floor.hasMimic(), ScoreMarks.showsMimic(floor), floor.name());
		}
		assertTrue(ScoreMarks.showsMimic(DungeonFloor.F6));
		assertTrue(ScoreMarks.showsMimic(DungeonFloor.F7));
		assertFalse(ScoreMarks.showsMimic(DungeonFloor.F5));
	}

	@Test
	void batNotInTheEntrance() {
		assertFalse(ScoreMarks.showsBat(DungeonFloor.E));
		assertTrue(ScoreMarks.showsBat(DungeonFloor.F3));
		assertTrue(ScoreMarks.showsPrince(DungeonFloor.E));
		assertFalse(ScoreMarks.showsMimic(null));
	}

	@Test
	void theThreeStates() {
		assertEquals(ScoreMarks.State.GOT, ScoreMarks.state(true, false));
		assertEquals(ScoreMarks.State.GOT, ScoreMarks.state(true, true));
		assertEquals(ScoreMarks.State.NOT_YET, ScoreMarks.state(false, false));
		assertEquals(ScoreMarks.State.LOST, ScoreMarks.state(false, true));
		assertEquals("✓", ScoreMarks.State.GOT.sign);
		assertEquals("–", ScoreMarks.State.NOT_YET.sign);
		assertEquals("✗", ScoreMarks.State.LOST.sign);
	}

	@Test
	void theBatCountsOnceAndShowsAsGot() {
		DungeonStats stats = new DungeonStats();
		stats.batSlain = true;
		stats.batKillers.add("Kiwi");
		stats.batKillers.add("Mango");
		assertEquals(1, stats.otherBonus());
		assertTrue(stats.batBonus());
		assertEquals(ScoreMarks.State.GOT, ScoreMarks.state(stats.batBonus(), false));
	}
}
