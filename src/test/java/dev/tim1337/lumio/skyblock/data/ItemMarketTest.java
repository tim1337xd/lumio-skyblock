package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cache of the price history, and the numbers made from it. */
class ItemMarketTest {
	private static final double CLOSE = 0.001d;
	private final List<String> asked = new ArrayList<>();
	private long now = 1_000_000L;

	private static JsonArray history(double... avgAndVolume) {
		JsonArray array = new JsonArray();
		for (int i = 0; i + 1 < avgAndVolume.length; i += 2) {
			JsonObject point = new JsonObject();
			point.addProperty("avg", avgAndVolume[i]);
			point.addProperty("volume", avgAndVolume[i + 1]);
			array.add(point);
		}
		return array;
	}

	private ItemMarket market(JsonElement answer) {
		return new ItemMarket(url -> {
			asked.add(url);
			return answer;
		}, Runnable::run, () -> now);
	}

	@Test
	void anItemTheMouseOnlyPassesIsNotAskedFor() {
		ItemMarket market = market(history(100d, 1d));
		market.look("A", false);
		now += 50L;
		market.look("B", false);
		now += 50L;
		market.look("C", false);
		assertTrue(asked.isEmpty());
	}

	@Test
	void anItemTheMouseStaysOnIsAskedForOnce() {
		ItemMarket market = market(history(100d, 1d));
		market.look("A", false);
		now += 400L;
		market.look("A", false);
		now += 400L;
		market.look("A", false);
		assertEquals(3, asked.size());
		assertTrue(asked.getFirst().startsWith("https://sky.coflnet.com/api/item/price/A/history/"));
	}

	@Test
	void whatIsKnownIsKeptForFiveMinutes() {
		ItemMarket market = market(history(100d, 1d));
		market.look("A", false);
		now += 400L;
		market.look("A", false);
		int first = asked.size();
		now += 4 * 60_000L;
		assertTrue(market.look("A", false).fetchedAt() > 0L);
		assertEquals(first, asked.size());
		now += 2 * 60_000L;
		market.look("A", false);
		assertEquals(2 * first, asked.size());
	}

	@Test
	void aFailedRequestIsNotRepeatedAtOnceAndNothingIsMadeUp() {
		ItemMarket market = new ItemMarket(url -> {
			asked.add(url);
			throw new IllegalStateException("offline");
		}, Runnable::run, () -> now);
		market.look("A", false);
		now += 400L;
		ItemMarket.Snapshot snapshot = market.look("A", false);
		assertEquals(1, asked.size());
		assertTrue(Double.isNaN(snapshot.median(7)));
		now += 1_000L;
		market.look("A", false);
		assertEquals(1, asked.size());
		now += 60_000L;
		market.look("A", false);
		assertEquals(2, asked.size());
	}

	@Test
	void aBazaarItemAsksForTheBazaarsHistory() {
		ItemMarket market = market(new JsonArray());
		market.look("B", true);
		now += 400L;
		market.look("B", true);
		assertEquals(List.of("https://sky.coflnet.com/api/bazaar/B/history/week"), asked);
	}

	@Test
	void theCacheHasALimit() {
		ItemMarket market = market(history(100d, 1d));
		for (int i = 0; i < 300; i++) {
			market.look("I" + i, false);
			now += 400L;
			market.look("I" + i, false);
		}
		int all = asked.size();
		market.look("I0", false);
		now += 400L;
		market.look("I0", false);
		assertTrue(asked.size() > all);
	}

	@Test
	void theMedianIsWeightedBySales() {
		assertEquals(10d, MarketMath.weightedMedian(new double[]{10d, 100d, 1000d}, new double[]{100d, 1d, 1d}), CLOSE);
		assertEquals(1000d, MarketMath.weightedMedian(new double[]{10d, 100d, 1000d}, new double[]{1d, 1d, 100d}), CLOSE);
		assertTrue(Double.isNaN(MarketMath.weightedMedian(new double[]{10d}, new double[]{0d})));
		assertTrue(Double.isNaN(MarketMath.weightedMedian(new double[0], new double[0])));
	}

	@Test
	void soldPerDayIsSpreadOverTheDays() {
		assertEquals(3d, MarketMath.perDay(new double[]{10d, 11d}, 7), CLOSE);
		assertTrue(Double.isNaN(MarketMath.perDay(new double[0], 7)));
	}

	@Test
	void theTrendComparesTheStartWithTheEnd() {
		double[] prices = {100d, 100d, 100d, 100d, 100d, 100d, 110d};
		assertEquals(10d, MarketMath.trendPercent(prices, null), CLOSE);
		assertTrue(Double.isNaN(MarketMath.trendPercent(new double[]{100d}, null)));
		assertFalse(Double.isNaN(MarketMath.trendPercent(prices, new double[]{1d, 1d, 1d, 1d, 1d, 1d, 1d})));
	}
}
