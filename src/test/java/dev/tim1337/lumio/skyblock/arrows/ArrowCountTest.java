package dev.tim1337.lumio.skyblock.arrows;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Counting down per shot, the server's value going first, refills, and the alarm once per drop and at none. */
class ArrowCountTest {
	private static final int WARN = 100;
	private static final String FLINT = "Flint Arrow";

	private static ArrowCount with(int value) {
		ArrowCount count = new ArrowCount();
		count.real(FLINT, value, WARN);
		return count;
	}

	@Test
	void nothingIsKnownAtFirst() {
		ArrowCount count = new ArrowCount();
		assertFalse(count.known());
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		assertEquals(0, count.count());
	}

	@Test
	void countsDownPerShot() {
		ArrowCount count = with(2880);
		count.shot(WARN);
		count.shot(WARN);
		assertEquals(2878, count.count());
	}

	@Test
	void neverBelowNone() {
		ArrowCount count = with(1);
		count.shot(WARN);
		count.shot(WARN);
		assertEquals(0, count.count());
	}

	@Test
	void theRealValueGoesFirst() {
		ArrowCount count = with(2880);
		count.shot(WARN);
		count.shot(WARN);
		count.real(FLINT, 2879, WARN);
		assertEquals(2879, count.count());
	}

	@Test
	void theBarIsFullAtTheMostSinceTheRefill() {
		ArrowCount count = with(2880);
		count.real(FLINT, 1000, WARN);
		assertEquals(2880, count.most());
		count.real(FLINT, 1800, WARN);
		assertEquals(1800, count.most(), "a refill starts again");
		count.shot(WARN);
		assertEquals(1800, count.most());
	}

	@Test
	void aSmallCorrectionIsNoRefill() {
		ArrowCount count = with(2880);
		count.real(FLINT, 2000, WARN);
		count.real(FLINT, 2005, WARN);
		assertEquals(2880, count.most());
	}

	@Test
	void anotherArrowStartsAgain() {
		ArrowCount count = with(2880);
		count.real("Explosive Arrow", 64, WARN);
		assertEquals("Explosive Arrow", count.name());
		assertEquals(64, count.most());
	}

	@Test
	void theAlarmOncePerDrop() {
		ArrowCount count = with(101);
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		assertEquals(100, count.count());
		assertEquals(ArrowCount.Alarm.LOW, count.shot(WARN));
		for (int i = 0; i < 50; i++) {
			assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		}
	}

	@Test
	void aRealValueCrossesTooAndOnlyOnce() {
		ArrowCount count = with(500);
		assertEquals(ArrowCount.Alarm.LOW, count.real(FLINT, 50, WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 10, WARN));
	}

	@Test
	void oneMoreAtNone() {
		ArrowCount count = with(101);
		count.shot(WARN);
		assertEquals(ArrowCount.Alarm.LOW, count.shot(WARN));
		count.real(FLINT, 1, WARN);
		assertEquals(ArrowCount.Alarm.OUT, count.shot(WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 0, WARN));
	}

	@Test
	void straightToNoneIsOneAlarm() {
		ArrowCount count = with(500);
		assertEquals(ArrowCount.Alarm.OUT, count.real(FLINT, 0, WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
	}

	@Test
	void noRepeatUntilARefillAboveTheLevel() {
		ArrowCount count = with(100);
		assertEquals(ArrowCount.Alarm.LOW, count.real(FLINT, 80, WARN));
		// the count going up a little, or only up to under the level, is no refill
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 95, WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 80, WARN));
		// over the level: the next drop warns again
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 2000, WARN));
		assertEquals(ArrowCount.Alarm.LOW, count.real(FLINT, 99, WARN));
	}

	@Test
	void aCountThatWentTooFarDoesNotRepeatTheAlarm() {
		ArrowCount count = with(100);
		assertEquals(ArrowCount.Alarm.LOW, count.shot(WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 103, WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		assertEquals(ArrowCount.Alarm.NONE, count.real(FLINT, 99, WARN));
	}

	@Test
	void lowFromTheStartIsNoDrop() {
		ArrowCount count = with(50);
		assertEquals(ArrowCount.Alarm.NONE, count.shot(WARN));
		assertEquals(49, count.count());
	}

	@Test
	void changingTheLevelIsNoDrop() {
		ArrowCount count = with(150);
		assertEquals(ArrowCount.Alarm.NONE, count.shot(200));
	}

	@Test
	void jaxAddsToTheSelectedArrow() {
		ArrowCount count = with(50);
		assertEquals(ArrowCount.Alarm.NONE, count.forged(FLINT, 2842, WARN));
		assertEquals(2892, count.count());
		assertEquals(2892, count.most());
		assertEquals(ArrowCount.Alarm.NONE, count.forged("Explosive Arrow", 2880, WARN));
		assertEquals(2892, count.count());
		assertTrue(count.known());
		assertEquals(ArrowCount.Alarm.LOW, count.real(FLINT, 99, WARN), "a refill: the next drop warns");
	}
}
