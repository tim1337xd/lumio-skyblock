package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The terracotta timers: a flower pot where none runs starts one, 15 s in F6 and 12 s in M6 counted in server ticks, gone at 0; all of them go on a
 * world change and half a second after Sadan's "ENOUGH!".
 */
class TerracottaTimersTest {
	private static final Object WORLD = new Object();

	private static TerracottaTimers inWorld() {
		TerracottaTimers timers = new TerracottaTimers();
		timers.tick(0, WORLD, place -> { });
		return timers;
	}

	@Test
	void aFlowerPotStartsATimer() {
		TerracottaTimers timers = inWorld();
		assertEquals(-1, timers.ticksLeft(7, 100));
		assertTrue(timers.potAppeared(7, 100, false));
		assertEquals(300, timers.ticksLeft(7, 100), "F6: 300 server ticks");
		assertTrue(timers.potAppeared(9, 100, true));
		assertEquals(240, timers.ticksLeft(9, 100), "M6: 240 server ticks");
		assertEquals(280, timers.ticksLeft(7, 120), "0.05 s off per server tick");
	}

	@Test
	void neverStartedTwice() {
		TerracottaTimers timers = inWorld();
		timers.potAppeared(7, 100, false);
		assertFalse(timers.potAppeared(7, 140, false), "more updates of a pot whose timer runs change nothing");
		assertEquals(260, timers.ticksLeft(7, 140));
	}

	@Test
	void removedAtZero() {
		TerracottaTimers timers = inWorld();
		List<Long> removed = new ArrayList<>();
		timers.potAppeared(7, 100, true);
		timers.tick(339, WORLD, removed::add);
		assertEquals(1, timers.ticksLeft(7, 339));
		timers.tick(340, WORLD, removed::add);
		assertEquals(-1, timers.ticksLeft(7, 340), "at 0 the timer is gone");
		assertEquals(List.of(7L), removed);
		assertTrue(timers.potAppeared(7, 400, true), "the next death starts a new one");
	}

	@Test
	void clearedHalfASecondAfterEnough() {
		TerracottaTimers timers = inWorld();
		timers.potAppeared(7, 100, false);
		timers.enough(150);
		timers.tick(159, WORLD, place -> { });
		assertFalse(timers.isEmpty(), "still there within the half second");
		timers.tick(160, WORLD, place -> { });
		assertTrue(timers.isEmpty());
	}

	@Test
	void clearedOnWorldChange() {
		TerracottaTimers timers = inWorld();
		timers.potAppeared(7, 100, false);
		timers.tick(101, new Object(), place -> { });
		assertTrue(timers.isEmpty());
	}

	@Test
	void textAndColors() {
		assertEquals("15.0s", TerracottaTimers.text(TerracottaTimers.tenths(300)));
		assertEquals("7.4s", TerracottaTimers.text(TerracottaTimers.tenths(148)));
		assertEquals("0.1s", TerracottaTimers.text(TerracottaTimers.tenths(1)));
		assertEquals(TerracottaTimers.Stage.GREEN, TerracottaTimers.stage(101), "green above 5 s");
		assertEquals(TerracottaTimers.Stage.ORANGE, TerracottaTimers.stage(100));
		assertEquals(TerracottaTimers.Stage.ORANGE, TerracottaTimers.stage(41), "orange above 2 s");
		assertEquals(TerracottaTimers.Stage.RED, TerracottaTimers.stage(40));
	}

	@Test
	void sizeAndReach() {
		assertTrue(TerracottaTimers.inReach(64.0));
		assertFalse(TerracottaTimers.inReach(64.5));
		assertEquals(0.05f, TerracottaTimers.worldScale(3), 1e-6, "twice a name tag near");
		assertEquals(TerracottaTimers.worldScale(32) / 32, TerracottaTimers.worldScale(64) / 64, 1e-6, "the same size on the screen far away");
	}

	@Test
	void theTextStandsInTheFirstAirAboveThePot() {
		// blocks at 70 and 71 above a pot at 69: the text goes into 72
		assertEquals(72, TerracottaTimers.firstAirAbove(69, y -> y >= 72));
		// air right above
		assertEquals(70, TerracottaTimers.firstAirAbove(69, y -> true));
		// never inside a block: with no air within reach it stays at the top of the climb
		assertEquals(69 + TerracottaTimers.MOST_CLIMB, TerracottaTimers.firstAirAbove(69, y -> false));
	}

	@Test
	void textsCloseTogetherArePushedApart() {
		double[] x = {10.5, 10.5, 20.5};
		double[] y = {72.5, 72.5, 72.5};
		double[] z = {5.5, 6.2, 5.5};
		TerracottaTimers.spread(x, y, z, 3, 1.0, 0.0);
		assertEquals(10.5 - TerracottaTimers.SIDEWAYS_BLOCKS, x[0], 1e-9);
		assertEquals(10.5 + TerracottaTimers.SIDEWAYS_BLOCKS, x[1], 1e-9);
		// one far away stays
		assertEquals(20.5, x[2], 1e-9);
	}

	@Test
	void aTimerEndsWhenItsPotTurnsBack() {
		TerracottaTimers timers = new TerracottaTimers();
		timers.potAppeared(1L, 100, false);
		// back 3 ticks before the predicted end
		assertEquals(3, timers.back(1L, 100 + TerracottaTimers.RESPAWN_TICKS - 3));
		assertEquals(-1, timers.ticksLeft(1L, 100 + TerracottaTimers.RESPAWN_TICKS - 2));
		assertEquals(-1, timers.back(2L, 500));
	}
}
