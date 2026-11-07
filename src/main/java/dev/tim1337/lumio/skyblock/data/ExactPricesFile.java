package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** How the exact prices are kept in a file between starts. What a file says is not trusted: all that is not as it should be is left out. Plain data, so it is unit tested. */
final class ExactPricesFile {
	private static final int MOST_ITEMS = 500;
	private static final int KEY_MOST_CHARS = 32_768;

	private ExactPricesFile() {
	}

	static JsonObject toJson(Map<String, ExactPrices.Prices> known) {
		JsonObject root = new JsonObject();
		known.forEach((key, prices) -> {
			JsonArray values = new JsonArray();
			values.add(Double.isNaN(prices.lowest()) ? -1d : prices.lowest());
			values.add(Double.isNaN(prices.median()) ? -1d : prices.median());
			values.add(Double.isNaN(prices.craft()) ? -1d : prices.craft());
			values.add(prices.fetchedAt());
			root.add(key, values);
		});
		return root;
	}

	static Map<String, ExactPrices.Prices> fromJson(JsonObject root) {
		Map<String, ExactPrices.Prices> kept = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (kept.size() >= MOST_ITEMS) {
				break;
			}
			ExactPrices.Prices prices = entry.getKey().length() > KEY_MOST_CHARS ? null : pricesOf(entry.getValue());
			if (prices != null) {
				kept.put(entry.getKey(), prices);
			}
		}
		return kept;
	}

	private static ExactPrices.Prices pricesOf(JsonElement element) {
		if (!element.isJsonArray() || element.getAsJsonArray().size() != 4) {
			return null;
		}
		JsonArray values = element.getAsJsonArray();
		try {
			long fetchedAt = values.get(3).getAsLong();
			return fetchedAt <= 0L ? null : new ExactPrices.Prices(coins(values.get(0)), coins(values.get(1)), coins(values.get(2)), fetchedAt);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static double coins(JsonElement value) {
		double coins = value.getAsDouble();
		return coins > 0d && !Double.isInfinite(coins) ? coins : Double.NaN;
	}
}
