package dev.tim1337.lumio.skyblock.dungeon;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the run from the lines the server shows: the player list (players and classes, secrets, crypts, rooms, deaths, puzzles), the scoreboard
 * (floor, cleared share) and the chat. The patterns follow what Odin reads (BSD-3-Clause, see CREDITS.md). Only plain text without colors is read,
 * and every number is bounded before it is taken.
 */
public final class DungeonParse {
	/** A player in the list: "[300] Ash (Mage XL)", or with a rank and a symbol "[287] [MVP+] Ash ♲ (Healer L)"; "(DEAD)" while dead. */
	static final Pattern PLAYER = Pattern.compile("^\\[(\\d+)] (?:\\[[^]]+] )*(\\w{1,16})(?: [^(]*)? ?\\((\\w+)(?: ([IVXLCDM]+))?\\)$");
	static final Pattern SECRETS_PERCENT = Pattern.compile("^Secrets Found: ([\\d.]+)%$");
	static final Pattern SECRETS_COUNT = Pattern.compile("^Secrets Found: (\\d+)$");
	static final Pattern CRYPTS = Pattern.compile("^Crypts: (\\d+)$");
	static final Pattern COMPLETED_ROOMS = Pattern.compile("^Completed Rooms: (\\d+)$");
	static final Pattern OPENED_ROOMS = Pattern.compile("^Opened Rooms: (\\d+)$");
	static final Pattern DEATHS = Pattern.compile("^Team Deaths: (\\d+)$");
	static final Pattern PUZZLE_COUNT = Pattern.compile("^Puzzles: \\((\\d+)\\)$");
	static final Pattern PUZZLE = Pattern.compile("^(\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])] ?(?:\\((\\w+)\\))?$");
	static final Pattern TIME = Pattern.compile("^Time: ((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$");
	static final Pattern FLOOR = Pattern.compile("The Catacombs \\((\\w+)\\)");
	static final Pattern CLEARED = Pattern.compile("^Cleared: (\\d+)%");

	/** The start of a run, and the first line of the splits. */
	/** The secrets of the room the player is in, in the action bar: "3/7 Secrets". */
	public static final Pattern ROOM_SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");
	public static final Pattern START = Pattern.compile("^Starting in 1 second\\.$");
	public static final Pattern BLOOD_OPEN = Pattern.compile("^\\[BOSS] The Watcher: (?:Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$");
	public static final Pattern WATCHER_DONE = Pattern.compile("^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
	/** The Watcher leaves his place; his mobs then fly the shorter way. */
	public static final Pattern WATCHER_MOVES = Pattern.compile("^\\[BOSS] The Watcher: Let's see how you can handle this\\.$");
	/** The Watcher sends no more mobs: "That will be enough for now." */
	public static final Pattern WATCHER_ENOUGH = Pattern.compile("^\\[BOSS] The Watcher: That will be enough for now\\.$");
	/** The class ability and the ultimate are ready again: "Healing Circle is now available!", "Wish is ready to use! Press DROP to activate it!". */
	public static final Pattern ABILITY_READY = Pattern.compile("^([A-Z][\\w' ]{1,30}) is now available!$");
	public static final Pattern ULTIMATE_READY = Pattern.compile("^([A-Z][\\w' ]{1,30}) is ready to use! Press DROP to activate it!$");
	/** The moments to use the ultimate: Maxor enraged (F7), the end of Goldor (F7), the giants (F6), the start of Livid (F5). */
	public static final Pattern MAXOR_ENRAGED = Pattern.compile("^⚠ Maxor is enraged! ⚠$");
	public static final Pattern GOLDOR_DONE = Pattern.compile("^\\[BOSS] Goldor: You have done it, you destroyed the factory…$");
	public static final Pattern GIANTS = Pattern.compile("^\\[BOSS] Sadan: My giants! Unleashed!$");
	public static final Pattern LIVID_START = Pattern.compile("^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$");
	public static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
	public static final Pattern DEATH = Pattern.compile("☠ (\\w{1,16}) .* and became a ghost\\.");
	public static final Pattern PRINCE = Pattern.compile("^A Prince falls\\. \\+1 Bonus Score$");
	public static final Pattern BAT = Pattern.compile("^A Bat has been slain\\. \\+1 Bonus Score$");
	/** Any other bonus the game announces: "... +N Bonus Score". */
	public static final Pattern BONUS = Pattern.compile("^(.+?)\\.? \\+(\\d{1,2}) Bonus Score\\.?$");
	/** The end of a run: "Team Score: 302 (S+)". */
	public static final Pattern TEAM_SCORE = Pattern.compile("^\\s*Team Score: (\\d{1,3}) \\((S\\+|S|A|B|C|D)\\)");
	/** The scoreboard's run time: "Time Elapsed: 08m 12s". */
	static final Pattern ELAPSED = Pattern.compile("Time Elapsed: (?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?");
	/** The largest number one part of a time may have, a bound for what is read. */
	private static final int MOST_TIME_PART = 100_000;
	public static final Pattern LEAPED = Pattern.compile("^You have teleported to (\\w{1,16})!$");
	public static final Pattern KEY_PICKED = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16}) has obtained (Wither|Blood) Key!$|^A (Wither|Blood) Key was picked up!$");
	public static final Pattern DOOR_OPENED = Pattern.compile("^(?:\\[[^]]+] )?(\\w{1,16}) opened a (WITHER|Blood) door!$|^The BLOOD DOOR has been opened!$");
	/** A party member's line: Odin, Skytils and others post the Mimic, the Prince and bats there for each other's score. */
	public static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^]]+] )*(\\w{1,16}): (.+)$");

	/** The most a count in the list can be: the lines are the server's, but nothing unbounded is taken. */
	private static final int MOST = 10_000;

	private DungeonParse() {
	}

	/** Reads the run's numbers and the puzzles from the player list. */
	public static void readTab(List<String> lines, DungeonStats stats) {
		for (String raw : lines) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			Matcher matcher;
			if ((matcher = SECRETS_PERCENT.matcher(line)).matches()) {
				stats.secretsPercent = Math.clamp(decimal(matcher.group(1), stats.secretsPercent), 0.0, 100.0);
			} else if ((matcher = SECRETS_COUNT.matcher(line)).matches()) {
				stats.secretsFound = number(matcher.group(1), stats.secretsFound);
			} else if ((matcher = CRYPTS.matcher(line)).matches()) {
				stats.crypts = number(matcher.group(1), stats.crypts);
			} else if ((matcher = COMPLETED_ROOMS.matcher(line)).matches()) {
				stats.completedRooms = number(matcher.group(1), stats.completedRooms);
			} else if ((matcher = OPENED_ROOMS.matcher(line)).matches()) {
				stats.openedRooms = number(matcher.group(1), stats.openedRooms);
			} else if ((matcher = DEATHS.matcher(line)).matches()) {
				stats.deaths = number(matcher.group(1), stats.deaths);
			} else if ((matcher = PUZZLE_COUNT.matcher(line)).matches()) {
				stats.puzzleCount = number(matcher.group(1), stats.puzzleCount);
			} else if ((matcher = TIME.matcher(line)).matches()) {
				stats.time = matcher.group(1);
				stats.elapsedSeconds = Math.max(stats.elapsedSeconds, seconds(matcher.group(1)));
			} else if ((matcher = PUZZLE.matcher(line)).matches()) {
				DungeonPuzzle puzzle = DungeonPuzzle.of(matcher.group(1));
				if (puzzle != null) {
					stats.puzzles.put(puzzle, switch (matcher.group(2)) {
						case "✔" -> DungeonPuzzle.Status.DONE;
						case "✖" -> DungeonPuzzle.Status.FAILED;
						default -> DungeonPuzzle.Status.OPEN;
					});
				}
			}
		}
	}

	/** Reads the players and their classes from the player list into the party, keeping those known and their deaths. */
	public static void readPlayers(List<String> lines, List<DungeonPlayer> party) {
		for (String raw : lines) {
			Matcher matcher = PLAYER.matcher(raw.strip());
			if (!matcher.matches()) {
				continue;
			}
			String name = matcher.group(2);
			String word = matcher.group(3);
			boolean dead = word.equals("DEAD");
			DungeonPlayer known = find(party, name);
			if (known == null) {
				if (party.size() >= Dungeon.MOST_PLAYERS) {
					continue;
				}
				known = new DungeonPlayer(name, dead ? DungeonClass.NONE : DungeonClass.of(word), roman(matcher.group(4)));
				party.add(known);
			} else if (!dead && known.dungeonClass() == DungeonClass.NONE) {
				known.dungeonClass(DungeonClass.of(word), roman(matcher.group(4)));
			}
			known.dead(dead);
		}
	}

	/** @return the floor the scoreboard names, or null */
	public static DungeonFloor readFloor(List<String> sidebar) {
		for (String line : sidebar) {
			Matcher matcher = FLOOR.matcher(line);
			if (matcher.find()) {
				return DungeonFloor.of(matcher.group(1));
			}
		}
		return null;
	}

	/** Reads the run's time from the scoreboard ("Time Elapsed: 08m 12s"), where it shows. */
	public static void readElapsed(List<String> sidebar, DungeonStats stats) {
		for (String line : sidebar) {
			Matcher matcher = ELAPSED.matcher(line.strip());
			if (matcher.find() && (matcher.group(2) != null || matcher.group(3) != null)) {
				int seconds = part(matcher.group(1)) * 3600 + part(matcher.group(2)) * 60 + part(matcher.group(3));
				stats.elapsedSeconds = Math.max(stats.elapsedSeconds, seconds);
				return;
			}
		}
	}

	private static int part(String digits) {
		return digits == null ? 0 : Math.min(number(digits, 0), MOST_TIME_PART);
	}

	/** @return the seconds of a time as the player list writes it ("1h 2m 3s", "8m 12s", "45s"), or 0 */
	static int seconds(String time) {
		int seconds = 0;
		int value = 0;
		for (int i = 0; i < time.length(); i++) {
			char c = time.charAt(i);
			if (c >= '0' && c <= '9') {
				value = Math.min(value * 10 + (c - '0'), MOST_TIME_PART);
			} else if (c == 'h' || c == 'm' || c == 's') {
				seconds += value * (c == 'h' ? 3600 : c == 'm' ? 60 : 1);
				value = 0;
			}
		}
		return seconds;
	}

	/** @return the share of the dungeon cleared, as the scoreboard shows it, or -1 */
	public static int readCleared(List<String> sidebar) {
		for (String line : sidebar) {
			Matcher matcher = CLEARED.matcher(line.strip());
			if (matcher.find()) {
				return Math.clamp(number(matcher.group(1), 0), 0, 100);
			}
		}
		return -1;
	}

	public static DungeonPlayer find(List<DungeonPlayer> party, String name) {
		for (DungeonPlayer player : party) {
			if (player.name().equals(name)) {
				return player;
			}
		}
		return null;
	}

	/** @return a roman number ("XL" = 40), or 0 */
	static int roman(String text) {
		if (text == null || text.isEmpty() || text.length() > 8) {
			return 0;
		}
		int total = 0;
		int previous = 0;
		for (int i = text.length() - 1; i >= 0; i--) {
			int value = switch (text.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1000;
				default -> 0;
			};
			total += value < previous ? -value : value;
			previous = Math.max(previous, value);
		}
		return total;
	}

	static int number(String text, int fallback) {
		if (text.length() > 6) {
			return fallback;
		}
		try {
			return Math.min(MOST, Integer.parseInt(text));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double decimal(String text, double fallback) {
		if (text.length() > 8) {
			return fallback;
		}
		try {
			return Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
