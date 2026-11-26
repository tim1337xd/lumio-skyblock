package dev.tim1337.lumio.skyblock.dungeon;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The enchanted books of the reward chests: their bazaar id from the item's SkyBlock data (its "enchantments", such as {ultimate_wise: 1}, gives
 * ENCHANTMENT_ULTIMATE_WISE_1), a book shown only by its enchantment ("Ultimate Wise I") recognized, and the colors the game gives ultimate and
 * other enchantments.
 */
final class ChestBooks {
	/** The SkyBlock id every enchanted book has, whatever its enchantment: never the id to price a book by. */
	static final String BOOK_ID = "ENCHANTED_BOOK";
	static final String PREFIX = "ENCHANTMENT_";
	private static final String ULTIMATE = PREFIX + "ULTIMATE_";
	/** The game's colors: ultimate enchantments light purple, others blue. */
	static final int ULTIMATE_RGB = 0xFF55FF;
	static final int NORMAL_RGB = 0x5555FF;
	private static final int MOST_LEVEL = 10;
	private static final Pattern ENCHANTMENT = Pattern.compile("^([A-Za-z][A-Za-z' -]{1,40}) ([IVX]{1,4})$");
	private static final Pattern KEY = Pattern.compile("^[a-z_]{1,40}$");

	private ChestBooks() {
	}

	/** @return the bazaar id of a book from its custom data's enchantments, or null when it has not exactly one */
	static String idFromData(CompoundTag data) {
		CompoundTag enchantments = data == null ? null : data.getCompound("enchantments").orElse(null);
		if (enchantments == null) {
			return null;
		}
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (String key : enchantments.keySet()) {
			levels.put(key, enchantments.getIntOr(key, 0));
		}
		return idFromEnchantments(levels);
	}

	/** @return ENCHANTMENT_<NAME>_<LEVEL> for a book of one enchantment ({ultimate_wise: 1}), or null for none, several or an odd one */
	static String idFromEnchantments(Map<String, Integer> levels) {
		if (levels.size() != 1) {
			return null;
		}
		Map.Entry<String, Integer> only = levels.entrySet().iterator().next();
		int level = only.getValue();
		if (!KEY.matcher(only.getKey()).matches() || level < 1 || level > MOST_LEVEL) {
			return null;
		}
		return PREFIX + only.getKey().toUpperCase(Locale.ROOT) + "_" + level;
	}

	/** @return the two ids an enchantment's shown name may stand for ("Wisdom V": ENCHANTMENT_WISDOM_5 and ENCHANTMENT_ULTIMATE_WISDOM_5), or null */
	static String[] idsForName(String shown) {
		Matcher matcher = ENCHANTMENT.matcher(shown.strip());
		if (!matcher.matches()) {
			return null;
		}
		int level = DungeonParse.roman(matcher.group(2));
		if (level < 1 || level > MOST_LEVEL) {
			return null;
		}
		String name = matcher.group(1).toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replace("'", "");
		return new String[] {PREFIX + name + "_" + level, ULTIMATE + name + "_" + level};
	}

	/** @return whether an id is an enchanted book's */
	static boolean isBook(String id) {
		return id != null && id.startsWith(PREFIX);
	}

	/** @return the color the game gives a book's enchantment: light purple for an ultimate one, else blue */
	static int color(String id) {
		return id.startsWith(ULTIMATE) ? ULTIMATE_RGB : NORMAL_RGB;
	}

	/** @return a book's enchantment as a row shows it when its lore does not say it: "Ultimate Wise I" from ENCHANTMENT_ULTIMATE_WISE_1 */
	static String display(String id) {
		String rest = id.substring(PREFIX.length());
		int cut = rest.lastIndexOf('_');
		int level;
		try {
			level = Integer.parseInt(rest.substring(cut + 1));
		} catch (NumberFormatException e) {
			return rest;
		}
		StringBuilder name = new StringBuilder();
		for (String word : rest.substring(0, cut).split("_")) {
			if (!word.isEmpty()) {
				name.append(name.isEmpty() ? "" : " ").append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return name + " " + roman(level);
	}

	private static String roman(int level) {
		String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return level >= 0 && level < numerals.length ? numerals[level] : Integer.toString(level);
	}
}
