package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.HttpLimits;
import dev.tim1337.lumio.api.experimental.util.TextColors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Hypixel SkyBlock item values in coins: bazaar prices from the public Hypixel API (no key needed), and auction house lowest BIN prices as listed by
 * NotEnoughUpdates. They are refreshed periodically on a background thread, and profit is computed from them. An item found in neither is valued
 * at zero but still counted.
 * <p>
 * Item names are derived from ids ({@code ENCHANTED_CARROT} is "Enchanted Carrot"), which is how a sack message, containing only the name, is
 * mapped back to an item.
 */
public final class SkyBlockPrices {
	private static final String BAZAAR = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final String LOWEST_BIN = "https://moulberry.codes/lowestbin.json";
	/** The same list from Coflnet, used when the NotEnoughUpdates one does not respond. */
	private static final String LOWEST_BIN_COFLNET = "https://sky.coflnet.com/api/prices/neu";
	/** Recent auction house sale prices of a single item with their median, from Coflnet. */
	private static final String ITEM_PRICE = "https://sky.coflnet.com/api/item/price/";
	/** Every SkyBlock item with its name and NPC sell price: the price of items neither on the bazaar nor the auction house. */
	private static final String ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final Map<String, Double> NPC_SELL = new ConcurrentHashMap<>();
	private static volatile boolean itemsFetched;
	private static final long REFRESH_MS = 5 * 60_000L;

	/** Which price to value items at: instant sell into bazaar buy orders, a sell offer, or the NPC price. */
	public enum Mode {
		BUY_ORDER("Buy order"),
		SELL_OFFER("Sell offer"),
		NPC("NPC");

		private final String title;

		Mode(String title) {
			this.title = title;
		}

		public String title() {
			return title;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	/** Instant sell price on the bazaar, by item id. */
	private static final Map<String, Double> BAZAAR_SELL = new ConcurrentHashMap<>();
	/** Lowest bazaar sell offer, by item id: what a sell offer that is filled first gets. */
	private static final Map<String, Double> BAZAAR_OFFER = new ConcurrentHashMap<>();
	/** Lowest auction house BIN price, by item id. */
	private static final Map<String, Double> LOWEST = new ConcurrentHashMap<>();
	/** Item id by lowercase name: from the bazaar ids and from items seen in the inventory. */
	private static final Map<String, String> ID_BY_NAME = new ConcurrentHashMap<>();
	/** Item stars and the server's private-use font glyphs, which are not part of a name. Compiled once: names are cleaned every frame. */
	private static final Pattern NOT_NAME = Pattern.compile("[✪➊➋➌➍➎✦⚚]|[\\uE000-\\uF8FF]");
	private static final Pattern SIGN_IN_FRONT = Pattern.compile("^[^A-Za-z0-9\\[(]+\\s+");
	/** Ids whose names are not simply their words: legacy farming and mining items. */
	private static final Map<String, String> SPECIAL_NAMES = new HashMap<>();

	static {
		String[][] names = {
			{"INK_SACK:3", "Cocoa Beans"}, {"INK_SACK", "Ink Sack"}, {"LOG", "Oak Log"}, {"LOG:1", "Spruce Log"}, {"LOG:2", "Birch Log"}, {"LOG:3", "Jungle Log"},
			{"LOG_2", "Acacia Log"}, {"LOG_2:1", "Dark Oak Log"}, {"CARROT_ITEM", "Carrot"}, {"POTATO_ITEM", "Potato"}, {"NETHER_STALK", "Nether Wart"},
			{"HUGE_MUSHROOM_1", "Brown Mushroom Block"}, {"HUGE_MUSHROOM_2", "Red Mushroom Block"}, {"RAW_FISH", "Raw Fish"}, {"RAW_FISH:1", "Raw Salmon"},
			{"RAW_FISH:2", "Clownfish"}, {"RAW_FISH:3", "Pufferfish"}, {"MITHRIL_ORE", "Mithril"}, {"TITANIUM_ORE", "Titanium"}, {"ENDER_STONE", "End Stone"},
			{"MYCEL", "Mycelium"}, {"SULPHUR_ORE", "Sulphur"}, {"HAY_BLOCK", "Hay Bale"}, {"SNOW_BALL", "Snowball"}, {"SLIME_BALL", "Slimeball"},
			{"RABBIT", "Raw Rabbit"}, {"PORK", "Raw Porkchop"}, {"RAW_BEEF", "Raw Beef"}, {"RAW_CHICKEN", "Raw Chicken"}, {"MUTTON", "Raw Mutton"},
			{"SEEDS", "Wheat Seeds"}, {"SUGAR_CANE", "Sugar Cane"}, {"ENCHANTED_CARROT_ON_A_STICK", "Enchanted Carrot on a Stick"},
			{"GLOWSTONE_DUST", "Glowstone Dust"}, {"QUARTZ", "Nether Quartz"}, {"IRON_INGOT", "Iron Ingot"}, {"GOLD_INGOT", "Gold Ingot"},
			{"ENCHANTED_GLOWSTONE_DUST", "Enchanted Glowstone Dust"}, {"HARD_STONE", "Hard Stone"}, {"UMBER", "Umber"}, {"TUNGSTEN", "Tungsten"}, {"GLACITE", "Glacite"},
		};
		for (String[] pair : names) {
			SPECIAL_NAMES.put(pair[0], pair[1]);
		}
	}

	private static volatile long fetchedAt;
	private static volatile long bazaarFetchedAt;
	private static final long BAZAAR_REFRESH_MS = 2 * 60_000L;
	private static volatile boolean fetching;
	private static volatile String lastError = "";

	private SkyBlockPrices() {
	}

	/** Refreshes the prices if they are stale; called whenever prices are needed and usually does nothing. */
	public static void refreshIfOld() {
		long now = System.currentTimeMillis();
		boolean bazaarOld = now - bazaarFetchedAt >= BAZAAR_REFRESH_MS;
		if (fetching || now - fetchedAt < REFRESH_MS && !bazaarOld) {
			return;
		}
		fetching = true;
		boolean onlyBazaar = now - fetchedAt < REFRESH_MS;
		Thread thread = new Thread(() -> {
			if (onlyBazaar) {
				// Bazaar prices move quickly: when an item's best buy orders are filled, the next ones can be a twentieth of them, and that price used to
				// be kept for ten minutes. The bazaar is refreshed every two minutes, the rest every ten.
				try {
					fetchBazaar();
					bazaarFetchedAt = System.currentTimeMillis();
				} catch (Exception e) {
					bazaarFetchedAt = System.currentTimeMillis() - BAZAAR_REFRESH_MS + 30_000L;
					Debug.log("Could not fetch the bazaar: {}", e.toString());
				} finally {
					fetching = false;
				}
				return;
			}
			try {
				if (!itemsFetched) {
					// names and NPC prices only change with game updates: fetching once is enough
					try {
						fetchItems();
						itemsFetched = true;
					} catch (Exception e) {
						Debug.log("Could not fetch the items of SkyBlock: {}", e.toString());
					}
				}
				fetchBazaar();
				bazaarFetchedAt = System.currentTimeMillis();
				fetchLowestBin();
				fetchedAt = System.currentTimeMillis();
				lastError = "";
			} catch (Exception e) {
				lastError = e.getMessage() == null ? e.toString() : e.getMessage();
				fetchedAt = System.currentTimeMillis() - REFRESH_MS + 60_000L; // retry in a minute
				Debug.log("Could not fetch the prices of SkyBlock: {}", lastError);
			} finally {
				fetching = false;
			}
		}, "Lumio SkyBlock prices");
		thread.setDaemon(true);
		thread.start();
	}

	/** @return whether any prices are known */
	public static boolean ready() {
		return !BAZAAR_SELL.isEmpty() || !LOWEST.isEmpty();
	}

	/** @return whether both lists, the lowest BINs and the bazaar, have come */
	public static boolean listsLoaded() {
		return !BAZAAR_SELL.isEmpty() && !LOWEST.isEmpty();
	}

	/** @return whether an id is in the lowest BIN list or on the bazaar: anything else has no market to ask */
	public static boolean onAMarket(String id) {
		return id != null && (LOWEST.containsKey(id) || BAZAAR_SELL.containsKey(id) || BAZAAR_OFFER.containsKey(id));
	}

	public static String lastError() {
		return lastError;
	}

	private static JsonElement fetch(String url) throws Exception {
		return fetch(url, null);
	}

	private static JsonElement fetch(String url, String body) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(8000);
		connection.setReadTimeout(20000);
		connection.setRequestProperty("User-Agent", "Lumio (Minecraft mod)");
		connection.setRequestProperty("Accept-Encoding", "gzip");
		try {
			if (body != null) {
				byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Type", "application/json");
				connection.setFixedLengthStreamingMode(bytes.length);
				try (var out = connection.getOutputStream()) {
					out.write(bytes);
				}
			}
			int code = connection.getResponseCode();
			if (code != 200) {
				throw new IllegalStateException(url + " answered " + code);
			}
			var stream = connection.getInputStream();
			if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
				stream = new GZIPInputStream(stream);
			}
			try (Reader reader = new InputStreamReader(HttpLimits.limited(stream, HttpLimits.DATA_BYTES), StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader);
			}
		} finally {
			connection.disconnect();
		}
	}

	private static void fetchBazaar() throws Exception {
		JsonObject root = fetch(BAZAAR).getAsJsonObject();
		if (!root.has("products")) {
			throw new IllegalStateException("the bazaar has no products");
		}
		Map<String, Double> prices = new HashMap<>();
		Map<String, Double> offers = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("products").entrySet()) {
			String id = entry.getKey();
			JsonObject product = entry.getValue().getAsJsonObject();
			double sell = 0;
			if (product.has("quick_status")) {
				JsonObject status = product.getAsJsonObject("quick_status");
				sell = status.has("sellPrice") ? status.get("sellPrice").getAsDouble() : 0;
				double week = (status.has("buyMovingWeek") ? status.get("buyMovingWeek").getAsDouble() : 0) + (status.has("sellMovingWeek") ? status.get("sellMovingWeek").getAsDouble() : 0);
				if (week > 0) {
					BAZAAR_WEEK.put(id, week);
				}
			}
			// the best buy order pays out immediately, which is the instant sell price: more precise than the quick status average
			if (product.has("sell_summary") && product.get("sell_summary").isJsonArray() && !product.getAsJsonArray("sell_summary").isEmpty()) {
				JsonObject top = product.getAsJsonArray("sell_summary").get(0).getAsJsonObject();
				if (top.has("pricePerUnit")) {
					sell = top.get("pricePerUnit").getAsDouble();
				}
			}
			if (sell > 0) {
				prices.put(id, sell);
			}
			double offer = 0;
			if (product.has("quick_status")) {
				JsonObject status = product.getAsJsonObject("quick_status");
				offer = status.has("buyPrice") ? status.get("buyPrice").getAsDouble() : 0;
			}
			if (product.has("buy_summary") && product.get("buy_summary").isJsonArray() && !product.getAsJsonArray("buy_summary").isEmpty()) {
				JsonObject top = product.getAsJsonArray("buy_summary").get(0).getAsJsonObject();
				if (top.has("pricePerUnit")) {
					offer = top.get("pricePerUnit").getAsDouble();
				}
			}
			if (offer > 0) {
				offers.put(id, offer);
			}
			ID_BY_NAME.putIfAbsent(nameOf(id).toLowerCase(Locale.ROOT), id);
		}
		BAZAAR_SELL.putAll(prices);
		BAZAAR_OFFER.putAll(offers);
	}

	private static void fetchItems() throws Exception {
		JsonObject root = fetch(ITEMS).getAsJsonObject();
		if (!root.has("items") || !root.get("items").isJsonArray()) {
			throw new IllegalStateException("the list of the items has no items");
		}
		for (JsonElement element : root.getAsJsonArray("items")) {
			try {
				JsonObject item = element.getAsJsonObject();
				String id = item.get("id").getAsString();
				if (item.has("name")) {
					// the list's names are the in-game names, which is what sack messages contain
					ID_BY_NAME.put(clean(item.get("name").getAsString()).toLowerCase(Locale.ROOT), id);
				}
				if (item.has("npc_sell_price")) {
					double npc = item.get("npc_sell_price").getAsDouble();
					if (npc > 0) {
						NPC_SELL.put(id, npc);
					}
				}
			} catch (RuntimeException ignored) {
			}
		}
	}

	private static void fetchLowestBin() throws Exception {
		JsonObject root;
		try {
			root = fetch(LOWEST_BIN).getAsJsonObject();
			lowestSource = "Moulberry";
		} catch (Exception e) {
			// the NotEnoughUpdates list is not always available; Coflnet's has the same format
			Debug.log("The lowest prices of NotEnoughUpdates could not be fetched, Coflnet is asked: {}", e.toString());
			root = fetch(LOWEST_BIN_COFLNET).getAsJsonObject();
			lowestSource = "Coflnet";
		}
		Map<String, Double> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			try {
				double price = entry.getValue().getAsDouble();
				if (price > 0) {
					prices.put(entry.getKey(), price);
				}
			} catch (RuntimeException ignored) {
			}
		}
		LOWEST.putAll(prices);
	}

	private static volatile String lowestSource = "";
	private static final Map<String, Double> BAZAAR_WEEK = new ConcurrentHashMap<>();

	/** @return who the list of lowest BIN prices came from, or "" before it did */
	public static String lowestSource() {
		return lowestSource;
	}

	/** @return when the list of lowest BIN prices was fetched */
	public static long lowestFetchedAt() {
		return fetchedAt;
	}

	/** @return when the bazaar's prices were fetched; 0 for not yet */
	public static long bazaarFetchedAt() {
		return bazaarFetchedAt;
	}

	/** @return what the list says the lowest BIN is, or 0; this asks nothing of anybody */
	public static double listedLowest(String id) {
		Double lowest = id == null ? null : LOWEST.get(id);
		return lowest == null ? 0d : lowest;
	}

	/** @return what one costs right now as far as the lists know: to buy from the bazaar, or the lowest BIN; 0 if neither knows. This asks nothing of anybody */
	public static double listedPrice(String id) {
		double buy = bazaarBuy(id);
		return buy > 0d ? buy : listedLowest(id);
	}

	/** @return how many were bought and sold on the bazaar in the last week, or 0 */
	public static double bazaarVolumeWeek(String id) {
		Double volume = id == null ? null : BAZAAR_WEEK.get(id);
		return volume == null ? 0d : volume;
	}

	/** @return what an NPC pays for one, or 0 */
	public static double npcSell(String id) {
		Double npc = id == null ? null : NPC_SELL.get(id);
		return npc == null ? 0d : npc;
	}

	/** @return an answer from Coflnet for one item the mouse is on, a little sooner than its other requests: such an item takes a few */
	static JsonElement askCoflnet(String url) {
		return exactRequest(url, null);
	}

	/** @return the same with data about the item */
	static JsonElement postCoflnet(String url, String body) {
		return exactRequest(url, body);
	}

	private static JsonElement exactRequest(String url, String body) {
		try {
			synchronized (SkyBlockPrices.class) {
				long wait = lastCoflnetAsk + EXACT_ASK_INTERVAL_MILLIS - System.currentTimeMillis();
				if (wait > 0L) {
					Thread.sleep(wait);
				}
				lastCoflnetAsk = System.currentTimeMillis();
			}
			return fetch(url, body);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** Fetches a URL of Coflnet, waiting for its turn: its public API is shared by many users, so all requests of the mod keep their distance. For a background thread only. */
	static JsonElement fetchCoflnet(String url) {
		try {
			synchronized (SkyBlockPrices.class) {
				long wait = lastCoflnetAsk + ASK_INTERVAL_MILLIS - System.currentTimeMillis();
				if (wait > 0L) {
					Thread.sleep(wait);
				}
				lastCoflnetAsk = System.currentTimeMillis();
			}
			return fetch(url);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** @return the value of one item in coins, or 0 if unknown */
	public static double price(String id) {
		if (id == null || id.isEmpty()) {
			return 0;
		}
		Double bazaar = BAZAAR_SELL.get(id);
		if (bazaar != null) {
			return bazaar;
		}
		// the item's own lowest BIN, requested for displayed items: the bulk list is sometimes far off (8.8M for a 290k talisman)
		double exact = exactLowest(id);
		if (exact > 0) {
			return exact;
		}
		Double lowest = LOWEST.get(id);
		if (lowest != null) {
			return lowest;
		}
		// pets, runes and leveled items have a suffix in their id: the auction list keys them by their base id
		int cut = id.indexOf(';');
		if (cut > 0) {
			lowest = LOWEST.get(id.substring(0, cut));
			if (lowest != null) {
				return lowest;
			}
		}
		// items only sellable to NPCs, such as hard stone
		Double npc = NPC_SELL.get(id);
		return npc == null ? 0 : npc;
	}

	// ------------------------------------------------------------------------------------------------ single item prices, for the item list

	/** @return the lowest auction house BIN price, or 0 */
	public static double lowestBin(String id) {
		double exact = exactLowest(id);
		if (exact > 0) {
			return exact;
		}
		Double lowest = id == null ? null : LOWEST.get(id);
		return lowest == null ? 0 : lowest;
	}

	/** Single-item lowest BIN prices from Coflnet by id, with fetch time; NaN while pending or if there is none. */
	private static final Map<String, double[]> EXACT_LOWEST = new ConcurrentHashMap<>();
	/** Minimum time between two single-item requests to Coflnet, of either kind: its public API is shared by many users. */
	private static final long ASK_INTERVAL_MILLIS = 1000L;
	private static final long EXACT_ASK_INTERVAL_MILLIS = 300L;
	private static volatile long lastCoflnetAsk;

	/**
	 * @return the item's lowest BIN as Coflnet reports it for this item alone, or NaN while unknown. Only requested for displayed items (one at a
	 *         time, at most once per second) and cached for ten minutes; never for bazaar items.
	 */
	private static double exactLowest(String id) {
		if (id == null || id.isEmpty() || id.indexOf(';') >= 0 || BAZAAR_SELL.containsKey(id)) {
			return Double.NaN;
		}
		long now = System.currentTimeMillis();
		double[] known = EXACT_LOWEST.get(id);
		if (known != null && now - (long) known[1] < 10 * 60_000L) {
			return known[0];
		}
		if (now - lastCoflnetAsk < ASK_INTERVAL_MILLIS) {
			return known == null ? Double.NaN : known[0];
		}
		lastCoflnetAsk = now;
		EXACT_LOWEST.put(id, new double[]{known == null ? Double.NaN : known[0], now});
		MEDIAN_THREAD.execute(() -> {
			try {
				JsonObject root = fetch(ITEM_PRICE + URLEncoder.encode(id, StandardCharsets.UTF_8) + "/bin").getAsJsonObject();
				double lowest = root.has("lowest") && !root.get("lowest").isJsonNull() ? root.get("lowest").getAsDouble() : Double.NaN;
				EXACT_LOWEST.put(id, new double[]{lowest > 0 ? lowest : Double.NaN, System.currentTimeMillis()});
			} catch (Exception e) {
				Debug.log("Could not fetch the lowest price of {}: {}", id, e.toString());
			}
		});
		return known == null ? Double.NaN : known[0];
	}

	/** @return the instant sell price on the bazaar (best buy order), or 0 if it is not a bazaar item */
	public static double bazaarSell(String id) {
		Double sell = id == null ? null : BAZAAR_SELL.get(id);
		return sell == null ? 0 : sell;
	}

	/** @return the instant buy price on the bazaar (lowest sell offer), or 0 if it is not a bazaar item */
	public static double bazaarBuy(String id) {
		Double buy = id == null ? null : BAZAAR_OFFER.get(id);
		return buy == null ? 0 : buy;
	}

	/** Requested auction house medians by id, with fetch time; NaN while pending or if there is none. */
	private static final Map<String, double[]> MEDIANS = new ConcurrentHashMap<>();
	private static final ExecutorService MEDIAN_THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Lumio auction median");
		thread.setDaemon(true);
		return thread;
	});

	/**
	 * @return the median auction house sale price over recent days from Coflnet, or NaN while unknown. Only requested for viewed items (one at a time,
	 *         at most once per second) and cached for ten minutes.
	 */
	public static double median(String id) {
		if (id == null || id.isEmpty() || BAZAAR_SELL.containsKey(id)) {
			return Double.NaN;
		}
		long now = System.currentTimeMillis();
		double[] known = MEDIANS.get(id);
		if (known != null && now - (long) known[1] < 10 * 60_000L) {
			return known[0];
		}
		if (now - lastCoflnetAsk < ASK_INTERVAL_MILLIS) {
			return known == null ? Double.NaN : known[0];
		}
		lastCoflnetAsk = now;
		MEDIANS.put(id, new double[]{known == null ? Double.NaN : known[0], now});
		MEDIAN_THREAD.execute(() -> {
			try {
				JsonObject root = fetch(ITEM_PRICE + URLEncoder.encode(id, StandardCharsets.UTF_8)).getAsJsonObject();
				double median = root.has("median") ? root.get("median").getAsDouble() : Double.NaN;
				MEDIANS.put(id, new double[]{median > 0 ? median : Double.NaN, System.currentTimeMillis()});
			} catch (Exception e) {
				Debug.log("Could not fetch the median of {}: {}", id, e.toString());
			}
		});
		return known == null ? Double.NaN : known[0];
	}

	/** @return the value of one item in coins by the given price mode, or 0 if unknown */
	public static double price(String id, Mode mode) {
		if (id == null || id.isEmpty()) {
			return 0;
		}
		return switch (mode) {
			case BUY_ORDER -> price(id);
			case SELL_OFFER -> {
				Double offer = BAZAAR_OFFER.get(id);
				yield offer != null ? offer : price(id);
			}
			case NPC -> {
				Double npc = NPC_SELL.get(id);
				yield npc == null ? 0 : npc;
			}
		};
	}

	/** @return the id of the item with this name, or null if unknown */
	public static String idForName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String key = clean(name).toLowerCase(Locale.ROOT);
		String id = ID_BY_NAME.get(key);
		if (id != null) {
			return id;
		}
		String guess = key.toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "").replace("-", "_");
		if (BAZAAR_SELL.containsKey(guess) || LOWEST.containsKey(guess)) {
			return guess;
		}
		return null;
	}

	/** Records the name of an item seen with its id. */
	public static void learnName(String id, String name) {
		if (id != null && name != null && !name.isBlank()) {
			ID_BY_NAME.putIfAbsent(clean(name).toLowerCase(Locale.ROOT), id);
		}
	}

	/** @return an item name without color codes, stars and reforges, as found on inventory items */
	public static String clean(String name) {
		String text = NOT_NAME.matcher(TextColors.noColors(name)).replaceAll("").strip();
		// the symbol in front of a gemstone ("☘ Flawed Peridot Gemstone") is not part of its name
		return SIGN_IN_FRONT.matcher(text).replaceAll("").strip();
	}

	/** @return the display name of an item derived from its id: "Enchanted Carrot" */
	public static String nameOf(String id) {
		String special = SPECIAL_NAMES.get(id);
		if (special != null) {
			return special;
		}
		String base = id;
		int cut = base.indexOf(';');
		if (cut > 0) {
			base = base.substring(0, cut);
		}
		cut = base.indexOf(':');
		if (cut > 0) {
			base = base.substring(0, cut);
		}
		if (base.endsWith("_GEM")) {
			base = base.substring(0, base.length() - 4) + "_GEMSTONE";
		}
		StringBuilder out = new StringBuilder();
		for (String word : base.toLowerCase(Locale.ROOT).split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			boolean small = out.length() > 0 && (word.equals("of") || word.equals("the") || word.equals("a") || word.equals("on") || word.equals("and"));
			out.append(out.isEmpty() ? "" : " ").append(small ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
		}
		return out.toString();
	}
}
