package dev.tim1337.lumio.skyblock.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.ToDoubleFunction;

/**
 * Reads the game's Wardrobe menu: "(1/3) Armor Sets", with nine sets to a page: four rows of pieces, a row of dyes as buttons named "Slot 4: Equipped", and page buttons at the ends of the last row. Plain text, so it is unit tested. The forms are
 * the ones SkyHanni reads from the server (the title, the slots, "Equipped", a red dye for a locked set); the other words on the buttons were not confirmed, so the dye decides where a name says nothing, and what is not understood is logged
 * under the debug switch.
 */
public final class WardrobeMenu {
	/** Sets to a page, and the rows of pieces above the buttons. */
	public static final int SETS = 9;
	public static final int PIECE_ROWS = 4;
	public static final int BUTTON_ROW = 4;
	public static final int PREVIOUS_PAGE_SLOT = 45;
	public static final int NEXT_PAGE_SLOT = 53;

	private static final Pattern TITLE = Pattern.compile("^\\((\\d{1,2})/(\\d{1,2})\\) Armor Sets$");
	private static final Pattern BUTTON = Pattern.compile("^Slot (\\d{1,3}): (\\S.*)$");
	private static final Pattern RANK = Pattern.compile("\\b(MVP\\+\\+|MVP\\+|MVP|VIP\\+|VIP)(?![A-Za-z])");
	private static final Pattern STARS = Pattern.compile("[^\\p{L}\\p{N}' +-]");
	private static final String POSSESSIVE = "'s";
	private static final String LOCKED_WORD = "locked";
	private static final String DYE = "_dye";
	private static final String RED = "red";
	private static final String GRAY = "gray";

	/** What a set's button says. */
	public enum State {
		READY, EQUIPPED, EMPTY, LOCKED, UNKNOWN
	}

	/** Which page of how many, from the menu's title. */
	public record Page(int page, int pages) {
	}

	private WardrobeMenu() {
	}

	/** @return the page of a menu's title, or null for any other menu */
	public static Page page(String title) {
		Matcher matcher = TITLE.matcher(title.strip());
		if (!matcher.matches()) {
			return null;
		}
		int page = Integer.parseInt(matcher.group(1));
		int pages = Integer.parseInt(matcher.group(2));
		return page >= 1 && page <= pages ? new Page(page, pages) : null;
	}

	/** @return what a button's name says */
	public static State state(String buttonName) {
		Matcher matcher = BUTTON.matcher(buttonName.strip());
		if (!matcher.matches()) {
			return State.UNKNOWN;
		}
		return switch (matcher.group(2).strip().toLowerCase(Locale.ROOT)) {
			case "ready" -> State.READY;
			case "equipped" -> State.EQUIPPED;
			case "empty" -> State.EMPTY;
			case LOCKED_WORD -> State.LOCKED;
			default -> State.UNKNOWN;
		};
	}

	/** @return what a button says by its name, or where that says nothing by its dye: red for locked, gray for empty, another for ready */
	public static State state(String buttonName, String itemPath) {
		State named = state(buttonName);
		if (named != State.UNKNOWN || !itemPath.endsWith(DYE)) {
			return named;
		}
		return itemPath.startsWith(RED) ? State.LOCKED : itemPath.startsWith(GRAY) ? State.EMPTY : State.READY;
	}

	/** @return the number of the set a button's name says, or the given one if it says none */
	public static int number(String buttonName, int otherwise) {
		Matcher matcher = BUTTON.matcher(buttonName.strip());
		return matcher.matches() ? Integer.parseInt(matcher.group(1)) : otherwise;
	}

	/** @return the rank a locked set needs, from its button's lore, or null if it says none */
	public static String neededRank(List<String> lore) {
		for (String line : lore) {
			Matcher matcher = RANK.matcher(line);
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return null;
	}

	/** @return the name of a set, from the names of its pieces: what all of them end with before the piece's own word, as "Necron" from "Necron's Helmet" and "Ancient Necron's Boots" */
	public static String setName(List<String> pieces) {
		List<List<String>> words = new ArrayList<>();
		for (String piece : pieces) {
			String[] split = STARS.matcher(piece).replaceAll("").strip().split("\\s+");
			if (split.length > 1) {
				words.add(List.of(split).subList(0, split.length - 1));
			}
		}
		if (words.isEmpty()) {
			return pieces.isEmpty() ? "" : STARS.matcher(pieces.getFirst()).replaceAll("").strip();
		}
		List<String> first = words.getFirst();
		int shared = first.size();
		for (List<String> other : words) {
			int same = 0;
			while (same < shared && same < other.size() && first.get(first.size() - 1 - same).equals(other.get(other.size() - 1 - same))) {
				same++;
			}
			shared = same;
		}
		List<String> kept = shared == 0 ? first : first.subList(first.size() - shared, first.size());
		String name = String.join(" ", kept);
		return name.endsWith(POSSESSIVE) ? name.substring(0, name.length() - POSSESSIVE.length()) : name;
	}

	/** What a click on a card sends: one click on its button, or one on the game's page button toward its page, or nothing. */
	public enum CardClick {
		WEAR, NEXT_PAGE, PREVIOUS_PAGE, NONE
	}

	/**
	 * @param number   the card's slot
	 * @param openPage the page the game has open, from 1
	 * @param state    what its button says
	 * @return what a click on it sends
	 */
	public static CardClick clickFor(int number, int openPage, State state) {
		int page = WardrobeBook.pageOf(number);
		if (page != openPage) {
			return page > openPage ? CardClick.NEXT_PAGE : CardClick.PREVIOUS_PAGE;
		}
		return state == State.READY || state == State.EQUIPPED ? CardClick.WEAR : CardClick.NONE;
	}

	/** How a name stands on its card: one or two rows, at a size. */
	public record NameFit(String first, String second, float scale) {
	}

	/**
	 * A name is never cut: it stays at its size if it fits, else it takes two rows where there is room for them, and only then gets smaller.
	 *
	 * @param room    the width it has
	 * @param scale   its usual size
	 * @param twoRows whether the card has room for two rows
	 * @param measure a text's width at size 1
	 */
	public static NameFit fitName(String name, float room, float scale, boolean twoRows, ToDoubleFunction<String> measure) {
		if (measure.applyAsDouble(name) * scale <= room) {
			return new NameFit(name, "", scale);
		}
		String first = name;
		String second = "";
		double widest = measure.applyAsDouble(name);
		for (int at = twoRows ? name.indexOf(' ') : -1; at > 0; at = name.indexOf(' ', at + 1)) {
			String left = name.substring(0, at);
			String right = name.substring(at + 1).strip();
			double wider = Math.max(measure.applyAsDouble(left), measure.applyAsDouble(right));
			if (wider < widest) {
				widest = wider;
				first = left;
				second = right;
			}
		}
		return new NameFit(first, second, Math.min(scale, (float) (room / widest)));
	}

	/**
	 * @param used    the used sets per page seen so far, -1 for a page not seen yet
	 * @param locked  the locked sets per page seen so far
	 * @param equipped the number of the worn set, or 0
	 * @return the header's second line, as "8 of 18 slots used · slot 1 worn"
	 */
	public static String summary(int[] used, int[] locked, int equipped) {
		int usedSum = 0;
		int total = used.length * SETS;
		for (int page = 0; page < used.length; page++) {
			usedSum += Math.max(0, used[page]);
			total -= locked[page];
		}
		String text = usedSum + " of " + total + " slots used";
		return equipped > 0 ? text + " · slot " + equipped + " worn" : text + " · nothing worn";
	}
}
