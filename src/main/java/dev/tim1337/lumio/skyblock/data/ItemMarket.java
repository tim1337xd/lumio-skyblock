package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Price history of the items someone looks at, from Coflnet: fetched in the background, kept per item for a few minutes, and never asked for while the mouse only passes
 * over an item. Reading it never blocks; what is not known yet is NaN.
 */
public final class ItemMarket {
	/** What is known of an item, and when it was fetched; 0 for never. */
	public record Snapshot(double medianDay, double medianWeek, double medianMonth, double soldPerDay, double trendPercent, long fetchedAt) {
		static final Snapshot EMPTY = new Snapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0L);

		/** @return the median over a span of days */
		public double median(int days) {
			return days <= 1 ? medianDay : days <= WEEK_DAYS ? medianWeek : medianMonth;
		}
	}

	private static final String PRICE_HISTORY = "https://sky.coflnet.com/api/item/price/";
	private static final String BAZAAR_HISTORY = "https://sky.coflnet.com/api/bazaar/";
	private static final int WEEK_DAYS = 7;
	private static final long KEEP_MILLIS = 5 * 60_000L;
	/** After a failed request it is tried again sooner than a good answer goes stale, but not at once. */
	private static final long RETRY_MILLIS = 60_000L;
	/** An item counts as looked at after the mouse stayed on it this long; passing over a chest full of items asks for nothing. */
	private static final long DWELL_MILLIS = 300L;
	private static final int MOST_ITEMS = 128;
	/** Longer answers than this are not read: a month has about as many stretches of time. */
	private static final int MOST_POINTS = 2_000;

	/** The one running in the game. */
	public static final ItemMarket INSTANCE = new ItemMarket(SkyBlockPrices::fetchCoflnet, Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio item market");
		thread.setDaemon(true);
		return thread;
	}), System::currentTimeMillis);

	private final Function<String, JsonElement> source;
	private final Executor background;
	private final LongSupplier clock;
	private final Map<String, Snapshot> known = new LinkedHashMap<>(MOST_ITEMS, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
			return size() > MOST_ITEMS;
		}
	};
	private final Map<String, Long> askedAt = new LinkedHashMap<>(MOST_ITEMS, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
			return size() > MOST_ITEMS;
		}
	};
	private String hovered;
	private long hoveredSince;

	/** @param source fetches a URL in the background; throws or returns null when it cannot */
	ItemMarket(Function<String, JsonElement> source, Executor background, LongSupplier clock) {
		this.source = source;
		this.background = background;
		this.clock = clock;
	}

	/**
	 * @param id     the item the mouse is on
	 * @param bazaar whether it is sold on the bazaar, which has a history of its own
	 * @return what is known of the item now; it is asked for in the background once the mouse has stayed on it, when it is not known or has gone stale
	 */
	public synchronized Snapshot look(String id, boolean bazaar) {
		long now = clock.getAsLong();
		if (!id.equals(hovered)) {
			hovered = id;
			hoveredSince = now;
		}
		Snapshot snapshot = known.get(id);
		boolean stale = snapshot == null || now - snapshot.fetchedAt() >= KEEP_MILLIS;
		Long asked = askedAt.get(id);
		boolean waiting = asked != null && now - asked < RETRY_MILLIS;
		if (stale && !waiting && now - hoveredSince >= DWELL_MILLIS) {
			askedAt.put(id, now);
			background.execute(() -> fetch(id, bazaar));
		}
		return snapshot == null ? Snapshot.EMPTY : snapshot;
	}

	private void fetch(String id, boolean bazaar) {
		try {
			Snapshot snapshot = bazaar ? fetchBazaar(id) : fetchAuctions(id);
			synchronized (this) {
				known.put(id, snapshot);
			}
		} catch (RuntimeException e) {
			Debug.log("Could not fetch the price history of {}: {}", id, e.toString());
		}
	}

	private Snapshot fetchAuctions(String id) {
		History week = auctionHistory(id, "week");
		History day = auctionHistory(id, "day");
		History month = auctionHistory(id, "month");
		return new Snapshot(MarketMath.weightedMedian(day.prices, day.sales), MarketMath.weightedMedian(week.prices, week.sales), MarketMath.weightedMedian(month.prices, month.sales),
			MarketMath.perDay(week.sales, WEEK_DAYS), MarketMath.trendPercent(week.prices, week.sales), clock.getAsLong());
	}

	private Snapshot fetchBazaar(String id) {
		History week = read(source.apply(BAZAAR_HISTORY + id + "/history/week"), "buy", null);
		return new Snapshot(Double.NaN, Double.NaN, Double.NaN, Double.NaN, MarketMath.trendPercent(week.prices, null), clock.getAsLong());
	}

	private History auctionHistory(String id, String span) {
		return read(source.apply(PRICE_HISTORY + id + "/history/" + span), "avg", "volume");
	}

	private record History(double[] prices, double[] sales) {
	}

	/** Reads a history as it comes: external data, so anything that is not a number counts as nothing, and it is bounded. */
	private static History read(JsonElement json, String priceKey, String salesKey) {
		if (json == null || !json.isJsonArray()) {
			throw new IllegalStateException("no history");
		}
		JsonArray array = json.getAsJsonArray();
		int count = Math.min(array.size(), MOST_POINTS);
		double[] prices = new double[count];
		double[] sales = new double[count];
		for (int i = 0; i < count; i++) {
			if (array.get(i).isJsonObject()) {
				JsonObject point = array.get(i).getAsJsonObject();
				prices[i] = number(point, priceKey);
				sales[i] = salesKey == null ? 1d : number(point, salesKey);
			}
		}
		return new History(prices, sales);
	}

	private static double number(JsonObject point, String key) {
		JsonElement value = point.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return 0d;
		}
		return value.getAsDouble();
	}
}
