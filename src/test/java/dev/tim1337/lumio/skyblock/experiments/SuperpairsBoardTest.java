package dev.tim1337.lumio.skyblock.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class SuperpairsBoardTest {
	private static final int CARDS = 6;
	private final SuperpairsBoard<String> board = new SuperpairsBoard<>();

	/** The board with these slots face up: "slot=key". */
	private void look(int clicked, int clicksLeft, String... up) {
		List<SuperpairsBoard.Card<String>> cards = new ArrayList<>();
		for (int slot = 0; slot < CARDS; slot++) {
			String key = null;
			for (String card : up) {
				if (card.startsWith(slot + "=")) {
					key = card.substring(2);
				}
			}
			boolean powerup = key != null && key.equals("power");
			cards.add(key == null ? new SuperpairsBoard.Card<>(slot, true, "", false, 0L, "") : new SuperpairsBoard.Card<>(slot, false, key, powerup, key.equals("xp") ? 5000L : 0L, key));
		}
		board.update(cards, clicked, clicksLeft);
	}

	@Test
	void aCardNeverShownIsUnknown() {
		look(-1, 10);
		for (int slot = 0; slot < CARDS; slot++) {
			assertNull(board.at(slot), "only what the game showed");
		}
	}

	@Test
	void aCardSeenOnceIsMarked() {
		look(0, 10, "0=a");
		look(0, 9);
		assertEquals(SuperpairsBoard.Mark.SEEN_ONCE, board.at(0).mark());
		assertTrue(board.at(0).faceDown());
		assertEquals("a", board.at(0).payload(), "kept visible");
	}

	@Test
	void twoSeenAreAKnownPairWithANumber() {
		look(0, 10, "0=a");
		look(1, 9, "1=b");
		look(2, 8, "2=a");
		look(2, 7);
		assertEquals(SuperpairsBoard.Mark.KNOWN_PAIR, board.at(0).mark());
		assertEquals(SuperpairsBoard.Mark.KNOWN_PAIR, board.at(2).mark());
		assertEquals(1, board.at(0).pair());
		assertEquals(1, board.at(2).pair());
		assertEquals(SuperpairsBoard.Mark.SEEN_ONCE, board.at(1).mark());
	}

	@Test
	void numbersStayWhenAnotherPairComes() {
		look(0, 10, "0=a");
		look(1, 9, "1=a");
		look(1, 8);
		look(2, 7, "2=b");
		look(3, 6, "3=b");
		look(3, 5);
		assertEquals(1, board.at(0).pair());
		assertEquals(2, board.at(2).pair());
	}

	@Test
	void twoFaceUpAreCollected() {
		look(1, 10, "0=xp", "1=xp");
		assertEquals(SuperpairsBoard.Mark.COLLECTED, board.at(0).mark());
		assertEquals(SuperpairsBoard.Mark.COLLECTED, board.at(1).mark());
		assertEquals(1, board.collectedPairs());
		assertEquals(5000L, board.xpCollected(), "a pair gives its Exp once");
	}

	@Test
	void aFreshCardSameAsACollectedPairIsNotCollected() {
		look(1, 10, "0=a", "1=a");
		look(2, 9, "0=a", "1=a", "2=a");
		assertEquals(1, board.collectedPairs());
		assertEquals(SuperpairsBoard.Mark.COLLECTED, board.at(0).mark());
		assertEquals(SuperpairsBoard.Mark.NONE, board.at(2).mark());
	}

	@Test
	void aPowerUpIsMarkedAndNeverAPair() {
		look(0, 10, "0=power");
		assertEquals(SuperpairsBoard.Mark.POWERUP, board.at(0).mark());
		look(1, 9, "0=power", "1=power");
		assertEquals(0, board.collectedPairs());
	}

	@Test
	void theLastRevealIsNotThePlayersFinding() {
		look(0, 1, "0=a");
		look(0, 0, "0=a");
		look(0, 0, "0=a", "1=b", "2=b", "3=c", "4=c");
		assertEquals(0, board.collectedPairs(), "the board the game shows at the end does not count");
	}

	@Test
	void aNewGameForgets() {
		look(0, 10, "0=a");
		look(0, 9);
		board.clear();
		look(-1, 10);
		assertNull(board.at(0));
	}

	@Test
	void theBestCardStillOpen() {
		SuperpairsBoard<String> board = new SuperpairsBoard<>();
		assertEquals(null, board.bestStillOpen(), "nothing flipped yet: nothing is known");
		board.update(java.util.List.of(new SuperpairsBoard.Card<>(1, false, "small", false, 5L, "small"), new SuperpairsBoard.Card<>(2, true, "", false, 0L, "")), 1, 5);
		board.update(java.util.List.of(new SuperpairsBoard.Card<>(1, true, "", false, 0L, ""), new SuperpairsBoard.Card<>(2, false, "large", false, 50L, "large")), 2, 4);
		board.update(java.util.List.of(new SuperpairsBoard.Card<>(1, true, "", false, 0L, ""), new SuperpairsBoard.Card<>(2, true, "", false, 0L, "")), 2, 4);
		assertEquals("large", board.bestStillOpen());
	}

	/** A recorded game: two cards flipped per turn, then face down again; hovering a face-down card gives what the game showed there, for every slot. */
	@Test
	void aFaceDownCardGivesTheNameTheGameShowedThere() {
		String[] names = {"Ultimate Wise III", "Experience Bottle", "Grand Experience Bottle", "Ultimate Wise III", "Titanic Experience Bottle", "Experience Bottle"};
		look(-1, 10);
		for (int slot = 0; slot < CARDS; slot++) {
			assertNull(board.seenFaceDown(slot), "nothing for a card never shown");
		}
		for (int turn = 0; turn < CARDS / 2; turn++) {
			int first = turn * 2;
			look(first, 10, first + "=" + names[first]);
			assertNull(board.seenFaceDown(first), "a card face up has its own name");
			look(first + 1, 9, first + "=" + names[first], (first + 1) + "=" + names[first + 1]);
			look(-1, 9);
		}
		for (int slot = 0; slot < CARDS; slot++) {
			assertEquals(names[slot], board.seenFaceDown(slot).key(), "slot " + slot);
		}
		// shown again with another card (as when the game shuffles): the newer one counts
		look(4, 8, "4=Enchanted Book");
		look(-1, 8);
		assertEquals("Enchanted Book", board.seenFaceDown(4).key());
		board.clear();
		look(-1, 10);
		assertNull(board.seenFaceDown(0), "a new game knows nothing");
	}
}
