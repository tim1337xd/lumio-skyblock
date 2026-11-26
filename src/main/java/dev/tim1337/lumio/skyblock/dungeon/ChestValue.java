package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a dungeon reward chest is worth: its contents (items, essence, enchanted books) by their prices, less what opening it costs (coins, a chest
 * key). Read from the lines the game shows for a chest (the item names in its menu, or the lines of a chest in Croesus' list); an item without a
 * price counts as nothing and is marked so. Plain logic with the prices given to it, so it is tested with recorded chests.
 */
public final class ChestValue {
	/** The kinds of chests, in their order. */
	public static final List<String> CHESTS = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock");
	static final Pattern CHEST_TITLE = Pattern.compile("^(Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)( Chest)?$");
	private static final Pattern COUNT = Pattern.compile("^(.+?) x([\\d,]{1,6})$");
	private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence$");
	private static final Pattern BOOK = Pattern.compile("^Enchanted Book \\((.+) ([IVX]{1,4})\\)$");
	private static final Pattern COINS = Pattern.compile("^([\\d,]{1,13}) Coins$");
	private static final String KEY = "Dungeon Chest Key";
	private static final int MOST_ITEMS = 32;

	/** One item of a chest: its name as shown, its id (or null), how many, and its value; no price is marked. */
	public record Item(String name, String id, int count, double value, boolean priced) {
	}

	/** A chest: its kind, its items, what they are worth, what it costs, and the profit. */
	public record Chest(String kind, List<Item> items, double contents, double cost, boolean key) {
		public double profit() {
			return contents - cost;
		}
	}

	private ChestValue() {
	}

	/** @return the kind of a chest by the title of its menu ("Obsidian Chest" is "Obsidian"), or null */
	/** @return whether a chest title had the word "Chest" (Hypixel shows both "Obsidian Chest" and plain "Obsidian") */
	public static boolean namedChest(String title) {
		return title.strip().endsWith(" Chest");
	}

	public static String kindOf(String title) {
		Matcher matcher = CHEST_TITLE.matcher(title.strip());
		return matcher.matches() ? matcher.group(1) : null;
	}

	/**
	 * @param kind      the chest's kind
	 * @param contents  the names of its items as shown, each with " x18" where there are more
	 * @param costLines the lines after "Cost" ("100,000 Coins", "Dungeon Chest Key", "FREE")
	 * @param ids       the id of an item name, or null where it is not known
	 * @param prices    the price of one item of an id, 0 where none is known
	 * @param keyPrice  what a Dungeon Chest Key costs
	 */
	public static Chest of(String kind, List<String> contents, List<String> costLines, Function<String, String> ids, ToDoubleFunction<String> prices,
		double keyPrice) {
		List<Item> items = new ArrayList<>();
		double total = 0;
		for (String raw : contents) {
			if (items.size() >= MOST_ITEMS) {
				break;
			}
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			int count = 1;
			String name = line;
			Matcher counted = COUNT.matcher(line);
			if (counted.matches()) {
				name = counted.group(1);
				count = Math.clamp(parse(counted.group(2)), 1, 100_000);
			}
			String id = idOf(name, ids, prices);
			double each = id == null ? 0 : prices.applyAsDouble(id);
			boolean priced = id != null && each > 0 && Double.isFinite(each);
			double value = priced ? each * count : 0;
			items.add(new Item(name, id, count, value, priced));
			total += value;
		}
		double cost = 0;
		boolean key = false;
		for (String raw : costLines) {
			String line = raw.strip();
			Matcher coins = COINS.matcher(line);
			if (coins.matches()) {
				cost += parse(coins.group(1));
			} else if (line.equals(KEY)) {
				key = true;
				cost += keyPrice;
			}
		}
		return new Chest(kind, items, total, cost, key);
	}

	/** @return the id of an item of a chest: essence and enchanted books by their names, others as the game knows them */
	static String idOf(String name, Function<String, String> ids, ToDoubleFunction<String> prices) {
		Matcher essence = ESSENCE.matcher(name);
		if (essence.matches()) {
			return "ESSENCE_" + essence.group(1).toUpperCase(Locale.ROOT);
		}
		Matcher book = BOOK.matcher(name);
		String known = ids.apply(name);
		if (book.matches()) {
			// the id learned from the book's own data (ENCHANTMENT_…), never the id every book has
			if (ChestBooks.isBook(known)) {
				return known;
			}
			String[] both = ChestBooks.idsForName(book.group(1) + " " + book.group(2));
			// the name does not say whether it is an ultimate enchantment: the one the market knows
			return both == null ? null : prices.applyAsDouble(both[1]) > 0 ? both[1] : both[0];
		}
		if (known == null) {
			// a book shown only by its enchantment ("Ultimate Wise I", as Croesus lists them): one the market knows
			String[] both = ChestBooks.idsForName(name);
			if (both != null) {
				return prices.applyAsDouble(both[1]) > 0 ? both[1] : prices.applyAsDouble(both[0]) > 0 ? both[0] : null;
			}
		}
		return ChestBooks.BOOK_ID.equals(known) ? null : known;
	}

	private static long parse(String digits) {
		try {
			return Long.parseLong(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** @return "+5.48M", "−80k", "+12.4k" */
	public static String coins(double value) {
		double size = Math.abs(value);
		String sign = value < 0 ? "−" : "+";
		if (size >= 1_000_000) {
			return sign + String.format(Locale.ROOT, "%.2fM", size / 1_000_000);
		}
		if (size >= 1_000) {
			return sign + String.format(Locale.ROOT, size >= 100_000 ? "%.0fk" : "%.1fk", size / 1_000);
		}
		return sign + Math.round(size);
	}
}
