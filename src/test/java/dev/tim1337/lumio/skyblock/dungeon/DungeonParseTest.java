package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The run read from recorded player list, scoreboard and chat lines of an F7 run (colors already taken out, as the add-on reads them). */
class DungeonParseTest {
	private static final List<String> TAB = List.of(
		"         Party (5)",
		" [312] [MVP+] Ash ♲ (Mage XLII)",
		" [298] Kiwi (Berserk XXXVIII)",
		" [301] [VIP] Rae (Healer L)",
		" [276] Nox (Tank XL)",
		" [289] Sol (DEAD)",
		"     Dungeon Stats",
		" Secrets Found: 34",
		" Secrets Found: 82.9%",
		" Crypts: 5",
		" Completed Rooms: 22",
		" Opened Rooms: 25",
		"Team Deaths: 1",
		" Time: 6m 42s",
		"Puzzles: (3)",
		" Three Weirdos: [✔]",
		" Water Board: [✦] (Kiwi)",
		" ???: [✦]",
		" Ice Path: [✖]");

	@Test
	void readsTheNumbersOfTheRun() {
		DungeonStats stats = new DungeonStats();
		DungeonParse.readTab(TAB, stats);
		assertEquals(34, stats.secretsFound);
		assertEquals(82.9, stats.secretsPercent, 1e-9);
		assertEquals(41, stats.totalSecrets());
		assertEquals(5, stats.crypts);
		assertEquals(22, stats.completedRooms);
		assertEquals(25, stats.openedRooms);
		assertEquals(1, stats.deaths);
		assertEquals("6m 42s", stats.time);
		assertEquals(3, stats.puzzleCount);
		assertEquals(DungeonPuzzle.Status.DONE, stats.puzzles.get(DungeonPuzzle.WEIRDOS));
		assertEquals(DungeonPuzzle.Status.OPEN, stats.puzzles.get(DungeonPuzzle.WATER_BOARD));
		assertEquals(DungeonPuzzle.Status.FAILED, stats.puzzles.get(DungeonPuzzle.ICE_PATH));
		assertEquals(1, stats.puzzlesDone());
	}

	@Test
	void readsThePlayersAndTheirClasses() {
		List<DungeonPlayer> party = new ArrayList<>();
		DungeonParse.readPlayers(TAB, party);
		assertEquals(5, party.size());
		DungeonPlayer tim = DungeonParse.find(party, "Ash");
		assertEquals(DungeonClass.MAGE, tim.dungeonClass());
		assertEquals(42, tim.level());
		assertEquals(DungeonClass.BERSERK, DungeonParse.find(party, "Kiwi").dungeonClass());
		assertEquals(DungeonClass.HEALER, DungeonParse.find(party, "Rae").dungeonClass());
		DungeonPlayer sol = DungeonParse.find(party, "Sol");
		assertTrue(sol.dead());
		// alive again with a class: the class is taken, the player kept
		DungeonParse.readPlayers(List.of(" [289] Sol (Archer XXXV)"), party);
		assertEquals(DungeonClass.ARCHER, sol.dungeonClass());
		assertFalse(sol.dead());
		assertEquals(5, party.size());
	}

	@Test
	void readsTheFloorAndTheClearedShareFromTheScoreboard() {
		List<String> sidebar = List.of("SKYBLOCK", "11/08/26 m21CE", "⏣ The Catacombs (F7)", "", "Time Elapsed: 06m 42s", "Cleared: 61% (22)", "www.hypixel.net");
		assertEquals(DungeonFloor.F7, DungeonParse.readFloor(sidebar));
		assertEquals(61, DungeonParse.readCleared(sidebar));
		assertEquals(DungeonFloor.M5, DungeonParse.readFloor(List.of(" ⏣ The Catacombs (M5)")));
		assertNull(DungeonParse.readFloor(List.of("⏣ Village")));
		assertEquals(-1, DungeonParse.readCleared(List.of("Purse: 1,000")));
	}

	@Test
	void readsTheServersLines() {
		assertTrue(DungeonParse.START.matcher("Starting in 1 second.").matches());
		assertTrue(DungeonParse.BLOOD_OPEN.matcher("The BLOOD DOOR has been opened!").matches());
		assertTrue(DungeonParse.BLOOD_OPEN.matcher("[BOSS] The Watcher: Things feel a little more roomy now, eh?").matches());
		assertTrue(DungeonParse.WATCHER_DONE.matcher("[BOSS] The Watcher: You have proven yourself. You may pass.").matches());
		assertTrue(DungeonParse.PRINCE.matcher("A Prince falls. +1 Bonus Score").matches());
		assertTrue(DungeonParse.BAT.matcher("A Bat has been slain. +1 Bonus Score").matches());
		var leaped = DungeonParse.LEAPED.matcher("You have teleported to Kiwi!");
		assertTrue(leaped.matches());
		assertEquals("Kiwi", leaped.group(1));
		var key = DungeonParse.KEY_PICKED.matcher("[MVP+] Nox has obtained Wither Key!");
		assertTrue(key.matches());
		assertEquals("Wither", key.group(2));
		assertTrue(DungeonParse.KEY_PICKED.matcher("A Blood Key was picked up!").matches());
		assertTrue(DungeonParse.DOOR_OPENED.matcher("Nox opened a WITHER door!").matches());
		assertTrue(DungeonParse.DEFEATED.matcher("                ☠ Defeated Maxor, Storm, Goldor, and Necron in 05m 23s").matches());
		var death = DungeonParse.DEATH.matcher(" ☠ Kiwi was killed by Withermancer and became a ghost.");
		assertTrue(death.find());
		assertEquals("Kiwi", death.group(1));
		var party = DungeonParse.PARTY.matcher("Party > [MVP+] Kiwi: Mimic Killed!");
		assertTrue(party.matches());
		assertEquals("Mimic Killed!", party.group(2));
	}

	@Test
	void romanNumbers() {
		assertEquals(42, DungeonParse.roman("XLII"));
		assertEquals(50, DungeonParse.roman("L"));
		assertEquals(0, DungeonParse.roman(""));
	}

	@Test
	void theScoreOfARun() {
		DungeonStats stats = new DungeonStats();
		DungeonParse.readTab(TAB, stats);
		stats.clearedPercent = 61;
		stats.mimicKilled = true;
		DungeonScore.Result result = DungeonScore.of(stats, DungeonFloor.F7, false, DungeonScore.Options.DEFAULT);
		// rooms: 22 done of 36 (22 / 0.61), plus the blood and the boss room counted ahead
		assertEquals(36, stats.totalRooms());
		assertEquals(5 + 2, result.bonus());
		assertEquals(100, result.speed());
		assertTrue(result.total() > 200 && result.total() < 300, "score " + result.total());
		assertEquals(DungeonScore.rank(result.total()), result.rank());
		// all done: S+
		stats.secretsFound = 41;
		stats.secretsPercent = 100;
		stats.completedRooms = 36;
		stats.clearedPercent = 100;
		stats.bloodDone = true;
		stats.deaths = 0;
		stats.puzzles.put(DungeonPuzzle.WATER_BOARD, DungeonPuzzle.Status.DONE);
		stats.puzzles.put(DungeonPuzzle.ICE_PATH, DungeonPuzzle.Status.DONE);
		DungeonScore.Result done = DungeonScore.of(stats, DungeonFloor.F7, true, DungeonScore.Options.DEFAULT);
		assertEquals(100, done.exploration());
		assertEquals(100, done.skill());
		assertEquals("S+", done.rank());
	}

	@Test
	void ranks() {
		assertEquals("S+", DungeonScore.rank(300));
		assertEquals("S", DungeonScore.rank(270));
		assertEquals("A", DungeonScore.rank(250));
		assertEquals("D", DungeonScore.rank(50));
	}

	@Test
	void roomsTurnBothWays() {
		for (CurrentRoom.Rotation rotation : CurrentRoom.Rotation.values()) {
			int[] inRoom = CurrentRoom.toRoom(-120, 70, -88, -100, -100, rotation);
			int[] back = CurrentRoom.toWorld(inRoom[0], inRoom[1], inRoom[2], -100, -100, rotation);
			assertEquals(-120, back[0], rotation.name());
			assertEquals(70, back[1]);
			assertEquals(-88, back[2], rotation.name());
		}
	}

	@Test
	void roomsTurnAsTheSharedListsCount() {
		// a place 3 across and 5 down from the clay corner, counted in each turn of the room
		assertArrayEquals(new int[] {3, 70, 5}, CurrentRoom.toRoom(3, 70, 5, 0, 0, CurrentRoom.Rotation.SOUTH));
		assertArrayEquals(new int[] {5, 70, -3}, CurrentRoom.toRoom(3, 70, 5, 0, 0, CurrentRoom.Rotation.WEST));
		assertArrayEquals(new int[] {-3, 70, -5}, CurrentRoom.toRoom(3, 70, 5, 0, 0, CurrentRoom.Rotation.NORTH));
		assertArrayEquals(new int[] {-5, 70, 3}, CurrentRoom.toRoom(3, 70, 5, 0, 0, CurrentRoom.Rotation.EAST));
		assertEquals(0, CurrentRoom.tileOf(-200));
		assertEquals(5, CurrentRoom.tileOf(-10));
		assertEquals(6, CurrentRoom.tileOf(-9));
		assertEquals(-185, CurrentRoom.middleOf(0));
	}

	@Test
	void theRoomDataLoads() {
		var rooms = CurrentRoom.load();
		assertTrue(rooms.size() > 100, "cores " + rooms.size());
		assertTrue(rooms.values().stream().anyMatch(room -> room.name().equals("Temple")));
		assertEquals(4, CurrentRoom.tilesOf("2x2"));
	}
}
