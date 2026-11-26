package dev.tim1337.lumio.skyblock.hud;

import java.util.Locale;

/**
 * Where the item search shows: in the player's own inventory, in chests (containers that hold the player's items, also SkyBlock's storage pages),
 * or in any other menu (the Auction House, the Bazaar, the SkyBlock menu, crafting, ...). Each has its switch under "Show in"; by default only the
 * inventory. Plain text, so it is unit tested.
 */
final class ItemSearchPlaces {
	/** The kinds of screens the "Show in" switches tell apart. */
	enum Kind {
		INVENTORY, CHEST, MENU
	}

	/** Titles of containers that hold items, as the game and SkyBlock name them; a title that starts with one counts ("Ender Chest (2/9)"). */
	private static final String[] CHEST_TITLES = {"chest", "large chest", "ender chest", "barrel", "shulker box", "trapped chest", "minecart with chest",
		"personal vault"};
	/** A SkyBlock backpack page: "Jumbo Backpack (Slot 3)". */
	private static final String BACKPACK = "backpack";

	private ItemSearchPlaces() {
	}

	/**
	 * @param inventory   whether it is the player's own inventory screen
	 * @param chestScreen whether it is a chest-like screen (rows of slots over the inventory), which SkyBlock's menus are too
	 * @param title       the screen's title
	 * @return the kind of the screen
	 */
	static Kind kind(boolean inventory, boolean chestScreen, String title) {
		if (inventory) {
			return Kind.INVENTORY;
		}
		if (!chestScreen) {
			return Kind.MENU;
		}
		String plain = title == null ? "" : title.strip().toLowerCase(Locale.ROOT);
		if (plain.contains(BACKPACK)) {
			return Kind.CHEST;
		}
		for (String chest : CHEST_TITLES) {
			if (plain.equals(chest) || plain.startsWith(chest + " (")) {
				return Kind.CHEST;
			}
		}
		return Kind.MENU;
	}

	/**
	 * @param shownByKey what the "Show / hide the item list" key last said for this screen, or null when it was not pressed there
	 * @param hidden     whether the key has hidden the list where it shows by itself (it stays so until pressed again)
	 * @param switchOn   whether the screen's kind is switched on under "Show in"
	 * @return whether the panels show on a screen
	 */
	static boolean shown(Boolean shownByKey, boolean hidden, boolean switchOn) {
		if (shownByKey != null) {
			return shownByKey;
		}
		return switchOn && !hidden;
	}
}
