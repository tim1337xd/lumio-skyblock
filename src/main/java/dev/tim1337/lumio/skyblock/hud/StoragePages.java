package dev.tim1337.lumio.skyblock.hud;

import java.util.Locale;

/** The pages of the storage (nine ender chest pages, eighteen backpacks), and how the storages panel shows each, from what the game has shown. Plain text and numbers, so it is unit tested. */
final class StoragePages {
	static final int ENDER_PAGES = 9;
	static final int BACKPACKS = 18;
	static final int ALL = ENDER_PAGES + BACKPACKS;
	/** The panel's rows: the ender chest's, then two of backpacks. */
	static final int PER_ROW = 9;
	static final int ROWS = 3;
	/** The dot of an ender chest page, and of a backpack whose size is not known yet. */
	static final int ENDER_RGB = 0x9B6BFF;
	static final int UNKNOWN_RGB = 0xA9A9B2;
	private static final int SMALL_RGB = 0xC8A27A;
	private static final int MEDIUM_RGB = 0x7FE0A0;
	private static final int LARGE_RGB = 0x7FB3FF;
	private static final int GREATER_RGB = 0xB98CFF;
	private static final int JUMBO_RGB = 0xFFB54D;

	/** How a page's slot looks: the page open now, one that can be opened, one the game says is locked or empty, and one never seen. */
	enum State {
		OPEN, READY, LOCKED, UNSEEN
	}

	private StoragePages() {
	}

	/** @return the key of a page by its place: ender chest pages first, then the backpacks */
	static String key(int page) {
		return page < ENDER_PAGES ? "e" + (page + 1) : "b" + (page - ENDER_PAGES + 1);
	}

	/** @return a page's short name, for its tooltip */
	static String shortName(int page) {
		return page < ENDER_PAGES ? "Ender chest " + (page + 1) : "Backpack " + (page - ENDER_PAGES + 1);
	}

	/** @return the number of a page among its kind */
	static int number(int page) {
		return page < ENDER_PAGES ? page + 1 : page - ENDER_PAGES + 1;
	}

	/** @return the panel's row of a page */
	static int row(int page) {
		return page < ENDER_PAGES ? 0 : 1 + (page - ENDER_PAGES) / PER_ROW;
	}

	/** @return the panel's column of a page */
	static int column(int page) {
		return (page < ENDER_PAGES ? page : page - ENDER_PAGES) % PER_ROW;
	}

	/**
	 * @param open     whether this page is open now
	 * @param seen     whether its contents were seen
	 * @param menuName the name of its item in the storage menu, or null if that item is not known
	 * @return how its slot looks
	 */
	static State state(boolean open, boolean seen, String menuName) {
		if (open) {
			return State.OPEN;
		}
		if (menuName != null) {
			String name = menuName.toLowerCase(Locale.ROOT);
			if (name.contains("locked") || name.contains("empty")) {
				return State.LOCKED;
			}
			return State.READY;
		}
		return seen ? State.READY : State.UNSEEN;
	}

	/** @return a backpack's color by its size, read from its page's title ("Jumbo Backpack (Slot #3)"), or a neutral one before it was seen */
	static int backpackRgb(String title) {
		if (title == null) {
			return UNKNOWN_RGB;
		}
		String name = title.toLowerCase(Locale.ROOT);
		if (name.contains("jumbo")) {
			return JUMBO_RGB;
		}
		if (name.contains("greater")) {
			return GREATER_RGB;
		}
		if (name.contains("large")) {
			return LARGE_RGB;
		}
		if (name.contains("medium")) {
			return MEDIUM_RGB;
		}
		return name.contains("small") ? SMALL_RGB : UNKNOWN_RGB;
	}
}
