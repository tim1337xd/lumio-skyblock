package dev.tim1337.lumio.skyblock.misc;

import java.util.Set;

/** What item protection decides, without the game: which menu a title stands for, and whether a click would give a protected item away. */
final class ProtectionRules {
	/** The game's slot number for a click outside of the window. */
	static final int OUTSIDE = -999;
	/** How long the second press that takes a protection away may wait. */
	static final long SECOND_PRESS_MILLIS = 2000L;
	/** These turn into something better with time, and eating them before that throws it away; the evolved ones have ids of their own. */
	private static final Set<String> EVOLVING = Set.of("NEW_BOTTLE_OF_JYRRE", "DARK_CACAO_TRUFFLE", "DISCRITE", "MOBY_DUCK", "ROSEWATER_FLASK");
	private static final String TRADE_TITLE_START = "You  ";
	private static final String SALVAGE_TITLE_START = "Salvage";
	private static final String AUCTION_HOUSE_END = "Auction House";
	private static final Set<String> AUCTION_TITLES = Set.of("Create Auction", "Create BIN Auction");
	private static final Set<String> DUNGEON_CHEST_TITLES = Set.of("Chest", "Large Chest");

	/** What a menu can do to the items clicked in it. */
	enum Menu {
		OTHER,
		SHOP,
		SALVAGE,
		TRADE,
		AUCTION,
		DUNGEON_CHEST
	}

	/** What was stopped, and how the notice says it. */
	enum Blocked {
		NOTHING(""),
		DROP("It can't be dropped"),
		SELL("It can't be sold"),
		SALVAGE("It can't be salvaged"),
		TRADE("It can't be traded"),
		AUCTION("It can't be auctioned"),
		SWAP("It can't go into this chest"),
		FRAME("It can't go into a frame"),
		CONSUME("It hasn't evolved yet");

		final String notice;

		Blocked(String notice) {
			this.notice = notice;
		}
	}

	/** The switches of the feature. */
	record Switches(boolean drop, boolean sell, boolean salvage, boolean trade, boolean auction, boolean dungeonChest, boolean dropInDungeons) {
	}

	/**
	 * A click in a menu, with protected meaning a protected item.
	 *
	 * @param onProtected  the slot clicked holds one
	 * @param carrying     the cursor holds one
	 * @param swapping     the number key pressed over the slot would bring one from the hotbar
	 * @param outside      the click is outside of the window
	 * @param throwing     it is the drop key
	 * @param quickMove    it is a Shift-click
	 * @param inMenuPart   the slot belongs to the menu, not to the player's inventory
	 */
	record Click(boolean onProtected, boolean carrying, boolean swapping, boolean outside, boolean throwing, boolean quickMove, boolean inMenuPart) {
	}

	private ProtectionRules() {
	}

	/** @return which menu a title says it is; the shop has no title of its own and is told by its buttons, so it is asked for last */
	static Menu menuOf(String title, boolean inDungeon) {
		if (title.startsWith(TRADE_TITLE_START)) {
			return Menu.TRADE;
		}
		if (title.startsWith(SALVAGE_TITLE_START)) {
			return Menu.SALVAGE;
		}
		if (title.endsWith(AUCTION_HOUSE_END) || AUCTION_TITLES.contains(title)) {
			return Menu.AUCTION;
		}
		if (inDungeon && DUNGEON_CHEST_TITLES.contains(title)) {
			return Menu.DUNGEON_CHEST;
		}
		return Menu.OTHER;
	}

	/** @return what a click would do that its switch stops; moving items around in the player's own inventory and storage is never stopped */
	static Blocked check(Click click, Menu menu, Switches switches, boolean inDungeon) {
		boolean dropped = click.outside() ? click.carrying() : click.throwing() && click.onProtected();
		if (dropped) {
			return switches.drop() && !(switches.dropInDungeons() && inDungeon) ? Blocked.DROP : Blocked.NOTHING;
		}
		if (click.outside()) {
			return Blocked.NOTHING;
		}
		boolean touches = click.onProtected() || click.swapping() || click.carrying() && click.inMenuPart();
		return switch (menu) {
			case OTHER -> Blocked.NOTHING;
			case SHOP -> touches && switches.sell() ? Blocked.SELL : Blocked.NOTHING;
			case SALVAGE -> touches && switches.salvage() ? Blocked.SALVAGE : Blocked.NOTHING;
			case TRADE -> touches && switches.trade() ? Blocked.TRADE : Blocked.NOTHING;
			case AUCTION -> touches && switches.auction() ? Blocked.AUCTION : Blocked.NOTHING;
			// only what goes into the chest: picking an item up in the inventory is still moving it around
			case DUNGEON_CHEST -> switches.dungeonChest() && (click.carrying() && click.inMenuPart() || click.swapping() && click.inMenuPart()
				|| click.onProtected() && (click.quickMove() || click.inMenuPart())) ? Blocked.SWAP : Blocked.NOTHING;
		};
	}

	/** @return whether an item with this SkyBlock id still has to evolve */
	static boolean evolving(String id) {
		return id != null && EVOLVING.contains(id);
	}

	/** @return whether a press comes soon enough after the one before it to take a protection away */
	static boolean secondPress(long firstMillis, long nowMillis) {
		return firstMillis != 0L && nowMillis - firstMillis <= SECOND_PRESS_MILLIS;
	}
}
