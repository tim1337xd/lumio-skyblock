package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The prices of the items the player looked at, kept over time for the line in the item window: the price sources only say what an item costs now and
 * its three-day average. A new item's line goes from that average to the price now, and fills up as prices come in. Bounded in items and in points, and
 * saved in the config folder from a background thread.
 */
public final class PriceHistory {
	private static final int MOST_ITEMS = 200;
	static final int MOST_POINTS = 48;
	/** Prices are kept this far apart at least. */
	static final long STEP_MILLIS = 30 * 60_000L;
	private static final String FILE = "price-history.json";
	/** Bounds a broken file: more than this is not read. */
	private static final long MOST_FILE_BYTES = 2_000_000L;

	private record Series(List<Double> points, long[] lastAt) {
	}

	/** In the order they were last looked at, so the one looked at longest ago goes first. */
	private static final Map<String, Series> ITEMS = new LinkedHashMap<>(MOST_ITEMS, 0.75f, true);
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio price history");
		thread.setDaemon(true);
		return thread;
	});
	/** Whether the file was read in, and whether its reading has begun (on the writer thread: up to two megabytes, too much for a frame). */
	private static boolean loaded;
	private static boolean loading;

	private PriceHistory() {
	}

	/**
	 * Notes the price of an item that is looked at now.
	 *
	 * @param average the three-day average, or NaN or 0 without one
	 * @param now     the price now, or 0 without one
	 * @return the points of its line, oldest first, the last the price now; empty without a price
	 */
	public static synchronized double[] line(String id, double average, double now) {
		if (!(now > 0)) {
			return new double[0];
		}
		if (!loaded) {
			load();
			return new double[0];
		}
		Series series = ITEMS.computeIfAbsent(id, key -> new Series(new ArrayList<>(), new long[1]));
		if (add(series.points(), series.lastAt(), now, System.currentTimeMillis())) {
			trim();
			save();
		}
		return pointsOf(series.points(), average, now);
	}

	/** @return whether the price was kept: the first one, or one far enough from the last */
	static boolean add(List<Double> points, long[] lastAt, double price, long nowMillis) {
		if (!points.isEmpty() && nowMillis - lastAt[0] < STEP_MILLIS) {
			return false;
		}
		points.add(price);
		lastAt[0] = nowMillis;
		while (points.size() > MOST_POINTS) {
			points.removeFirst();
		}
		return true;
	}

	/** @return the line of an item from what is kept: the average first while there is too little yet, and always the price now at the end */
	static double[] pointsOf(List<Double> kept, double average, double now) {
		boolean withAverage = kept.size() < 2 && average > 0;
		int size = kept.size() + (withAverage ? 1 : 0) + 1;
		double[] points = new double[size];
		int at = 0;
		if (withAverage) {
			points[at++] = average;
		}
		for (double point : kept) {
			points[at++] = point;
		}
		points[at] = now;
		return points;
	}

	private static void trim() {
		while (ITEMS.size() > MOST_ITEMS) {
			var oldest = ITEMS.keySet().iterator();
			oldest.next();
			oldest.remove();
		}
	}

	/** Starts reading the file on the writer thread, once; what it holds is taken in under the class's lock. */
	private static void load() {
		if (loading) {
			return;
		}
		loading = true;
		WRITER.execute(() -> {
			Map<String, Series> read = read();
			synchronized (PriceHistory.class) {
				read.forEach(ITEMS::putIfAbsent);
				trim();
				loaded = true;
			}
		});
	}

	/** @return the series the file holds, by item id; empty without a file or with a broken one */
	private static Map<String, Series> read() {
		Map<String, Series> read = new LinkedHashMap<>();
		try {
			Path file = ModFiles.file(FILE);
			if (!Files.isRegularFile(file) || Files.size(file) > MOST_FILE_BYTES) {
				return read;
			}
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
				JsonObject item = entry.getValue().getAsJsonObject();
				List<Double> points = new ArrayList<>();
				for (JsonElement point : item.getAsJsonArray("points")) {
					double value = point.getAsDouble();
					if (value > 0 && Double.isFinite(value) && points.size() < MOST_POINTS) {
						points.add(value);
					}
				}
				read.put(entry.getKey(), new Series(points, new long[]{item.get("at").getAsLong()}));
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the kept prices", e);
		}
		return read;
	}

	private static void save() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, Series> entry : ITEMS.entrySet()) {
			JsonObject item = new JsonObject();
			JsonArray points = new JsonArray();
			entry.getValue().points().forEach(points::add);
			item.add("points", points);
			item.addProperty("at", entry.getValue().lastAt()[0]);
			root.add(entry.getKey(), item);
		}
		WRITER.execute(() -> {
			try {
				ModFiles.writeAtomically(ModFiles.file(FILE), new Gson().toJson(root));
			} catch (IOException e) {
				Logs.warn("Could not keep the prices", e);
			}
		});
	}
}
