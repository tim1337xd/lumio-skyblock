package dev.tim1337.lumio.skyblock.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The rounds for the most Superpairs clicks, read from the tier clicked in the stakes. The lore is laid out as other mods read it (SkyHanni's
 * "Chain of N:" and "Series of N:"), not captured from the server: the debug log writes the real lines.
 */
class MostClicksTest {
	private static final ExperimentFormats.Game CHRONO = ExperimentFormats.Game.CHRONOMATRON;
	private static final ExperimentFormats.Game ULTRA = ExperimentFormats.Game.ULTRASEQUENCER;

	/** A tier's lore: its rewards by chain length, the longest last; with the serum, each length shortened and the old one struck before it. */
	private static List<String> tier(ExperimentFormats.Game game, int longest, int serum) {
		String word = game == CHRONO ? "Chain of " : "Series of ";
		List<String> lore = new ArrayList<>(List.of("Repeat the pattern shown to earn rewards!", "", "Rewards:"));
		for (int length = longest - 2; length <= longest; length++) {
			if (serum > 0) {
				lore.add(word + length + ": +1 Superpairs click (before the serum)");
			}
			lore.add(word + (length - serum) + ": +" + (length - longest + 3) + " Superpairs clicks");
		}
		lore.add("");
		lore.add("Click to play!");
		return lore;
	}

	@Test
	void readsEveryTierWithAndWithoutTheSerum() {
		int[][] tiers = {{8, 0}, {11, 0}, {14, 0}, {8, 3}, {11, 2}, {14, 1}};
		for (ExperimentFormats.Game game : List.of(CHRONO, ULTRA)) {
			for (int[] tier : tiers) {
				MostClicks.Tier read = MostClicks.tierOf(game, tier(game, tier[0], tier[1]));
				assertEquals(tier[0] - tier[1], read.rounds(), game + " " + tier[0] + " with serum " + tier[1]);
				assertFalse(read.practice());
			}
		}
	}

	@Test
	void anotherGamesWordIsNotRead() {
		assertEquals(0, MostClicks.tierOf(ULTRA, tier(CHRONO, 11, 0)).rounds());
	}

	@Test
	void practiceGivesNoAlert() {
		MostClicks clicks = new MostClicks();
		List<String> lore = new ArrayList<>(tier(CHRONO, 11, 0));
		lore.add(ExperimentFormats.PRACTICE_TEXT);
		assertTrue(clicks.picked(CHRONO, lore, 0L).practice());
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "Practice"), 100L);
		for (int round = 1; round < 30; round++) {
			assertFalse(clicks.reached(round));
		}
	}

	@Test
	void chronomatronIsDoneOnceTheRoundAfterNBegan() {
		MostClicks clicks = run(CHRONO, "Supreme", 11);
		for (int round = 1; round <= 11; round++) {
			assertFalse(clicks.reached(round), "round " + round);
		}
		assertTrue(clicks.reached(12));
		assertFalse(clicks.reached(13), "once per run");
		assertEquals("Chronomatron (Supreme): max Superpairs clicks reached", clicks.text());
	}

	@Test
	void ultrasequencerIsDoneOneRoundEarlier() {
		MostClicks clicks = run(ULTRA, "Metaphysical", 14);
		for (int round = 1; round <= 13; round++) {
			assertFalse(clicks.reached(round), "round " + round);
		}
		assertTrue(clicks.reached(14));
		assertEquals("Ultrasequencer (Metaphysical): max Superpairs clicks reached", clicks.text());
	}

	@Test
	void theClickedTierCountsNotTheHighest() {
		MostClicks clicks = new MostClicks();
		clicks.picked(CHRONO, tier(CHRONO, 8, 0), 0L);
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "High"), 50L);
		assertTrue(clicks.reached(9));
	}

	@Test
	void aLockedTierKeepsThePickBefore() {
		MostClicks clicks = new MostClicks();
		clicks.picked(CHRONO, tier(CHRONO, 8, 0), 0L);
		List<String> locked = new ArrayList<>(tier(CHRONO, 14, 0));
		locked.add(ExperimentFormats.LEVEL_TOO_LOW_TEXT);
		assertTrue(clicks.picked(CHRONO, locked, 10L).locked());
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "High"), 50L);
		assertEquals(8, clicks.rounds());
	}

	@Test
	void eachRunNeedsItsOwnClick() {
		MostClicks clicks = run(CHRONO, "Supreme", 11);
		clicks.closed();
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "Supreme"), 10_000L);
		assertEquals(0, clicks.rounds(), "a run without a clicked tier knows nothing");
		assertFalse(clicks.reached(20));
	}

	@Test
	void aPickForAnotherGameOrTooLongAgoIsNotUsed() {
		MostClicks clicks = new MostClicks();
		clicks.picked(ULTRA, tier(ULTRA, 11, 0), 0L);
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "Supreme"), 10L);
		assertEquals(0, clicks.rounds());
		clicks.picked(CHRONO, tier(CHRONO, 11, 0), 0L);
		clicks.runStarted(new ExperimentFormats.Menu(CHRONO, "Supreme"), MostClicks.START_MILLIS + 1L);
		assertEquals(0, clicks.rounds());
	}

	@Test
	void aShortCloseBetweenTheStakesAndTheRunKeepsThePick() {
		MostClicks clicks = new MostClicks();
		clicks.picked(ULTRA, tier(ULTRA, 11, 0), 0L);
		clicks.closed();
		clicks.runStarted(new ExperimentFormats.Menu(ULTRA, "Supreme"), 200L);
		assertEquals(11, clicks.rounds());
	}

	@Test
	void stakesTitlesAreRead() {
		assertEquals(CHRONO, ExperimentFormats.stakesGame("Chronomatron ➜ Stakes"));
		assertEquals(ULTRA, ExperimentFormats.stakesGame("Ultrasequencer ➜ Stakes"));
		assertEquals(null, ExperimentFormats.stakesGame("Experimentation Table"));
	}

	private static MostClicks run(ExperimentFormats.Game game, String tierName, int rounds) {
		MostClicks clicks = new MostClicks();
		clicks.picked(game, tier(game, rounds, 0), 0L);
		clicks.runStarted(new ExperimentFormats.Menu(game, tierName), 100L);
		assertEquals(rounds, clicks.rounds());
		return clicks;
	}
}
