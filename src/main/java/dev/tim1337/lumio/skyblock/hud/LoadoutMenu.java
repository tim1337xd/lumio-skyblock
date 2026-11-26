package dev.tim1337.lumio.skyblock.hud;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the game's Loadouts menu, "(1/2) Loadouts": twelve loadouts to a page in the block of three columns and four rows on the right, and the page
 * arrows. Plain text, so it is unit tested. What a key may click is decided here: a loadout that is there, never an empty or a locked one.
 */
public final class LoadoutMenu {
	/** Loadouts to a page, each with its own key. */
	public static final int LOADOUTS = 12;
	/** The menu's container slots of loadouts 1 to 12, row by row: the 3 × 4 block on the right of the six-row menu. */
	private static final int[] SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
	/** The slots of a six-row menu, which the loadouts' block needs. */
	public static final int MENU_SLOTS = 54;
	/** Hotbar keys there are, for "Use the hotbar keys": they pick loadouts 1 to 9. */
	public static final int HOTBAR_KEYS = 9;

	private static final Pattern TITLE = Pattern.compile("^(?:\\((\\d{1,2})/(\\d{1,2})\\) )?Loadouts$");
	private static final Pattern LOCKED = Pattern.compile("\\blocked\\b|\\bclick to unlock\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern EMPTY = Pattern.compile("\\bempty\\b", Pattern.CASE_INSENSITIVE);
	private static final String NEXT_PAGE = "next page";
	private static final String PREVIOUS_PAGE = "previous page";
	/** The fillers of a menu, never a loadout. */
	private static final String GLASS_PANE = "glass_pane";
	private static final String BARRIER = "barrier";
	/** The most characters a key's label has on a slot. */
	private static final int LABEL_CHARS = 3;
	private static final Pattern WORDS = Pattern.compile("[\\s_-]+");
	private static final Pattern DIGITS = Pattern.compile("\\d+");

	/** A page arrow of the menu, by its item's name. */
	public enum Arrow {
		NEXT, PREVIOUS, NONE
	}

	private LoadoutMenu() {
	}

	/** @return whether a menu's title (without colors) is the Loadouts menu, with or without its page in front */
	public static boolean isMenu(String title) {
		if (title == null) {
			return false;
		}
		Matcher matcher = TITLE.matcher(title.strip());
		if (!matcher.matches()) {
			return false;
		}
		if (matcher.group(1) == null) {
			return true;
		}
		int page = Integer.parseInt(matcher.group(1));
		int pages = Integer.parseInt(matcher.group(2));
		return page >= 1 && page <= pages;
	}

	/** @return the menu's container slot of a loadout (1 to 12), or -1 for any other number */
	public static int slotOf(int loadout) {
		return loadout >= 1 && loadout <= LOADOUTS ? SLOTS[loadout - 1] : -1;
	}

	/** @return the loadout (1 to 12) in a container slot of the menu, or 0 for a slot that holds none */
	public static int loadoutAt(int slot) {
		for (int i = 0; i < SLOTS.length; i++) {
			if (SLOTS[i] == slot) {
				return i + 1;
			}
		}
		return 0;
	}

	/**
	 * @param name     the item's name, without colors ("" for an empty slot)
	 * @param lore     its lore lines, without colors
	 * @param itemPath the item's id path, such as "diamond_chestplate"
	 * @return whether a key may click this loadout slot: something is there, and it is neither a filler nor an empty or locked loadout
	 */
	public static boolean usable(String name, List<String> lore, String itemPath) {
		if (name == null || name.isBlank() || itemPath == null || itemPath.isEmpty() || "air".equals(itemPath)) {
			return false;
		}
		if (itemPath.endsWith(GLASS_PANE) || itemPath.equals(BARRIER) || LOCKED.matcher(name).find() || EMPTY.matcher(name).find()) {
			return false;
		}
		for (String line : lore) {
			if (LOCKED.matcher(line).find()) {
				return false;
			}
		}
		return true;
	}

	/** @return which page arrow an item is, by its name without colors */
	public static Arrow arrow(String name) {
		String plain = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
		return switch (plain) {
			case NEXT_PAGE -> Arrow.NEXT;
			case PREVIOUS_PAGE -> Arrow.PREVIOUS;
			default -> Arrow.NONE;
		};
	}

	/**
	 * @return a key's name short enough for the corner of a slot: "R" stays "R", "Mouse Button 4" becomes "MB4", "Left Shift" "LS", "Tab + R"
	 *         "T+R"; "" for an unbound key
	 */
	public static String label(String keyName) {
		if (keyName == null || keyName.isBlank()) {
			return "";
		}
		String plain = keyName.strip().replace(" + ", "+");
		if (plain.length() <= LABEL_CHARS) {
			return plain.toUpperCase(Locale.ROOT);
		}
		StringBuilder label = new StringBuilder();
		for (String part : plain.split("\\+")) {
			if (!label.isEmpty()) {
				label.append('+');
			}
			for (String word : WORDS.split(part.strip())) {
				if (word.isEmpty()) {
					continue;
				}
				label.append(DIGITS.matcher(word).matches() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT));
			}
		}
		return label.length() <= LABEL_CHARS ? label.toString() : label.substring(0, LABEL_CHARS);
	}

	/** Lets one press through and holds back a second one that comes too soon after it, so a double press clicks once. */
	public static final class PressGuard {
		/** How long after a press a second one is held back. */
		public static final long GUARD_MILLIS = 300L;

		private long lastPress = Long.MIN_VALUE;

		/** @return whether a press at this time goes through (and counts as the last one) */
		public boolean press(long nowMillis) {
			if (lastPress != Long.MIN_VALUE && nowMillis - lastPress < GUARD_MILLIS) {
				return false;
			}
			lastPress = nowMillis;
			return true;
		}

		/** Forgets the last press, as when the menu closes. */
		public void reset() {
			lastPress = Long.MIN_VALUE;
		}
	}
}
