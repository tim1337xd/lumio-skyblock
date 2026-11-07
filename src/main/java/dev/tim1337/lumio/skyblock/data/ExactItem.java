package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An item as Coflnet prices it: what makes it this item and not just its id (enchants, stars, recombobulator, gems, reforge, attributes, a pet's level), what is sent about it, and how Coflnet's answers are read. External data: everything is bounded and
 * what is no number counts as nothing. Plain data, so it is unit tested with Coflnet's real answers.
 */
public final class ExactItem {
	private static final String ID = "id";
	private static final String PET_INFO = "petInfo";
	private static final String ENCHANTMENTS = "enchantments";
	private static final String ENCHANTED_BOOK = "ENCHANTED_BOOK";
	private static final String ENCHANTMENT_PREFIX = "ENCHANTMENT_";
	private static final String PET_PREFIX = "PET_";
	/** Never sent: these say which single item it is and when it was made, which says nothing of its value. */
	private static final Set<String> NOT_SENT = Set.of("uuid", "timestamp", "originTag", "uniqueId");
	/** Nor anything that could name a person: an item can be signed, or kept for its owner. */
	private static final Pattern NAMES_SOMEONE = Pattern.compile("(?i)uuid|owner|player|signature|signed|profile|username|skull");
	/** Do not change an item's value: without anything else it is priced by its id alone. */
	private static final Set<String> SAYS_NOTHING = Set.of(ID, "uuid", "timestamp", "originTag", "uniqueId", "donated_museum", "stats_book", "quality");
	/** Coflnet's filter for a pet is by its experience, which finds no pet of exactly that much: its own site filters by the level and rarity. */
	private static final String PET_EXP_FILTER = "CostPerExpPlusBase";
	private static final String LEVEL_FILTER = "PetLevel";
	private static final String RARITY_FILTER = "Rarity";
	private static final Pattern PET_LEVEL = Pattern.compile("\\[Lvl (\\d{1,3})]");
	private static final String CRAFT_LIST = "craftPrice";
	/** The whole item again, under its base price: counting it would count the item twice. */
	private static final String SAME_ITEM = "id";
	private static final int MOST_PARTS = 256;
	private static final double MOST_COINS = 1e15;

	private ExactItem() {
	}

	/** @return whether anything about an item changes its value beyond its id, from the keys of its data */
	public static boolean relevant(Set<String> keys) {
		for (String key : keys) {
			if (!SAYS_NOTHING.contains(key)) {
				return true;
			}
		}
		return false;
	}

	/** @return the id to price an item by: a pet by its kind, an enchanted book with one enchant as that enchant, which is its own item at the bazaar; otherwise its id */
	public static String pricedId(String id, JsonObject data) {
		if (ENCHANTED_BOOK.equals(id) && data.has(ENCHANTMENTS) && data.get(ENCHANTMENTS).isJsonObject()) {
			JsonObject enchantments = data.getAsJsonObject(ENCHANTMENTS);
			if (enchantments.size() == 1) {
				Map.Entry<String, JsonElement> only = enchantments.entrySet().iterator().next();
				return ENCHANTMENT_PREFIX + only.getKey().toUpperCase(Locale.ROOT) + "_" + number(only.getValue());
			}
		}
		JsonObject pet = petInfo(data);
		if (pet != null && pet.has("type") && pet.get("type").isJsonPrimitive()) {
			return PET_PREFIX + pet.get("type").getAsString().toUpperCase(Locale.ROOT);
		}
		return id;
	}

	private static long number(JsonElement value) {
		try {
			return value.getAsLong();
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	private static JsonObject petInfo(JsonObject data) {
		if (!data.has(PET_INFO) || !data.get(PET_INFO).isJsonPrimitive()) {
			return null;
		}
		try {
			JsonElement parsed = JsonParser.parseString(data.get(PET_INFO).getAsString());
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** @return all that is sent about an item: its id, name and its own data, without what says which single item it is. Never the player's name, profile or anything else. */
	public static JsonObject request(String tag, String name, JsonObject data) {
		JsonObject extra = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
			if (!NOT_SENT.contains(entry.getKey()) && !NAMES_SOMEONE.matcher(entry.getKey()).find()) {
				extra.add(entry.getKey(), entry.getValue());
			}
		}
		JsonObject item = new JsonObject();
		item.addProperty("count", 1);
		item.addProperty("tag", tag);
		item.addProperty("itemName", name);
		if (data.has(ENCHANTMENTS) && data.get(ENCHANTMENTS).isJsonObject()) {
			item.add(ENCHANTMENTS, data.get(ENCHANTMENTS));
		}
		item.add("extraAttributes", extra);
		return item;
	}

	/**
	 * @param filters what Coflnet says makes items like this one
	 * @param name    the item's name, which has a pet's level
	 * @param data    the item's own data, which has a pet's rarity
	 * @return the filters as the end of a URL, "?Stars=5&Recombobulated=1", or "" for none
	 */
	public static String query(JsonElement filters, String name, JsonObject data) {
		Map<String, String> sorted = new TreeMap<>();
		if (filters != null && filters.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : filters.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					sorted.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		}
		if (sorted.remove(PET_EXP_FILTER) != null) {
			Matcher level = PET_LEVEL.matcher(name);
			if (level.find()) {
				sorted.put(LEVEL_FILTER, level.group(1));
			}
			JsonObject pet = petInfo(data);
			if (pet != null && pet.has("tier") && pet.get("tier").isJsonPrimitive()) {
				sorted.put(RARITY_FILTER, pet.get("tier").getAsString());
			}
		}
		StringBuilder query = new StringBuilder();
		for (Map.Entry<String, String> entry : sorted.entrySet()) {
			query.append(query.isEmpty() ? '?' : '&').append(encoded(entry.getKey())).append('=').append(encoded(entry.getValue()));
		}
		return query.toString();
	}

	private static String encoded(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
	}

	/** @return a number in an answer, as coins; NaN when it is missing, no number, not above zero or beyond all reason: Coflnet says 0 when it has no such item */
	public static double coins(JsonElement answer, String key) {
		if (answer == null || !answer.isJsonObject() || !answer.getAsJsonObject().has(key)) {
			return Double.NaN;
		}
		try {
			double value = answer.getAsJsonObject().get(key).getAsDouble();
			return value > 0d && value <= MOST_COINS ? value : Double.NaN;
		} catch (RuntimeException e) {
			return Double.NaN;
		}
	}

	/** @return what an item costs from its parts in Coflnet's breakdown: the base item and all that is applied to it; NaN when it says nothing */
	public static double craftCost(JsonElement breakdown) {
		if (breakdown == null || !breakdown.isJsonArray() || breakdown.getAsJsonArray().isEmpty() || !breakdown.getAsJsonArray().get(0).isJsonObject()) {
			return Double.NaN;
		}
		JsonObject first = breakdown.getAsJsonArray().get(0).getAsJsonObject();
		if (!first.has(CRAFT_LIST) || !first.get(CRAFT_LIST).isJsonArray()) {
			return Double.NaN;
		}
		JsonArray parts = first.getAsJsonArray(CRAFT_LIST);
		double total = 0d;
		for (int i = 0; i < parts.size() && i < MOST_PARTS; i++) {
			if (parts.get(i).isJsonObject()) {
				JsonObject part = parts.get(i).getAsJsonObject();
				JsonElement attribute = part.get("attribute");
				boolean sameItem = attribute != null && attribute.isJsonPrimitive() && SAME_ITEM.equals(attribute.getAsString());
				double price = coins(part, "price");
				if (!sameItem && !Double.isNaN(price)) {
					total += price;
				}
			}
		}
		return total > 0d && total <= MOST_COINS ? total : Double.NaN;
	}

	/** @return the keys of an item's data, for {@link #relevant} */
	public static List<String> keys(JsonObject data) {
		return List.copyOf(data.keySet());
	}
}
