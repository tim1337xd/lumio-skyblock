package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/** The score's rules against fixed tables of inputs: one case per floor, per death count, per time bracket, and the rooms counted ahead. */
class DungeonScoreTablesTest {
	private static final DungeonScore.Options SPIRIT = new DungeonScore.Options(true, false);

	/** A run with every room done in the boss, no deaths, in time: only what a test sets differs from a full score. */
	private static DungeonStats full() {
		DungeonStats stats = new DungeonStats();
		stats.secretsFound = 40;
		stats.secretsPercent = 100;
		stats.completedRooms = 30;
		stats.clearedPercent = 100;
		stats.bloodDone = true;
		stats.elapsedSeconds = 60;
		return stats;
	}

	private static DungeonScore.Result of(DungeonStats stats, DungeonFloor floor) {
		return DungeonScore.of(stats, floor, true, SPIRIT);
	}

	@Test
	void theSecretQuotaOfEveryFloor() {
		// the floor's needed share of the secrets, and the points 20 % and exactly the quota give: floor(40 × found% ÷ needed%)
		Object[][] table = {
			{DungeonFloor.E, 30.0}, {DungeonFloor.F1, 30.0}, {DungeonFloor.F2, 40.0}, {DungeonFloor.F3, 50.0}, {DungeonFloor.F4, 60.0},
			{DungeonFloor.F5, 70.0}, {DungeonFloor.F6, 85.0}, {DungeonFloor.F7, 100.0}, {DungeonFloor.M1, 100.0}, {DungeonFloor.M2, 100.0},
			{DungeonFloor.M3, 100.0}, {DungeonFloor.M4, 100.0}, {DungeonFloor.M5, 100.0}, {DungeonFloor.M6, 100.0}, {DungeonFloor.M7, 100.0}};
		for (Object[] row : table) {
			DungeonFloor floor = (DungeonFloor) row[0];
			double quota = (double) row[1];
			DungeonStats stats = full();
			stats.secretsPercent = quota;
			assertEquals(100, of(stats, floor).exploration(), floor + ": the quota gives all forty");
			stats.secretsPercent = 20.0;
			assertEquals(60 + (int) Math.floor(40 * 20.0 / quota), of(stats, floor).exploration(), floor + ": 20 % of the secrets");
			// more than the quota is no more than forty
			stats.secretsPercent = 100.0;
			assertEquals(100, of(stats, floor).exploration(), floor + ": all secrets");
		}
	}

	@Test
	void theSecretsScoreComesFromThePercentageNotTheCount() {
		// 23 of what the list says is 57.9 %: floor(40 × 0.579) = 23, whatever rounding the count's total would give
		DungeonStats stats = full();
		stats.secretsFound = 23;
		stats.secretsPercent = 57.9;
		assertEquals(60 + 23, of(stats, DungeonFloor.F7).exploration());
		// the count shown may be off by one against the percentage: the percentage decides
		stats.secretsFound = 22;
		assertEquals(60 + 23, of(stats, DungeonFloor.F7).exploration());
		// no percentage yet (nothing found): the count stands in, against the map's secrets
		DungeonStats early = full();
		early.secretsFound = 0;
		early.secretsPercent = 0;
		early.mapSecrets = 40;
		assertEquals(60, of(early, DungeonFloor.F7).exploration());
	}

	@Test
	void deathsCostTwoEachTheFirstOneLessWithASpiritPet() {
		int[][] table = {{0, 0}, {1, 1}, {2, 3}, {3, 5}};
		for (int[] row : table) {
			DungeonStats stats = full();
			stats.deaths = row[0];
			assertEquals(100 - row[1], of(stats, DungeonFloor.F7).skill(), row[0] + " deaths");
			assertEquals(row[1], Math.max(0, row[0] * 2 - 1), "max(0, deaths × 2 − 1)");
		}
		// without the Spirit pet's first death: two each
		DungeonStats stats = full();
		stats.deaths = 2;
		assertEquals(100 - 4, DungeonScore.of(stats, DungeonFloor.F7, true, new DungeonScore.Options(false, false)).skill());
	}

	@Test
	void everyPuzzleNotCompletedCostsTen() {
		DungeonStats stats = full();
		stats.puzzleCount = 3;
		stats.puzzles.put(DungeonPuzzle.WEIRDOS, DungeonPuzzle.Status.DONE);
		// one done, one failed, one not started
		stats.puzzles.put(DungeonPuzzle.BOULDER, DungeonPuzzle.Status.FAILED);
		assertEquals(100 - 20, of(stats, DungeonFloor.F7).skill());
		stats.puzzles.put(DungeonPuzzle.BOULDER, DungeonPuzzle.Status.DONE);
		assertEquals(100 - 10, of(stats, DungeonFloor.F7).skill());
		stats.puzzles.put(DungeonPuzzle.QUIZ, DungeonPuzzle.Status.DONE);
		assertEquals(100, of(stats, DungeonFloor.F7).skill());
	}

	@Test
	void skillNeverGoesBelowTwenty() {
		DungeonStats stats = full();
		stats.completedRooms = 1;
		stats.clearedPercent = 3;
		stats.deaths = 30;
		stats.puzzleCount = 5;
		assertEquals(20, DungeonScore.of(stats, DungeonFloor.F7, true, SPIRIT).skill());
	}

	@Test
	void speedOverTheLimitInEveryStep() {
		// F7's limit is 840 s; the points lost for the percent over it: the first 20 % ÷ 2, the next 20 % ÷ 3.5, 10 % ÷ 4, 10 % ÷ 5, the rest ÷ 6
		int limit = DungeonScore.timeLimitSeconds(DungeonFloor.F7);
		double[][] table = {
			{0, 100}, {10, 95}, {20, 90}, {30, 100 - 10 - 10 / 3.5}, {40, 100 - 10 - 20 / 3.5}, {50, 100 - 10 - 20 / 3.5 - 2.5},
			{60, 100 - 10 - 20 / 3.5 - 2.5 - 2}, {120, 100 - 10 - 20 / 3.5 - 2.5 - 2 - 10}};
		for (double[] row : table) {
			int seconds = (int) Math.round(limit * (1 + row[0] / 100.0));
			assertEquals((int) Math.floor(row[1] + 1e-9), DungeonScore.speed(DungeonFloor.F7, seconds), row[0] + " % over the limit");
		}
		assertEquals(100, DungeonScore.speed(DungeonFloor.F7, limit));
		// each floor has its own limit
		assertEquals(100, DungeonScore.speed(DungeonFloor.M3, DungeonScore.timeLimitSeconds(DungeonFloor.M3)));
		assertTrue(DungeonScore.speed(DungeonFloor.M3, limit) < 100);
	}

	@Test
	void theMimicCountsOnlyOnFloorsSixAndSeven() {
		for (DungeonFloor floor : DungeonFloor.values()) {
			DungeonStats stats = full();
			stats.mimicKilled = true;
			boolean has = floor == DungeonFloor.F6 || floor == DungeonFloor.F7 || floor == DungeonFloor.M6 || floor == DungeonFloor.M7;
			assertEquals(has ? 2 : 0, of(stats, floor).bonus(), floor.name());
		}
	}

	@Test
	void theBonusIsCryptsMimicPrinceBatAndPaul() {
		DungeonStats stats = full();
		stats.crypts = 8;
		assertEquals(5, of(stats, DungeonFloor.F7).bonus(), "five crypts at most");
		stats.mimicKilled = true;
		stats.princeKilled = true;
		stats.batSlain = true;
		assertEquals(5 + 2 + 1 + 1, of(stats, DungeonFloor.F7).bonus());
		assertEquals(5 + 2 + 1 + 1 + 10, DungeonScore.of(stats, DungeonFloor.F7, true, new DungeonScore.Options(true, true)).bonus());
	}

	@Test
	void theRoomsCountTheBloodRoomAndTheBossAhead() {
		// 28 of 30 rooms done: the Blood room and the boss room are the two missing
		DungeonStats stats = full();
		stats.completedRooms = 28;
		stats.clearedPercent = 93;
		assertEquals(30, DungeonScore.of(stats, DungeonFloor.F7, false, SPIRIT).totalRooms());
		// before the Watcher is done and before the boss: both count ahead, so the score during the run is the end's
		stats.bloodDone = false;
		assertEquals(30, DungeonScore.of(stats, DungeonFloor.F7, false, SPIRIT).roomsCounted());
		assertEquals(100, DungeonScore.of(stats, DungeonFloor.F7, false, SPIRIT).skill());
		// the Watcher done (the room is among the completed ones now), not in the boss yet: one ahead
		stats.bloodDone = true;
		stats.completedRooms = 29;
		stats.clearedPercent = 97;
		assertEquals(30, DungeonScore.of(stats, DungeonFloor.F7, false, SPIRIT).roomsCounted());
		// in the boss with a room left undone: nothing ahead
		assertEquals(29, DungeonScore.of(stats, DungeonFloor.F7, true, SPIRIT).roomsCounted());
		assertEquals(20 + (int) Math.floor(80 * 29 / 30.0), DungeonScore.of(stats, DungeonFloor.F7, true, SPIRIT).skill());
		assertEquals((int) Math.floor(60 * 29 / 30.0) + 40, DungeonScore.of(stats, DungeonFloor.F7, true, SPIRIT).exploration());
	}

	@Test
	void theRoomsOfTheDungeonComeFromTheCompletedOnesAndTheShareCleared() {
		DungeonStats stats = new DungeonStats();
		// until both are known: a usual dungeon's rooms
		assertEquals(DungeonScore.ROOMS_UNKNOWN, DungeonScore.of(stats, DungeonFloor.F7, false, SPIRIT).totalRooms());
		stats.completedRooms = 12;
		stats.clearedPercent = 40;
		assertEquals(30, stats.totalRooms());
		stats.completedRooms = 7;
		stats.clearedPercent = 23;
		// 7 ÷ 0.23 = 30.43: with the 0.4 added and cut off, 30
		assertEquals(30, stats.totalRooms());
	}

	@Test
	void theNextStepInWords() {
		DungeonStats stats = DungeonScoreFeature.sampleMidRun();
		DungeonScore.Step toSPlus = DungeonScore.step(stats, DungeonFloor.F7, false, DungeonScore.Options.DEFAULT, DungeonScore.S_PLUS_SCORE);
		assertFalse(toSPlus.reached());
		assertFalse(toSPlus.outOfReach());
		assertTrue(toSPlus.what().contains("secret"), toSPlus.what());
		assertEquals(toSPlus.what() + " to S+", DungeonScoreFeature.stepWords(toSPlus, "S+", false));
		assertEquals("+" + toSPlus.what(), DungeonScoreFeature.stepWords(toSPlus, "S+", true));
		// S needs less than S+
		DungeonScore.Step toS = DungeonScore.step(stats, DungeonFloor.F7, false, DungeonScore.Options.DEFAULT, DungeonScore.S_SCORE);
		assertTrue(toS.reached() || !toS.what().equals(toSPlus.what()));
		// the end of the run: reached, and the one line says nothing more
		DungeonScore.Step done = DungeonScore.step(DungeonScoreFeature.sampleEnd(), DungeonFloor.F7, true, DungeonScore.Options.DEFAULT, DungeonScore.S_PLUS_SCORE);
		assertTrue(done.reached());
		assertEquals("S+ reached", DungeonScoreFeature.stepWords(done, "S+", false));
		assertEquals("", DungeonScoreFeature.stepWords(done, "S+", true));
		assertEquals("S out of reach", DungeonScoreFeature.stepWords(DungeonScore.Step.OUT_OF_REACH, "S", false));
		assertEquals("", DungeonScoreFeature.stepWords(DungeonScore.Step.OUT_OF_REACH, "S", true));
	}

	@Test
	void theExamplesAreAMidRunAndAnSPlusEnd() {
		DungeonScore.Result mid = DungeonScore.of(DungeonScoreFeature.sampleMidRun(), DungeonFloor.F7, false, DungeonScore.Options.DEFAULT);
		assertEquals("C", mid.rank());
		DungeonScore.Result end = DungeonScore.of(DungeonScoreFeature.sampleEnd(), DungeonFloor.F7, true, DungeonScore.Options.DEFAULT);
		assertEquals("S+", end.rank());
	}

	@Test
	void theOldStylesBecomeTheTwoNewOnes() {
		JsonObject json = new JsonObject();
		json.addProperty("style", "MINIMAL");
		DungeonScoreFeature.migrateStyle(json);
		assertEquals("ONE_LINE", json.get("style").getAsString());
		for (String old : new String[] {"COMPACT", "DETAILED"}) {
			json.addProperty("style", old);
			DungeonScoreFeature.migrateStyle(json);
			assertEquals("SCORE_BAR", json.get("style").getAsString());
		}
		json.addProperty("style", "ONE_LINE");
		DungeonScoreFeature.migrateStyle(json);
		assertEquals("ONE_LINE", json.get("style").getAsString());
	}
}
