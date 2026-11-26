package dev.tim1337.lumio.skyblock.dungeon;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the reward chest menus show besides the rewards: their buttons (never an item of the chest), the enchanted books' real names, and which
 * chest was opened or had the run's Kismet Feather, as the game marks them (Croesus' run lines, the reroll button). Plain logic, tested with
 * recorded lines.
 */
final class ChestMarks {
	/** The buttons of the chest menus; any other line that asks for a click is one too. */
	private static final Set<String> BUTTONS = Set.of("Open Reward Chest", "Reroll Chest", "Close", "Go Back");
	private static final String CLICK = "Click to";
	private static final String BOOK = "Enchanted Book";
	private static final Pattern ENCHANT_LINE = Pattern.compile("^(.+) ([IVX]{1,4})$");
	private static final Pattern BOOK_NAME = Pattern.compile("^Enchanted Book \\((.+)\\)$");
	private static final Pattern OPENED = Pattern.compile("^Opened [cC]hest: (\\w+)$");
	private static final String REROLLED = "You already rerolled a chest!";

	private ChestMarks() {
	}

	/** @return whether a slot of a chest menu is a button, not an item of the chest */
	static boolean isButton(String name, List<String> lore) {
		if (BUTTONS.contains(name)) {
			return true;
		}
		for (String line : lore) {
			if (line.startsWith(CLICK)) {
				return true;
			}
		}
		return false;
	}

	/** @return an enchanted book's name with its enchantment, "Enchanted Book (Wisdom V)", from the first line of its lore; other names as they are */
	static String bookName(String name, List<String> lore) {
		if (!name.equals(BOOK)) {
			return name;
		}
		for (String line : lore) {
			if (!line.isBlank()) {
				return ENCHANT_LINE.matcher(line.strip()).matches() ? BOOK + " (" + line.strip() + ")" : name;
			}
		}
		return name;
	}

	/** @return the name a row shows: an enchanted book by its enchantment ("Wisdom V"), others as they are */
	static String shownName(String name) {
		Matcher book = BOOK_NAME.matcher(name);
		return book.matches() ? book.group(1) : name;
	}

	/** @return the kind of chest a run's line in Croesus says was opened ("Opened Chest: Obsidian"), or null */
	static String openedKind(List<String> runLore) {
		for (String line : runLore) {
			Matcher opened = OPENED.matcher(line.strip());
			if (opened.matches()) {
				return opened.group(1);
			}
		}
		return null;
	}

	/** @return whether the reroll button says the run's Kismet Feather was used */
	static boolean rerolled(List<String> rerollLore) {
		for (String line : rerollLore) {
			if (line.strip().equals(REROLLED)) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a chest of a run is not opened yet; an opened one gets no colored border */
	static boolean unopened(String kind, Set<String> opened) {
		return !opened.contains(kind);
	}

	/** What a chest's border in a run's list of chests says, and the color of its number in the "All chests" panel. */
	enum Verdict {
		/** Opening it gains coins: green. */
		PROFIT,
		/** Opening it loses coins: red. */
		LOSS,
		/** The price of what is in it is not known well enough to say: grey. */
		UNKNOWN,
		/** Opened already: no colored border, dimmed, with the check mark. */
		OPENED
	}

	/**
	 * @param opened   whether the chest was opened
	 * @param profit   its contents as far as priced, less its cost
	 * @param items    how many items it has
	 * @param unpriced how many of them have no price
	 * @return what the chest's border says: an opened one nothing; a profit also when an item has no price (it can only be more); a loss only when
	 *         every item is priced, as an item without a price may make up for it
	 */
	static Verdict verdict(boolean opened, double profit, int items, int unpriced) {
		if (opened) {
			return Verdict.OPENED;
		}
		if (items > 0 && unpriced >= items) {
			return Verdict.UNKNOWN;
		}
		if (profit >= 0) {
			return Verdict.PROFIT;
		}
		return unpriced > 0 ? Verdict.UNKNOWN : Verdict.LOSS;
	}
}
