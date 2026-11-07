package dev.tim1337.lumio.skyblock.experiments;

import java.util.List;

/**
 * When Chronomatron or Ultrasequencer adds no more Superpairs clicks. The rounds it takes are read from the tier the player clicks in the game's
 * "Stakes" menu ("Chain of N:" or "Series of N:" in its lore, with the Metaphysical Serum already taken off), never guessed: they hold for the
 * run that click starts, and for that game only. The round counts as shown in the game's top slot: Chronomatron is done once the round after N
 * has begun, Ultrasequencer one round earlier, as the game needs one round less there. Plain logic, so it is unit tested.
 */
public final class MostClicks {
	/** How long a click on a tier waits for its run to open; the next menu comes at once, so this is plenty. */
	static final long START_MILLIS = 5_000L;

	/** What a click on a tier says: the rounds it needs, none for practice, or no run at all for a locked tier. */
	public record Tier(int rounds, boolean practice, boolean locked, String line) {
	}

	private ExperimentFormats.Game pickedGame;
	private int pickedRounds;
	private long pickedAt;
	/** The run in progress: its game, its tier's name, its rounds; 0 rounds for none. */
	private ExperimentFormats.Game runGame;
	private String runTier = "";
	private int runRounds;
	private boolean told;

	/** @return what the lore of a tier of a game's stakes says */
	public static Tier tierOf(ExperimentFormats.Game game, List<String> lore) {
		String word = game == ExperimentFormats.Game.CHRONOMATRON ? "Chain of " : "Series of ";
		int rounds = 0;
		String found = "";
		boolean practice = false;
		boolean locked = false;
		for (String line : lore) {
			practice |= line.contains(ExperimentFormats.PRACTICE_TEXT);
			locked |= line.contains(ExperimentFormats.LEVEL_TOO_LOW_TEXT) || line.contains(ExperimentFormats.NOT_ENOUGH_XP_TEXT);
			int count = ExperimentFormats.chainLength(line, word);
			if (count > 0) {
				// the last such line counts: with the serum, the shortened one comes after the struck-through one
				rounds = count;
				found = line;
			}
		}
		return new Tier(practice ? 0 : rounds, practice, locked, found);
	}

	/**
	 * The player clicked a tier in a game's stakes.
	 *
	 * @return what was read, for the log
	 */
	public Tier picked(ExperimentFormats.Game game, List<String> lore, long nowMillis) {
		Tier tier = tierOf(game, lore);
		if (tier.locked()) {
			// a locked tier starts nothing: what was picked before stays
			return tier;
		}
		pickedGame = game;
		pickedRounds = tier.rounds();
		pickedAt = nowMillis;
		return tier;
	}

	/** A game's menu opened: a new run, which takes the rounds of the tier clicked for it, if any. */
	public void runStarted(ExperimentFormats.Menu menu, long nowMillis) {
		boolean fromPick = pickedGame == menu.game() && nowMillis - pickedAt <= START_MILLIS;
		runGame = menu.game();
		runTier = menu.tier();
		runRounds = fromPick ? pickedRounds : 0;
		told = false;
		pickedGame = null;
	}

	/** The menu closed: the run is forgotten. A tier just picked stays for its run, which may open after a moment without a menu. */
	public void closed() {
		runGame = null;
		runRounds = 0;
		told = false;
	}

	/** @return whether the run is done with a round shown now, and was not told yet: true only once per run */
	public boolean reached(int roundShown) {
		if (told || runGame == null || runRounds <= 0 || roundShown <= 0) {
			return false;
		}
		int needed = runGame == ExperimentFormats.Game.ULTRASEQUENCER ? runRounds - 1 : runRounds;
		if (roundShown > needed) {
			told = true;
			return true;
		}
		return false;
	}

	/** @return the rounds of the run in progress, 0 for none */
	public int rounds() {
		return runRounds;
	}

	/** @return the alert's text, as "Ultrasequencer (Supreme): max Superpairs clicks reached" */
	public String text() {
		String game = runGame == null ? "" : switch (runGame) {
			case CHRONOMATRON -> "Chronomatron";
			case ULTRASEQUENCER -> "Ultrasequencer";
			case SUPERPAIRS -> "Superpairs";
		};
		return game + " (" + runTier + "): max Superpairs clicks reached";
	}
}
