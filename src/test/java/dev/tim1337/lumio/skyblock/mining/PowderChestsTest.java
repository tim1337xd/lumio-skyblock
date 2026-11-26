package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The treasure chests' lifecycle: every chest block that appears counts, with or without its chat line (two from one line, none at all, a late
 * line, one while another waits); the line only confirms; opened, gone, timed out, a world change, the newest one, the timer colors.
 */
class PowderChestsTest {
	private static final long A = 11;
	private static final long B = 22;
	private static final long C = 33;

	@Test
	void aChestBlockAloneIsAChest() {
		PowderChests chests = new PowderChests();
		assertTrue(chests.chestAppeared(A, 100), "without any chat line");
		assertTrue(chests.contains(A));
		assertFalse(chests.confirmed(A));
		assertFalse(chests.chestAppeared(A, 101), "the same block again is not a new chest");
		assertEquals(100, chests.appearedAt(A));
	}

	@Test
	void aLateLineStillConfirmsNothingButTheChestStays() {
		PowderChests chests = new PowderChests();
		chests.chestAppeared(A, 100);
		assertEquals(PowderChests.NONE, chests.uncovered(100 + PowderChests.PAIR_WINDOW_TICKS + 1), "the line came too late to be this chest's");
		assertTrue(chests.contains(A), "the chest counts all the same");
		assertFalse(chests.confirmed(A));
	}

	@Test
	void theBlockThenTheLineConfirmIt() {
		PowderChests chests = new PowderChests();
		chests.chestAppeared(A, 100);
		assertEquals(A, chests.uncovered(125), "more than a second later, too");
		assertTrue(chests.confirmed(A));
		assertEquals(100, chests.appearedAt(A), "it counts from the block");
	}

	@Test
	void theLineThenTheBlockConfirmIt() {
		PowderChests chests = new PowderChests();
		assertEquals(PowderChests.NONE, chests.uncovered(100));
		assertTrue(chests.chestAppeared(A, 103));
		assertTrue(chests.confirmed(A));
	}

	@Test
	void twoChestsFromOneLineBothCount() {
		PowderChests chests = new PowderChests();
		chests.uncovered(100);
		assertTrue(chests.chestAppeared(A, 101));
		assertTrue(chests.chestAppeared(B, 101), "the second chest of the same line");
		assertEquals(2, chests.size());
		assertTrue(chests.confirmed(A));
		assertFalse(chests.confirmed(B), "one line confirms one chest");
	}

	@Test
	void aChestWhileAnotherStillWaitsForItsLineCounts() {
		PowderChests chests = new PowderChests();
		chests.chestAppeared(A, 100);
		chests.chestAppeared(B, 105);
		assertEquals(2, chests.size());
		assertEquals(A, chests.uncovered(106), "the first line is the older chest's");
		assertEquals(B, chests.uncovered(107), "the second the newer one's");
		assertEquals(PowderChests.NONE, chests.uncovered(108), "a third waits for a chest to come");
		assertTrue(chests.chestAppeared(C, 110));
		assertTrue(chests.confirmed(C));
	}

	@Test
	void openedOrGoneLeaves() {
		PowderChests chests = new PowderChests();
		chests.uncovered(100);
		chests.chestAppeared(A, 100);
		assertTrue(chests.remove(A));
		assertFalse(chests.remove(A), "only once");
		assertTrue(chests.isEmpty());
	}

	@Test
	void itTimesOutAfterItsLifetime() {
		PowderChests chests = new PowderChests();
		chests.uncovered(0);
		chests.chestAppeared(A, 0);
		assertEquals(List.of(), chests.expire(PowderChests.LIFETIME_TICKS - 1));
		assertEquals(1, chests.secondsLeft(A, PowderChests.LIFETIME_TICKS - 1), "rounded up");
		assertEquals(60, chests.secondsLeft(A, 0));
		assertEquals(List.of(A), chests.expire(PowderChests.LIFETIME_TICKS));
		assertTrue(chests.isEmpty());
		assertEquals(-1, chests.secondsLeft(A, 0));
	}

	@Test
	void aWorldChangeClearsAll() {
		PowderChests chests = new PowderChests();
		chests.uncovered(0);
		chests.chestAppeared(A, 0);
		chests.chestAppeared(B, 1);
		chests.clear();
		assertTrue(chests.isEmpty());
		chests.uncovered(2);
		chests.clear();
		assertTrue(chests.chestAppeared(C, 3));
		assertFalse(chests.confirmed(C), "the waiting line went too");
	}

	@Test
	void theMostRecentIsTheNewestActive() {
		PowderChests chests = new PowderChests();
		assertEquals(PowderChests.NONE, chests.mostRecent());
		chests.uncovered(0);
		chests.chestAppeared(A, 0);
		chests.uncovered(200);
		chests.chestAppeared(B, 200);
		chests.uncovered(400);
		chests.chestAppeared(C, 400);
		assertEquals(C, chests.mostRecent());
		chests.remove(C);
		assertEquals(B, chests.mostRecent(), "the next newest still there");
	}

	@Test
	void timerColors() {
		assertEquals(PowderChests.WHITE, PowderChests.timerColor(60));
		assertEquals(PowderChests.WHITE, PowderChests.timerColor(20));
		assertEquals(PowderChests.ORANGE, PowderChests.timerColor(19));
		assertEquals(PowderChests.ORANGE, PowderChests.timerColor(10));
		assertEquals(PowderChests.RED, PowderChests.timerColor(9));
		assertEquals(PowderChests.RED, PowderChests.timerColor(0));
	}
}
