package dev.tim1337.lumio.skyblock.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Which lines an item gets, from what is known of it: in the order the player set, and only those switched on. A value that is not known is a dash. No game, no files, no
 * network, so it is unit tested.
 */
final class AddedLines {
	static final String DUNGEON = "dungeon";
	static final String LOWEST_BIN = "lowest_bin";
	static final String MEDIAN = "median";
	static final String SOLD = "sold";
	static final String CRAFT = "craft";
	static final String BAZAAR = "bazaar";
	static final String TREND = "trend";
	static final String NPC = "npc";

	private static final int RISING_RGB = 0x30D158;
	private static final int FALLING_RGB = 0xFF453A;
	/** The estimate of a median is marked as one: the market's own median covers only two days. */
	private static final String ESTIMATE = "≈";

	/**
	 * What is known of an item. Where its prices come from and how old they are is kept here, and not shown in the tooltip.
	 *
	 * @param bazaar     whether it is sold on the bazaar and not at auction
	 * @param dungeon    whether it has a dungeon quality
	 * @param lowestFrom who says what the lowest BIN is, or "" while nobody did
	 * @param lowestAge  how old that list is, in milliseconds
	 * @param marketAge  how old the history from Coflnet is, in milliseconds; negative for not fetched yet
	 * @param bazaarAge  how old the bazaar's prices are, in milliseconds; negative for not fetched yet
	 * @param count      how many the hovered stack holds
	 * @param shift      whether Shift is held: the prices are then for the whole stack
	 * @param soulbound  whether it can't be sold, so that its prices are only its base item's
	 * @param loading    whether its prices may still come: what is not known is then shown as such
	 */
	record Facts(boolean bazaar, boolean dungeon, int quality, String floor, double lowest, String lowestFrom, long lowestAge, double median, int medianDays, double soldPerDay, double craft, double trend,
				 double buyNow, double sellNow, double volumeWeek, int count, boolean shift, double npc, long marketAge, long bazaarAge, Exact exact, boolean soulbound, boolean loading) {
	}

	/**
	 * How the numbers of an item with properties were found.
	 *
	 * @param forThisItem  whether they are for items like this one, or the item is priced by its id
	 * @param lowestById   whether no item like it is on sale, so the lowest BIN is its id's
	 * @param medianById   the same for the median
	 */
	record Exact(boolean forThisItem, boolean lowestById, boolean medianById) {
		static final Exact NO = new Exact(false, false, false);
	}

	/** The price is the item type's, not this exact item's. */
	static final String BASE_ITEM = "(base item)";
	static final String SOULBOUND = "Soulbound: can't be sold";
	/** What a price shows while it may still come. */
	static final String WAITING = "…";
	private static final String BAZAAR_TITLE = "BAZAAR";
	private static final String AUCTION_TITLE = "AUCTION";
	private static final String PRICE_TITLE = "PRICE";
	private static final String TIMES = " · ×";
	private static final String SHIFT_HINT = "Shift: ×";
	private static final int HINT_RGB = 0x8E8E93;

	private AddedLines() {
	}

	static List<TooltipLine> build(List<String> order, Facts facts, PriceText.Style style) {
		List<TooltipLine> rows = new ArrayList<>();
		boolean headed = false;
		boolean noted = false;
		// with Shift the prices are for the stack
		int times = facts.shift() && facts.count() > 1 ? facts.count() : 1;
		for (String id : order) {
			if (id.equals(DUNGEON)) {
				if (facts.dungeon()) {
					rows.add(TooltipLine.row("Quality", facts.quality() + " / " + DungeonData.MOST_QUALITY, ""));
					rows.add(TooltipLine.row("Floor obtained", facts.floor(), ""));
				}
				continue;
			}
			if (!appliesTo(id, facts)) {
				continue;
			}
			if (!noted && facts.soulbound() && !facts.bazaar()) {
				rows.add(TooltipLine.footnote(SOULBOUND));
				noted = true;
			}
			// an item on the bazaar has its label; the auction's lines follow directly, unless a stack needs one to say what they are for
			boolean auctionPrice = id.equals(LOWEST_BIN) || id.equals(MEDIAN);
			boolean stackPrice = auctionPrice || id.equals(CRAFT) || id.equals(NPC);
			if (!headed && (facts.bazaar() || stackPrice && facts.count() > 1)) {
				rows.add(heading(facts.bazaar() ? BAZAAR_TITLE : auctionPrice ? AUCTION_TITLE : PRICE_TITLE, facts));
				headed = true;
			}
			switch (id) {
				case LOWEST_BIN -> rows.add(TooltipLine.row("Lowest BIN", price(facts.lowest() * times, facts, style), facts.exact().lowestById() ? BASE_ITEM : ""));
				case MEDIAN -> rows.add(medianRow(facts, times, style));
				case SOLD -> rows.add(TooltipLine.row("Sold", PriceText.number(facts.soldPerDay(), style), "per day"));
				case CRAFT -> rows.add(TooltipLine.row("Craft cost", PriceText.number(facts.craft() * times, style), facts.exact().forThisItem() ? "with upgrades" : ""));
				case BAZAAR -> addBazaar(rows, facts, times, style);
				case TREND -> rows.add(trend(facts.trend()));
				case NPC -> rows.add(TooltipLine.row("NPC price", PriceText.number(facts.npc() * times, style), ""));
				default -> {
				}
			}
		}
		return rows;
	}

	/** @return whether a line says anything about this kind of item: the bazaar's are for bazaar items, the auction's for the others */
	private static boolean appliesTo(String id, Facts facts) {
		return switch (id) {
			case LOWEST_BIN, MEDIAN, SOLD -> !facts.bazaar();
			case BAZAAR -> facts.bazaar();
			case CRAFT, TREND, NPC -> true;
			default -> false;
		};
	}

	/** @return a heading that says what the prices are for: with Shift the stack, and without it a hint that a stack can be priced */
	private static TooltipLine heading(String title, Facts facts) {
		if (facts.count() <= 1) {
			return TooltipLine.heading(title, "", TooltipLine.PLAIN);
		}
		return facts.shift() ? TooltipLine.heading(title + TIMES + facts.count(), "", TooltipLine.PLAIN) : TooltipLine.heading(title, SHIFT_HINT + facts.count(), HINT_RGB);
	}

	private static void addBazaar(List<TooltipLine> rows, Facts facts, int times, PriceText.Style style) {
		rows.add(TooltipLine.row("Buy now", PriceText.number(facts.buyNow() * times, style), ""));
		rows.add(TooltipLine.row("Sell now", PriceText.number(facts.sellNow() * times, style), ""));
		rows.add(TooltipLine.row("Volume", PriceText.number(facts.volumeWeek(), style), "per week"));
	}

	/** @return a price, or that it is still to come */
	private static String price(double value, Facts facts, PriceText.Style style) {
		return !PriceText.known(value) && facts.loading() ? WAITING : PriceText.number(value, style);
	}

	/** The median of items like this one is Coflnet's own number; the one from an id's history is an estimate, over the chosen span. */
	private static TooltipLine medianRow(Facts facts, int times, PriceText.Style style) {
		double median = facts.median() * times;
		if (facts.exact().forThisItem()) {
			return TooltipLine.row("Median", price(median, facts, style), facts.exact().medianById() ? BASE_ITEM : "");
		}
		return TooltipLine.row("Median", estimate(median, facts, style), facts.medianDays() == 1 ? "1 day" : facts.medianDays() + " days");
	}

	private static String estimate(double median, Facts facts, PriceText.Style style) {
		return PriceText.known(median) ? ESTIMATE + PriceText.number(median, style) : facts.loading() ? WAITING : TooltipLine.MISSING;
	}

	private static TooltipLine trend(double change) {
		int rgb = Double.isNaN(change) || Math.round(change) == 0L ? TooltipLine.PLAIN : change > 0d ? RISING_RGB : FALLING_RGB;
		return new TooltipLine(TooltipLine.Kind.ROW, "7-day trend", PriceText.percent(change), "", rgb);
	}
}
