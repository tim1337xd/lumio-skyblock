package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * When the dungeon panels show: a run starts only in the Catacombs from a scoreboard read after the world change, ends in the Dungeon Hub, and the
 * final score shows for a few seconds after the run; a new run starts from nothing, so the last run's values never show in the next.
 */
class DungeonPresenceTest {
	@Test
	void aRunStartsOnlyInTheCatacombsFromAFreshScoreboard() {
		assertTrue(DungeonPresence.starts(false, DungeonFloor.F7, true, "Catacombs"));
		assertTrue(DungeonPresence.starts(false, DungeonFloor.F7, true, ""), "where the player list shows no area");
		assertFalse(DungeonPresence.starts(false, DungeonFloor.F7, false, "Catacombs"), "the scoreboard of the world just left");
		assertFalse(DungeonPresence.starts(false, DungeonFloor.F7, true, "Dungeon Hub"));
		assertFalse(DungeonPresence.starts(false, null, true, "Catacombs"));
		assertFalse(DungeonPresence.starts(true, DungeonFloor.F7, true, "Catacombs"), "already in one");
	}

	@Test
	void theDungeonHubEndsIt() {
		assertTrue(DungeonPresence.ends(true, "Dungeon Hub"));
		assertFalse(DungeonPresence.ends(true, "Catacombs"));
		assertFalse(DungeonPresence.ends(true, ""));
		assertFalse(DungeonPresence.ends(false, "Dungeon Hub"));
	}

	@Test
	void theFinalScoreShowsAFewSecondsThenFades() {
		long end = 1_000_000L;
		assertEquals(0f, DungeonPresence.finalShare(0, end));
		assertEquals(1f, DungeonPresence.finalShare(end, end + 1_000));
		assertEquals(1f, DungeonPresence.finalShare(end, end + DungeonPresence.FINAL_MILLIS));
		float fading = DungeonPresence.finalShare(end, end + DungeonPresence.FINAL_MILLIS + DungeonPresence.FADE_MILLIS / 2);
		assertTrue(fading > 0f && fading < 1f);
		assertEquals(0f, DungeonPresence.finalShare(end, end + DungeonPresence.FINAL_MILLIS + DungeonPresence.FADE_MILLIS + 1));
	}

	@Test
	void aNewRunStartsFromNothing() {
		DungeonStats last = new DungeonStats();
		last.secretsFound = 40;
		last.crypts = 5;
		last.chatDeaths = 2;
		last.bonusLines.put("A Bat has been slain #0", 1);
		DungeonStats next = new DungeonStats();
		DungeonScore.Result score = DungeonScore.of(next, DungeonFloor.F7, false, DungeonScore.Options.DEFAULT);
		assertEquals(0, next.secretsFound);
		assertEquals(0, score.bonus());
		assertEquals(0, next.deaths());
		assertEquals(0, score.totalSecrets(), "no secrets known shows as a dash, not 0 of something");
	}
}
