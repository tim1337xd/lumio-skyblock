package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The phases of a run, each from one line of the server to the next, timed in real time and in server ticks: Blood open, Blood clear, Portal, the
 * boss's phases of the floor, and the total. The lines and phases follow Odin's splits (BSD-3-Clause, see CREDITS.md). A run's splits are made
 * when it starts and only take lines in their order.
 */
public final class DungeonSplits {
	/** One line of the server that ends a phase and starts the next, with the name of the phase it starts. */
	private record Mark(Pattern line, String name) {
	}

	/** One phase as shown: its name, how long it took (or has taken so far), and whether it is going on now. */
	public record Row(String name, long millis, long ticks, boolean current, boolean done) {
	}

	/** A finished phase as the debug log shows it: its server ticks, its real time and the server's game time over it (-1 if unknown). */
	public record Check(String name, long ticks, long millis, long gameTicks) {
	}

	private static final Pattern PORTAL = Pattern.compile("^\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.$");
	private static final List<List<Mark>> FLOORS = List.of(
		List.of(),
		List.of(mark("^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$", "Bonzo"),
			mark("^\\[BOSS] Bonzo: Oh I'm dead!$", "Cleared")),
		List.of(mark("^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$", "Minions"),
			mark("^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$", "Scarf")),
		List.of(mark("^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$", "Guardians"),
			mark("^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$", "Professor"),
			mark("^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$", "Cleared")),
		List.of(mark("^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$", "Thorn")),
		List.of(mark("^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$", "Livid")),
		List.of(mark("^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$", "Terracottas"),
			mark("^\\[BOSS] Sadan: ENOUGH!$", "Giants"),
			mark("^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$", "Sadan")),
		List.of(mark("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$", "Maxor"),
			mark("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$", "Storm"),
			mark("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$", "Terminals"),
			mark("^The Core entrance is opening!$", "Goldor"),
			mark("^\\[BOSS] Necron: You went further than any human before, congratulations\\.$", "Necron"),
			mark("^\\[BOSS] Wither King: You\\.\\.\\. again\\?$", "Wither King")));

	private final List<Mark> marks = new ArrayList<>();
	private final long[] millis;
	private final long[] ticks;
	private final long[] gameTimes;
	private int reached;

	/** @param floor the floor of the run; its boss's phases follow the clear */
	public DungeonSplits(DungeonFloor floor, long startMillis, long startTick) {
		this(floor, startMillis, startTick, -1);
	}

	/** @param startGameTime the server's game time at the start, or -1 if none is known (only for the debug cross-check) */
	public DungeonSplits(DungeonFloor floor, long startMillis, long startTick, long startGameTime) {
		marks.add(mark(DungeonParse.START.pattern(), "Blood open"));
		marks.add(new Mark(DungeonParse.BLOOD_OPEN, "Blood clear"));
		marks.add(new Mark(PORTAL, "Portal"));
		List<Mark> boss = FLOORS.get(floor == null ? 0 : floor.number());
		marks.addAll(boss);
		marks.add(new Mark(DungeonParse.DEFEATED, "Total"));
		millis = new long[marks.size()];
		ticks = new long[marks.size()];
		gameTimes = new long[marks.size()];
		millis[0] = startMillis;
		ticks[0] = startTick;
		gameTimes[0] = startGameTime;
		reached = 1;
	}

	private static Mark mark(String line, String name) {
		return new Mark(Pattern.compile(line), name);
	}

	/** Takes a line of the server: the next phase starts if it is the line it starts with (or a later one). */
	public void line(String line, long nowMillis, long nowTick) {
		line(line, nowMillis, nowTick, -1);
	}

	/**
	 * Takes a line of the server with the server's game time of now (-1 if unknown).
	 *
	 * @return how many phases it finished, 0 if it is none of the lines
	 */
	public int line(String line, long nowMillis, long nowTick, long nowGameTime) {
		for (int i = reached; i < marks.size(); i++) {
			if (marks.get(i).line().matcher(line).find()) {
				for (int skipped = reached; skipped <= i; skipped++) {
					millis[skipped] = nowMillis;
					ticks[skipped] = nowTick;
					gameTimes[skipped] = nowGameTime;
				}
				int finished = i + 1 - reached;
				reached = i + 1;
				return finished;
			}
		}
		return 0;
	}

	/** @return the finished phase at this place (from 0), with its game time over it, -1 where a game time was unknown */
	public Check check(int phase) {
		long gameTicks = gameTimes[phase] < 0 || gameTimes[phase + 1] < 0 ? -1 : gameTimes[phase + 1] - gameTimes[phase];
		return new Check(marks.get(phase).name(), ticks[phase + 1] - ticks[phase], millis[phase + 1] - millis[phase], gameTicks);
	}

	/** @return how many phases are finished so far */
	public int finished() {
		return reached - 1;
	}

	/** @return whether the run is over (the boss defeated) */
	public boolean over() {
		return reached >= marks.size();
	}

	/** @return how many phases there are, the total not counted */
	public int phases() {
		return marks.size() - 1;
	}

	/** @return the phases so far, each as long as it took or has taken until now, and the total last */
	public List<Row> rows(long nowMillis, long nowTick) {
		List<Row> rows = new ArrayList<>(marks.size());
		for (int i = 0; i < marks.size() - 1; i++) {
			boolean started = i < reached;
			boolean done = i + 1 < reached;
			long millisSpent = !started ? 0 : (done ? millis[i + 1] : nowMillis) - millis[i];
			long ticksSpent = !started ? 0 : (done ? ticks[i + 1] : nowTick) - ticks[i];
			rows.add(new Row(marks.get(i).name(), millisSpent, ticksSpent, started && !done, done));
		}
		boolean over = over();
		rows.add(new Row("Total", (over ? millis[marks.size() - 1] : nowMillis) - millis[0], (over ? ticks[marks.size() - 1] : nowTick) - ticks[0], false,
			over));
		return rows;
	}

	/** How much a tick time may be above the real time by rounding alone. */
	static final long TICK_ROUNDING_MILLIS = 50;

	/**
	 * @return whether a split's server ticks can be true: as time they are never more than its real time (they are less when the server lags),
	 *         but for rounding. More means ticks were counted that never passed, and the tick time is not shown then.
	 */
	public static boolean ticksPlausible(long ticks, long millis, long millisPerTick) {
		return ticks * millisPerTick <= millis + TICK_ROUNDING_MILLIS;
	}

	/** @return "1:12.05" for a time in milliseconds, "0:31.40" under a minute */
	public static String time(long millis) {
		long safe = Math.max(0, millis);
		long minutes = safe / 60_000;
		long seconds = safe / 1000 % 60;
		long hundredths = safe / 10 % 100;
		return minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + (hundredths < 10 ? "0" : "") + hundredths;
	}

	/** @return "−1.2" or "+0.8": a difference in seconds, one decimal */
	public static String delta(long millis) {
		long tenths = Math.round(millis / 100.0);
		return (tenths < 0 ? "−" : "+") + Math.abs(tenths) / 10 + "." + Math.abs(tenths) % 10;
	}
}
