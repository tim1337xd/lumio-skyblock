package dev.tim1337.lumio.skyblock.farming;

/**
 * What the one row of the Pests panel's simple style shows: the pests while some are alive, else the time until the next can come, or that
 * it can come now. Plain logic, tested without the game.
 */
enum PestRow {
	/** The pest icon, "Pests", the bar of pests alive out of the most, and the number. */
	PESTS,
	/** The timer icon, "Next pest", the progress bar and the time left. */
	COOLDOWN,
	/** "Next pest · ready": the cooldown is over and no pest has come yet. */
	READY;

	/**
	 * @param alive       the pests alive
	 * @param showsNext   "Next pest" is on in Shows: off, the row shows the pest count only, also 0
	 * @param canCome     the cooldown is over
	 * @return what the row shows
	 */
	static PestRow of(int alive, boolean showsNext, boolean canCome) {
		if (alive > 0 || !showsNext) {
			return PESTS;
		}
		return canCome ? READY : COOLDOWN;
	}

	/** @return the number of the pests row, with the plots after it when they are shown and there are pests: "3 · 4, 12" */
	static String countText(int alive, boolean showsPlots, String plots) {
		return alive > 0 && showsPlots && plots != null && !plots.isEmpty() ? alive + " · " + plots : Integer.toString(alive);
	}
}
