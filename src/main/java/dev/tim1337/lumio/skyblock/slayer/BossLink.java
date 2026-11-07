package dev.tim1337.lumio.skyblock.slayer;

/**
 * How long a boss found by its name tag stays that same mob: through every phase, whatever its name tag says or where that tag goes, also while it rides something, until it dies or is gone. Only numbers, so it is unit tested.
 */
final class BossLink {
	/** Farther than this from its name tag the mob is not that boss any more, in blocks; a boss teleports only a little. */
	static final double LOST_BLOCKS = 32.0;
	/** How long the mob stays linked while its name tag cannot be read, in ticks. */
	static final int NO_TAG_TICKS = 60;

	private BossLink() {
	}

	/**
	 * @param removed   whether the mob is gone or dead
	 * @param sameBoss  whether the boss now watched is still the kind it was linked for, and the same one (own or carried)
	 * @param tagKnown  whether the boss's name tag is known now
	 * @param blocks    how far the mob is from the name tag, while it is known
	 * @param noTagFor  how many ticks the name tag has not been known
	 * @return whether the mob is still the boss
	 */
	static boolean keeps(boolean removed, boolean sameBoss, boolean tagKnown, double blocks, int noTagFor) {
		if (removed || !sameBoss) {
			return false;
		}
		return tagKnown ? blocks <= LOST_BLOCKS : noTagFor <= NO_TAG_TICKS;
	}
}
