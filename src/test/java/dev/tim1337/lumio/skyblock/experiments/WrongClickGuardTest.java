package dev.tim1337.lumio.skyblock.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

class WrongClickGuardTest {
	private static final ExperimentFormats.Phase SHOW = ExperimentFormats.Phase.READ;
	private static final ExperimentFormats.Phase REPEAT = ExperimentFormats.Phase.REPLICATE;
	private final SequenceMemory memory = new SequenceMemory();

	private void chronomatronRoundOf(String... order) {
		for (String color : order) {
			memory.seeChronomatron(SHOW, order.length, Set.of(color));
			memory.seeChronomatron(SHOW, order.length, Set.of());
		}
		memory.seeChronomatron(REPEAT, order.length, Set.of());
	}

	private void ultrasequencerRound() {
		memory.seeUltrasequencer(SHOW, Map.of(20, 2, 11, 1, 30, 3));
		memory.seeUltrasequencer(REPEAT, Map.of());
	}

	private boolean holdsColor(String color) {
		return WrongClickGuard.holdsBack(true, true, false, memory.colorStepsLeft(), memory.isNextColor(color));
	}

	private boolean holdsSlot(int slot) {
		return WrongClickGuard.holdsBack(true, true, false, memory.slotStepsLeft(), memory.isNextSlot(slot));
	}

	@Test
	void aRightColorGoesThroughAndMovesTheProgress() {
		chronomatronRoundOf("Red", "Blue");
		assertFalse(holdsColor("Red"));
		memory.clickedColor("Red");
		assertEquals(1, memory.done());
		assertFalse(holdsColor("Blue"));
	}

	@Test
	void aWrongColorIsHeldBackAndTheProgressStays() {
		chronomatronRoundOf("Red", "Blue");
		assertTrue(holdsColor("Blue"));
		assertEquals(0, memory.done());
		assertFalse(holdsColor("Red"), "the next color is still the first one");
	}

	@Test
	void theColorCountsNotWhereItIs() {
		chronomatronRoundOf("Red", "Red");
		memory.clickedColor("Red");
		assertFalse(holdsColor("Red"), "the same color again is right, on any slot of it");
	}

	@Test
	void aRightSlotGoesThroughAndMovesTheProgress() {
		ultrasequencerRound();
		assertFalse(holdsSlot(11));
		memory.clickedSlot(11);
		assertEquals(1, memory.done());
		assertFalse(holdsSlot(20));
	}

	@Test
	void aWrongSlotIsHeldBackAndTheProgressStays() {
		ultrasequencerRound();
		assertTrue(holdsSlot(20), "a number of the round, but not the next");
		assertTrue(holdsSlot(45), "a slot without a number");
		assertEquals(0, memory.done());
	}

	@Test
	void shiftLetsAnyClickThrough() {
		chronomatronRoundOf("Red", "Blue");
		assertFalse(WrongClickGuard.holdsBack(true, true, true, memory.colorStepsLeft(), memory.isNextColor("Blue")));
		ultrasequencerRound();
		assertFalse(WrongClickGuard.holdsBack(true, true, true, memory.slotStepsLeft(), memory.isNextSlot(20)));
	}

	@Test
	void theSwitchOffLetsAnyClickThrough() {
		chronomatronRoundOf("Red", "Blue");
		assertFalse(WrongClickGuard.holdsBack(false, true, false, memory.colorStepsLeft(), memory.isNextColor("Blue")));
	}

	@Test
	void offThePrivateIslandAnyClickGoesThrough() {
		chronomatronRoundOf("Red", "Blue");
		assertFalse(WrongClickGuard.holdsBack(true, false, false, memory.colorStepsLeft(), memory.isNextColor("Blue")));
		ultrasequencerRound();
		assertFalse(WrongClickGuard.holdsBack(true, false, false, memory.slotStepsLeft(), memory.isNextSlot(20)));
	}

	@Test
	void whileTheGameShowsThePatternNothingIsHeldBack() {
		memory.seeChronomatron(SHOW, 2, Set.of("Red"));
		assertFalse(holdsColor("Blue"));
		memory.clear();
		memory.seeUltrasequencer(SHOW, Map.of(20, 2, 11, 1));
		assertFalse(holdsSlot(20));
	}

	@Test
	void withNoStepsLeftNothingIsHeldBack() {
		chronomatronRoundOf("Red");
		memory.clickedColor("Red");
		assertFalse(holdsColor("Blue"));
		memory.clear();
		memory.seeUltrasequencer(SHOW, Map.of(11, 1));
		memory.seeUltrasequencer(REPEAT, Map.of());
		memory.clickedSlot(11);
		assertFalse(holdsSlot(20));
	}

	@Test
	void onceTheRoundIsOverNothingIsHeldBack() {
		chronomatronRoundOf("Red", "Blue");
		memory.seeChronomatron(ExperimentFormats.Phase.NONE, 2, Set.of());
		assertFalse(holdsColor("Blue"));
	}

	@Test
	void anOrderNotFullySeenHoldsNothingBack() {
		memory.seeChronomatron(SHOW, 3, Set.of("Red"));
		memory.seeChronomatron(SHOW, 3, Set.of());
		memory.seeChronomatron(REPEAT, 3, Set.of());
		assertFalse(holdsColor("Blue"), "only one of three flashes was seen");
		memory.clear();
		memory.seeUltrasequencer(SHOW, Map.of(11, 1, 30, 3));
		memory.seeUltrasequencer(REPEAT, Map.of());
		assertFalse(holdsSlot(30), "number 2 was not seen");
	}

	@Test
	void theRightClickMovesTheMarkAndTheUpdateConfirmsIt() {
		chronomatronRoundOf("Red", "Blue");
		memory.clickedColor("Red");
		assertEquals("blue", memory.nextColor(0), "the right click counts at once");
		assertEquals(0, memory.confirmed(), "not yet followed by the game");
		memory.seeChronomatron(ExperimentFormats.Phase.REPLICATE, 2, Set.of());
		assertEquals(1, memory.confirmed(), "the next update confirms it");
	}

	@Test
	void anAnswerThatEndsTheRoundMovesNoMark() {
		chronomatronRoundOf("Red", "Blue");
		memory.clickedColor("Red");
		memory.seeChronomatron(ExperimentFormats.Phase.NONE, 2, Set.of());
		assertEquals(0, memory.confirmed());
	}
}
