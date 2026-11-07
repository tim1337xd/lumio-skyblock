package dev.tim1337.lumio.skyblock.slayer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The line under the boss in the scoreboard's slayer quest, in the formats it comes in: "5/18 Kills", "(1,840/2,400) Combat XP", also with k ("1.2k/2.4k Combat XP"), and what comes after them ("Slay the boss!", "Boss slain!"). Plain text, so it is unit
 * tested with the lines as they are shown.
 *
 * @param state  what the line says
 * @param done   how far the quest is, in its unit, while it counts
 * @param needed how far it has to go, in its unit, while it counts
 */
record SlayerProgress(State state, long done, long needed) {
	/** What the line says. */
	enum State {
		KILLS, XP, BOSS_UP, BOSS_SLAIN, NONE, UNKNOWN
	}

	private static final Pattern COUNT = Pattern.compile("^\\(?\\s*([\\d.,]{1,12})(k?)\\s*/\\s*([\\d.,]{1,12})(k?)\\s*\\)?\\s*(kills?|combat xp|xp)$");
	private static final Pattern COLOR_CODE = Pattern.compile("§.");
	/** Every kind of space a line can have, also those the game's fonts do not show. */
	private static final Pattern ODD_SPACE = Pattern.compile("[\\s\\p{Z}]+");
	private static final int THOUSAND = 1000;
	private static final SlayerProgress NONE = new SlayerProgress(State.NONE, 0L, 0L);
	private static final SlayerProgress UNKNOWN = new SlayerProgress(State.UNKNOWN, 0L, 0L);

	/** @return what a line of the scoreboard says */
	static SlayerProgress of(String line) {
		if (line == null) {
			return NONE;
		}
		String plain = ODD_SPACE.matcher(COLOR_CODE.matcher(line).replaceAll("")).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
		if (plain.isEmpty()) {
			return NONE;
		}
		if (plain.contains("slay the boss")) {
			return new SlayerProgress(State.BOSS_UP, 0L, 0L);
		}
		if (plain.contains("boss slain")) {
			return new SlayerProgress(State.BOSS_SLAIN, 0L, 0L);
		}
		Matcher count = COUNT.matcher(plain);
		if (!count.matches()) {
			return UNKNOWN;
		}
		long done = number(count.group(1), count.group(2));
		long needed = number(count.group(3), count.group(4));
		if (done < 0L || needed <= 0L) {
			return UNKNOWN;
		}
		return new SlayerProgress(count.group(5).startsWith("kill") ? State.KILLS : State.XP, Math.min(done, needed), needed);
	}

	/** @return a number as the scoreboard writes it, or -1 */
	private static long number(String digits, String thousands) {
		try {
			double value = Double.parseDouble(digits.replace(",", ""));
			return Math.round(thousands.isEmpty() ? value : value * THOUSAND);
		} catch (NumberFormatException e) {
			return -1L;
		}
	}

	/** @return whether it counts toward the boss */
	boolean counting() {
		return state == State.KILLS || state == State.XP;
	}

	/** @return how far along it is, from 0 to 1 */
	float share() {
		return needed > 0L ? done / (float) needed : 0f;
	}

	/** @return what the panel says, in the unit the scoreboard uses: "Boss in 13 kills", "Boss in 1.2k XP"; while the boss is up or slain, that; else nothing */
	String text() {
		return switch (state) {
			case KILLS -> {
				long left = needed - done;
				yield "Boss in " + left + (left == 1L ? " kill" : " kills");
			}
			case XP -> "Boss in " + amount(needed - done) + " XP";
			case BOSS_UP -> "Slay the boss!";
			case BOSS_SLAIN -> "Boss slain!";
			case NONE, UNKNOWN -> "";
		};
	}

	/** @return an amount as the panel writes it: 840, 1.2k, 22k */
	private static String amount(long value) {
		if (value < THOUSAND) {
			return Long.toString(value);
		}
		long tenths = Math.round(value / 100.0);
		return tenths % 10L == 0L ? tenths / 10L + "k" : tenths / 10L + "." + tenths % 10L + "k";
	}
}
