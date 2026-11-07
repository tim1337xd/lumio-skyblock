package dev.tim1337.lumio.skyblock.misc;

import java.util.regex.Pattern;

/**
 * Whether an armor stand is a cocoon. Its head alone does not tell: the Tarantula's egg sacs and other helpers wear heads too. A cocoon has exactly the larva's texture, is no part of a boss, and
 * appears where a mob just died: with the chat line "CAUGHT! You cocooned a ...!" for the player's own, within a second of the death for another player's. Plain text and numbers, so it is unit
 * tested.
 */
final class CocoonCheck {
	/** How far apart the chat line and the mob's death may be from the cocoon, for the player's own, in seconds. */
	static final double OWN_SECONDS = 3.0;
	/** How far apart the mob's death may be from the cocoon, without a chat line, for another player's, in seconds. */
	static final double OTHERS_SECONDS = 1.0;
	/** How long a stand with the texture waits for what it lacks: the death or the chat line can come a moment after it. */
	static final double WAIT_SECONDS = 1.0;
	/** It has not happened. */
	static final double NEVER = Double.MAX_VALUE;

	/** The name above an egg sac, and its timer: "11s 7/9". */
	private static final String SAC_LABEL = "SHOOT ME!";
	private static final Pattern SAC_TIMER = Pattern.compile("^\\d+s \\d+/\\d+$");

	/** What it is, as far as is known now. */
	enum Verdict {
		COCOON, WAIT, NOT
	}

	/**
	 * A stand as it is seen.
	 *
	 * @param texture     the end of its head's texture address, "" when it has none
	 * @param bossPart    whether a boss's reader counts it as a part of the boss, such as of an egg sac
	 * @param helperLabel whether an egg sac's name or timer stands right at it
	 * @param fromChat    how long ago the player's cocoon line came, in seconds, or {@link #NEVER}
	 * @param fromDeath   how long ago a mob died at its place, in seconds, or {@link #NEVER}
	 * @param sameHeads   how many other stands with the same head stand near it: egg sacs come in groups, a cocoon alone
	 * @param tarantula   whether a Tarantula boss is being fought here: its little spiders keep dying right at its egg sacs
	 */
	record Sighting(String texture, boolean bossPart, boolean helperLabel, double fromChat, double fromDeath, int sameHeads, boolean tarantula) {
	}

	private CocoonCheck() {
	}

	/**
	 * @param wanted the end of the larva's texture address
	 * @param waited how long the stand has been looked at, in seconds
	 * @return what the stand is
	 */
	static Verdict judge(Sighting stand, String wanted, double waited) {
		if (!wanted.equals(stand.texture()) || stand.bossPart() || stand.helperLabel()) {
			return Verdict.NOT;
		}
		boolean own = stand.fromChat() <= OWN_SECONDS && stand.fromDeath() <= OWN_SECONDS;
		if (own) {
			return Verdict.COCOON;
		}
		// without the server's line about the player's own cocoon, the head and a death nearby are all a sac has too: during such a fight, or in a group, it is none
		if (stand.tarantula() || stand.sameHeads() > 0) {
			return waited < WAIT_SECONDS ? Verdict.WAIT : Verdict.NOT;
		}
		if (stand.fromDeath() <= OTHERS_SECONDS) {
			return Verdict.COCOON;
		}
		return waited < WAIT_SECONDS ? Verdict.WAIT : Verdict.NOT;
	}

	/** @return whether a stand's name, without colors, says that an egg sac is here */
	static boolean isHelperLabel(String name) {
		String shown = name.strip();
		return SAC_LABEL.equalsIgnoreCase(shown) || SAC_TIMER.matcher(shown).matches();
	}
}
