package dev.tim1337.lumio.skyblock.hud;

/** The sizes of the storage overlay's window, from the columns it shows and what is in it. Plain numbers, so it is unit tested. */
final class StorageLayout {
	/** A card: exactly its 9 slots and its padding, at every screen size. */
	static final int CARD_WIDTH = 178;
	static final int GAP = 8;
	/** The room left and right of the cards inside the window. */
	static final int SIDE = 10;
	/** Cards per row when it is not set. */
	static final int MOST_COLUMNS_UNSET = 3;

	/** The storages panel: its padding, where its slots start, what is left of it when folded, and the room between the ender chest's row and the backpacks'. */
	static final int SHORTCUT_PAD = 8;
	static final int SHORTCUT_TOP = 20;
	static final int SHORTCUT_FOLDED_HEIGHT = 18;
	private static final int SHORTCUT_ROW_GAP = 3;
	private static final int SHORTCUT_BOTTOM = 6;
	/** A slot as in the inventory where there is room, and the smallest that still shows something. */
	private static final int FULL_SLOT = 18;
	private static final int LEAST_SLOT = 4;

	/**
	 * The storages panel's slots in the room they have.
	 *
	 * @param slot the size of a slot
	 */
	record ShortcutGrid(int slot) {
		/** @return the panel's width */
		int width() {
			return 2 * SHORTCUT_PAD + StoragePages.PER_ROW * slot;
		}

		/** @return the opened panel's height */
		int height() {
			return SHORTCUT_TOP + StoragePages.ROWS * slot + SHORTCUT_ROW_GAP + SHORTCUT_BOTTOM;
		}

		/** @return where a row starts, from the first: the backpacks stand a little apart */
		int rowY(int row) {
			return row * slot + (row > 0 ? SHORTCUT_ROW_GAP : 0);
		}
	}

	private StorageLayout() {

	}

	/** @return where the inventory goes: in the middle under the window */
	static int inventoryX(int screenWidth, int inventoryWidth) {
		return (screenWidth - inventoryWidth) / 2;
	}

	/** @return the room right of the inventory, up to the screen's edge */
	static int roomBeside(int screenWidth, int inventoryWidth, int gap, int edge) {
		return screenWidth - inventoryX(screenWidth, inventoryWidth) - inventoryWidth - gap - edge;
	}

	/** @return the largest slots that fit beside the inventory, or null where not even the smallest do: the panel never goes over anything */
	static ShortcutGrid shortcutGrid(int roomWidth) {
		int slot = Math.min(FULL_SLOT, (roomWidth - 2 * SHORTCUT_PAD) / StoragePages.PER_ROW);
		return slot >= LEAST_SLOT ? new ShortcutGrid(slot) : null;
	}

	/**
	 * @param set  the Columns setting, 0 for as many as fit up to the usual most
	 * @param room the screen's width that the window may take
	 * @return the cards per row: as set, fewer only when the screen is too narrow for them
	 */
	static int columns(int set, int room) {
		int fit = Math.max(1, (room - 2 * SIDE + GAP) / (CARD_WIDTH + GAP));
		return set > 0 ? Math.min(set, fit) : Math.min(MOST_COLUMNS_UNSET, fit);
	}

	/** @return the window's width: only as wide as its columns */
	static int windowWidth(int columns) {
		return columns * CARD_WIDTH + (columns - 1) * GAP + 2 * SIDE;
	}

	/**
	 * @param chrome  the header and the hints
	 * @param content the height of the sections with their cards
	 * @param least   the smallest the window may be
	 * @param most    the tallest it may be; beyond it, its content scrolls
	 * @return the window's height: what its content needs, up to the most
	 */
	static int windowHeight(int chrome, int content, int least, int most) {
		return Math.max(least, Math.min(most, chrome + content));
	}
}
