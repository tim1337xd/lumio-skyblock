package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * The run in the Catacombs the player is in: the floor, the party and their classes, the run's numbers and score, the phase (clear, blood, boss) and
 * the room the player is in. Read from what the server shows (scoreboard, player list, chat) once per tick and only while in a dungeon; outside one
 * everything is empty and nothing is read. Other dungeon features ask it and listen to its events.
 */
public final class Dungeon {
	/** The most players a run has, with some room for players that join late. */
	static final int MOST_PLAYERS = 8;

	/** Where the run is. */
	public enum Phase {
		/** In the dungeon before the run started. */
		WAITING,
		/** Clearing rooms before the blood room is opened. */
		CLEAR,
		/** The blood room is open: its mobs come out. */
		BLOOD,
		/** The Watcher is done; the way to the boss is open. */
		BLOOD_DONE,
		/** In the boss room. */
		BOSS,
		/** The boss is defeated: the run is over. */
		DONE
	}

	/** What the dungeon features hear from the run. Every method is called on the game's thread. */
	public interface Listener {
		default void runStarted(DungeonFloor floor) {
		}

		/** A line of the server's chat during the run, plain text. */
		default void serverLine(String line) {
		}

		default void phaseChanged(Phase phase) {
		}

		default void mimicKilled() {
		}

		default void princeKilled() {
		}

		default void batKilled() {
		}

		default void leaped(String name) {
		}

		/** The score reached a mark (270, 300) for the first time in the run. */
		default void scoreReached(int mark, int score) {
		}

		/** The run ended: the game's end screen came ("Team Score"), after a completed run as after a failed one. */
		default void runEnded() {
		}

		/** The world changed: everything of the run is gone. */
		default void left() {
		}
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static final int[] SCORE_MARKS = {DungeonScore.S_SCORE, DungeonScore.S_PLUS_SCORE};
	/** Where the boss room begins on F1 to F7 (and the same Master Mode floors), across and down. */
	private static final int[][] BOSS_CORNERS = {{-71, -39}, {-39, -39}, {-39, -39}, {-39, -39}, {-39, -7}, {-39, -7}, {-7, -7}};

	private static boolean inDungeon;
	private static DungeonFloor floor;
	private static Phase phase = Phase.WAITING;
	private static DungeonStats stats = new DungeonStats();
	/** The player's choices for the score (Spirit pet, Paul), set by the score panel. */
	private static DungeonScore.Options scoreOptions = DungeonScore.Options.DEFAULT;
	/** The scoreboard of the world just left, which must not start a run. */
	private static List<String> staleSidebar;
	/** The most one bonus line may add, a bound for what the chat says. */
	private static final int MOST_BONUS_LINE = 20;
	/** The final score of the run that just ended, and when it ended (0 while none did); for the panel's last look at it. */
	private static DungeonScore.Result finalScore;
	private static long endedAt;
	private static final List<DungeonPlayer> PARTY = new ArrayList<>();
	private static DungeonScore.Result score = DungeonScore.of(stats, null, false, DungeonScore.Options.DEFAULT);
	private static final boolean[] MARKS_REACHED = new boolean[SCORE_MARKS.length];
	private static long startedAt;
	private static long startedTick;
	/** The server's game time at the start, -1 if none came yet; for the splits' cross-check. */
	private static long startedGameTime = -1;
	/** The party's door keys, from the scoreboard and the chat. */
	private static final DoorKeys KEYS = new DoorKeys();
	/** The lists read last: the same list is not read again. */
	private static List<String> readTab;
	private static List<String> readSidebar;
	private static ClientLevel level;

	private Dungeon() {
	}

	/** Adds a listener; done once while the add-on starts. */
	public static void listen(Listener listener) {
		LISTENERS.add(listener);
	}

	/** @return whether the player is in the Catacombs now */
	public static boolean in() {
		return inDungeon;
	}

	/** @return whether the run is in the clear part: rooms, not the boss */
	public static boolean inClear() {
		return inDungeon && phase != Phase.BOSS && phase != Phase.DONE;
	}

	public static boolean inBoss() {
		return inDungeon && phase == Phase.BOSS;
	}

	public static DungeonFloor floor() {
		return floor;
	}

	public static Phase phase() {
		return phase;
	}

	public static DungeonStats stats() {
		return stats;
	}

	/** @return the run's players, the player too; empty outside a dungeon */
	public static List<DungeonPlayer> party() {
		return PARTY;
	}

	public static DungeonScore.Result score() {
		return score;
	}

	/** @return when the run started, in milliseconds of the system clock, or 0 before */
	public static long startedAt() {
		return startedAt;
	}

	/** @return the server tick the run started at, or 0 before */
	public static long startedTick() {
		return startedTick;
	}

	/** @return the server's game time when the run started, or -1 if none was known */
	public static long startedGameTime() {
		return startedGameTime;
	}

	public static boolean hasWitherKey() {
		return KEYS.wither();
	}

	public static boolean hasBloodKey() {
		return KEYS.blood();
	}

	/** @return the player of the run with this name, or null */
	public static DungeonPlayer player(String name) {
		return DungeonParse.find(PARTY, name);
	}

	/** Called every tick: follows the world, the scoreboard and the player list. */
	public static void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			reset();
		}
		if (client.level == null || client.player == null) {
			return;
		}
		List<String> sidebar = SkyBlockArea.sidebar();
		if (sidebar != readSidebar) {
			readSidebar = sidebar;
			DungeonFloor seen = DungeonParse.readFloor(sidebar);
			if (DungeonPresence.starts(inDungeon, seen, sidebar != staleSidebar, SkyBlockArea.island())) {
				inDungeon = true;
				floor = seen;
			}
			for (String line : sidebar) {
				if (KEYS.scoreboard(line)) {
					break;
				}
			}
			int cleared = DungeonParse.readCleared(sidebar);
			if (cleared >= 0) {
				stats.clearedPercent = cleared;
			}
			if (inDungeon) {
				DungeonParse.readElapsed(sidebar, stats);
			}
		}
		if (DungeonPresence.ends(inDungeon, SkyBlockArea.island())) {
			// the Dungeon Hub or anywhere else: the run is over, nothing of it shows
			reset();
			return;
		}
		if (!inDungeon) {
			return;
		}
		List<String> tab = SkyBlockArea.tab();
		if (tab != readTab) {
			readTab = tab;
			DungeonParse.readTab(tab, stats);
			DungeonParse.readPlayers(tab, PARTY);
		}
		boolean bossNow = inBossRoom(client.player);
		if (bossNow && phase != Phase.BOSS && phase != Phase.DONE) {
			phase(Phase.BOSS);
		}
		CurrentRoom.tick(client);
		DungeonScan.tick(client);
		if (floor != null && floor.hasMimic() && !stats.mimicKilled && inClear()) {
			watchMimic(client.level);
		}
		rescore();
	}

	/** Reads a line of the server's chat during a run. */
	public static void serverLine(String line) {
		if (!inDungeon) {
			return;
		}
		Matcher matcher;
		if (DungeonParse.START.matcher(line).matches()) {
			startedAt = System.currentTimeMillis();
			startedTick = DungeonTicks.count();
			startedGameTime = dev.tim1337.lumio.api.experimental.util.ServerTicks.serverGameTime();
			phase(Phase.CLEAR);
			for (Listener listener : LISTENERS) {
				listener.runStarted(floor);
			}
		} else if (DungeonParse.BLOOD_OPEN.matcher(line).matches() && (phase == Phase.CLEAR || phase == Phase.WAITING)) {
			phase(Phase.BLOOD);
		} else if (DungeonParse.WATCHER_DONE.matcher(line).matches()) {
			stats.bloodDone = true;
			if (phase != Phase.BOSS && phase != Phase.DONE) {
				phase(Phase.BLOOD_DONE);
			}
		} else if (DungeonParse.DEFEATED.matcher(line).matches()) {
			phase(Phase.DONE);
		} else if ((matcher = DungeonParse.TEAM_SCORE.matcher(line)).find()) {
			runEnded(matcher.group(1), matcher.group(2));
		} else if ((matcher = DungeonParse.DEATH.matcher(line)).find()) {
			String name = matcher.group(1);
			LocalPlayer self = Minecraft.getInstance().player;
			stats.chatDeaths++;
			DungeonPlayer player = player(name.equals("You") && self != null ? self.getName().getString() : name);
			if (player != null) {
				player.died();
			}
		} else if (DungeonParse.PRINCE.matcher(line).matches()) {
			if (!stats.princeKilled) {
				stats.princeKilled = true;
				LISTENERS.forEach(Listener::princeKilled);
			}
		} else if (DungeonParse.BAT.matcher(line).matches()) {
			// the bat's bonus counts once, also when other players' mods report the same bat
			stats.batSlain = true;
			LISTENERS.forEach(Listener::batKilled);
		} else if (DungeonParse.BONUS.matcher(line).matches()) {
			bonusLine(line);
		} else if ((matcher = DungeonParse.LEAPED.matcher(line)).matches()) {
			String name = matcher.group(1);
			for (Listener listener : LISTENERS) {
				listener.leaped(name);
			}
		} else if ((matcher = DungeonParse.KEY_PICKED.matcher(line)).matches()) {
			String kind = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
			KEYS.picked("Wither".equals(kind));
		} else if ((matcher = DungeonParse.DOOR_OPENED.matcher(line)).matches()) {
			KEYS.opened(matcher.group(2) != null && !"Blood".equals(matcher.group(2)));
		}
		for (Listener listener : LISTENERS) {
			listener.serverLine(line);
		}
		rescore();
	}

	/**
	 * A party member's line, read only for the Mimic, the Prince and bats, which other players' mods post there for the score (asked for by the
	 * user so the score agrees with theirs); nothing else of what players write is read.
	 */
	public static void partyLine(String line) {
		if (!inDungeon) {
			return;
		}
		Matcher matcher = DungeonParse.PARTY.matcher(line);
		if (!matcher.matches()) {
			return;
		}
		String said = matcher.group(2).strip().toLowerCase(java.util.Locale.ROOT);
		switch (said) {
			case "mimic killed", "mimic killed!", "mimic slain", "mimic dead", "mimic dead!" -> {
				if (floor != null && floor.hasMimic()) {
					stats.mimicKilled = true;
				}
			}
			case "prince killed", "prince killed!", "prince slain", "prince dead", "prince dead!" -> stats.princeKilled = true;
			case "bat killed", "bat killed!", "bat slain", "bat dead", "bat dead!" -> {
				// another player's bat; the player's own already came as the game's bonus line
				LocalPlayer self = Minecraft.getInstance().player;
				if (self == null || !self.getName().getString().equals(matcher.group(1))) {
					stats.batKillers.add(matcher.group(1));
				}
			}
			default -> {
				return;
			}
		}
		rescore();
	}

	/** The Mimic is a baby zombie: once one dies in sight of the player during the clear, it is the Mimic. */
	private static void watchMimic(ClientLevel world) {
		for (Entity entity : world.entitiesForRendering()) {
			if (entity instanceof Zombie zombie && zombie.isBaby() && zombie.isDeadOrDying()) {
				stats.mimicKilled = true;
				LISTENERS.forEach(Listener::mimicKilled);
				return;
			}
		}
	}

	/**
	 * @return whether the player is in the boss room: past the corner where each floor's boss room begins (the room lies beyond it in both
	 *         directions; the Entrance has none)
	 */
	static boolean inBossRoom(LocalPlayer player) {
		if (floor == null || floor.number() == 0) {
			return false;
		}
		int[] corner = BOSS_CORNERS[floor.number() - 1];
		return player.getX() > corner[0] && player.getZ() > corner[1];
	}

	private static void phase(Phase next) {
		if (phase == next) {
			return;
		}
		phase = next;
		for (Listener listener : LISTENERS) {
			listener.phaseChanged(next);
		}
	}

	private static void rescore() {
		DungeonScore.Result now = DungeonScore.of(stats, floor, phase == Phase.BOSS || phase == Phase.DONE, scoreOptions);
		score = now;
		if (phase == Phase.WAITING) {
			return;
		}
		for (int i = 0; i < SCORE_MARKS.length; i++) {
			if (!MARKS_REACHED[i] && now.total() >= SCORE_MARKS[i]) {
				MARKS_REACHED[i] = true;
				for (Listener listener : LISTENERS) {
					listener.scoreReached(SCORE_MARKS[i], now.total());
				}
			}
		}
	}

	/** A "... +N Bonus Score" line of the game: each line counts once, so the same bonus never counts twice. */
	private static void bonusLine(String line) {
		Matcher matcher = DungeonParse.BONUS.matcher(line);
		if (matcher.matches()) {
			int points = Math.clamp(DungeonParse.number(matcher.group(2), 0), 0, MOST_BONUS_LINE);
			stats.bonusLines.put(matcher.group(1) + " #" + stats.bonusLines.size(), points);
		}
	}

	/**
	 * The run ended with Hypixel's "Team Score": the score is kept for the panel's last look, and checked against ours; a difference is written
	 * to the debug log with everything it was made of (and said in chat while Debug is on), so the rules can be corrected.
	 */
	private static void runEnded(String teamScore, String teamRank) {
		finalScore = score;
		endedAt = System.currentTimeMillis();
		for (Listener listener : LISTENERS) {
			listener.runEnded();
		}
		int theirs = DungeonParse.number(teamScore, -1);
		if (theirs >= 0 && theirs != score.total()) {
			String breakdown = "Score self-check: Hypixel " + theirs + " (" + teamRank + "), Lumio " + score.total() + " = skill " + score.skill()
				+ " + exploration " + score.exploration() + " + speed " + score.speed() + " + bonus " + score.bonus() + " | floor " + floor + ", rooms "
				+ score.roomsCounted() + "/" + score.totalRooms() + " (completed " + stats.completedRooms + ", cleared " + stats.clearedPercent + " %, Blood "
				+ (stats.bloodDone ? "done" : "not done") + ", " + (inBoss() ? "in the boss" : "not in the boss") + "), secrets " + stats.secretsFound + "/"
				+ score.secretsNeeded() + " needed of " + score.totalSecrets() + " (" + stats.secretsPercent + " %), crypts " + stats.crypts + ", puzzles "
				+ stats.puzzlesDone() + " done " + stats.puzzlesFailed() + " failed of " + stats.puzzleCount + ", deaths " + stats.deaths() + ", time "
				+ stats.elapsedSeconds + " s, mimic " + stats.mimicKilled + ", prince " + stats.princeKilled + ", other bonus " + stats.otherBonus()
				+ ", options " + scoreOptions;
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, breakdown);
			}
			if (DebugHooks.ACTIVE) {
				dev.tim1337.lumio.api.LumioChat.info(net.minecraft.network.chat.Component.literal(breakdown));
			}
		}
	}

	/** Sets the player's choices the score depends on. */
	public static void scoreOptions(DungeonScore.Options options) {
		scoreOptions = options;
	}

	/** @return the final score of the run that just ended, or null */
	public static DungeonScore.Result finalScore() {
		return finalScore;
	}

	/** @return when the last run ended (its Team Score line), or 0 */
	public static long endedAt() {
		return endedAt;
	}

	private static void reset() {
		boolean was = inDungeon;
		// the scoreboard as it is now still belongs to the world just left: a run may only start from one read after this
		staleSidebar = SkyBlockArea.sidebar();
		finalScore = null;
		endedAt = 0;
		inDungeon = false;
		floor = null;
		phase = Phase.WAITING;
		stats = new DungeonStats();
		PARTY.clear();
		java.util.Arrays.fill(MARKS_REACHED, false);
		startedAt = 0;
		startedTick = 0;
		startedGameTime = -1;
		DungeonTicks.reset();
		KEYS.reset();
		readTab = null;
		readSidebar = null;
		score = DungeonScore.of(stats, null, false, DungeonScore.Options.DEFAULT);
		CurrentRoom.reset();
		DungeonScan.reset();
		if (was) {
			LISTENERS.forEach(Listener::left);
		}
	}
}
