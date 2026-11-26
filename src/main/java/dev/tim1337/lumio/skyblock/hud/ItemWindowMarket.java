package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.AuctionPrices;
import dev.tim1337.lumio.skyblock.data.PriceHistory;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The item window's price and its Market tab. The window's top shows one number: the lowest BIN, or the Bazaar's buy price. The tab has the rest,
 * under the same names as the tooltip: the trend against the median, Median 7 days and Volume 7 days (or the Bazaar's buy and sell), the craft cost,
 * the price over time, the market's search, and for auction prices the "Prices: SkyCofl" line SkyCofl asks for, a link to the item's page there
 * that opens only after the game's "Open link?" prompt. Worked out once a second by the window, never per frame.
 */
final class ItemWindowMarket {
	/** One row of the tab: a quiet label, its value, and an optional quiet note after it. */
	private record Row(String label, String value, String note, int color) {
	}

	static final int GOLD = 0xFFFFC94A;
	/** Auction numbers older than this show their age: SkyCofl did not answer since. */
	private static final long STALE_MILLIS = 10 * 60_000L;
	private static final long MINUTE_MILLIS = 60_000L;
	private static final double PERCENT = 100.0;
	private static final int ROW = 11;
	private static final int VALUE_COLUMN = 64;
	private static final int CHART_HEIGHT = 34;
	private static final int CHART_GAP = 6;
	private static final float CHART_STEP = 1f;
	private static final float CHART_LINE = 1f;
	private static final float CHART_FILL = 0.12f;
	private static final int BUTTON = 15;
	private static final int BUTTON_ICON = 7;
	private static final int LINK_ROW = 10;
	private static final int CHIP_HEIGHT = 10;
	private static final int CHIP_SIDE = 4;
	private static final float CHIP_FILL = 0.2f;
	private static final float TEXT_HALF = 3.5f;
	private static final String ATTRIBUTION = "Prices: SkyCofl";

	private final List<Row> rows = new ArrayList<>();
	private String id;
	private boolean bazaar;
	private double topValue;
	private String trend = "";
	private int trendColor;
	private String age = "";
	private double[] line = new double[0];
	private MarketSearch.Market market = MarketSearch.Market.NONE;
	private final int[] marketButton = new int[4];
	private final int[] link = new int[4];

	/** Reads an item's numbers. @param craftCost what its recipe costs at the prices known, or 0 */
	void read(String itemId, double craftCost) {
		id = itemId;
		rows.clear();
		// a book of the item list is its enchantment's Bazaar product, a shard its own: priced there first, as in the tooltip
		String priced = SkyBlockPrices.priceIdOfListed(itemId);
		double buy = SkyBlockPrices.bazaarBuy(priced);
		double median = SkyBlockPrices.median(priced);
		bazaar = SkyBlockPrices.onBazaar(priced);
		age = "";
		if (bazaar) {
			topValue = buy > 0 ? buy : SkyBlockPrices.bazaarSell(priced);
			if (buy > 0) {
				rows.add(new Row("Bazaar buy", ItemDetailView.coins(buy), "", GOLD));
			}
			double sell = SkyBlockPrices.bazaarSell(priced);
			if (sell > 0) {
				rows.add(new Row("Bazaar sell", ItemDetailView.coins(sell), "", HudTheme.text()));
			}
			double volume = SkyBlockPrices.bazaarVolumeWeek(priced);
			if (!Double.isNaN(volume) && volume > 0) {
				rows.add(new Row("Volume", String.valueOf(Math.round(volume)), AuctionPrices.DAYS + " days", HudTheme.text()));
			}
		} else {
			// the same numbers of the same moment as the tooltip, with the same names (one source: AuctionPrices); an id with a level (a pet, a
			// rune) has only the list's lowest BIN
			AuctionPrices.Entry auction = AuctionPrices.look(priced);
			topValue = SkyBlockPrices.lowestBin(priced);
			if (topValue > 0) {
				rows.add(new Row("Lowest BIN", ItemDetailView.coins(topValue), "", GOLD));
			}
			if (!Double.isNaN(median) && median > 0) {
				rows.add(new Row("Median", ItemDetailView.coins(median), AuctionPrices.DAYS + " days", HudTheme.text()));
			}
			if (auction.known() && !Double.isNaN(auction.volume())) {
				rows.add(new Row("Volume", String.valueOf(Math.round(auction.volume())), AuctionPrices.DAYS + " days", HudTheme.text()));
			}
			long old = auction.age(System.currentTimeMillis());
			if (old > STALE_MILLIS) {
				age = "Last numbers, " + old / MINUTE_MILLIS + " min old";
			}
		}
		if (craftCost > 0) {
			rows.add(new Row("Craft cost", ItemDetailView.coins(craftCost), "", HudTheme.text()));
		}
		trend = "";
		if (topValue > 0 && !Double.isNaN(median) && median > 0) {
			double change = (topValue - median) / median * PERCENT;
			trend = String.format(Locale.ROOT, "%+.0f %%", change);
			trendColor = change >= 0 ? SystemColors.GREEN : SystemColors.RED;
		}
		line = PriceHistory.line(priced, median, topValue);
		market = MarketSearch.of(itemId);
	}

	/** @return the one number at the window's top, 0 for none */
	double topValue() {
		return topValue;
	}

	/** @return its label: "lowest BIN", or the Bazaar's buy price */
	String topLabel() {
		return bazaar ? "Bazaar buy" : "lowest BIN";
	}

	/** @return whether the tab has anything to show */
	boolean hasContent() {
		return topValue > 0 || market != MarketSearch.Market.NONE;
	}

	MarketSearch.Market market() {
		return market;
	}

	/** Forgets the drawn controls: the tab is not shown this frame. */
	void hide() {
		marketButton[2] = 0;
		link[2] = 0;
	}

	/** Draws the tab between a top and a bottom. */
	void draw(Canvas ui, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		marketButton[2] = 0;
		link[2] = 0;
		int y = top;
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			ui.textAtScaled(row.label(), left, y, TextScale.SMALL, HudTheme.secondary());
			ui.textAtScaledBold(row.value(), left + VALUE_COLUMN, y, TextScale.SMALL, row.color());
			if (!row.note().isEmpty()) {
				float valueWidth = ui.boldWidthScaled(row.value(), TextScale.SMALL);
				ui.textAtScaled(row.note(), left + VALUE_COLUMN + valueWidth + 4, y, TextScale.SMALL, HudTheme.secondary());
			}
			if (i == 0 && !trend.isEmpty()) {
				float chipWidth = ui.boldWidthScaled(trend, TextScale.SMALL) + 2 * CHIP_SIDE;
				float chipX = left + width - chipWidth;
				float chipY = y + TEXT_HALF - CHIP_HEIGHT / 2f;
				ui.roundedSmooth(chipX, chipY, chipWidth, CHIP_HEIGHT, Radius.XS, Draw.withOpacity(trendColor, CHIP_FILL));
				ui.textAtScaledBold(trend, chipX + CHIP_SIDE, y, TextScale.SMALL, trendColor);
			}
			y += ROW;
		}
		if (!age.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(age, width, TextScale.SMALL), left, y, TextScale.SMALL, HudTheme.secondary());
			y += ROW;
		}
		int buttonTop = bottom - (bazaar ? 0 : LINK_ROW + 2) - BUTTON;
		if (line.length >= 2 && y + CHART_GAP + CHART_HEIGHT + CHART_GAP <= buttonTop) {
			drawChart(ui, left, y + CHART_GAP, width, CHART_HEIGHT);
		}
		if (market != MarketSearch.Market.NONE && buttonTop >= y) {
			marketButton[0] = left;
			marketButton[1] = buttonTop;
			marketButton[2] = width;
			marketButton[3] = BUTTON;
			boolean over = PanelKit.inside(marketButton, mouseX, mouseY);
			ui.rounded(left, buttonTop, width, BUTTON, Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.16f : 0.08f));
			String label = market.label();
			float contentWidth = BUTTON_ICON + 4 + ui.widthScaled(label, TextScale.SMALL);
			float x = left + (width - contentWidth) / 2f;
			ui.icon("coins", Math.round(x), buttonTop + (BUTTON - BUTTON_ICON) / 2, BUTTON_ICON, GOLD);
			ui.textAtScaled(label, x + BUTTON_ICON + 4, buttonTop + BUTTON / 2f - TEXT_HALF, TextScale.SMALL, over ? HudTheme.text() : HudTheme.secondary());
		}
		if (!bazaar) {
			// the attribution SkyCofl asks for wherever its numbers are shown: a link to the item's page there
			int linkY = bottom - LINK_ROW;
			link[0] = left;
			link[1] = linkY - 1;
			link[2] = Math.round(ui.widthScaled(ATTRIBUTION, TextScale.SMALL));
			link[3] = LINK_ROW;
			boolean over = PanelKit.inside(link, mouseX, mouseY);
			ui.textAtScaled(ATTRIBUTION, left, linkY, TextScale.SMALL, over ? ui.accent() : HudTheme.secondary());
			if (over) {
				ui.fill(left, linkY + LINK_ROW - 2, link[2], 1, ui.accent());
			}
		}
	}

	/** The price over time in a soft gold line with the room under it faintly filled: what was kept of this item so far, ending in the price now. */
	private void drawChart(Canvas ui, float x, float y, float width, float height) {
		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (double point : line) {
			low = Math.min(low, point);
			high = Math.max(high, point);
		}
		double range = high - low;
		ui.rounded(Math.round(x), Math.round(y), Math.round(width), Math.round(height), Radius.SM, Draw.withOpacity(HudTheme.text(), 0.04f));
		if (range <= 0) {
			// no change kept yet: a flat line would say the price never moves
			ui.textAtScaled("No change kept yet", x + 4, y + height / 2f - TEXT_HALF, TextScale.SMALL, HudTheme.secondary());
			return;
		}
		float last = line.length - 1f;
		for (float px = 0f; px <= width - CHART_STEP; px += CHART_STEP) {
			float at = px / width * last;
			int index = Math.min((int) at, line.length - 2);
			double value = Mth.lerp(at - index, line[index], line[index + 1]);
			float share = (float) ((value - low) / range);
			float lineY = y + 2f + (height - 4f) * (1f - share);
			ui.roundedSmooth(x + px, lineY, CHART_STEP, y + height - lineY, 0f, Draw.withOpacity(GOLD, CHART_FILL));
			ui.roundedSmooth(x + px, lineY, CHART_STEP, CHART_LINE, 0f, GOLD);
		}
	}

	/** @return whether a point is on the market button */
	boolean onMarket(double x, double y) {
		return marketButton[2] > 0 && PanelKit.inside(marketButton, x, y);
	}

	/** @return whether a point is on the SkyCofl link */
	boolean onLink(double x, double y) {
		return link[2] > 0 && PanelKit.inside(link, x, y);
	}

	/** Opens the item's page on SkyCofl, after the game's prompt. */
	void openLink() {
		if (id != null) {
			ItemWindowActions.openLink(AuctionPrices.pageOf(id));
		}
	}
}
