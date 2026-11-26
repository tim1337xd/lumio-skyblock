package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where an item of the item search is traded and what to type to find it there: a Bazaar item opens the Bazaar's search ("/bz Enchanted Diamond"),
 * an auctionable item the Auction House's ("/ahs Hyperion"), with the plain name the market's search finds: no colors, no stars, no pet level, no
 * symbols in front. Which market comes from the price data, as the "On the Bazaar" and "On the AH" filters have it. Plain text, so it is unit tested.
 */
public final class MarketSearch {
	/** A market an item opens in, with its button's text and its command. */
	public enum Market {
		BAZAAR("Open in Bazaar", "bz"), AUCTION("Search the Auction House", "ahs"), NONE("", "");

		final String label;
		private final String command;

		Market(String label, String command) {
			this.label = label;
			this.command = command;
		}

		public String label() {
			return label;
		}
	}

	/** The pet level in front of a pet's name: "[Lvl 100] ", "[Lvl {LVL}] ". */
	private static final Pattern PET_LEVEL = Pattern.compile("^\\[Lvl [^\\]]{1,8}\\]\\s*");
	/** Every character a market's search has no use for: stars, rune and gem symbols, icons; letters, digits and the usual marks stay. */
	private static final Pattern NOT_IN_NAMES = Pattern.compile("[^\\p{L}\\p{Nd} '\\-.&()+!]");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	private static final String ENCHANTED_BOOK = "Enchanted Book";
	/** A book's enchantment in its lore: its name and its level in Roman numerals or digits, "Ultimate Wise V", "Sharpness 10". */
	private static final Pattern ENCHANTMENT_LINE = Pattern.compile("[\\p{L}][\\p{L} '\\-]{1,40} (?:[IVXLC]{1,6}|\\d{1,2})");
	/** An id with a level or rarity after a semicolon: "ULTIMATE_WISE;5" (a book), "ENDER_DRAGON;4" (a pet). */
	private static final Pattern LEVELLED_ID = Pattern.compile("([A-Z0-9_]+);(\\d{1,2})");
	private static final String BOOK_PREFIX = "ENCHANTMENT_";
	/** A command longer than chat allows is cut at the name's end. */
	private static final int MOST_NAME = 200;

	private MarketSearch() {
	}

	/** @return the market an item opens in: the Bazaar when it is sold there, else the Auction House when it is listed there, else none */
	public static Market of(boolean bazaar, boolean auction) {
		return bazaar ? Market.BAZAAR : auction ? Market.AUCTION : Market.NONE;
	}

	/** @return the market of an item by the price data: on the Bazaar, listed on the Auction House, or neither */
	public static Market of(String id) {
		String book = bookBazaarId(id);
		boolean bazaar = SkyBlockPrices.bazaarBuy(id) > 0 || book != null && SkyBlockPrices.bazaarBuy(book) > 0;
		return of(bazaar, SkyBlockPrices.listedLowest(id) > 0);
	}

	/**
	 * @return the Bazaar's id of an enchanted book of the item list ("ULTIMATE_WISE;5" is "ENCHANTMENT_ULTIMATE_WISE_5" there), or null for an id
	 *         that is no enchantment with a level
	 */
	static String bookBazaarId(String id) {
		Matcher matcher = id == null ? null : LEVELLED_ID.matcher(id);
		return matcher != null && matcher.matches() ? BOOK_PREFIX + matcher.group(1) + "_" + matcher.group(2) : null;
	}

	/**
	 * @return the name a market's search finds an item of the item list by; for an enchanted book (named only "Enchanted Book") the enchantment
	 *         and level its lore names, "Ultimate Wise V"
	 */
	public static String searchName(String name, List<String> lore) {
		String plain = searchName(name);
		if (!ENCHANTED_BOOK.equals(plain) || lore == null) {
			return plain;
		}
		for (String line : lore) {
			String enchantment = TextColors.noColors(line).strip();
			if (ENCHANTMENT_LINE.matcher(enchantment).matches()) {
				return searchName(enchantment);
			}
		}
		return plain;
	}

	/**
	 * @return the name a market's search finds an item by: "[Lvl {LVL}] Ender Dragon" gives "Ender Dragon", "§6Hyperion ✪✪✪✪✪" gives "Hyperion",
	 *         "◆ Music Rune I" gives "Music Rune I"; an enchanted book keeps its enchantment and level ("Ultimate Wise V")
	 */
	public static String searchName(String name) {
		if (name == null) {
			return "";
		}
		String plain = TextColors.noColors(name);
		plain = PET_LEVEL.matcher(plain.strip()).replaceFirst("");
		plain = NOT_IN_NAMES.matcher(plain).replaceAll(" ");
		plain = SPACES.matcher(plain).replaceAll(" ").strip();
		return plain.length() > MOST_NAME ? plain.substring(0, MOST_NAME).strip() : plain;
	}

	/** @return the command that opens the item's market with its name searched ("/bz Enchanted Diamond"), or "" for none */
	public static String command(Market market, String name) {
		String searched = searchName(name);
		if (market == Market.NONE || searched.isEmpty()) {
			return "";
		}
		return "/" + market.command + " " + searched;
	}
}
