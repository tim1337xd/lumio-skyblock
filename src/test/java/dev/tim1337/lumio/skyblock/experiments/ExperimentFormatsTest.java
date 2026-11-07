package dev.tim1337.lumio.skyblock.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class ExperimentFormatsTest {
	@Test
	void readsMenus() {
		assertEquals(new ExperimentFormats.Menu(ExperimentFormats.Game.SUPERPAIRS, "Metaphysical"), ExperimentFormats.menu("Superpairs (Metaphysical)"));
		assertEquals(ExperimentFormats.Game.CHRONOMATRON, ExperimentFormats.menu("Chronomatron (High)").game());
		assertEquals(ExperimentFormats.Game.ULTRASEQUENCER, ExperimentFormats.menu("Ultrasequencer (Grand)").game());
		assertNull(ExperimentFormats.menu("Superpairs Rewards"));
		assertNull(ExperimentFormats.menu("Experimentation Table"));
		assertTrue(ExperimentFormats.isOver("Experiment Over"));
		assertTrue(ExperimentFormats.isOver("Experiment over"));
		assertTrue(ExperimentFormats.isOver("Superpairs Rewards"));
		assertFalse(ExperimentFormats.isOver("Experimentation Table"));
		assertEquals(ExperimentFormats.Game.SUPERPAIRS, ExperimentFormats.stakesGame("Superpairs ➜ Stakes"));
	}

	@Test
	void boardsHaveTheirSize() {
		assertEquals(14, ExperimentFormats.cardSlots("Beginner").size());
		assertEquals(20, ExperimentFormats.cardSlots("High").size());
		assertEquals(20, ExperimentFormats.cardSlots("Grand").size());
		assertEquals(28, ExperimentFormats.cardSlots("Supreme").size());
		assertEquals(28, ExperimentFormats.cardSlots("Metaphysical").size());
		assertEquals(10, ExperimentFormats.cardSlots("Transcendent").getFirst());
		assertEquals(43, ExperimentFormats.cardSlots("Transcendent").getLast());
	}

	@Test
	void readsCards() {
		assertTrue(ExperimentFormats.isHiddenCard("?"));
		assertTrue(ExperimentFormats.isHiddenCard("Click any button!"));
		assertTrue(ExperimentFormats.isHiddenCard("Click a second button!"));
		assertTrue(ExperimentFormats.isHiddenCard("Next button is instantly rewarded!"));
		assertFalse(ExperimentFormats.isHiddenCard("Enchanted Book"));
		assertEquals(22, ExperimentFormats.remainingClicks("Remaining Clicks: 22"));
		assertEquals(-1, ExperimentFormats.remainingClicks("Clicks"));
		assertTrue(ExperimentFormats.isPowerup(List.of("Superpairs Powerup")));
		assertFalse(ExperimentFormats.isPowerup(List.of("Reward")));
		assertEquals(31_000L, ExperimentFormats.cardXp("31k Enchanting Exp"));
		assertEquals(6_500L, ExperimentFormats.cardXp("6.5k Enchanting Exp"));
		assertEquals(0L, ExperimentFormats.cardXp("Enchanted Book"));
		assertEquals("Smite VII", ExperimentFormats.cardKey("Enchanted Book", List.of("a", "b", "Smite VII")), "all books have one name");
		assertEquals("Experience Bottle", ExperimentFormats.cardKey("Experience Bottle", List.of()));
	}

	@Test
	void findsAnUltraRareBook() {
		assertEquals("Smite VII", ExperimentFormats.ultraRareBook(List.of("XX ULTRA-RARE BOOK! XX", "", "Smite VII")));
		assertNull(ExperimentFormats.ultraRareBook(List.of("Smite VII")));
		assertNull(ExperimentFormats.ultraRareBook(List.of()));
	}

	@Test
	void readsPhaseAndRound() {
		assertEquals(ExperimentFormats.Phase.READ, ExperimentFormats.phase("Remember the pattern!"));
		assertEquals(ExperimentFormats.Phase.REPLICATE, ExperimentFormats.phase("Timer: 10s"));
		assertEquals(ExperimentFormats.Phase.NONE, ExperimentFormats.phase("Close"));
		assertEquals(3, ExperimentFormats.round("Round: 3"));
		assertEquals(0, ExperimentFormats.round("Stakes"));
		assertEquals(7, ExperimentFormats.sequenceNumber("7"));
		assertEquals(0, ExperimentFormats.sequenceNumber("Red"));
		assertEquals(0, ExperimentFormats.sequenceNumber(""));
	}

	@Test
	void readsTheStakes() {
		assertEquals(9, ExperimentFormats.chainLength("Chain of 9: +3 clicks", "Chain of "));
		assertEquals(5, ExperimentFormats.chainLength("Series of 5: +1", "Series of "));
		assertEquals(0, ExperimentFormats.chainLength("Series of 5: +1", "Chain of "), "the other game's word");
		assertEquals(0, ExperimentFormats.chainLength("Practice mode has no rewards", "Chain of "));
	}

	@Test
	void readsTheRewards() {
		List<String> lore = List.of("Stakes: Metaphysical", "", "Rewards:", "+300,000 Enchanting Exp (Stakes)", "+151,000 Enchanting Exp (Pairs)", "+[Lvl 1] Guardian", "+Growth VI", "+Titanic Experience Bottle", "+6 Superpairs clicks",
			"", "Click to claim rewards!");
		List<String> lines = ExperimentFormats.rewardLines(lore);
		assertEquals(6, lines.size());
		assertEquals(new ExperimentFormats.Reward(ExperimentFormats.Reward.Kind.XP, "300,000 Enchanting Exp (Stakes)", 300_000L), ExperimentFormats.reward(" + 300,000 Enchanting Exp".replace(" + ", "+").concat(" (Stakes)")).kind() == ExperimentFormats.Reward.Kind.XP
			? new ExperimentFormats.Reward(ExperimentFormats.Reward.Kind.XP, "300,000 Enchanting Exp (Stakes)", 300_000L) : null);
		assertEquals(300_000L, ExperimentFormats.reward(lines.get(0)).amount());
		assertEquals(ExperimentFormats.Reward.Kind.XP, ExperimentFormats.reward(lines.get(1)).kind());
		assertEquals(ExperimentFormats.Reward.Kind.OTHER, ExperimentFormats.reward(lines.get(2)).kind());
		assertEquals("Guardian", ExperimentFormats.reward(lines.get(2)).name());
		assertEquals(ExperimentFormats.Reward.Kind.BOOK, ExperimentFormats.reward(lines.get(3)).kind());
		assertEquals(ExperimentFormats.Reward.Kind.BOTTLE, ExperimentFormats.reward(lines.get(4)).kind());
		assertNull(ExperimentFormats.reward(lines.get(5)), "the clicks are no reward");
		assertEquals(131_000L, ExperimentFormats.reward("+131k Enchanting Exp").amount());
		assertNull(ExperimentFormats.reward("no mark"));
		assertTrue(ExperimentFormats.rewardLines(List.of("Stakes")).isEmpty());
	}

	@Test
	void namesItems() {
		assertEquals("ENCHANTMENT_GROWTH_6", ExperimentFormats.bookId("Growth VI"));
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_1", ExperimentFormats.bookId("Ultimate Wise I"));
		assertNull(ExperimentFormats.bookId("Metaphysical Serum"));
		assertEquals("TITANIC_EXP_BOTTLE", ExperimentFormats.bottleId("Titanic Experience Bottle"));
		assertNull(ExperimentFormats.bottleId("Water Bottle"));
		assertTrue(ExperimentFormats.isBottleId("GRAND_EXP_BOTTLE"));
		assertFalse(ExperimentFormats.isBottleId(null));
		assertEquals("Superpairs", ExperimentFormats.claimedGame("You claimed the Superpairs rewards!"));
		assertNull(ExperimentFormats.claimedGame("[VIP] Bob: You claimed the Superpairs rewards!"), "a player's words are not the server's");
	}

	@Test
	void theGameSaysItIsOver() {
		assertTrue(ExperimentFormats.isProceed("Click any to proceed!"));
		assertFalse(ExperimentFormats.isProceed("Click any button!"));
		assertFalse(ExperimentFormats.isProceed("Remaining Clicks: 3"));
	}

	@Test
	void superpairsEnds() {
		assertFalse(ExperimentFormats.superpairsOver(5, 10, 28, false), "cards are hidden and clicks left");
		assertFalse(ExperimentFormats.superpairsOver(-1, 10, 28, false), "the clicks are not known yet");
		assertFalse(ExperimentFormats.superpairsOver(5, 0, 0, false), "no cards read yet");
		assertTrue(ExperimentFormats.superpairsOver(5, 10, 28, true), "the game says so");
		assertTrue(ExperimentFormats.superpairsOver(0, 10, 28, false), "the clicks are used up");
		assertTrue(ExperimentFormats.superpairsOver(5, 0, 28, false), "the game shows all its cards");
	}
}
