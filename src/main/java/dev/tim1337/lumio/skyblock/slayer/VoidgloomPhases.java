package dev.tim1337.lumio.skyblock.slayer;

/**
 * The phases of a Voidgloom Seraph fight. The boss spawns behind a hit shield, which comes back at two thirds and at one third of its health: every
 * shield is a phase ("Hits", while its name tag shows the hits left instead of its health), and so is every part between them ("Damage"). Tier IV
 * has three shields, so six phases: Hits, Damage, Hits, Damage, Hits, Damage. For a tier whose count is not known the shields are counted as they
 * come, and the phases have no total.
 * <p>
 * Fed with each reading of the name tag; a moment without one (the boss turned away) changes nothing. Shields are counted as they come up, also
 * one right after another when a fast kill skips the damage phase between them (the hits jump back up); the health places a fight joined late. No
 * game classes, so it is unit tested with recorded name tags.
 */
final class VoidgloomPhases {
	/** The shields per tier, where known; 0 for unknown. */
	private static final int[] SHIELDS_BY_TIER = {0, 0, 0, 0, 3, 0};
	/** How far the hits must jump up within a shield to be a new one (a shield broken and the next one up before any damage was read). */
	private static final int LEAST_JUMP = 3;

	/** What the panel shows: the phase (from 1) and how many there are (0 when not known), its kind, and its bar from 0 to 1. */
	record View(int step, int steps, boolean hits, int hitsLeft, int hitsTotal, float bar) {
	}

	private int shields;
	/** The shield reached, from 1; 0 before the first reading. */
	private int shield;
	private boolean inShield;
	private int hitsLeft;
	/** The shield's total: the highest number of hits seen since it came up. */
	private int hitsTotal;
	/** The boss's health as a share of its most, as last read; -1 while not known. */
	private double health = -1;

	/** Forgets the fight, for a new boss. */
	void clear() {
		shields = 0;
		shield = 0;
		inShield = false;
		hitsLeft = 0;
		hitsTotal = 0;
		health = -1;
	}

	/** Sets the boss's tier, which tells how many shields it has; only before the first reading. */
	void tier(int tier) {
		if (shield == 0) {
			shields = tier >= 0 && tier < SHIELDS_BY_TIER.length ? SHIELDS_BY_TIER[tier] : 0;
		}
	}

	/**
	 * A reading of the name tag.
	 *
	 * @param hits     the hits left on the shield, or -1 when the name tag shows none (it shows the health instead)
	 * @param fraction the health as a share of the most, or -1 when not known (no health in the name tag)
	 */
	void read(int hits, double fraction) {
		if (hits >= 0) {
			if (!inShield) {
				// a shield came up: the next one. Counted, not taken from the health: a hit can take the health below the threshold a moment before
				// the shield shows, which would skip a phase.
				shield = shield == 0 ? 1 : shield + 1;
				hitsTotal = hits;
			} else if (hits > hitsLeft + Math.max(LEAST_JUMP, hitsTotal / 2)) {
				// a full shield again while the last one was nearly broken: it broke, and the next came up before any damage was read
				shield++;
				hitsTotal = hits;
			}
			shield = capped(shield);
			inShield = true;
			hitsLeft = hits;
			hitsTotal = Math.max(hitsTotal, hits);
		} else if (fraction >= 0) {
			health = fraction;
			// The health tells the damage phase where counting cannot: a fight joined late, or a shield never seen (two phases ahead). One phase
			// ahead is the dip of a hit just before the next shield shows, which is not a new phase yet.
			int at = shieldAt(fraction);
			if (shield == 0) {
				shield = Math.max(1, at);
			} else if (at > shield + 1) {
				shield = at;
			}
			shield = capped(shield);
			inShield = false;
		}
	}

	/** @return the shield whose damage phase this health belongs to: the first above two thirds, then the second, then the third; 0 when unknown */
	private int shieldAt(double fraction) {
		if (shields == 0 || fraction < 0) {
			return 0;
		}
		int at = shields - (int) Math.ceil(fraction * shields - 1e-9) + 1;
		return Math.clamp(at, 1, shields);
	}

	private int capped(int value) {
		return shields > 0 ? Math.min(value, shields) : value;
	}

	/** @return whether a reading was taken yet */
	boolean started() {
		return shield > 0;
	}

	/** @return what the panel shows now */
	View view() {
		int step = inShield ? 2 * shield - 1 : 2 * shield;
		float bar;
		if (inShield) {
			bar = hitsTotal > 0 ? (float) hitsLeft / hitsTotal : 1f;
		} else if (shields > 0 && health >= 0) {
			// this damage phase's own part of the health: full when it starts, empty at the next shield
			double part = 1.0 / shields;
			double low = 1.0 - shield * part;
			bar = (float) Math.clamp((health - low) / part, 0.0, 1.0);
		} else {
			bar = health >= 0 ? (float) health : 1f;
		}
		return new View(Math.max(1, step), shields > 0 ? 2 * shields : 0, inShield, hitsLeft, hitsTotal, bar);
	}
}
