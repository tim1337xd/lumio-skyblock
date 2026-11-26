package dev.tim1337.lumio.skyblock.dungeon.f7;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Floor 7 boss as the server's lines tell it: which phase (Maxor, Storm, Goldor, Necron, and the Wither King in Master Mode), in Goldor's
 * phase which section (1 to 4) with how many of its terminals, devices and levers are done, whether its device and its gate are, what,
 * and the server ticks of the moments the timers count from ({@link F7Timers}). Plain strings and ticks, tested without the game.
 */
public final class F7Progress {
	/** The phases of the fight. */
	public enum Phase {
		NONE("", ""), MAXOR("Maxor", "P1"), STORM("Storm", "P2"), GOLDOR("Goldor", "P3"), NECRON("Necron", "P4"), WITHER_KING("Wither King", "P5");

		private final String boss;
		private final String number;

		Phase(String boss, String number) {
			this.boss = boss;
			this.number = number;
		}

		public String boss() {
			return boss;
		}

		public String number() {
			return number;
		}
	}

	static final Pattern MAXOR_START = Pattern.compile("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$");
	static final Pattern STORM_START = Pattern.compile("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$");
	static final Pattern STORM_END = Pattern.compile("^\\[BOSS] Storm: I should have known that I stood no chance\\.$");
	static final Pattern GOLDOR_START = Pattern.compile("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$");
	static final Pattern CORE_OPENING = Pattern.compile("^The Core entrance is opening!$");
	static final Pattern NECRON_DROPS = Pattern.compile("^\\[BOSS] Necron: I'm afraid, your journey ends now\\.$");
	static final Pattern WITHER_KING = Pattern.compile("^\\[BOSS] Wither King: .*$");
	static final Pattern DONE = Pattern.compile("^(\\w{1,16}) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$");
	static final Pattern GATE = Pattern.compile("^The gate has been destroyed!$");
	private static final int SECTIONS = 4;
	public static final long NEVER = Long.MIN_VALUE / 2;

	private Phase phase = Phase.NONE;
	private int section;
	private int done;
	private int total;
	private boolean gate;
	private boolean device;
	private int devices;
	private long phaseStart = NEVER;
	private long sectionStart = NEVER;
	private long stormStart = NEVER;
	private long stormEnd = NEVER;
	private long goldorStart = NEVER;
	private long necronDrops = NEVER;

	/** Reads one server line at a server tick. */
	public void read(String line, long tick) {
		Matcher matcher;
		if ((matcher = DONE.matcher(line)).matches()) {
			count(matcher, tick);
		} else if (GATE.matcher(line).matches()) {
			gate = true;
			nextSectionIfDone(tick);
		} else if (MAXOR_START.matcher(line).matches()) {
			enter(Phase.MAXOR, tick);
		} else if (STORM_START.matcher(line).matches()) {
			enter(Phase.STORM, tick);
			stormStart = tick;
		} else if (STORM_END.matcher(line).matches()) {
			stormEnd = tick;
		} else if (GOLDOR_START.matcher(line).matches()) {
			enter(Phase.GOLDOR, tick);
			goldorStart = tick;
			startSection(1, tick);
		} else if (CORE_OPENING.matcher(line).matches()) {
			enter(Phase.NECRON, tick);
		} else if (NECRON_DROPS.matcher(line).matches()) {
			necronDrops = tick;
		} else if (WITHER_KING.matcher(line).matches() && phase == Phase.NECRON) {
			enter(Phase.WITHER_KING, tick);
		}
	}

	private void enter(Phase next, long tick) {
		phase = next;
		phaseStart = tick;
	}

	private void count(Matcher matcher, long tick) {
		if (phase != Phase.GOLDOR) {
			return;
		}
		String kind = matcher.group(3);
		done = matcher.group(4).charAt(0) - '0';
		total = matcher.group(5).charAt(0) - '0';
		if (kind.equals("device")) {
			device = true;
			devices++;
		}
		nextSectionIfDone(tick);
	}

	/** A section is over when all its parts are done and its gate is down; after the fourth the core opens instead. */
	private void nextSectionIfDone(long tick) {
		if (total > 0 && done >= total && gate && section < SECTIONS) {
			startSection(section + 1, tick);
		}
	}

	private void startSection(int next, long tick) {
		section = next;
		sectionStart = tick;
		done = 0;
		total = 0;
		gate = false;
		device = false;
	}

	/** The run is over or the world changed. */
	public void reset() {
		phase = Phase.NONE;
		section = 0;
		done = 0;
		total = 0;
		gate = false;
		device = false;
		devices = 0;
		phaseStart = sectionStart = stormStart = stormEnd = goldorStart = necronDrops = NEVER;
	}

	public Phase phase() {
		return phase;
	}

	/** @return the section of Goldor's phase, 1 to 4, or 0 outside it */
	public int section() {
		return phase == Phase.GOLDOR ? section : 0;
	}

	public int done() {
		return done;
	}

	/** @return how many parts the section has, as the server last said, or 0 before the first */
	public int total() {
		return total;
	}

	public boolean gate() {
		return gate;
	}

	public boolean device() {
		return device;
	}

	/** @return the devices done in all sections so far */
	public int devices() {
		return devices;
	}

	public long phaseStart() {
		return phaseStart;
	}

	public long sectionStart() {
		return sectionStart;
	}

	public long stormStart() {
		return stormStart;
	}

	public long stormEnd() {
		return stormEnd;
	}

	public long goldorStart() {
		return goldorStart;
	}

	public long necronDrops() {
		return necronDrops;
	}
}
