package dev.tim1337.lumio.skyblock.dungeon;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** What the player list, the scoreboard and the chat tell of the run, for the score and the info panel. Reset with every run. */
public final class DungeonStats {
	public int secretsFound;
	public double secretsPercent;
	public int crypts;
	public int openedRooms;
	public int completedRooms;
	/** The team's deaths as the player list counts them. */
	public int deaths;
	/** The deaths the chat announced (a reconnect is not one). */
	public int chatDeaths;
	public int clearedPercent;
	public int puzzleCount;
	public String time = "";
	/** The run's time in seconds, from the scoreboard's or the player list's time. */
	public int elapsedSeconds;
	/** The rooms and the secrets of the rooms on the map, for while the game has not told the totals yet; 0 while not known. */
	public int mapRooms;
	public int mapSecrets;
	public boolean mimicKilled;
	public boolean princeKilled;
	public boolean bloodDone;
	/** Bats other players' mods reported in party chat (the player's own come as a bonus line). */
	public final Set<String> batKillers = new HashSet<>();
	/** The game's other "... +N Bonus Score" lines, each counted once by its line. */
	/** Whether the game's own line for the bat's bonus came. */
	public boolean batSlain;
	public final Map<String, Integer> bonusLines = new java.util.LinkedHashMap<>();
	public final Map<DungeonPuzzle, DungeonPuzzle.Status> puzzles = new EnumMap<>(DungeonPuzzle.class);

	/** @return the puzzles done */
	public int puzzlesDone() {
		int done = 0;
		for (DungeonPuzzle.Status status : puzzles.values()) {
			if (status == DungeonPuzzle.Status.DONE) {
				done++;
			}
		}
		return done;
	}

	/** @return the puzzles failed */
	public int puzzlesFailed() {
		int failed = 0;
		for (DungeonPuzzle.Status status : puzzles.values()) {
			if (status == DungeonPuzzle.Status.FAILED) {
				failed++;
			}
		}
		return failed;
	}

	/** @return the deaths: the chat's count, or the player list's where it knows more */
	public int deaths() {
		return Math.max(deaths, chatDeaths);
	}

	/** @return whether the bat's bonus was got: the game's own line for it, or a bat another player's mod reported */
	public boolean batBonus() {
		return batSlain || !batKillers.isEmpty();
	}

	/** @return the bonus of the game's bonus lines and of the bat, which counts once however many reported it */
	public int otherBonus() {
		int sum = batBonus() ? 1 : 0;
		for (int points : bonusLines.values()) {
			sum += points;
		}
		return sum;
	}

	/** @return the secrets of the whole dungeon, from those found and the share they are of all, or 0 while unknown */
	public int totalSecrets() {
		return secretsFound == 0 || secretsPercent <= 0 ? 0 : (int) Math.floor(100.0 / secretsPercent * secretsFound + 0.5);
	}

	/** @return the rooms of the dungeon, from those done and the share cleared, or 0 while unknown */
	public int totalRooms() {
		return completedRooms == 0 || clearedPercent == 0 ? 0 : (int) Math.floor(completedRooms / (clearedPercent * 0.01) + 0.4);
	}
}
