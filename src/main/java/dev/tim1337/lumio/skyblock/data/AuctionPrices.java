package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The auction house numbers of an item from SkyCofl, the one place every view takes them from (the tooltip, the item window, the item search,
 * the trackers): its lowest BIN, its median over seven days and how many sold in seven days, all of one moment. Asked for only while an item is
 * shown, one item at a time through SkyCofl's paced requests ({@link SkyBlockPrices#fetchCoflnet}: at most one a second, none during a pause it
 * asked for), and kept for {@link #FRESH_MILLIS}. When SkyCofl does not answer, the last numbers stay with their age; nothing is made up.
 * Nothing about the player is sent: only the item's id.
 */
public final class AuctionPrices {
	/** What is known of an item: NaN for a number not known; when it was fetched and last tried (milliseconds, 0 for never). */
	public record Entry(double lowest, double median, double volume, long fetchedAt, long triedAt) {
		public static final Entry NONE = new Entry(Double.NaN, Double.NaN, Double.NaN, 0L, 0L);

		/** @return whether numbers have come at all */
		public boolean known() {
			return fetchedAt > 0L;
		}

		/** @return how old the numbers are, in milliseconds, or -1 when there are none */
		public long age(long now) {
			return fetchedAt > 0L ? Math.max(0L, now - fetchedAt) : -1L;
		}
	}

	/** The span of the median and the volume, in days: SkyCofl's free span. */
	public static final int DAYS = 7;
	/** How long numbers stay fresh, and how long after a failed try an item is asked for again. */
	static final long FRESH_MILLIS = 7 * 60_000L;
	private static final long RETRY_MILLIS = 60_000L;
	private static final int MOST_KNOWN = 600;
	private static final int MOST_WAITING = 120;
	private static final String PRICE = "https://sky.coflnet.com/api/item/price/";

	private static final Map<String, AuctionPrices.Entry> KNOWN = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, AuctionPrices.Entry> eldest) {
			return size() > MOST_KNOWN;
		}
	};
	private static final Set<String> WAITING = ConcurrentHashMap.newKeySet();
	private static final ExecutorService THREAD = Threads.daemonExecutor("Lumio auction prices");

	private AuctionPrices() {
	}

	/**
	 * @return what is known of an item's auction numbers now; asks SkyCofl for them, in turn, when they are not known or no longer fresh. Bazaar
	 *         items and items with a variant in their id are not auction items: nothing is asked for them
	 */
	public static Entry look(String id) {
		if (id == null || id.isEmpty() || id.indexOf(';') >= 0 || SkyBlockPrices.bazaarSell(id) > 0d || SkyBlockPrices.bazaarBuy(id) > 0d) {
			return Entry.NONE;
		}
		long now = System.currentTimeMillis();
		Entry known;
		synchronized (KNOWN) {
			known = KNOWN.get(id);
		}
		boolean stale = known == null || now - known.fetchedAt() > FRESH_MILLIS;
		boolean mayTry = known == null || now - known.triedAt() > RETRY_MILLIS;
		if (stale && mayTry && WAITING.size() < MOST_WAITING && WAITING.add(id)) {
			THREAD.execute(() -> fetch(id));
		}
		return known == null ? Entry.NONE : known;
	}

	private static void fetch(String id) {
		long now = System.currentTimeMillis();
		Entry before;
		synchronized (KNOWN) {
			before = KNOWN.getOrDefault(id, Entry.NONE);
		}
		try {
			String item = PRICE + URLEncoder.encode(id, StandardCharsets.UTF_8);
			JsonObject bin = SkyBlockPrices.fetchCoflnet(item + "/bin").getAsJsonObject();
			JsonObject analysis = SkyBlockPrices.fetchCoflnet(item + "/analysis?days=" + DAYS).getAsJsonObject();
			Entry fresh = new Entry(positive(bin, "lowest"), positive(analysis, "medianPrice"), atLeastZero(analysis, "totalSales"), System.currentTimeMillis(),
				now);
			synchronized (KNOWN) {
				KNOWN.put(id, fresh);
			}
		} catch (RuntimeException e) {
			// the last numbers stay, with their age; tried again after a while
			synchronized (KNOWN) {
				KNOWN.put(id, new Entry(before.lowest(), before.median(), before.volume(), before.fetchedAt(), now));
			}
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.PRICES, "SkyCofl gave no auction numbers for {}: {}", id, e.toString());
			}
		} finally {
			WAITING.remove(id);
		}
	}

	/** @return a field that must be a positive number, else NaN */
	static double positive(JsonObject root, String key) {
		JsonElement value = root.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return Double.NaN;
		}
		try {
			double number = value.getAsDouble();
			return number > 0d && Double.isFinite(number) ? number : Double.NaN;
		} catch (RuntimeException e) {
			return Double.NaN;
		}
	}

	/** @return a count that must be zero or more, else NaN */
	static double atLeastZero(JsonObject root, String key) {
		JsonElement value = root.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return Double.NaN;
		}
		try {
			double number = value.getAsDouble();
			return number >= 0d && Double.isFinite(number) ? number : Double.NaN;
		} catch (RuntimeException e) {
			return Double.NaN;
		}
	}

	/** @return the address of an item's page on SkyCofl, for the attribution SkyCofl asks for wherever its numbers are shown */
	public static String pageOf(String id) {
		return "https://sky.coflnet.com/item/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
	}
}
