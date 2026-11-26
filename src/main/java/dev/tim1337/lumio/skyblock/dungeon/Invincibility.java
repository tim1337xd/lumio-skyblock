package dev.tim1337.lumio.skyblock.dungeon;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The items that save the player from death once in a while (Bonzo Mask, Spirit Mask, Phoenix pet): when one saved the player, from the server's
 * line, and from that how long the player is invincible and when it is ready again. Kept as the server tick of the save, so the times count in
 * server ticks and need no ticking of their own. Plain logic, tested without the game.
 */
public final class Invincibility {
	/** The three, with the server's line, how long they make the player invincible and their usual cooldown. */
	/**
	 * @param ownsStarred whether the player has the mask's starred variant
	 * @return the item list's ids for a kind's icon, the first one the list has is drawn: the starred mask if owned and then the plain one, or the
	 *         Phoenix pet from the highest rarity down
	 */
	static List<String> iconIds(Kind kind, boolean ownsStarred) {
		return switch (kind) {
			case BONZO -> ownsStarred ? List.of("STARRED_BONZO_MASK", "BONZO_MASK") : List.of("BONZO_MASK", "STARRED_BONZO_MASK");
			case SPIRIT -> ownsStarred ? List.of("STARRED_SPIRIT_MASK", "SPIRIT_MASK") : List.of("SPIRIT_MASK", "STARRED_SPIRIT_MASK");
			case PHOENIX -> PHOENIX_IDS;
		};
	}

	/** The Phoenix pet in the item list: one entry per rarity, "PHOENIX;<rarity>", the highest the list has first. */
	private static final List<String> PHOENIX_IDS = phoenixIds();
	private static final int MOST_PET_RARITY = 9;

	private static List<String> phoenixIds() {
		List<String> ids = new java.util.ArrayList<>();
		for (int rarity = MOST_PET_RARITY; rarity >= 0; rarity--) {
			ids.add("PHOENIX;" + rarity);
		}
		return List.copyOf(ids);
	}

	/** @return the id of a mask's starred variant, or null for the pet */
	static String starredId(Kind kind) {
		return switch (kind) {
			case BONZO -> "STARRED_BONZO_MASK";
			case SPIRIT -> "STARRED_SPIRIT_MASK";
			case PHOENIX -> null;
		};
	}

	/** @return how much an item of a size is scaled to sit centered in a box with an inset on every side */
	static float iconScale(int box, float inset, int itemSize) {
		return Math.max(0f, box - 2f * inset) / itemSize;
	}

	public enum Kind {
		BONZO("Bonzo Mask", "^Your (?:\\S )?Bonzo's Mask saved your life!$", 60, 180),
		SPIRIT("Spirit Mask", "^Second Wind Activated! Your Spirit Mask saved your life!$", 60, 30),
		PHOENIX("Phoenix", "^Your Phoenix Pet saved you from certain death!$", 80, 60);

		private final String label;
		private final Pattern line;
		private final int invincibleTicks;
		private final int cooldownSeconds;

		Kind(String label, String line, int invincibleTicks, int cooldownSeconds) {
			this.label = label;
			this.line = Pattern.compile(line);
			this.invincibleTicks = invincibleTicks;
			this.cooldownSeconds = cooldownSeconds;
		}

		public String label() {
			return label;
		}

		public int cooldownSeconds() {
			return cooldownSeconds;
		}
	}

	/** The three, in order, without a new array per call. */
	public static final java.util.List<Kind> KINDS = java.util.List.of(Kind.values());

	/** Where one stands: ready, invincible or cooling down, with the ticks left of that. */
	public enum State {
		READY, INVINCIBLE, COOLDOWN
	}

	/** The cooldown the Bonzo Mask writes in its lore, "Cooldown: 162s", which shrinks with its stars. */
	private static final Pattern LORE_COOLDOWN = Pattern.compile("^Cooldown: (\\d{1,4})s$");
	private static final int TICKS_PER_SECOND = 20;
	private static final long NEVER = Long.MIN_VALUE / 2;

	private final long[] savedAt = new long[KINDS.size()];
	private final long[] cooldownTicks = new long[KINDS.size()];

	public Invincibility() {
		reset();
	}

	/** @return the kind whose save a server line tells, or null */
	public static Kind savedBy(String line) {
		for (Kind kind : Kind.values()) {
			if (kind.line.matcher(line).matches()) {
				return kind;
			}
		}
		return null;
	}

	/** @return the cooldown in a lore line ("Cooldown: 162s"), in seconds, or -1 */
	public static int loreCooldown(String loreLine) {
		Matcher matcher = LORE_COOLDOWN.matcher(loreLine.strip());
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
	}

	/**
	 * A save at a server tick.
	 *
	 * @param cooldownSeconds the cooldown read from the item, or -1 for the usual one
	 */
	public void saved(Kind kind, long tick, int cooldownSeconds) {
		savedAt[kind.ordinal()] = tick;
		cooldownTicks[kind.ordinal()] = (long) (cooldownSeconds > 0 ? cooldownSeconds : kind.cooldownSeconds) * TICKS_PER_SECOND;
	}

	public State state(Kind kind, long tick) {
		long since = tick - savedAt[kind.ordinal()];
		if (since < kind.invincibleTicks) {
			return State.INVINCIBLE;
		}
		return since < cooldownTicks[kind.ordinal()] ? State.COOLDOWN : State.READY;
	}

	/** @return the ticks left of the state it is in: invincible or cooldown; 0 when ready */
	public long ticksLeft(Kind kind, long tick) {
		long since = tick - savedAt[kind.ordinal()];
		return switch (state(kind, tick)) {
			case INVINCIBLE -> kind.invincibleTicks - since;
			case COOLDOWN -> cooldownTicks[kind.ordinal()] - since;
			case READY -> 0;
		};
	}

	/** @return how much of the cooldown is still left, from 1 (just saved) to 0 (ready) */
	public float cooldownShare(Kind kind, long tick) {
		long total = cooldownTicks[kind.ordinal()];
		long since = tick - savedAt[kind.ordinal()];
		return total <= 0 || since >= total ? 0f : 1f - (float) since / total;
	}

	/** @return how many of the three are not ready */
	public int used(long tick) {
		int count = 0;
		for (Kind kind : KINDS) {
			if (state(kind, tick) != State.READY) {
				count++;
			}
		}
		return count;
	}

	public void reset() {
		for (int i = 0; i < savedAt.length; i++) {
			savedAt[i] = NEVER;
			cooldownTicks[i] = 0;
		}
	}
}
