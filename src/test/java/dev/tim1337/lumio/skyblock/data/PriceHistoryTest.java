package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The prices kept for the line in the item window. */
class PriceHistoryTest {
	@Test
	void keepsPricesApartAndBounded() {
		List<Double> points = new ArrayList<>();
		long[] at = new long[1];
		assertTrue(PriceHistory.add(points, at, 10.0, 1000L));
		assertFalse(PriceHistory.add(points, at, 11.0, 1000L + PriceHistory.STEP_MILLIS - 1));
		assertTrue(PriceHistory.add(points, at, 12.0, 1000L + PriceHistory.STEP_MILLIS));
		assertEquals(List.of(10.0, 12.0), points);
		for (int i = 0; i < 2 * PriceHistory.MOST_POINTS; i++) {
			PriceHistory.add(points, at, i, at[0] + PriceHistory.STEP_MILLIS);
		}
		assertEquals(PriceHistory.MOST_POINTS, points.size());
		assertEquals(2.0 * PriceHistory.MOST_POINTS - 1, points.getLast());
	}

	@Test
	void aNewItemsLineStartsAtTheAverage() {
		assertArrayEquals(new double[]{8.0, 10.0, 11.0}, PriceHistory.pointsOf(List.of(10.0), 8.0, 11.0));
		assertArrayEquals(new double[]{10.0, 11.0}, PriceHistory.pointsOf(List.of(10.0), Double.NaN, 11.0));
		assertArrayEquals(new double[]{9.0, 10.0, 11.0}, PriceHistory.pointsOf(List.of(9.0, 10.0), 8.0, 11.0));
	}

	@Test
	void theTrendOfTheLastDayNeedsAPriceFromAboutADayAgo() {
		long hour = 3_600_000L;
		long now = 100 * hour;
		// a price 24 hours ago: the change from it
		assertEquals(10.0, PriceHistory.dayChange(List.of(100.0, 105.0), List.of(now - 24 * hour, now - hour), 110.0, now), 1e-9);
		// only prices from the last hours: no trend
		assertTrue(Double.isNaN(PriceHistory.dayChange(List.of(100.0), List.of(now - 2 * hour), 110.0, now)));
		// from an older file, without times: no trend
		assertTrue(Double.isNaN(PriceHistory.dayChange(List.of(100.0), List.of(0L), 110.0, now)));
		// the one nearest to a day ago counts
		assertEquals(-50.0, PriceHistory.dayChange(List.of(300.0, 200.0), List.of(now - 29 * hour, now - 23 * hour), 100.0, now), 1e-9);
	}
}
