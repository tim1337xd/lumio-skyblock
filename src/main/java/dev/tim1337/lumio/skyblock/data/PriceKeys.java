package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an item is priced by, where its value hangs on what is inside it. The price lists name such items in their own way: the Bazaar sells
 * each enchantment as its own product ("ENCHANTMENT_ULTIMATE_WISE_5"), the lowest BIN list keys a pet by its kind and rarity ("ENDERMAN;4"), a rune by
 * its kind and level ("RUNE_ZAP_3") and a potion by its effect ("POTION_magic_find"); an attribute shard is a Bazaar product of its own
 * ("SHARD_GROVE"). Anything else is priced by its id. No game, no files, no network, so it is unit tested.
 */
public final class PriceKeys {
	private static final String ENCHANTED_BOOK = "ENCHANTED_BOOK";
	private static final String ENCHANTMENTS = "enchantments";
	private static final String ENCHANTMENT_PREFIX = "ENCHANTMENT_";
	private static final String PET_PREFIX = "PET_";
	private static final String PET_INFO = "petInfo";
	private static final String RUNE_PREFIX = "RUNE_";
	private static final String RUNES = "runes";
	/** The items that are a rune themselves; any other item may carry an applied rune in the same field. */
	private static final Set<String> RUNE_ITEMS = Set.of("RUNE", "UNIQUE_RUNE");
	private static final String PET = "PET";
	private static final String POTION = "POTION";
	private static final String POTION_PREFIX = "POTION_";
	private static final String SHARD_PREFIX = "ATTRIBUTE_SHARD";
	private static final String ATTRIBUTES = "attributes";
	private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
	/** An id of the item list with a level: "ULTIMATE_WISE;5", "ZAP_RUNE;3". */
	private static final Pattern LEVELLED = Pattern.compile("^([A-Z0-9_]+);(\\d+)$");
	private static final String RUNE_SUFFIX = "_RUNE";

	private PriceKeys() {
	}

	/**
	 * What an item is priced by.
	 *
	 * @param id           the id its own prices are asked for (the Bazaar's product, or SkyCofl's tag for an item's own numbers)
	 * @param listId       the id in the lowest BIN list, which may differ from {@code id} (a pet: "ENDERMAN;4")
	 * @param ownAuction   whether the item's own auction numbers may be asked for by {@code id}: not for a pet (SkyCofl would answer for its
	 *                     cheapest rarity) nor for a rune (SkyCofl does not know it by that id)
	 * @param exact        whether SkyCofl may be asked for the numbers of items just like this one, by {@code id} and the item's data (a pet by its
	 *                     level and rarity); not for a rune, which SkyCofl does not know by that id, nor for a book of several enchantments
	 * @param sum          for an enchanted book with several enchantments: the Bazaar product of each, whose prices add up to the book's; else empty
	 */
	public record Target(String id, String listId, boolean ownAuction, boolean exact, List<String> sum) {
		static Target plain(String id) {
			return new Target(id, id, true, true, List.of());
		}

		/** @return whether the price is the sum of the book's enchantments */
		public boolean summed() {
			return !sum.isEmpty();
		}
	}

	/**
	 * @param id           the item's SkyBlock id
	 * @param data         the item's SkyBlock data (its custom data as JSON)
	 * @param shardProduct the Bazaar product of an attribute shard by the item list's name of it ("ATTRIBUTE_SHARD_ARACHNO;1"), or null
	 * @return what it is priced by
	 */
	public static Target of(String id, JsonObject data, Function<String, String> shardProduct) {
		if (id == null) {
			return Target.plain(null);
		}
		if (ENCHANTED_BOOK.equals(id)) {
			List<String> books = bookProducts(data);
			if (books.size() == 1) {
				return Target.plain(books.get(0));
			}
			if (books.size() > 1) {
				return new Target(id, id, false, false, List.copyOf(books));
			}
			return Target.plain(id);
		}
		// only a pet item is priced as its pet, and only a rune item as its rune: a weapon with a rune applied (a Heroic Hyperion) is the weapon
		JsonObject pet = PET.equals(id) ? petInfo(data) : null;
		if (pet != null && text(pet, "type") != null) {
			String type = text(pet, "type").toUpperCase(Locale.ROOT);
			int rarity = RARITIES.indexOf(String.valueOf(text(pet, "tier")).toUpperCase(Locale.ROOT));
			return new Target(PET_PREFIX + type, rarity < 0 ? PET_PREFIX + type : type + ";" + rarity, false, true, List.of());
		}
		JsonObject runes = RUNE_ITEMS.contains(id) ? object(data, RUNES) : null;
		if (runes != null && runes.size() == 1) {
			Map.Entry<String, JsonElement> rune = runes.entrySet().iterator().next();
			long level = number(rune.getValue());
			if (level > 0) {
				String key = RUNE_PREFIX + rune.getKey().toUpperCase(Locale.ROOT) + "_" + level;
				return new Target(key, key, false, false, List.of());
			}
		}
		if (POTION.equals(id) && text(data, "potion") != null) {
			return Target.plain(POTION_PREFIX + text(data, "potion"));
		}
		JsonObject attributes = object(data, ATTRIBUTES);
		if (id.startsWith(SHARD_PREFIX) && attributes != null && attributes.size() == 1) {
			String internal = SHARD_PREFIX + "_" + attributes.keySet().iterator().next().toUpperCase(Locale.ROOT) + ";1";
			String product = shardProduct.apply(internal);
			if (product != null) {
				return Target.plain(product);
			}
		}
		return Target.plain(id);
	}

	/**
	 * The id an item of the item list (the item window, the pinned list) is priced by: the Bazaar's product where it is one, else its id in the
	 * lowest BIN list.
	 *
	 * @param repoId       the item list's id: "ULTIMATE_WISE;5", "ENDERMAN;4", "ZAP_RUNE;3", "ATTRIBUTE_SHARD_ARACHNO;1", "MAGMA_LORD_FRAGMENT"
	 * @param onBazaar     whether an id is a Bazaar product
	 * @param shardProduct the Bazaar product of an attribute shard by the item list's name of it, or null
	 */
	public static String forListed(String repoId, Predicate<String> onBazaar, Function<String, String> shardProduct) {
		if (repoId == null || onBazaar.test(repoId)) {
			return repoId;
		}
		String shard = repoId.startsWith(SHARD_PREFIX) ? shardProduct.apply(repoId) : null;
		if (shard != null) {
			return shard;
		}
		Matcher levelled = LEVELLED.matcher(repoId);
		if (levelled.matches()) {
			String name = levelled.group(1);
			String level = levelled.group(2);
			if (name.endsWith(RUNE_SUFFIX)) {
				return RUNE_PREFIX + name.substring(0, name.length() - RUNE_SUFFIX.length()) + "_" + level;
			}
			String book = ENCHANTMENT_PREFIX + name + "_" + level;
			if (onBazaar.test(book)) {
				return book;
			}
		}
		return repoId;
	}

	/** An item as the profit trackers count it: by its price key, under the name a player knows it by (null: the item's own name). */
	public record Counted(String key, String name) {
	}

	/**
	 * @param id        the item's SkyBlock id
	 * @param data      the item's SkyBlock data
	 * @param shards    the Bazaar product of an attribute shard by the item list's name of it, or null
	 * @param bookNames the name of a book's enchantment by its Bazaar product ("Chimera I"), or null
	 * @return how a gained item is counted: an enchanted book by its enchantment ("Flash I", never "Enchanted Book"), a pet by its kind and rarity, a
	 *         rune, an attribute shard or a potion by its price key; anything else by its id
	 */
	public static Counted counted(String id, JsonObject data, Function<String, String> shards, Function<String, String> bookNames) {
		Target target = of(id, data, shards);
		if (ENCHANTED_BOOK.equals(id) && !target.summed() && !ENCHANTED_BOOK.equals(target.id())) {
			String known = bookNames.apply(target.id());
			return new Counted(target.id(), known != null ? known : bookName(target.id()));
		}
		return new Counted(target.listId() == null ? id : target.listId(), null);
	}

	/** @return the Bazaar product of a book of the item list ("ULTIMATE_WISE;5" is ENCHANTMENT_ULTIMATE_WISE_5), or null for an id without a level */
	public static String bookProduct(String repoId) {
		Matcher levelled = repoId == null ? null : LEVELLED.matcher(repoId);
		return levelled != null && levelled.matches() ? ENCHANTMENT_PREFIX + levelled.group(1) + "_" + levelled.group(2) : null;
	}

	/** @return a book's name from its Bazaar product where the item list has none: ENCHANTMENT_FLASH_1 is "Flash I" */
	static String bookName(String product) {
		String rest = product.startsWith(ENCHANTMENT_PREFIX) ? product.substring(ENCHANTMENT_PREFIX.length()) : product;
		int cut = rest.lastIndexOf('_');
		long level = cut > 0 ? parse(rest.substring(cut + 1)) : 0L;
		String words = level > 0 ? rest.substring(0, cut) : rest;
		StringBuilder name = new StringBuilder();
		for (String word : words.toLowerCase(Locale.ROOT).split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return level > 0 ? name + " " + roman(level) : name.toString();
	}

	private static long parse(String digits) {
		try {
			return Long.parseLong(digits);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	/** @return a level as a Roman numeral, as SkyBlock writes enchantment levels (1 to 10, and on to 39) */
	private static String roman(long level) {
		String[] tens = {"", "X", "XX", "XXX"};
		String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
		return level > 0 && level < 40 ? tens[(int) (level / 10)] + ones[(int) (level % 10)] : String.valueOf(level);
	}

	/** @return the Bazaar product of each enchantment on a book: "ENCHANTMENT_ULTIMATE_WISE_5", ultimate enchantments as they are named there */
	static List<String> bookProducts(JsonObject data) {
		JsonObject enchantments = object(data, ENCHANTMENTS);
		List<String> products = new ArrayList<>();
		if (enchantments == null) {
			return products;
		}
		for (Map.Entry<String, JsonElement> enchantment : enchantments.entrySet()) {
			products.add(ENCHANTMENT_PREFIX + enchantment.getKey().toUpperCase(Locale.ROOT) + "_" + number(enchantment.getValue()));
		}
		return products;
	}

	/**
	 * @param constants the item list's {@code constants/attribute_shards.json}
	 * @return the Bazaar product of each attribute shard by the item list's name of it; empty for a file it cannot read
	 */
	public static Map<String, String> shardProducts(JsonObject constants) {
		Map<String, String> products = new HashMap<>();
		if (constants == null || !constants.has(ATTRIBUTES) || !constants.get(ATTRIBUTES).isJsonArray()) {
			return products;
		}
		JsonArray list = constants.getAsJsonArray(ATTRIBUTES);
		for (JsonElement element : list) {
			if (element.isJsonObject()) {
				String internal = text(element.getAsJsonObject(), "internalName");
				String bazaar = text(element.getAsJsonObject(), "bazaarName");
				if (internal != null && bazaar != null) {
					products.put(internal, bazaar);
				}
			}
		}
		return products;
	}

	/**
	 * @param constants the item list's {@code constants/attribute_shards.json}
	 * @return the Bazaar product of each shard by its name, lower case without "Shard" ("packrat" is SHARD_PACKRAT_SKULL, "inkling" SHARD_NIGHT_SQUID)
	 */
	public static Map<String, String> shardsByName(JsonObject constants) {
		Map<String, String> products = new HashMap<>();
		if (constants == null || !constants.has(ATTRIBUTES) || !constants.get(ATTRIBUTES).isJsonArray()) {
			return products;
		}
		for (JsonElement element : constants.getAsJsonArray(ATTRIBUTES)) {
			if (element.isJsonObject()) {
				String name = text(element.getAsJsonObject(), "displayName");
				String bazaar = text(element.getAsJsonObject(), "bazaarName");
				if (name != null && bazaar != null) {
					products.putIfAbsent(name.toLowerCase(Locale.ROOT), bazaar);
				}
			}
		}
		return products;
	}

	/** @return a shard's Bazaar product by its name ("Flaming Spider"): the item list's where it knows it, else as the Bazaar names most ("SHARD_FLAMING_SPIDER") */
	public static String shardProduct(String name, Function<String, String> known) {
		String product = known.apply(name.strip().toLowerCase(Locale.ROOT));
		return product != null ? product : "SHARD_" + name.strip().toUpperCase(Locale.ROOT).replace('\'', ' ').replace('-', ' ').replaceAll("\\s+", "_");
	}

	private static JsonObject petInfo(JsonObject data) {
		if (data == null || !data.has(PET_INFO)) {
			return null;
		}
		JsonElement value = data.get(PET_INFO);
		if (value.isJsonObject()) {
			return value.getAsJsonObject();
		}
		if (!value.isJsonPrimitive()) {
			return null;
		}
		try {
			JsonElement parsed = JsonParser.parseString(value.getAsString());
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static JsonObject object(JsonObject data, String key) {
		return data != null && data.has(key) && data.get(key).isJsonObject() ? data.getAsJsonObject(key) : null;
	}

	private static String text(JsonObject data, String key) {
		if (data == null || !data.has(key) || !data.get(key).isJsonPrimitive()) {
			return null;
		}
		String value = data.get(key).getAsString();
		return value.isBlank() ? null : value;
	}

	private static long number(JsonElement value) {
		try {
			return value.getAsLong();
		} catch (RuntimeException e) {
			return 0L;
		}
	}
}
