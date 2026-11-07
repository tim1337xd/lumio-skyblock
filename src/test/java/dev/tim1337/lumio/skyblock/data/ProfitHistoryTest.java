package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profit chart's history: recorded by the tracker for its whole session (whatever the panel shows), a sample every few seconds and on gains,
 * thinned without losing its shape when full, starting over only when the session does; and the trend.
 */
class ProfitHistoryTest {
	private static final long SECOND = 1_000L;
	private static final long MINUTE = 60 * SECOND;
	private static final SkyBlockPrices.Mode MODE = SkyBlockPrices.Mode.BUY_ORDER;

	/** Plays a session second by second: active time, a gain every so often, and the tracker's tick that records the history. */
	private static void play(ProfitTracker tracker, long seconds, long gainEverySeconds, double gain) {
		for (long s = 1; s <= seconds; s++) {
			tracker.addTime(SECOND);
			if (s % gainEverySeconds == 0) {
				tracker.addCoins(gain);
			}
			tracker.recordHistory(MODE);
		}
	}

	@Test
	void theWholeSessionIsThereWhenTheChartIsShownLater() {
		// eleven minutes of slayer while the panel shows the breakdown (or nothing): the tracker records anyway
		ProfitTracker tracker = new ProfitTracker();
		play(tracker, 11 * 60, 30, 10_000);
		// then the chart is picked: it reads the tracker's history
		ProfitHistory history = tracker.history();
		assertTrue(history.count() >= 60, "a sample every ten seconds at least: " + history.count());
		assertTrue(history.time(0) <= 10 * SECOND, "it starts at the session's start, not when the chart was shown");
		assertEquals(11 * MINUTE, history.time(history.count() - 1), SECOND);
		assertEquals(tracker.bucket(ProfitTracker.Range.SESSION).total(MODE), history.total(history.count() - 1), 0.01);
	}

	@Test
	void aGainIsSampledRightAway() {
		ProfitHistory history = new ProfitHistory();
		history.record(0, 0);
		assertTrue(!history.due(500, true), "not twice within a second");
		assertTrue(history.due(1_500, true), "a gain a second later is");
		assertTrue(!history.due(5_000, false), "without a gain, only every ten seconds");
		assertTrue(history.due(10_000, false));
	}

	@Test
	void aThreeHourSessionFitsAndKeepsItsShape() {
		ProfitTracker tracker = new ProfitTracker();
		// three hours: a slow first hour, a fast second one, a pause in gains, then steady again
		play(tracker, 3600, 20, 2_000);
		play(tracker, 3600, 5, 4_000);
		play(tracker, 1800, 100_000, 0);
		play(tracker, 1800, 10, 3_000);
		ProfitHistory history = tracker.history();
		assertTrue(history.count() <= ProfitHistory.MOST);
		assertEquals(0, history.time(0), SECOND * 2, "still from the start");
		assertEquals(3 * 60 * MINUTE, history.time(history.count() - 1), SECOND);
		double end = history.total(history.count() - 1);
		// the shape: at every minute, the kept line is within 2 % of the real total
		double[] real = realTotals();
		for (int minute = 0; minute <= 180; minute++) {
			double kept = at(history, minute * MINUTE);
			assertEquals(real[minute], kept, end * 0.02, "at minute " + minute);
		}
	}

	/** @return the real session total at every minute of the three-hour session above */
	private static double[] realTotals() {
		double[] totals = new double[181];
		double total = 0;
		for (int s = 1; s <= 3 * 3600; s++) {
			if (s <= 3600 && s % 20 == 0) {
				total += 2_000;
			} else if (s > 3600 && s <= 7200 && (s - 3600) % 5 == 0) {
				total += 4_000;
			} else if (s > 9000 && (s - 9000) % 10 == 0) {
				total += 3_000;
			}
			if (s % 60 == 0) {
				totals[s / 60] = total;
			}
		}
		return totals;
	}

	/** @return the history's total at a time, on the straight line between its samples, as the chart draws it */
	private static double at(ProfitHistory history, long time) {
		for (int i = 1; i < history.count(); i++) {
			if (history.time(i) >= time) {
				long t0 = history.time(i - 1);
				long t1 = history.time(i);
				double share = t1 == t0 ? 1 : (double) (time - t0) / (t1 - t0);
				return history.total(i - 1) + (history.total(i) - history.total(i - 1)) * Math.max(0, Math.min(1, share));
			}
		}
		return history.total(0);
	}

	@Test
	void onlyASessionResetStartsOver() {
		ProfitTracker tracker = new ProfitTracker();
		play(tracker, 600, 30, 5_000);
		int count = tracker.history().count();
		tracker.reset(ProfitTracker.Range.TODAY);
		play(tracker, 1, 1, 0);
		assertTrue(tracker.history().count() >= count, "resetting today keeps the session's history");
		tracker.reset(ProfitTracker.Range.SESSION);
		assertEquals(0, tracker.history().count(), "the session was reset");
		play(tracker, 30, 10, 1_000);
		assertTrue(tracker.history().count() >= 2 && tracker.history().time(0) <= 10 * SECOND, "and it records the new session from its start");
	}

	@Test
	void aTimeGoingBackStartsOver() {
		ProfitHistory history = new ProfitHistory();
		history.record(0, 0);
		history.record(30 * MINUTE, 1_000_000);
		int before = history.version();
		history.record(10_000, 5_000);
		assertEquals(1, history.count(), "the time went back: the session was reset");
		assertTrue(history.version() != before);
	}

	@Test
	void theTrendComparesTheRateWithTenMinutesAgo() {
		ProfitHistory history = new ProfitHistory();
		// a steady million an hour for twenty minutes
		for (long t = 0; t <= 20 * MINUTE; t += 20_000L) {
			history.record(t, t / 3_600_000.0 * 1_000_000);
		}
		assertEquals(0.0, history.trend(20 * MINUTE, 20 / 60.0 * 1_000_000), 0.01, "a steady rate has no trend");
		double trend = history.trend(20 * MINUTE, 20 / 60.0 * 1_120_000);
		assertEquals(0.12, trend, 0.01);
		assertTrue(Double.isNaN(new ProfitHistory().trend(MINUTE, 100)), "no history, no trend");
		ProfitHistory young = new ProfitHistory();
		young.record(0, 0);
		young.record(MINUTE, 100);
		assertTrue(Double.isNaN(young.trend(2 * MINUTE, 200)), "less than ten minutes, no trend");
	}
}
