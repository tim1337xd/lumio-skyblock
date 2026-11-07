package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * What Coflnet says this exact item is worth, with its enchants, stars, recombobulator, gems and so on: asked for once the mouse has stayed on it a moment, kept per item and its properties for a few minutes, one item at a time, and with a pause after a
 * failure. Only the item's own data is sent.
 */
public final class ExactPrices {
	/** What is known of an item; a number is NaN when it is not known. */
	public record Prices(double lowest, double median, double craft, long fetchedAt) {
		static final Prices NONE = new Prices(Double.NaN, Double.NaN, Double.NaN, 0L);

		/** @return whether an answer has come */
		public boolean answered() {
			return fetchedAt > 0L;
		}
	}

	private static final String BASE = "https://sky.coflnet.com/api/";
	private static final String FILTERS = BASE + "item/filters";
	private static final String PRICE = BASE + "item/price/";
	private static final String BREAKDOWN = BASE + "mod/pricing/breakdown";
	private static final String LOWEST_KEY = "lowest";
	private static final String MEDIAN_KEY = "median";
	private static final long DWELL_MILLIS = 150L;
	private static final long KEEP_MILLIS = 30 * 60_000L;
	/** Between two items asked for ahead of the mouse: the hovered item never waits for this. */
	private static final long PREFETCH_GAP_MILLIS = 1500L;
	private static final int MOST_WAITING = 200;
	/** The kept prices are written at most this often, and only when they changed. */
	private static final long SAVE_EVERY_MILLIS = 60_000L;
	private static final String FILE = "exact-prices.json";
	/** After a failure, or when Coflnet says too many, nothing is asked for this long. */
	private static final long PAUSE_MILLIS = 60_000L;
	private static final int MOST_ITEMS = 500;

	/** The one running in the game. */
	public static final ExactPrices INSTANCE = new ExactPrices(SkyBlockPrices::askCoflnet, SkyBlockPrices::postCoflnet, Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio item prices");
		thread.setDaemon(true);
		return thread;
	}), System::currentTimeMillis).keptInFile();

	private final Function<String, JsonElement> get;
	private final BiFunction<String, String, JsonElement> post;
	private final Executor background;
	private final LongSupplier clock;
	private final Map<String, Prices> known = new LinkedHashMap<>(MOST_ITEMS, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Prices> eldest) {
			return size() > MOST_ITEMS;
		}
	};
	private String hovered;
	private long hoveredSince;
	private boolean running;
	private long pausedUntil;
	/** Items to ask for ahead of the mouse, each once. */
	private final Map<String, Runnable> waiting = new LinkedHashMap<>();
	private long nextPrefetchAt;
	private boolean changed;
	private long savedAt;
	private boolean fileRead = true;
	private boolean inFile;

	ExactPrices(Function<String, JsonElement> get, BiFunction<String, String, JsonElement> post, Executor background, LongSupplier clock) {
		this.get = get;
		this.post = post;
		this.background = background;
		this.clock = clock;
	}

	private ExactPrices keptInFile() {
		inFile = true;
		fileRead = false;
		return this;
	}

	/**
	 * @param tag       the id it is priced by
	 * @param name      its name without colors
	 * @param data      its own data
	 * @param key       what tells it from other items: its id and its properties
	 * @param wantCraft whether its craft cost is shown at all
	 * @return what is known of the item now; it is asked for in the background once the mouse has stayed on it, when it is not known or has gone stale
	 */
	public synchronized Prices look(String tag, String name, JsonObject data, String key, boolean wantCraft) {
		long now = clock.getAsLong();
		if (!key.equals(hovered)) {
			hovered = key;
			hoveredSince = now;
		}
		Prices prices = known.get(key);
		boolean stale = prices == null || now - prices.fetchedAt() >= KEEP_MILLIS;
		if (stale && !running && now >= pausedUntil && now - hoveredSince >= DWELL_MILLIS) {
			running = true;
			waiting.remove(key);
			background.execute(() -> fetch(tag, name, data, key, wantCraft));
		}
		return prices == null ? Prices.NONE : prices;
	}

	/** Runs something on the thread of the prices. */
	public void inBackground(Runnable work) {
		background.execute(work);
	}

	/** Puts an item in line to be asked for: unless it is known already or in line, and never many. */
	public synchronized void prefetch(String tag, String name, JsonObject data, String key, boolean wantCraft) {
		Prices prices = known.get(key);
		if (prices != null && clock.getAsLong() - prices.fetchedAt() < KEEP_MILLIS || waiting.containsKey(key) || waiting.size() >= MOST_WAITING) {
			return;
		}
		waiting.put(key, () -> fetch(tag, name, data, key, wantCraft));
	}

	/** Asks for the next one in line, when the way is free: slowly, and the hovered item goes first, as it starts as soon as nothing runs. */
	public synchronized void tick() {
		long now = clock.getAsLong();
		if (!fileRead) {
			fileRead = true;
			readFile();
		}
		if (inFile && changed && now - savedAt >= SAVE_EVERY_MILLIS) {
			changed = false;
			savedAt = now;
			writeFile(ExactPricesFile.toJson(known));
		}
		if (running || waiting.isEmpty() || now < pausedUntil || now < nextPrefetchAt) {
			return;
		}
		var next = waiting.entrySet().iterator().next();
		waiting.remove(next.getKey());
		Prices prices = known.get(next.getKey());
		if (prices != null && now - prices.fetchedAt() < KEEP_MILLIS) {
			return;
		}
		running = true;
		nextPrefetchAt = now + PREFETCH_GAP_MILLIS;
		background.execute(next.getValue());
	}

	/** Takes what a file kept, as long as it is not stale and nothing newer is known. */
	synchronized void adopt(Map<String, Prices> kept) {
		long now = clock.getAsLong();
		kept.forEach((key, prices) -> {
			if (now - prices.fetchedAt() < KEEP_MILLIS) {
				known.putIfAbsent(key, prices);
			}
		});
	}

	private void readFile() {
		background.execute(() -> {
			try {
				Path file = ModFiles.file(FILE);
				if (Files.isRegularFile(file)) {
					adopt(ExactPricesFile.fromJson(JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
				}
			} catch (IOException | RuntimeException e) {
				Debug.log("Could not read the kept prices: {}", e.toString());
			}
		});
	}

	private void writeFile(JsonObject root) {
		background.execute(() -> {
			try {
				ModFiles.writeAtomically(ModFiles.file(FILE), root.toString());
			} catch (IOException | RuntimeException e) {
				Debug.log("Could not keep the prices: {}", e.toString());
			}
		});
	}

	private void fetch(String tag, String name, JsonObject data, String key, boolean wantCraft) {
		try {
			Prices prices = ask(tag, name, data, wantCraft);
			synchronized (this) {
				known.put(key, prices);
				changed = true;
			}
		} catch (RuntimeException e) {
			Debug.log("Could not fetch the prices of {}: {}", tag, e.toString());
			synchronized (this) {
				pausedUntil = clock.getAsLong() + PAUSE_MILLIS;
			}
		} finally {
			synchronized (this) {
				running = false;
			}
		}
	}

	private Prices ask(String tag, String name, JsonObject data, boolean wantCraft) {
		JsonObject item = ExactItem.request(tag, name, data);
		String query = ExactItem.query(post.apply(FILTERS, item.toString()), name, data);
		double lowest = ExactItem.coins(get.apply(PRICE + tag + "/bin" + query), LOWEST_KEY);
		double median = ExactItem.coins(get.apply(PRICE + tag + query), MEDIAN_KEY);
		double craft = Double.NaN;
		if (wantCraft) {
			JsonArray one = new JsonArray();
			one.add(item);
			craft = ExactItem.craftCost(post.apply(BREAKDOWN, one.toString()));
		}
		return new Prices(lowest, median, craft, clock.getAsLong());
	}
}
