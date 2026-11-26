package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The run's score as Hypixel's end-of-run "Team Score" counts it: Skill + Exploration + Speed + Bonus, from what the run shows now, together with
 * the most it can still reach and the cheapest piece still missing for 300. These are the game's rules, written out in our own code. Plain numbers,
 * so it is tested without the game.
 */
public final class DungeonScore {
	/** The rooms a dungeon is counted with while neither the game nor the map has told its size. */
	static final int ROOMS_UNKNOWN = 36;
	private static final int MOST_PART = 100;
	private static final int SECRET_POINTS = 40;
	private static final int ROOM_EXPLORATION_POINTS = 60;
	private static final int ROOM_SKILL_POINTS = 80;
	private static final int SKILL_FLOOR = 20;
	private static final int PUZZLE_COST = 10;
	private static final int MOST_CRYPTS = 5;
	private static final int MIMIC_POINTS = 2;
	private static final int PRINCE_POINTS = 1;
	private static final int PAUL_POINTS = 10;
	public static final int S_SCORE = 270;
	public static final int S_PLUS_SCORE = 300;
	static final int A_SCORE = 230;
	static final int B_SCORE = 160;
	static final int C_SCORE = 100;

	/**
	 * The time limit of each floor in seconds, up to which speed is full: E, F1 to F7, then M1 to M7, in {@link DungeonFloor}'s order. One table,
	 * easy to update when Hypixel changes them.
	 */
	private static final int[] TIME_LIMIT_SECONDS = {600, 600, 600, 600, 720, 600, 720, 840, 480, 480, 480, 480, 480, 600, 840};
	/** Speed over the limit: the share of the time over it in brackets, each costing a point per this many percent; the last goes on. */
	private static final double[] BRACKET_PERCENT = {20, 20, 10, 10, Double.MAX_VALUE};
	private static final double[] BRACKET_DIVISOR = {2, 3.5, 4, 5, 6};

	private DungeonScore() {
	}

	/** The player's choices the score depends on. */
	public record Options(boolean spiritFirstDeath, boolean paul) {
		public static final Options DEFAULT = new Options(true, false);
	}

	/**
	 * The score's parts, the total and its rank, the most it can still reach, the secrets the full secret points need, and the cheapest piece still
	 * missing for 300 ("" at 300 or more).
	 */
	public record Result(int skill, int exploration, int speed, int bonus, int total, int best, int secretsNeeded, int totalSecrets, int totalRooms,
		int roomsCounted, String for300) {
		/** @return the rank the score has: S+, S, A, B, C or D */
		public String rank() {
			return DungeonScore.rank(total);
		}
	}

	/** @param inBoss whether the boss fight is on: before it the boss room counts as done ahead, as the end screen counts it */
	public static Result of(DungeonStats stats, DungeonFloor floor, boolean inBoss, Options options) {
		int totalRooms = stats.totalRooms() > 0 ? stats.totalRooms() : stats.mapRooms > 0 ? stats.mapRooms : ROOMS_UNKNOWN;
		// the blood room counts as done until the Watcher is, and the boss room until the fight: the game's final score counts both
		int roomsCounted = Math.min(totalRooms, stats.completedRooms + (stats.bloodDone ? 0 : 1) + (inBoss ? 0 : 1));
		int totalSecrets = stats.totalSecrets() > 0 ? stats.totalSecrets() : stats.mapSecrets;
		int needed = totalSecrets <= 0 ? 0 : (int) Math.ceil(totalSecrets * secretShare(floor) - 1e-9);
		int failed = stats.puzzlesFailed();
		int open = Math.max(0, stats.puzzleCount - stats.puzzlesDone() - failed);
		int deathCost = deathCost(stats.deaths(), options.spiritFirstDeath());

		int roomSkill = points(roomsCounted, totalRooms, ROOM_SKILL_POINTS);
		int skill = Math.clamp(SKILL_FLOOR + roomSkill - PUZZLE_COST * (failed + open) - deathCost, SKILL_FLOOR, MOST_PART);
		int secretPoints = secretPoints(stats, floor, needed);
		int exploration = points(roomsCounted, totalRooms, ROOM_EXPLORATION_POINTS) + secretPoints;
		int speed = speed(floor, stats.elapsedSeconds);
		boolean mimicFloor = floor != null && floor.hasMimic();
		int bonus = Math.min(stats.crypts, MOST_CRYPTS) + (mimicFloor && stats.mimicKilled ? MIMIC_POINTS : 0) + (stats.princeKilled ? PRINCE_POINTS : 0)
			+ stats.otherBonus() + (options.paul() ? PAUL_POINTS : 0);
		int total = skill + exploration + speed + bonus;

		// everything still open done: all rooms, open puzzles (failed ones stay lost), the needed secrets, five crypts, the Mimic and the Prince
		// while they can still happen; speed as it is now
		boolean stillClearing = !inBoss;
		int bestSkill = Math.clamp(SKILL_FLOOR + ROOM_SKILL_POINTS - PUZZLE_COST * failed - deathCost, SKILL_FLOOR, MOST_PART);
		int bestBonus = MOST_CRYPTS + (mimicFloor && (stats.mimicKilled || stillClearing) ? MIMIC_POINTS : 0)
			+ (stats.princeKilled || stillClearing ? PRINCE_POINTS : 0) + stats.otherBonus() + (options.paul() ? PAUL_POINTS : 0);
		int best = Math.max(total, bestSkill + MOST_PART + speed + bestBonus);

		Step to300 = step(stats, floor, needed, bestSkill, speed, mimicFloor, stillClearing, options, S_PLUS_SCORE);
		String for300 = total >= S_PLUS_SCORE || to300.reached() ? "" : to300.outOfReach() ? "300 out of reach" : "+" + to300.what() + " for 300";
		return new Result(skill, exploration, speed, bonus, total, best, needed, totalSecrets, totalRooms, roomsCounted, for300);
	}

	/**
	 * The next step toward a score: what is still missing for it, in words without a sign ("30 secrets", "2 crypts", "the Prince", "30 secrets, 2
	 * crypts"); reached when nothing is; out of reach when nothing left can make it.
	 */
	public record Step(boolean reached, boolean outOfReach, String what) {
		static final Step REACHED = new Step(true, false, "");
		static final Step OUT_OF_REACH = new Step(false, true, "");
	}

	/**
	 * @param target the score aimed for: 300 for S+, 270 for S
	 * @return the cheapest piece still missing for the target, the remaining rooms and open puzzles taken as done
	 */
	public static Step step(DungeonStats stats, DungeonFloor floor, boolean inBoss, Options options, int target) {
		int totalSecrets = stats.totalSecrets() > 0 ? stats.totalSecrets() : stats.mapSecrets;
		int needed = totalSecrets <= 0 ? 0 : (int) Math.ceil(totalSecrets * secretShare(floor) - 1e-9);
		int bestSkill = Math.clamp(SKILL_FLOOR + ROOM_SKILL_POINTS - PUZZLE_COST * stats.puzzlesFailed() - deathCost(stats.deaths(), options.spiritFirstDeath()),
			SKILL_FLOOR, MOST_PART);
		return step(stats, floor, needed, bestSkill, speed(floor, stats.elapsedSeconds), floor != null && floor.hasMimic(), !inBoss, options, target);
	}

	private static Step step(DungeonStats stats, DungeonFloor floor, int needed, int bestSkill, int speed, boolean mimicFloor, boolean stillClearing,
		Options options, int target) {
		int secretPoints = secretPoints(stats, floor, needed);
		int crypts = Math.min(stats.crypts, MOST_CRYPTS);
		int bonus = crypts + (mimicFloor && stats.mimicKilled ? MIMIC_POINTS : 0) + (stats.princeKilled ? PRINCE_POINTS : 0) + stats.otherBonus()
			+ (options.paul() ? PAUL_POINTS : 0);
		int missing = target - (bestSkill + ROOM_EXPLORATION_POINTS + secretPoints + speed + bonus);
		if (missing <= 0) {
			return Step.REACHED;
		}
		int cryptsLeft = MOST_CRYPTS - crypts;
		int secrets = needed == 0 ? -1 : secretsFor(stats.secretsFound, needed, secretPoints + missing);
		if (missing <= cryptsLeft && (secrets < 0 || missing <= secrets)) {
			return new Step(false, false, missing + (missing == 1 ? " crypt" : " crypts"));
		}
		if (secrets > 0) {
			return new Step(false, false, secrets + (secrets == 1 ? " secret" : " secrets"));
		}
		if (stillClearing && missing <= PRINCE_POINTS && !stats.princeKilled) {
			return new Step(false, false, "the Prince");
		}
		if (stillClearing && mimicFloor && missing <= MIMIC_POINTS && !stats.mimicKilled) {
			return new Step(false, false, "the Mimic");
		}
		// the secrets' points cannot cover it alone: all of them and the crypts that are left
		int fromSecrets = needed == 0 ? 0 : SECRET_POINTS - secretPoints;
		int cryptsToo = missing - fromSecrets;
		if (needed > 0 && cryptsToo > 0 && cryptsToo <= cryptsLeft) {
			int all = Math.max(0, needed - stats.secretsFound);
			return new Step(false, false, all + " secrets, +" + cryptsToo + " crypts");
		}
		return Step.OUT_OF_REACH;
	}

	/**
	 * The points of the secrets, 40 at most. The player list's percentage of the secrets found is taken where it is there, as the game counts by it:
	 * floor(40 × found% ÷ the floor's needed%). The count found only stands in while the list has no percentage yet (then against the secrets of
	 * the rooms on the map).
	 */
	static int secretPoints(DungeonStats stats, DungeonFloor floor, int needed) {
		if (stats.secretsPercent > 0) {
			return Math.clamp((int) Math.floor(SECRET_POINTS * stats.secretsPercent / (secretShare(floor) * 100.0) + 1e-9), 0, SECRET_POINTS);
		}
		return needed == 0 ? 0 : points(stats.secretsFound, needed, SECRET_POINTS);
	}

	/** @return the secrets more that bring the secret points to a value, or -1 where even all needed ones do not */
	private static int secretsFor(int found, int needed, int wantedPoints) {
		if (wantedPoints > SECRET_POINTS) {
			return -1;
		}
		for (int more = 1; found + more <= needed; more++) {
			if (points(found + more, needed, SECRET_POINTS) >= wantedPoints) {
				return more;
			}
		}
		return -1;
	}

	/** @return the points of a part worth at most {@code most}, for a done of a whole, rounded down */
	private static int points(int done, int whole, int most) {
		return whole <= 0 ? 0 : Math.clamp((int) Math.floor(most * (double) done / whole), 0, most);
	}

	/** @return the share of the secrets a floor needs for the full secret points */
	private static double secretShare(DungeonFloor floor) {
		return floor == null ? 1.0 : floor.secretShare();
	}

	/** @return what the deaths cost: two points each, the first one only one with a Spirit pet */
	static int deathCost(int deaths, boolean spiritFirstDeath) {
		return deaths <= 0 ? 0 : 2 * deaths - (spiritFirstDeath ? 1 : 0);
	}

	/** @return the speed points: full up to the floor's time limit, then less for every percent over it, in brackets */
	static int speed(DungeonFloor floor, int elapsedSeconds) {
		int limit = timeLimitSeconds(floor);
		if (elapsedSeconds <= limit) {
			return MOST_PART;
		}
		double over = (elapsedSeconds - limit) * 100.0 / limit;
		double lost = 0;
		for (int i = 0; i < BRACKET_PERCENT.length && over > 0; i++) {
			double part = Math.min(over, BRACKET_PERCENT[i]);
			lost += part / BRACKET_DIVISOR[i];
			over -= part;
		}
		return Math.max(0, (int) Math.floor(MOST_PART - lost + 1e-9));
	}

	/** @return a floor's time limit in seconds; a floor not known gets the longest */
	static int timeLimitSeconds(DungeonFloor floor) {
		return floor == null ? TIME_LIMIT_SECONDS[DungeonFloor.F7.ordinal()] : TIME_LIMIT_SECONDS[floor.ordinal()];
	}

	/** @return the rank of a score: S+ from 300, S from 270, A from 230, B from 160, C from 100, D below */
	public static String rank(int score) {
		if (score >= S_PLUS_SCORE) {
			return "S+";
		}
		if (score >= S_SCORE) {
			return "S";
		}
		if (score >= A_SCORE) {
			return "A";
		}
		if (score >= B_SCORE) {
			return "B";
		}
		return score >= C_SCORE ? "C" : "D";
	}
}
