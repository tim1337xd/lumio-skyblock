package dev.tim1337.lumio.skyblock.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Recorded states of the menu, update by update, as the game sends them. */
class SequenceMemoryTest {
	private static final ExperimentFormats.Phase READ = ExperimentFormats.Phase.READ;
	private static final ExperimentFormats.Phase TURN = ExperimentFormats.Phase.REPLICATE;
	private static final ExperimentFormats.Phase NONE = ExperimentFormats.Phase.NONE;
	private static final Set<String> EMPTY = Set.of();

	/** The game shows a round: every color flashes, with the board empty between two flashes, as the updates come. */
	private static void show(SequenceMemory memory, int round, String... colors) {
		memory.seeChronomatron(READ, round, EMPTY);
		for (String color : colors) {
			memory.seeChronomatron(READ, round, Set.of(color));
			memory.seeChronomatron(READ, round, EMPTY);
		}
		memory.seeChronomatron(TURN, round, EMPTY);
	}

	/** The player repeats: every click, and the game's update after it. */
	private static void repeat(SequenceMemory memory, int round, String... colors) {
		for (String color : colors) {
			memory.clickedColor(color);
			memory.seeChronomatron(TURN, round, EMPTY);
		}
	}

	@Test
	void chronomatronAddsOnlyTheNewColorEachRound() {
		SequenceMemory memory = new SequenceMemory();
		show(memory, 1, "Red");
		assertEquals("red", memory.nextColor(0));
		assertNull(memory.nextColor(1));
		repeat(memory, 1, "Red");
		show(memory, 2, "Red", "Lime");
		assertEquals("red", memory.nextColor(0));
		assertEquals("lime", memory.nextColor(1), "the color after that");
		repeat(memory, 2, "Red", "Lime");
		show(memory, 3, "Red", "Lime", "Blue");
		assertEquals(3, memory.length());
		memory.clickedColor("Red");
		assertEquals("lime", memory.nextColor(0), "the progress moves on with the right click");
		assertEquals("blue", memory.nextColor(1));
	}

	@Test
	void theSameColorTwiceCountsTwice() {
		SequenceMemory memory = new SequenceMemory();
		show(memory, 1, "Pink");
		repeat(memory, 1, "Pink");
		show(memory, 2, "Pink", "Pink");
		assertEquals(2, memory.length(), "the board was empty between the two flashes");
		assertEquals("pink", memory.nextColor(1));
	}

	@Test
	void oneFlashSeenTwiceIsOneStep() {
		SequenceMemory memory = new SequenceMemory();
		memory.seeChronomatron(READ, 1, EMPTY);
		memory.seeChronomatron(READ, 1, Set.of("Cyan"));
		memory.seeChronomatron(READ, 1, Set.of("Cyan"));
		memory.seeChronomatron(READ, 1, EMPTY);
		memory.seeChronomatron(TURN, 1, EMPTY);
		assertEquals(1, memory.length());
	}

	@Test
	void aWrongClickDoesNotMoveOn() {
		SequenceMemory memory = new SequenceMemory();
		show(memory, 1, "Red");
		memory.clickedColor("Blue");
		assertEquals("red", memory.nextColor(0));
		assertTrue(memory.isNextColor("red"), "colors count, whatever their case");
	}

	@Test
	void theMenuClosingForgetsAll() {
		SequenceMemory memory = new SequenceMemory();
		show(memory, 1, "Red");
		memory.clear();
		assertEquals(0, memory.length());
		assertFalse(memory.repeating());
	}

	@Test
	void noPhaseDoesNothing() {
		SequenceMemory memory = new SequenceMemory();
		show(memory, 1, "Red");
		memory.seeChronomatron(NONE, 1, Set.of("Lime"));
		assertFalse(memory.repeating());
		assertEquals(1, memory.length());
	}

	private static Map<Integer, Integer> board(int... slotAndNumber) {
		Map<Integer, Integer> board = new HashMap<>();
		for (int i = 0; i < slotAndNumber.length; i += 2) {
			board.put(slotAndNumber[i], slotAndNumber[i + 1]);
		}
		return board;
	}

	@Test
	void ultrasequencerGathersTheWholeOrder() {
		SequenceMemory memory = new SequenceMemory();
		// slow drawing: the numbers come one by one; the highest is there before the rest
		memory.seeUltrasequencer(READ, board(10, 1));
		memory.seeUltrasequencer(READ, board(10, 1, 22, 3));
		assertFalse(memory.orderComplete(), "a number not seen yet leaves its step unknown");
		memory.seeUltrasequencer(READ, board(10, 1, 22, 3, 14, 2));
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals(3, memory.length());
		assertTrue(memory.orderComplete());
		assertEquals(10, memory.nextSlot(0));
		assertEquals(14, memory.nextSlot(1));
		assertEquals(22, memory.nextSlot(2));
		assertEquals(2, memory.numberAt(14), "the numbers are kept to show them again");
		assertFalse(memory.clickedSlot(14));
		assertEquals(10, memory.nextSlot(0), "only the expected click counts");
		assertTrue(memory.clickedSlot(10));
		assertEquals(14, memory.nextSlot(0), "the next step lights up at once, without waiting for the server");
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals(14, memory.nextSlot(0));
		assertEquals(1, memory.confirmed(), "the game's update followed the click");
	}

	@Test
	void ultrasequencerKeepsStepsTheGameTakesOffBeforeTheTurn() {
		// as seen in the game: three numbers shown, then taken off one by one while it still showed, and the turn began with one left
		SequenceMemory memory = new SequenceMemory();
		memory.seeUltrasequencer(READ, board(43, 1, 21, 2, 15, 3));
		memory.seeUltrasequencer(READ, board(43, 1, 21, 2));
		memory.seeUltrasequencer(READ, board(43, 1));
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals(3, memory.length(), "no step is dropped");
		assertEquals("1>43, 2>21, 3>15", memory.known());
		assertTrue(memory.slotStepsLeft());
	}

	@Test
	void ultrasequencerTakesTheLastNumberThatComesWithTheTurn() {
		SequenceMemory memory = new SequenceMemory();
		memory.seeUltrasequencer(READ, board(12, 1, 30, 2, 19, 3));
		// the update that turns to the player's turn still shows the fourth number, drawn last
		memory.seeUltrasequencer(TURN, board(12, 1, 30, 2, 19, 3, 40, 4));
		assertEquals(4, memory.length());
		assertEquals(40, memory.nextSlot(3));
		assertTrue(memory.orderComplete());
		memory.seeUltrasequencer(TURN, board(40, 4));
		assertEquals(4, memory.length(), "numbers in the turn after that are not read");
	}

	@Test
	void ultrasequencerWithAStepNeverSeenJudgesNoClick() {
		SequenceMemory memory = new SequenceMemory();
		memory.seeUltrasequencer(READ, board(12, 1, 19, 3));
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals(3, memory.length());
		assertFalse(memory.slotStepsLeft(), "with step 2 unknown, wrong clicks are not held back");
		assertEquals("1>12, 2>?, 3>19", memory.known());
	}

	@Test
	void ultrasequencerAnUpdateWithoutAPhaseDoesNotStartTheShowingAnew() {
		SequenceMemory memory = new SequenceMemory();
		memory.seeUltrasequencer(READ, board(12, 1, 30, 2));
		memory.seeUltrasequencer(NONE, Map.of());
		memory.seeUltrasequencer(READ, board(19, 3));
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals("1>12, 2>30, 3>19", memory.known());
	}

	@Test
	void ultrasequencerTakesTheNextRoundAfresh() {
		SequenceMemory memory = new SequenceMemory();
		memory.seeUltrasequencer(READ, board(10, 1));
		memory.seeUltrasequencer(TURN, Map.of());
		memory.clickedSlot(10);
		memory.seeUltrasequencer(READ, board(10, 1, 30, 2));
		memory.seeUltrasequencer(TURN, Map.of());
		assertEquals(2, memory.length());
		assertEquals(10, memory.nextSlot(0), "the progress starts again with the round");
		assertEquals(0, memory.done());
	}

	/**
	 * Lag: the server's answers come late (300 ms is about six clicks of a fast player, 1000 ms the whole round), so several clicks come before any
	 * update. Each expected click counts at once and the next step is the expected one at once; the late updates then change nothing, and only the
	 * menu showing a new round takes the progress from it again.
	 */
	@Test
	void clicksCountAtOnceWhileTheServerLags() {
		for (int lateUpdates : new int[]{2, 5}) {
			SequenceMemory memory = new SequenceMemory();
			memory.seeUltrasequencer(READ, board(10, 1, 20, 2, 30, 3, 40, 4, 11, 5));
			memory.seeUltrasequencer(TURN, Map.of());
			int[] order = {10, 20, 30, 40, 11};
			for (int step = 0; step < order.length; step++) {
				assertEquals(order[step], memory.nextSlot(0), "step " + step);
				assertFalse(WrongClickGuard.holdsBack(true, true, false, memory.slotStepsLeft(), memory.isNextSlot(order[step])), "the expected click is never held back while the answers are late");
				assertTrue(memory.clickedSlot(order[step]));
				if (step == lateUpdates - 1) {
					// the late answers arrive together
					memory.seeUltrasequencer(TURN, Map.of());
				}
			}
			assertEquals(5, memory.done());
			assertFalse(memory.repeating(), "all done: nothing is marked");
			// the round failed and the game shows the order again: the progress comes from the menu
			memory.seeUltrasequencer(READ, board(10, 1, 20, 2));
			memory.seeUltrasequencer(TURN, Map.of());
			assertEquals(0, memory.done());
			assertEquals(10, memory.nextSlot(0));
		}
	}
}
