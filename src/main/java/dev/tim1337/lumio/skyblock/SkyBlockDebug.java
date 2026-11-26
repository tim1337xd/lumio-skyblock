package dev.tim1337.lumio.skyblock;

/**
 * The categories of the add-on's probes ({@link dev.tim1337.lumio.api.experimental.debug.DebugHooks}): a debug tool filters its log by them. Lumio's
 * own categories ({@link dev.tim1337.lumio.api.experimental.debug.DebugCategory}) are used for what both share.
 */
public final class SkyBlockDebug {
	/** Where the player is, the action bar and the stats from it. */
	public static final String SKYBLOCK = "skyblock";
	/** Prices, the bazaar, the auction house and the item data. */
	public static final String PRICES = "prices";
	/** The Catacombs: chests, the blood camp, puzzles, splits, the bosses. */
	public static final String DUNGEON = "dungeon";
	/** Mining: abilities, Precision Mining, the break reset. */
	public static final String MINING = "mining";
	/** Fishing: the lines of the game around a catch, so a sea creature missing from the list shows up. */
	public static final String FISHING = "fishing";
	/** Farming: when the Mouse lock and the Farming Keys count the player as farming, and from which lines. */
	public static final String FARMING = "farming";
	/** The Experimentation Table. */
	public static final String EXPERIMENTS = "experiments";
	/** Slayer quests, bosses and minibosses. */
	public static final String SLAYER = "slayer";
	/** The tab list's cards and stats. */
	public static final String TAB = "tab";
	/** Panels, overlays and menus of the add-on (item list, wardrobe, pets, slot binds, scoreboard). */
	public static final String PANELS = "panels";
	/** Profit trackers and drops. */
	public static final String PROFIT = "profit";
	/** Asked for by a debug tool ({@link dev.tim1337.lumio.api.experimental.debug.DebugHooks#wants}): Break Reset Fix's line of each update while breaking. */
	public static final String BREAK = "break";
	/** Asked for by a debug tool: the slayer drop check's verdict on every dropped item. */
	public static final String DROPS = "drops";
	/** Asked for by a debug tool: a dungeon item's data in its tooltip and in the log. */
	public static final String ITEM_DATA = "item_data";

	private SkyBlockDebug() {
	}
}
