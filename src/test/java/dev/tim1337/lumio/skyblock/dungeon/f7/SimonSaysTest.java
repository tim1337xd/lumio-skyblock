package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The Simon Says order: a normal run, the skip with two and with three lanterns in the first phase, a second start, a teammate's clicks, the grid's reset. */
class SimonSaysTest {
	/** The button grid gone for a moment and back: what ends a round's showing. */
	private static void gridGoneAndBack(SimonSays simon) {
		for (int i = 0; i < SimonSays.GRID_TICKS; i++) {
			simon.grid(false);
		}
		for (int i = 0; i < SimonSays.GRID_TICKS; i++) {
			simon.grid(true);
		}
	}

	@Test
	void aNormalRunIsTakenAsShown() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		assertTrue(simon.firstPhase());
		// round one: one lantern, then the grid is back
		simon.shown(5);
		gridGoneAndBack(simon);
		assertFalse(simon.firstPhase());
		assertEquals(List.of(5), simon.remaining());
		simon.pressed(5);
		assertEquals(0, simon.size());
		// round two and three: the order as shown, a lantern shown twice counts once
		simon.shown(5);
		simon.shown(9);
		assertEquals(List.of(5, 9), simon.remaining());
		simon.pressed(5);
		simon.pressed(9);
		simon.shown(5);
		simon.shown(9);
		simon.shown(5);
		simon.shown(2);
		assertEquals(3, simon.size());
		assertEquals(0, simon.place(5));
		assertEquals(1, simon.place(9));
		assertEquals(2, simon.place(2));
		assertEquals(-1, simon.place(7));
		simon.pressed(5);
		assertEquals(-1, simon.place(5));
		assertEquals(0, simon.place(9));
		simon.pressed(9);
		simon.pressed(2);
		assertEquals(0, simon.size());
		assertEquals(-1, simon.place(2));
	}

	@Test
	void theSkipWithTwoLanternsTurnsTheOrderAround() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.startPressed();
		simon.shown(3);
		simon.shown(8);
		assertEquals(List.of(8, 3), simon.remaining());
		assertEquals(0, simon.place(8));
		assertEquals(1, simon.place(3));
		gridGoneAndBack(simon);
		assertFalse(simon.firstPhase());
		// the corrected order stays once the first phase is over
		assertEquals(List.of(8, 3), simon.remaining());
	}

	@Test
	void theSkipWithThreeLanternsDropsTheOneBeforeTheLast() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.startPressed();
		simon.startPressed();
		assertEquals(3, simon.startPresses());
		simon.shown(3);
		simon.shown(8);
		simon.shown(12);
		// 3, then 8 3 (turned around), then 8 3 12 with the one before the last dropped
		assertEquals(List.of(8, 12), simon.remaining());
		simon.pressed(8);
		simon.pressed(12);
		// the first round completed ends the first phase, also without the grid's reset
		assertFalse(simon.firstPhase());
		assertEquals(0, simon.startPresses());
		simon.shown(1);
		simon.shown(2);
		simon.shown(3);
		assertEquals(List.of(1, 2, 3), simon.remaining());
	}

	@Test
	void startPressedAgainMidWayStartsOver() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.shown(5);
		gridGoneAndBack(simon);
		simon.pressed(5);
		simon.shown(5);
		simon.shown(9);
		simon.pressed(5);
		assertFalse(simon.firstPhase());
		simon.startPressed();
		assertTrue(simon.firstPhase());
		assertEquals(0, simon.size());
		assertEquals(1, simon.startPresses());
		assertEquals(-1, simon.place(9));
		// and the first phase's correction applies again
		simon.shown(4);
		simon.shown(6);
		assertEquals(List.of(6, 4), simon.remaining());
	}

	@Test
	void aTeammatesClicksMoveTheOrderOn() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.shown(5);
		gridGoneAndBack(simon);
		simon.pressed(5);
		simon.shown(1);
		simon.shown(2);
		simon.shown(3);
		simon.shown(4);
		// presses seen in the world, whoever made them: a later one of the order jumps there, one already done changes nothing
		simon.pressed(1);
		simon.pressed(3);
		assertEquals(List.of(4), simon.remaining());
		simon.pressed(2);
		assertEquals(List.of(4), simon.remaining());
		// a button that is not in the order changes nothing
		simon.pressed(15);
		assertEquals(0, simon.place(4));
	}

	@Test
	void theGridsResetNeedsAFewTicksAndEndsTheFirstPhaseOnlyAfterItWasGone() {
		SimonSays simon = new SimonSays();
		simon.startPressed();
		simon.shown(7);
		// the grid standing all along ends nothing
		for (int i = 0; i < 10; i++) {
			simon.grid(true);
		}
		assertTrue(simon.firstPhase());
		// gone for a tick only (a block update in between): no reset seen
		simon.grid(false);
		simon.grid(true);
		simon.grid(true);
		simon.grid(true);
		assertTrue(simon.firstPhase());
		gridGoneAndBack(simon);
		assertFalse(simon.firstPhase());
		assertEquals(List.of(7), simon.remaining());
	}

	@Test
	void resetForgetsEverythingAndTheLogTellsWhatHappened() {
		List<String> said = new ArrayList<>();
		SimonSays simon = new SimonSays(said::add);
		simon.startPressed();
		simon.shown(3);
		simon.shown(8);
		simon.reset();
		assertEquals(0, simon.size());
		assertFalse(simon.firstPhase());
		assertEquals(-1, simon.place(8));
		assertEquals(4, said.size());
		assertTrue(said.get(0).startsWith("start pressed"));
		assertTrue(said.get(2).endsWith("order [8, 3]"), said.get(2));
		assertEquals("reset", said.get(3));
	}
}
