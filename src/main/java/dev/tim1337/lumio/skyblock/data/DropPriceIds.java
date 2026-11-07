package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ids a rare drop's price is looked up by, in the formats of the price data: a pet by its type and rarity ("ENDERMAN;4" for a legendary
 * Enderman, the rarity told by the color of its name), an enchanted book by its bazaar id ("ENCHANTMENT_ULTIMATE_WISE_5") and its auction id
 * ("ULTIMATE_WISE;5"), an item with a tier or level at the end of its name by its name and by its base with the level ("SNOW_RUNE;3"), any other
 * item by its name. No game classes, so it is unit tested; the prices themselves come from {@link SkyBlockPrices}.
 */
public final class DropPriceIds {
	/** The name colors of the rarities, from common to mythic, as the server colors a pet's name; a pet's id ends with the rarity's place. */
	private static final int[] RARITY_RGB = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF};
	private static final String BOOK_PREFIX = "ENCHANTMENT_";
	/** A tier or level at the end of a name: "Snow Rune III", "Bigger Teeth II". */
	private static final Pattern LEVEL_AT_END = Pattern.compile("^(.+?)\\s+([IVX]{1,4}|\\d{1,2})$");

	private DropPriceIds() {
	}

	/** @return the rarity's place (0 common to 5 mythic) of a name in this color, or -1 when the color is no rarity's */
	public static int rarity(int argb) {
		int rgb = argb & 0xFFFFFF;
		for (int i = 0; i < RARITY_RGB.length; i++) {
			if (RARITY_RGB[i] == rgb) {
				return i;
			}
		}
		return -1;
	}

	/** @return a pet's id for the price data: its type and rarity, "ENDERMAN;4"; null when the rarity is not known */
	public static String petId(String type, int argb) {
		int rarity = rarity(argb);
		return rarity < 0 || type.isBlank() ? null : idOfWords(type) + ";" + rarity;
	}

	/**
	 * @param drop       the drop as the server announced it
	 * @param argb       the color of the item's name in the message
	 * @param idForName  the id of an item by its name, or null when it is not known ({@link SkyBlockPrices#idForName})
	 * @return the ids to look the drop's price up by, best first; empty when there is none to try
	 */
	public static List<String> ids(DropMessage drop, int argb, Function<String, String> idForName) {
		List<String> ids = new ArrayList<>(2);
		if (drop.pet()) {
			// a pet is priced by its rarity only: the price of another rarity would be far off, so without one it has none
			String pet = petId(drop.name(), argb);
			if (pet != null) {
				ids.add(pet);
			}
			return ids;
		}
		if (drop.book()) {
			ids.add(drop.bookId());
			// the auction house keys books by enchantment and level: "ENCHANTMENT_ULTIMATE_WISE_5" is "ULTIMATE_WISE;5" there
			String rest = drop.bookId().substring(BOOK_PREFIX.length());
			int cut = rest.lastIndexOf('_');
			if (cut > 0) {
				ids.add(rest.substring(0, cut) + ";" + rest.substring(cut + 1));
			}
			return ids;
		}
		String byName = idForName.apply(drop.name());
		if (byName != null) {
			ids.add(byName);
		}
		Matcher leveled = LEVEL_AT_END.matcher(drop.name());
		if (leveled.matches()) {
			int level = level(leveled.group(2));
			if (level > 0) {
				ids.add(idOfWords(leveled.group(1)) + ";" + level);
			}
		}
		return ids;
	}

	/** @return the words of a name as an id: "Baby Yeti" is "BABY_YETI" */
	private static String idOfWords(String words) {
		return words.strip().toUpperCase(Locale.ROOT).replace("'", "").replace('-', '_').replace(' ', '_');
	}

	/** @return a level written as a number or a roman number (I to XX), or 0 */
	private static int level(String text) {
		if (Character.isDigit(text.charAt(0))) {
			return Integer.parseInt(text);
		}
		return DropMessage.roman(text);
	}
}
