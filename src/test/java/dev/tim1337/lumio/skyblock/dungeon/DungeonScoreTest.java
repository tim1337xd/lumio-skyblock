package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The score as Hypixel's Team Score counts it, part by part, from recorded player-list and scoreboard values. */
class DungeonScoreTest {
	/** A finished clear: every room, all puzzles, the given secrets of 40, 5 crypts, no deaths, in time. */
	private static DungeonStats run(int secretsFound, double secretsPercent) {
		DungeonStats stats = new DungeonStats();
		DungeonParse.readTab(List.of("Secrets Found: " + secretsFound, "Secrets Found: " + secretsPercent + "%", "Crypts: 5", "Completed Rooms: 30",
			"Puzzles: (2)", " Water Board: [✔]", " Ice Path: [✔]", "Time: 8m 12s"), stats);
		stats.clearedPercent = 100;
		stats.bloodDone = true;
		return stats;
	}

	private static DungeonScore.Result score(DungeonStats stats, DungeonFloor floor, boolean inBoss) {
		return DungeonScore.of(stats, floor, inBoss, DungeonScore.Options.DEFAULT);
	}

	@Test
	void everyFloorsSecretShare() {
		// 40 secrets in all; the secret points are full with the floor's share of them
		Object[][] floors = {{DungeonFloor.E, 12}, {DungeonFloor.F1, 12}, {DungeonFloor.F2, 16}, {DungeonFloor.F3, 20}, {DungeonFloor.F4, 24},
			{DungeonFloor.F5, 28}, {DungeonFloor.F6, 34}, {DungeonFloor.F7, 40}, {DungeonFloor.M1, 40}, {DungeonFloor.M7, 40}};
		for (Object[] row : floors) {
			DungeonScore.Result result = score(run(20, 50.0), (DungeonFloor) row[0], true);
			assertEquals(40, result.totalSecrets(), row[0].toString());
			assertEquals(row[1], result.secretsNeeded(), row[0].toString());
		}
		// F3 needs 20 of 40: 20 found is all of the secret points
		assertEquals(100, score(run(20, 50.0), DungeonFloor.F3, true).exploration());
		assertEquals(80, score(run(20, 50.0), DungeonFloor.F7, true).exploration());
	}

	@Test
	void speedBrackets() {
		int[][] cases = {
			// seconds on F7 (limit 840) and the speed points
			{840, 100}, {500, 100}, {924, 95}, {1008, 90}, {1176, 84}, {1260, 81}, {1344, 79}, {2520, 56}, {50_000, 0}};
		for (int[] row : cases) {
			assertEquals(row[1], DungeonScore.speed(DungeonFloor.F7, row[0]), row[0] + " s");
		}
		assertEquals(600, DungeonScore.timeLimitSeconds(DungeonFloor.F5));
		assertEquals(720, DungeonScore.timeLimitSeconds(DungeonFloor.F4));
		assertEquals(720, DungeonScore.timeLimitSeconds(DungeonFloor.F6));
		assertEquals(480, DungeonScore.timeLimitSeconds(DungeonFloor.M3));
		assertEquals(600, DungeonScore.timeLimitSeconds(DungeonFloor.M6));
		assertEquals(840, DungeonScore.timeLimitSeconds(DungeonFloor.M7));
		DungeonStats slow = run(40, 100.0);
		slow.elapsedSeconds = 1008;
		assertEquals(90, score(slow, DungeonFloor.F7, true).speed());
	}

	@Test
	void deathsWithAndWithoutSpirit() {
		assertEquals(0, DungeonScore.deathCost(0, true));
		assertEquals(1, DungeonScore.deathCost(1, true));
		assertEquals(5, DungeonScore.deathCost(3, true));
		assertEquals(2, DungeonScore.deathCost(1, false));
		assertEquals(6, DungeonScore.deathCost(3, false));
		DungeonStats stats = run(40, 100.0);
		stats.chatDeaths = 2;
		assertEquals(100 - 3, DungeonScore.of(stats, DungeonFloor.F7, true, new DungeonScore.Options(true, false)).skill());
		assertEquals(100 - 4, DungeonScore.of(stats, DungeonFloor.F7, true, new DungeonScore.Options(false, false)).skill());
	}

	@Test
	void failedAndOpenPuzzlesCostTen() {
		DungeonStats stats = run(40, 100.0);
		DungeonParse.readTab(List.of("Puzzles: (3)", " Water Board: [✔]", " Ice Path: [✖]", " Quiz: [✦]"), stats);
		DungeonScore.Result result = score(stats, DungeonFloor.F7, true);
		assertEquals(100 - 20, result.skill());
		// the most it can reach takes the open puzzle as done, the failed one stays lost
		assertEquals(90 + 100 + 100 + 5, result.best());
	}

	@Test
	void beforeBloodAndInBoss() {
		DungeonStats stats = new DungeonStats();
		DungeonParse.readTab(List.of("Completed Rooms: 20", "Crypts: 0"), stats);
		stats.clearedPercent = 80;
		// 20 done of 25; before blood and before the boss both count ahead: 22 of 25
		DungeonScore.Result clearing = score(stats, DungeonFloor.F7, false);
		assertEquals(25, clearing.totalRooms());
		assertEquals(22, clearing.roomsCounted());
		stats.bloodDone = true;
		assertEquals(21, score(stats, DungeonFloor.F7, false).roomsCounted());
		assertEquals(20, score(stats, DungeonFloor.F7, true).roomsCounted());
	}

	@Test
	void paulAddsTen() {
		DungeonStats stats = run(40, 100.0);
		int without = score(stats, DungeonFloor.F7, true).total();
		assertEquals(without + 10, DungeonScore.of(stats, DungeonFloor.F7, true, new DungeonScore.Options(true, true)).total());
	}

	@Test
	void noMimicOnF5() {
		DungeonStats stats = run(28, 70.0);
		stats.mimicKilled = true;
		assertEquals(5, score(stats, DungeonFloor.F5, true).bonus());
		assertEquals(5 + 2, score(stats, DungeonFloor.F6, true).bonus());
	}

	@Test
	void eachBonusLineOnce() {
		DungeonStats stats = run(40, 100.0);
		stats.bonusLines.put("A Bat has been slain #0", 1);
		stats.bonusLines.put("A Bat has been slain #1", 1);
		assertEquals(5 + 2, score(stats, DungeonFloor.F7, true).bonus());
		// a party message of the player's own bat is not counted again (only other players' are in this set)
		stats.batKillers.add("Kiwi");
		assertEquals(5 + 3, score(stats, DungeonFloor.F7, true).bonus());
	}

	@Test
	void whatThreeHundredNeeds() {
		// F7, all rooms and puzzles, 38 of 40 secrets (38 secret points), 5 crypts, in time: 100 + 98 + 100 + 5 = 303
		DungeonStats stats = run(38, 95.0);
		DungeonScore.Result result = score(stats, DungeonFloor.F7, true);
		assertEquals(303, result.total());
		assertEquals("", result.for300());
		// with no crypts (298): 300 needs 2 more; crypts are the cheapest
		stats.crypts = 0;
		assertEquals("+2 crypts for 300", score(stats, DungeonFloor.F7, true).for300());
		// with no crypts and few secrets: secrets
		stats.crypts = 5;
		stats.secretsFound = 30;
		stats.secretsPercent = 75.0;
		assertEquals("+5 secrets for 300", score(stats, DungeonFloor.F7, true).for300());
		assertTrue(score(stats, DungeonFloor.F7, true).best() >= 300);
	}

	@Test
	void timesAreRead() {
		assertEquals(492, DungeonParse.seconds("8m 12s"));
		assertEquals(3723, DungeonParse.seconds("1h 2m 3s"));
		assertEquals(45, DungeonParse.seconds("45s"));
		DungeonStats stats = new DungeonStats();
		DungeonParse.readElapsed(List.of("Time Elapsed: 08m 12s"), stats);
		assertEquals(492, stats.elapsedSeconds);
	}
}
