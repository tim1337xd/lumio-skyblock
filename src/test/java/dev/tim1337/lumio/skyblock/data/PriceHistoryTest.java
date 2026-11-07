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
}
