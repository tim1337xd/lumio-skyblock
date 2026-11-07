package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.draw.Draw;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gains of one activity (mining, farming, slayer, ...): items that went into the inventory and sacks during the activity, coins gained along with
 * them, and the time spent. Kept in three buckets (all time, today, current session), so each one's profit and the profit per hour (profit over time
 * spent) can be shown.
 * <p>
 * Items are counted; their value is computed from current prices, as other tracker mods do, so a price change changes the profit. The last known price
 * of every item is stored with it, for when prices have not been fetched yet.
 */
public final class ProfitTracker {
	/** Below this much active time the per-hour rate is not shown, as it would swing wildly. */
	private static final long MIN_ACTIVE_MILLIS = 10_000L;
	/** The time range the profit is shown for. */
	public enum Range {
		SESSION("Session", "Since the game was started"),
		TODAY("Today", "Since midnight"),
		LIFETIME("All time", "Everything since the tracker was first on");

		private final String title;
		private final String hint;

		Range(String title, String hint) {
			this.title = title;
			this.hint = hint;
		}

		public Component displayName() {
			return Component.literal(title);
		}

		public String title() {
			return title;
		}

		public String hint() {
			return hint;
		}
	}

	/** A gained item: its amount, name, and the last known price of one. */
	public static final class Entry {
		public final String id;
		public String name;
		public long count;
		public double lastPrice;
		/** The id resolved from the name when the item's own id has no price, and when it was resolved. */
		private String nameId;
		private long nameCheckedAt;

		public Entry(String id, String name) {
			this.id = id;
			this.name = name;
		}

		/** @return the total value in coins at current prices, or at the last known price when there is none */
		public double value() {
			return value(SkyBlockPrices.Mode.BUY_ORDER);
		}

		/** @return the total value by the given price mode; the last known price is only used for buy order mode, which is the stored one */
		public double value(SkyBlockPrices.Mode mode) {
			double price = SkyBlockPrices.price(id, mode);
			if (price <= 0) {
				// a sack item whose id was unknown on arrival: resolve it by name now that names are known. The lookup is throttled rather than done every
				// call, since it cleans the name with regexes and the panels query every value every frame
				long now = System.currentTimeMillis();
				if (now - nameCheckedAt > 30_000L) {
					nameCheckedAt = now;
					nameId = SkyBlockPrices.idForName(name);
				}
				if (nameId != null) {
					price = SkyBlockPrices.price(nameId, mode);
				}
			}
			if (mode != SkyBlockPrices.Mode.BUY_ORDER) {
				return count * Math.max(0, price);
			}
			if (price > 0) {
				lastPrice = price;
			}
			return count * lastPrice;
		}
	}

	/** The gains of one time range. */
	public static final class Bucket {
		public final Map<String, Entry> items = new LinkedHashMap<>();
		/** Coins gained directly rather than as items: coins from killed mobs. */
		public double coins;
		/** Coins spent on the activity: slayer quest costs. They are subtracted from the profit. */
		public double spent;
		/** Active time in milliseconds: only time spent doing the activity counts, not time away. */
		public long activeMillis;
		/** Completed activity rounds: fishing catches, slayer bosses. */
		public long events;
		/** What an activity counts besides its rounds, by name: the Enchanting Exp, the ULTRA-RARE books, the best place. */
		public final Map<String, Long> counters = new LinkedHashMap<>();
		/** The day this bucket belongs to, for the today bucket. */
		public String day = "";

		public double total() {
			return total(SkyBlockPrices.Mode.BUY_ORDER);
		}

		public double total(SkyBlockPrices.Mode mode) {
			return gross(mode) - spent;
		}

		/** @return the gross gain before costs are subtracted */
		public double gross(SkyBlockPrices.Mode mode) {
			double sum = coins;
			for (Entry entry : items.values()) {
				sum += entry.value(mode);
			}
			return sum;
		}

		/** @return the profit extrapolated to an hour from the time spent so far, also before an hour has passed; 0 during the first seconds, which are too few to judge */
		public double perHour() {
			return perHour(SkyBlockPrices.Mode.BUY_ORDER);
		}

		public double perHour(SkyBlockPrices.Mode mode) {
			if (activeMillis < MIN_ACTIVE_MILLIS) {
				return 0;
			}
			return total(mode) / (activeMillis / 3_600_000.0);
		}

		public boolean isEmpty() {
			return items.isEmpty() && coins == 0 && spent == 0;
		}

		void clear() {
			items.clear();
			coins = 0;
			spent = 0;
			activeMillis = 0;
			events = 0;
			counters.clear();
		}

		/** @return activity rounds per hour from the time spent so far, or 0 if too little time has passed */
		public double eventsPerHour() {
			return activeMillis < MIN_ACTIVE_MILLIS ? 0 : events / (activeMillis / 3_600_000.0);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("coins", coins);
			json.addProperty("spent", spent);
			json.addProperty("active_ms", activeMillis);
			json.addProperty("events", events);
			if (!day.isEmpty()) {
				json.addProperty("day", day);
			}
			JsonObject list = new JsonObject();
			for (Entry entry : items.values()) {
				JsonObject item = new JsonObject();
				item.addProperty("name", entry.name);
				item.addProperty("count", entry.count);
				item.addProperty("price", entry.lastPrice);
				list.add(entry.id, item);
			}
			json.add("items", list);
			if (!counters.isEmpty()) {
				JsonObject counted = new JsonObject();
				counters.forEach(counted::addProperty);
				json.add("counters", counted);
			}
			return json;
		}

		void fromJson(JsonObject json) {
			clear();
			try {
				coins = json.has("coins") ? json.get("coins").getAsDouble() : 0;
				spent = json.has("spent") ? json.get("spent").getAsDouble() : 0;
				activeMillis = json.has("active_ms") ? json.get("active_ms").getAsLong() : 0;
				events = json.has("events") ? json.get("events").getAsLong() : 0;
				day = json.has("day") ? json.get("day").getAsString() : "";
				if (json.has("counters") && json.get("counters").isJsonObject()) {
					for (Map.Entry<String, JsonElement> element : json.getAsJsonObject("counters").entrySet()) {
						counters.put(element.getKey(), element.getValue().getAsLong());
					}
				}
				if (json.has("items") && json.get("items").isJsonObject()) {
					for (Map.Entry<String, JsonElement> element : json.getAsJsonObject("items").entrySet()) {
						JsonObject item = element.getValue().getAsJsonObject();
						Entry entry = new Entry(element.getKey(), SkyBlockPrices.clean(item.has("name") ? item.get("name").getAsString() : SkyBlockPrices.nameOf(element.getKey())));
						entry.count = item.has("count") ? item.get("count").getAsLong() : 0;
						entry.lastPrice = item.has("price") ? item.get("price").getAsDouble() : 0;
						if (entry.count > 0) {
							items.put(entry.id, entry);
						}
					}
				}
			} catch (RuntimeException ignored) {
				clear();
			}
		}
	}

	private final Bucket session = new Bucket();
	private final Bucket today = new Bucket();
	private final Bucket lifetime = new Bucket();
	/** Incremented on every gain, for redrawing and saving. */
	private int changes;
	/** The session's profit over time, recorded here whatever the panel shows; and the gains it last saw. */
	private final ProfitHistory history = new ProfitHistory();
	private int historyChanges = -1;

	public Bucket bucket(Range range) {
		return switch (range) {
			case SESSION -> session;
			case TODAY -> today;
			case LIFETIME -> lifetime;
		};
	}

	public int changes() {
		return changes;
	}

	private static String todayKey() {
		return LocalDate.now().toString();
	}

	/** Resets the today bucket when the day changes. */
	private void rollDay() {
		String key = todayKey();
		if (!today.day.equals(key)) {
			today.clear();
			today.day = key;
			changes++;
		}
	}

	/** Records a gained item. */
	public void add(String id, String name, long count) {
		if (count <= 0 || id == null || id.isEmpty()) {
			return;
		}
		rollDay();
		for (Bucket bucket : new Bucket[]{session, today, lifetime}) {
			Entry entry = bucket.items.computeIfAbsent(id, key -> new Entry(key, name));
			if (name != null && !name.isBlank()) {
				entry.name = name;
			}
			entry.count += count;
			double price = SkyBlockPrices.price(id);
			if (price > 0) {
				entry.lastPrice = price;
			}
		}
		changes++;
	}

	/** Records directly gained coins. */
	public void addCoins(double coins) {
		if (coins <= 0) {
			return;
		}
		rollDay();
		session.coins += coins;
		today.coins += coins;
		lifetime.coins += coins;
		changes++;
	}

	/** Records coins spent on the activity: a slayer quest cost. */
	public void addCost(double coins) {
		if (coins <= 0) {
			return;
		}
		rollDay();
		session.spent += coins;
		today.spent += coins;
		lifetime.spent += coins;
		changes++;
	}

	/** Records one completed round: a catch, a boss. */
	public void addEvent() {
		rollDay();
		session.events++;
		today.events++;
		lifetime.events++;
		changes++;
	}

	/** Adds to one of what an activity counts, in every time range. */
	public void count(String key, long amount) {
		rollDay();
		for (Bucket bucket : new Bucket[]{session, today, lifetime}) {
			bucket.counters.merge(key, amount, Long::sum);
		}
		changes++;
	}

	/** Keeps the lowest value of one of what an activity counts, in every time range: a best place. */
	public void least(String key, long value) {
		rollDay();
		for (Bucket bucket : new Bucket[]{session, today, lifetime}) {
			bucket.counters.merge(key, value, Math::min);
		}
		changes++;
	}

	/** Sets one of what an activity counts in every time range, as a streak that ends. */
	public void setLifetimeCount(String key, long value) {
		rollDay();
		for (Bucket bucket : new Bucket[]{session, today, lifetime}) {
			bucket.counters.put(key, value);
		}
		changes++;
	}

	/** Records time spent on the activity. */
	public void addTime(long millis) {
		if (millis <= 0) {
			return;
		}
		rollDay();
		session.activeMillis += millis;
		today.activeMillis += millis;
		lifetime.activeMillis += millis;
	}

	public void reset(Range range) {
		bucket(range).clear();
		if (range == Range.SESSION) {
			history.clear();
		}
		if (range == Range.TODAY) {
			today.day = todayKey();
		}
		changes++;
	}

	/**
	 * Records the session's total for the chart when a sample is due: every few seconds of activity, and right after a gain. Called every tick by
	 * the tracker's feature, whatever its panel shows; the total is only worked out when a sample is taken.
	 */
	public void recordHistory(SkyBlockPrices.Mode mode) {
		boolean gained = changes != historyChanges;
		if ((session.activeMillis > 0 || gained) && history.due(session.activeMillis, gained)) {
			historyChanges = changes;
			history.record(session.activeMillis, session.total(mode));
		}
	}

	/** @return the session's profit over time */
	public ProfitHistory history() {
		return history;
	}

	/** @return the most valuable items, most valuable first */
	public List<Entry> top(Range range, int most) {
		return top(range, most, SkyBlockPrices.Mode.BUY_ORDER);
	}

	public List<Entry> top(Range range, int most, SkyBlockPrices.Mode mode) {
		List<Entry> list = new ArrayList<>(bucket(range).items.values());
		list.sort(Comparator.comparingDouble((Entry entry) -> entry.value(mode)).reversed().thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
		return list.size() > most ? new ArrayList<>(list.subList(0, most)) : list;
	}

	public JsonObject toJson() {
		rollDay();
		JsonObject json = new JsonObject();
		json.add("today", today.toJson());
		json.add("lifetime", lifetime.toJson());
		return json;
	}

	public void fromJson(JsonObject json) {
		if (json == null) {
			return;
		}
		if (json.has("today") && json.get("today").isJsonObject()) {
			today.fromJson(json.getAsJsonObject("today"));
		}
		if (json.has("lifetime") && json.get("lifetime").isJsonObject()) {
			lifetime.fromJson(json.getAsJsonObject("lifetime"));
		}
		rollDay();
		changes++;
	}

	// ------------------------------------------------------------------------------------------------ formatting

	/** @return coins as short text: 1,234 below ten thousand, then 12.3k, 1.24M, 2.1B */
	public static String coins(double value) {
		double abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000) {
			return sign + trim(abs / 1_000_000_000, 2) + "B";
		}
		if (abs >= 1_000_000) {
			return sign + trim(abs / 1_000_000, 2) + "M";
		}
		if (abs >= 10_000) {
			return sign + trim(abs / 1_000, 1) + "k";
		}
		return sign + String.format(Locale.ROOT, "%,d", Math.round(abs));
	}

	private static String trim(double value, int decimals) {
		return Draw.trimZeros(String.format(Locale.ROOT, decimals == 1 ? "%.1f" : "%.2f", value));
	}

	/** @return coins per hour as short text: 840/h, 84.5k/h, 1.12m/h, 2.1b/h */
	public static String rate(double value) {
		double abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000) {
			return sign + trim(abs / 1_000_000_000, 2) + "b/h";
		}
		if (abs >= 1_000_000) {
			return sign + trim(abs / 1_000_000, 2) + "m/h";
		}
		if (abs >= 1_000) {
			return sign + trim(abs / 1_000, 1) + "k/h";
		}
		return sign + Math.round(abs) + "/h";
	}

	/** @return a count as short text: 1,240 or 12.5k */
	public static String count(long value) {
		if (value >= 100_000) {
			return trim(value / 1_000.0, 1) + "k";
		}
		return String.format(Locale.ROOT, "%,d", value);
	}

	/** @return a duration as short text including seconds: 42s, 12m 05s, 1h 05m 09s, 3d 2h */
	public static String time(long millis) {
		long seconds = millis / 1000L;
		long minutes = seconds / 60;
		if (minutes < 1) {
			return seconds + "s";
		}
		if (minutes < 60) {
			return minutes + "m " + String.format(Locale.ROOT, "%02ds", seconds % 60);
		}
		long hours = minutes / 60;
		if (hours < 48) {
			return hours + "h " + String.format(Locale.ROOT, "%02dm %02ds", minutes % 60, seconds % 60);
		}
		return (hours / 24) + "d " + (hours % 24) + "h";
	}
}
