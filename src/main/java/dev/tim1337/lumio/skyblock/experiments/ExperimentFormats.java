package dev.tim1337.lumio.skyblock.experiments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the Experimentation Table's menus say, without colors. The forms are the ones other open mods read from the server (the test strings of SkyHanni and the solvers of NotEnoughUpdates); they were not captured here, so
 * what does not fit is logged under the debug switch. Plain text, so it is unit tested.
 */
public final class ExperimentFormats {
	/** The three games of the table. */
	public enum Game {
		SUPERPAIRS, CHRONOMATRON, ULTRASEQUENCER
	}

	/** What the status slot says: the game shows the order ("Remember the pattern!"), the player repeats it ("Timer: 5s"), or anything else, when nothing is done. */
	public enum Phase {
		READ, REPLICATE, NONE
	}

	/** A running game: which one, at which stakes. */
	public record Menu(Game game, String tier) {
	}

	/** One line of the rewards. */
	public record Reward(Kind kind, String name, long amount) {
		/** What it is. */
		public enum Kind {
			XP, BOOK, BOTTLE, OTHER
		}
	}

	public static final String TABLE_TITLE = "Experimentation Table";
	/** Slots of the menus: remaining clicks and the round on top, the phase in the last row, the reward's summary by game. */
	public static final int TOP_STATUS_SLOT = 4;
	public static final int PHASE_SLOT = 49;
	public static final int SUPERPAIRS_OVER_SLOT = 13;
	public static final int ADDON_OVER_SLOT = 11;
	private static final int ROW = 9;
	private static final int BOOK_NAME_LINE = 2;
	private static final long THOUSAND = 1000L;

	private static final Pattern RUNNING = Pattern.compile("^(Superpairs|Chronomatron|Ultrasequencer) \\((.{1,20})\\)$");
	private static final Pattern OVER = Pattern.compile("^(Experiment [Oo]ver|Superpairs Rewards)$");
	private static final Pattern CLICKS = Pattern.compile("^Remaining Clicks: (\\d{1,4})$");
	private static final Pattern CARD_XP = Pattern.compile("^(\\d{1,4}(?:\\.\\d{1,2})?)k Enchanting Exp$");
	private static final Pattern ROUND = Pattern.compile("^Round: (\\d{1,4})$");
	private static final Pattern TIMER = Pattern.compile("^Timer: \\d{1,3}s$");
	private static final String SHOW_TEXT = "Remember the pattern!";
	/** What the game shows on its slots when a game is over and it waits for one more click. */
	private static final String PROCEED_TEXT = "click any to proceed";
	private static final Pattern CHAIN = Pattern.compile("(Chain|Series) of (\\d{1,3}):");
	/** The title of a game's stakes, as "Chronomatron ➜ Stakes": the arrow may be any one sign. */
	private static final Pattern STAKES = Pattern.compile("^(Superpairs|Chronomatron|Ultrasequencer)\\s*\\S?\\s*Stakes$");
	/** What the lore of a tier in the stakes says when it gives no rewards, or cannot be played. */
	public static final String PRACTICE_TEXT = "Practice mode has no rewards";
	public static final String LEVEL_TOO_LOW_TEXT = "Enchanting level too low!";
	public static final String NOT_ENOUGH_XP_TEXT = "Not enough experience!";
	private static final Pattern CLAIMED = Pattern.compile("^You claimed the (\\S{1,20}) rewards!$");
	private static final Pattern REWARD_XP = Pattern.compile("^(\\d{1,12}(?:,\\d{3})*(?:\\.\\d{1,2})?)([kMB]?) Enchanting Exp(?: \\((?:Stakes|Pairs)\\))?$");
	private static final Pattern REWARD_MARK = Pattern.compile("^\\+\\s*(?:\\[Lvl \\d{1,3}]\\s*)?(.{1,60}?)(?:\\s\\((?:Stakes|Pairs)\\))?$");
	private static final Pattern BOOK = Pattern.compile("^(.{1,40}) (I|II|III|IV|V|VI|VII|VIII|IX|X)$");
	private static final Pattern BOTTLE = Pattern.compile("^(?:Colossal |Titanic |Grand )?Experience Bottle$");
	private static final String REWARDS_START = "Rewards:";
	private static final String REWARDS_END = "Click to claim rewards!";
	private static final String CLICKS_WORDS = "Superpairs clicks";
	private static final String POWERUP = "powerup";
	private static final String ULTRA_RARE = "ULTRA-RARE BOOK!";
	private static final String ENCHANTED_BOOK = "Enchanted Book";
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private ExperimentFormats() {
	}

	/** @return the running game of a menu's title, or null */
	public static Menu menu(String title) {
		Matcher matcher = RUNNING.matcher(title.strip());
		if (!matcher.matches()) {
			return null;
		}
		return new Menu(Game.valueOf(matcher.group(1).toUpperCase(Locale.ROOT)), matcher.group(2));
	}

	/** @return whether the name of an item in the menu says the game is over: "Click any to proceed!" */
	public static boolean isProceed(String name) {
		return name.toLowerCase(java.util.Locale.ROOT).contains(PROCEED_TEXT);
	}

	/**
	 * @param clicksLeft   the clicks left, or -1 when not known
	 * @param hiddenCards  how many cards are still face down
	 * @param cards        how many cards were read
	 * @param proceedShown whether the game shows "Click any to proceed!"
	 * @return whether a game of Superpairs is over: the game says so, the clicks are used up, or it shows all of its cards
	 */
	public static boolean superpairsOver(int clicksLeft, int hiddenCards, int cards, boolean proceedShown) {
		return proceedShown || clicksLeft == 0 || cards > 0 && hiddenCards == 0;
	}

	public static boolean isOver(String title) {
		return OVER.matcher(title.strip()).matches();
	}

	/** @return the slots of the cards of Superpairs at a tier, top left first; unknown tiers have the largest board */
	public static List<Integer> cardSlots(String tier) {
		return switch (tier.toLowerCase(Locale.ROOT)) {
			case "beginner" -> slots(18, 35, 1);
			case "high", "grand" -> slots(10, 43, 2);
			default -> slots(9, 44, 1);
		};
	}

	private static List<Integer> slots(int first, int last, int sideSpace) {
		List<Integer> slots = new ArrayList<>();
		for (int slot = first; slot <= last; slot++) {
			int column = slot % ROW;
			if (column >= sideSpace && column < ROW - sideSpace) {
				slots.add(slot);
			}
		}
		return slots;
	}

	/** @return whether a card's name says it is face down */
	public static boolean isHiddenCard(String name) {
		String shown = name.strip();
		return shown.equals("?") || shown.equals("Click any button!") || shown.equals("Click a second button!") || shown.equals("Next button is instantly rewarded!");
	}

	/** @return how many clicks are left, from the name of the top slot's item, or -1 */
	public static int remainingClicks(String name) {
		Matcher matcher = CLICKS.matcher(name.strip());
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) : -1;
	}

	/** @return whether a card is a power-up, by its lore */
	public static boolean isPowerup(List<String> lore) {
		for (String line : lore) {
			if (line.toLowerCase(Locale.ROOT).contains(POWERUP)) {
				return true;
			}
		}
		return false;
	}

	/** @return the book's name when a card is an ULTRA-RARE book, or null. Its first line says so, the name is two lines on */
	public static String ultraRareBook(List<String> lore) {
		if (lore.isEmpty() || !lore.getFirst().contains(ULTRA_RARE)) {
			return null;
		}
		return lore.size() > BOOK_NAME_LINE ? lore.get(BOOK_NAME_LINE).strip() : ENCHANTED_BOOK;
	}

	/** @return the Enchanting Exp a card gives, from its name, or 0 */
	public static long cardXp(String name) {
		Matcher matcher = CARD_XP.matcher(name.strip());
		return matcher.matches() ? Math.round(Double.parseDouble(matcher.group(1)) * THOUSAND) : 0L;
	}

	/** @return what makes two cards a pair: their name, and for a book the enchant in its lore, as all books have one name */
	public static String cardKey(String name, List<String> lore) {
		String shown = name.strip();
		return shown.equals(ENCHANTED_BOOK) && lore.size() > BOOK_NAME_LINE ? lore.get(BOOK_NAME_LINE).strip() : shown;
	}

	/** @return the phase, from the name of the item in the phase slot */
	public static Phase phase(String name) {
		String shown = name.strip();
		if (shown.equals(SHOW_TEXT)) {
			return Phase.READ;
		}
		return TIMER.matcher(shown).matches() ? Phase.REPLICATE : Phase.NONE;
	}

	/** @return the round, from the name of the top slot's item, or 0 */
	public static int round(String name) {
		Matcher matcher = ROUND.matcher(name.strip());
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
	}

	/** @return the number a card of Ultrasequencer shows while the order is on, or 0 */
	public static int sequenceNumber(String name) {
		String shown = name.strip();
		if (shown.isEmpty() || shown.length() > 3) {
			return 0;
		}
		for (int i = 0; i < shown.length(); i++) {
			if (!Character.isDigit(shown.charAt(i))) {
				return 0;
			}
		}
		return Integer.parseInt(shown);
	}

	/**
	 * @param word "Chain of " for Chronomatron, "Series of " for Ultrasequencer
	 * @return the rounds a line of a tier's lore names after that word, as "Chain of 9:", or 0
	 */
	public static int chainLength(String line, String word) {
		Matcher matcher = CHAIN.matcher(line);
		return matcher.find() && word.startsWith(matcher.group(1)) ? Integer.parseInt(matcher.group(2)) : 0;
	}

	/** @return the game whose stakes a title is, as "Chronomatron ➜ Stakes", or null */
	public static Game stakesGame(String title) {
		Matcher matcher = STAKES.matcher(title.strip());
		return matcher.matches() ? Game.valueOf(matcher.group(1).toUpperCase(Locale.ROOT)) : null;
	}

	/** @return which game's rewards the server says were claimed, or null */
	public static String claimedGame(String message) {
		Matcher matcher = CLAIMED.matcher(message.strip());
		return matcher.matches() ? matcher.group(1) : null;
	}

	/** @return the lines of the rewards in a summary's lore, as they are written; empty if it has none */
	public static List<String> rewardLines(List<String> lore) {
		int start = lore.indexOf(REWARDS_START);
		int end = lore.indexOf(REWARDS_END);
		if (start < 0) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		for (int i = start + 1; i < (end > start ? end : lore.size()); i++) {
			String line = lore.get(i).strip();
			if (!line.isEmpty()) {
				lines.add(line);
			}
		}
		return lines;
	}

	/** @return what one line of the rewards is, or null when it is no reward: the clicks, or an unknown form */
	public static Reward reward(String line) {
		Matcher marked = REWARD_MARK.matcher(line.strip());
		if (!marked.matches()) {
			return null;
		}
		String text = marked.group(1).strip();
		if (text.endsWith(CLICKS_WORDS)) {
			return null;
		}
		Matcher xp = REWARD_XP.matcher(text);
		if (xp.matches()) {
			double number = Double.parseDouble(xp.group(1).replace(",", ""));
			return new Reward(Reward.Kind.XP, text, Math.round(number * switch (xp.group(2)) {
				case "k" -> THOUSAND;
				case "M" -> THOUSAND * THOUSAND;
				case "B" -> THOUSAND * THOUSAND * THOUSAND;
				default -> 1L;
			}));
		}
		if (BOTTLE.matcher(text).matches()) {
			return new Reward(Reward.Kind.BOTTLE, text, 1L);
		}
		return new Reward(BOOK.matcher(text).matches() ? Reward.Kind.BOOK : Reward.Kind.OTHER, text, 1L);
	}

	/** @return the item id of an enchanted book by its enchant and level, as "ENCHANTMENT_GROWTH_6", or null */
	public static String bookId(String name) {
		Matcher matcher = BOOK.matcher(name.strip());
		if (!matcher.matches()) {
			return null;
		}
		int level = 0;
		for (int i = 0; i < ROMAN.length; i++) {
			if (ROMAN[i].equals(matcher.group(2))) {
				level = i + 1;
			}
		}
		return "ENCHANTMENT_" + matcher.group(1).strip().toUpperCase(Locale.ROOT).replace("'", "").replace('-', '_').replace(' ', '_') + "_" + level;
	}

	/** @return the item id of an experience bottle by its name, or null */
	public static String bottleId(String name) {
		return switch (name.strip()) {
			case "Experience Bottle" -> "EXP_BOTTLE";
			case "Grand Experience Bottle" -> "GRAND_EXP_BOTTLE";
			case "Titanic Experience Bottle" -> "TITANIC_EXP_BOTTLE";
			case "Colossal Experience Bottle" -> "COLOSSAL_EXP_BOTTLE";
			default -> null;
		};
	}

	/** @return whether an item id is an experience bottle */
	public static boolean isBottleId(String id) {
		return id != null && (id.equals("EXP_BOTTLE") || id.equals("GRAND_EXP_BOTTLE") || id.equals("TITANIC_EXP_BOTTLE") || id.equals("COLOSSAL_EXP_BOTTLE"));
	}
}
