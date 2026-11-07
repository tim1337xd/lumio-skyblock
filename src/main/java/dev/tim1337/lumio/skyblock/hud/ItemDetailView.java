package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.data.ItemHoldings;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;
import dev.tim1337.lumio.skyblock.data.PriceHistory;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An item's own window, centered where the inventory is, with the item list staying on the right; the inventory's slots are away while it is open. From the top: the item with its name, rarity and type, back, pin and close; the price with a
 * quiet line of other values and the trend; how many the player has and where, with Find; then tabs for what is known of it: its recipe, the forge, shops, trades, Kat, what it is used in and who drops it. A mob has its drops, an NPC what it
 * sells. Arrows go through several recipes and long lists. An item clicked in the window opens in the same window; Back, Esc and Backspace go back step by step to the inventory. A tab without data is left out.
 * <p>
 * Everything shown is worked out when the item opens and again once a second (what the player has changes as they move items), never per frame. Quick craft only opens SkyBlock's own crafting menu for the item, one command per click; it never
 * moves, crafts or clicks anything itself.
 */
final class ItemDetailView {
	private static final int PAD = 10;
	private static final int SECTION_GAP = 7;
	private static final int HEADER = 22;
	private static final int ICON_TILE = 22;
	private static final int BUTTON = 13;
	private static final int PRICE_HEIGHT = 28;
	/** The line of the price over time, beside the price: its size, the step it is drawn in and how it looks, in GUI pixels. */
	private static final float PRICE_LINE_WIDTH = 56f;
	private static final float PRICE_LINE_HEIGHT = 14f;
	private static final float PRICE_LINE_GAP = 6f;
	private static final float PRICE_LINE_STEP = 1f;
	private static final float PRICE_LINE_THICKNESS = 1f;
	private static final float PRICE_LINE_FILL = 0.12f;
	private static final int HAVE_HEIGHT = 11;
	private static final int CELL = 18;
	private static final int CELL_GAP = 2;
	private static final int NEED_ROW = 11;
	private static final int CRAFT_BUTTON = 16;
	private static final int CRAFT_ICON = 7;
	private static final int CRAFT_ICON_GAP = 4;
	private static final int TILE = 18;
	private static final int TILE_GAP = 4;
	private static final int ROW = 17;
	private static final int TABS = 14;
	private static final int TAB_GAP = 9;
	private static final int TAB_UNDERLINE = 8;
	private static final int NOTE_ROW = 10;
	private static final int ARROW = 10;
	private static final int LEAST_HEIGHT_FOR_PRICE = 190;
	private static final float RADIUS = 10f;
	private static final float OPEN_SECONDS = 0.15f;
	private static final long REFRESH_MILLIS = 1000L;
	private static final int MOST_BACK = 30;
	private static final int COVER = 0xFF12131A;
	private static final float COVER_OPACITY = 0.94f;
	private static final int GOOD = SystemColors.GREEN;
	private static final int BAD = SystemColors.RED;
	private static final int GOLD = 0xFFFFC94A;
	private static final String COIN = "SKYBLOCK_COIN";
	private static final String NOTHING = "Nothing is known of how to get this";

	/** An ingredient of the shown recipe: what, how many the recipe needs, and how many the player has. */
	private record Need(String id, String name, int color, long need, long have) {
		boolean enough() {
			return have >= need;
		}
	}

	/** What Back returns to. */
	private record View(String id, ItemTabs.Kind tab) {
	}

	/** One row of a list: its icon, words on the left and the right, and what a click opens, or null. */
	private record Row(String icon, String text, String right, String target) {
	}

	private final ItemListFeature feature;
	private final Deque<View> back = new ArrayDeque<>();
	private final Spring opening = Spring.smooth(OPEN_SECONDS);
	private final List<int[]> hitBoxes = new ArrayList<>();
	private final List<String> hitIds = new ArrayList<>();
	private final List<int[]> tabBoxes = new ArrayList<>();
	private String id;
	private ItemTabs.Kind wantedTab;
	/** The open tab, and how far along its recipes or its list. */
	private int tab;
	private int page;
	private int pages = 1;
	/** The window as drawn last, and the inventory's place it stands for. */
	private final int[] box = new int[4];
	private final int[] covered = new int[4];
	// worked out per item and refreshed once a second
	private long refreshedAt;
	private ItemRepo.Entry entry;
	private ItemTypes.Info info;
	private boolean source;
	private List<ItemTabs.Tab> tabs = List.of();
	private final List<Need> needs = new ArrayList<>();
	private final List<String> usedIn = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private String rowsNote = "";
	private int missingCount;
	private String priceLabel = "";
	private double priceValue;
	private String priceExtra = "";
	private String trend = "";
	private double[] priceLine = new double[0];
	private int trendColor;
	private String places = "";
	private int have;
	// last drawn boxes, for clicks
	private int[] backButton = new int[4];
	private int[] pinButton = new int[4];
	private int[] closeButton = new int[4];
	private int[] prevPage = new int[4];
	private int[] nextPage = new int[4];
	private int[] craftButton = new int[4];
	private int[] findButton = new int[4];
	private int[] sourceLink = new int[4];

	ItemDetailView(ItemListFeature feature) {
		this.feature = feature;
	}

	/** @return the open item's id, or null while no window is open */
	String id() {
		return id;
	}

	/** Opens an item; the one shown before is remembered for Back. */
	void open(String itemId) {
		if (itemId == null) {
			return;
		}
		if (id == null) {
			opening.snap(0f);
		} else if (!id.equals(itemId)) {
			back.push(new View(id, currentKind()));
			if (back.size() > MOST_BACK) {
				back.removeLast();
			}
		}
		show(itemId, null);
	}

	private void show(String itemId, ItemTabs.Kind kind) {
		id = itemId;
		wantedTab = kind;
		tab = 0;
		page = 0;
		refreshedAt = 0L;
	}

	/** Shows a tab of the open item, if it has it. */
	void showTab(ItemTabs.Kind kind) {
		wantedTab = kind;
		refreshedAt = 0L;
	}

	/** Goes back a step: to the item shown before, or to the inventory. */
	void back() {
		if (back.isEmpty()) {
			close();
			return;
		}
		View view = back.pop();
		show(view.id(), view.tab());
	}

	void close() {
		id = null;
		back.clear();
		feature.showSlots();
	}

	/** @return whether a point is on the window or on the inventory's place */
	boolean contains(double x, double y) {
		return id != null && (PanelKit.inside(box, x, y) || PanelKit.inside(covered, x, y));
	}

	private ItemTabs.Kind currentKind() {
		return tab < tabs.size() ? tabs.get(tab).kind() : null;
	}

	// --------------------------------------------------------------------------------------------------- data

	private void refresh() {
		long now = System.currentTimeMillis();
		if (now - refreshedAt < REFRESH_MILLIS) {
			return;
		}
		refreshedAt = now;
		entry = ItemRepo.get(id);
		info = entry == null ? null : ItemTypes.of(entry);
		source = ItemRepo.isSource(id);
		readUsedIn();
		tabs = ItemTabs.of(ItemRepo.recipesFor(id), usedIn.size(), ItemRepo.ofSource(id));
		if (wantedTab != null) {
			for (int i = 0; i < tabs.size(); i++) {
				if (tabs.get(i).kind() == wantedTab) {
					tab = i;
				}
			}
			wantedTab = null;
		}
		tab = tabs.isEmpty() ? 0 : Math.min(tab, tabs.size() - 1);
		readRows();
		readNeeds();
		readPrice();
		have = ItemHoldings.count(id);
		StringBuilder where = new StringBuilder();
		for (ItemHoldings.Place place : ItemHoldings.places(id)) {
			where.append(where.isEmpty() ? "" : ", ").append(place.name()).append(' ').append(place.count());
		}
		places = where.toString();
	}

	/** What it is used in: the results of its uses, each once. */
	private void readUsedIn() {
		Set<String> results = new LinkedHashSet<>();
		for (ItemRepo.Recipe use : ItemRepo.usagesOf(id)) {
			if (!use.outputs().isEmpty() && use.outputs().get(0) != null) {
				results.add(use.outputs().get(0).id());
			}
		}
		usedIn.clear();
		usedIn.addAll(results);
	}

	private List<ItemRepo.Recipe> shownRecipes() {
		return tab < tabs.size() ? tabs.get(tab).recipes() : List.of();
	}

	private boolean listTab() {
		ItemTabs.Kind kind = currentKind();
		return kind == ItemTabs.Kind.DROPS || kind == ItemTabs.Kind.SELLS || kind == ItemTabs.Kind.DROPPED_BY;
	}

	/** The rows of a tab that is a list. */
	private void readRows() {
		rows.clear();
		rowsNote = "";
		ItemTabs.Kind kind = currentKind();
		if (kind == ItemTabs.Kind.DROPS) {
			Set<String> seen = new LinkedHashSet<>();
			for (ItemRepo.Recipe recipe : shownRecipes()) {
				rowsNote = rowsNote.isEmpty() ? recipe.note() : rowsNote;
				for (ItemRepo.Stack drop : recipe.outputs()) {
					if (seen.add(drop.id())) {
						rows.add(new Row(drop.id(), nameOf(drop.id()) + times(drop.count()), drop.note(), drop.id()));
					}
				}
			}
		} else if (kind == ItemTabs.Kind.SELLS) {
			for (ItemRepo.Recipe recipe : shownRecipes()) {
				ItemRepo.Stack sold = recipe.outputs().getFirst();
				rows.add(new Row(sold.id(), nameOf(sold.id()) + times(sold.count()), costOf(recipe), sold.id()));
			}
		} else if (kind == ItemTabs.Kind.DROPPED_BY) {
			Set<String> seen = new LinkedHashSet<>();
			for (ItemRepo.Recipe recipe : shownRecipes()) {
				if (seen.add(recipe.sourceId().isEmpty() ? recipe.source() : recipe.sourceId())) {
					rows.add(new Row(recipe.sourceId(), recipe.source(), chanceHere(recipe), recipe.sourceId()));
				}
			}
		}
	}

	private String chanceHere(ItemRepo.Recipe recipe) {
		for (ItemRepo.Stack drop : recipe.outputs()) {
			if (drop.id().equals(id)) {
				return drop.note();
			}
		}
		return "";
	}

	private static String costOf(ItemRepo.Recipe recipe) {
		StringBuilder cost = new StringBuilder();
		for (ItemRepo.Stack input : recipe.inputs()) {
			cost.append(cost.isEmpty() ? "" : ", ");
			cost.append(input.id().equals(COIN) ? coins(input.count()) + " coins" : nameOf(input.id()) + times(input.count()));
		}
		return cost.toString();
	}

	private static String times(double count) {
		return count > 1 ? " ×" + PanelKit.count(Math.round(count)) : "";
	}

	private static String nameOf(String itemId) {
		ItemRepo.Entry item = ItemRepo.get(itemId);
		return item == null ? SkyBlockPrices.nameOf(itemId) : item.plainName();
	}

	/** @return the recipe the tab shows now, or null */
	private ItemRepo.Recipe shownRecipe() {
		List<ItemRepo.Recipe> recipes = shownRecipes();
		if (recipes.isEmpty() || listTab()) {
			return null;
		}
		page = Math.floorMod(page, recipes.size());
		return recipes.get(page);
	}

	/** The ingredients of the shown recipe, added up over its slots, with how many the player has. */
	private void readNeeds() {
		needs.clear();
		missingCount = 0;
		ItemRepo.Recipe shown = shownRecipe();
		if (shown == null) {
			return;
		}
		Map<String, Long> wanted = new LinkedHashMap<>();
		for (ItemRepo.Stack stack : shown.inputs()) {
			if (stack != null && !stack.id().equals(COIN)) {
				wanted.merge(stack.id(), Math.round(stack.count()), Long::sum);
			}
		}
		for (Map.Entry<String, Long> want : wanted.entrySet()) {
			ItemRepo.Entry ingredient = ItemRepo.get(want.getKey());
			int color = ingredient == null ? HudTheme.text() : ingredient.rarityColor();
			Need need = new Need(want.getKey(), nameOf(want.getKey()), color, want.getValue(), ItemHoldings.count(want.getKey()));
			needs.add(need);
			if (!need.enough()) {
				missingCount++;
			}
		}
	}

	/** @return what the shown recipe costs in coins, at the prices known */
	private double craftCost() {
		ItemRepo.Recipe shown = shownRecipe();
		if (shown == null) {
			return 0;
		}
		double cost = 0;
		for (ItemRepo.Stack stack : shown.inputs()) {
			if (stack != null) {
				cost += stack.id().equals(COIN) ? stack.count() : SkyBlockPrices.price(stack.id()) * stack.count();
			}
		}
		return cost;
	}

	/** The price: bazaar buy for bazaar items, otherwise the lowest BIN; one quiet line of the other values; the trend against the 3-day median. */
	private void readPrice() {
		double buy = SkyBlockPrices.bazaarBuy(id);
		double median = SkyBlockPrices.median(id);
		StringBuilder extra = new StringBuilder();
		if (buy > 0) {
			priceValue = buy;
			priceLabel = "buy";
			double sell = SkyBlockPrices.bazaarSell(id);
			if (sell > 0) {
				extra.append("Sell ").append(coins(sell));
			}
			extra.append(extra.isEmpty() ? "" : " · ").append("per item");
		} else {
			priceValue = SkyBlockPrices.lowestBin(id);
			priceLabel = "lowest BIN";
			if (!Double.isNaN(median) && median > 0) {
				extra.append("Avg 3 days ").append(coins(median));
			}
			double cost = craftCost();
			if (!needs.isEmpty() && cost > 0) {
				extra.append(extra.isEmpty() ? "" : " · ").append("craft ").append(coins(cost));
			}
		}
		priceExtra = extra.toString();
		trend = "";
		if (priceValue > 0 && !Double.isNaN(median) && median > 0) {
			double change = (priceValue - median) / median * 100.0;
			trend = String.format(Locale.ROOT, "%+.0f %%", change);
			trendColor = change >= 0 ? GOOD : BAD;
		}
		priceLine = PriceHistory.line(id, median, priceValue);
	}

	private boolean craftable() {
		ItemRepo.Recipe shown = shownRecipe();
		return entry != null && entry.craftable && shown != null && shown.crafting();
	}

	// --------------------------------------------------------------------------------------------------- drawing

	/**
	 * Draws the window where the inventory is.
	 *
	 * @param window   the inventory's place
	 * @param listLeft where the list starts, or the screen's width without a list
	 * @return the item under the mouse (an ingredient, a drop, a use), or null
	 */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, int[] window, int listLeft, float dt, int mouseX, int mouseY) {
		if (id == null) {
			return null;
		}
		refresh();
		hitBoxes.clear();
		hitIds.clear();
		tabBoxes.clear();
		System.arraycopy(ItemListLayout.detail(screen.width, screen.height, window, listLeft), 0, box, 0, 4);
		System.arraycopy(window, 0, covered, 0, 4);
		float open = Mth.clamp(opening.step(1f, dt), 0f, 1f);
		float alphaBefore = Draw.alpha();
		graphics.nextStratum();
		// the inventory's own background stays under it: it is covered at once, the window itself fades in
		ui.rounded(window[0], window[1], window[2], window[3], Radius.XL, COVER);
		ui.rounded(box[0], box[1], box[2], box[3], RADIUS, Draw.withOpacity(COVER, COVER_OPACITY));
		Draw.setAlpha(alphaBefore * open);
		ItemRepo.Entry hovered = null;
		try {
			HudTheme.panel(ui, box[0], box[1], box[2], box[3], RADIUS, HudTheme.opacity(), feature.glow());
			int left = box[0] + PAD;
			int width = box[2] - 2 * PAD;
			int bottom = box[1] + box[3] - PAD;
			int top = drawHeader(ui, graphics, left, width, box[1] + PAD, mouseX, mouseY);
			if (priceValue > 0 && !source && box[3] >= LEAST_HEIGHT_FOR_PRICE) {
				top = drawPrice(ui, left, width, top + SECTION_GAP);
			}
			if (!source) {
				top = drawHave(ui, left, width, top + SECTION_GAP, mouseX, mouseY);
			}
			top += SECTION_GAP;
			prevPage = new int[4];
			nextPage = new int[4];
			craftButton = new int[4];
			sourceLink = new int[4];
			if (tabs.isEmpty()) {
				ui.textAtScaled(ui.fitScaled(NOTHING, width, TextScale.SMALL), left, top + 4f, TextScale.SMALL, HudTheme.secondary());
			} else {
				top = drawTabs(ui, left, width, top, mouseX, mouseY);
				drawBody(ui, graphics, left, width, top, bottom, mouseX, mouseY);
			}
			for (int i = 0; i < hitBoxes.size(); i++) {
				if (PanelKit.inside(hitBoxes.get(i), mouseX, mouseY)) {
					hovered = ItemRepo.get(hitIds.get(i));
				}
			}
		} finally {
			Draw.setAlpha(alphaBefore);
		}
		return hovered;
	}

	/** The item on a soft glow in its rarity color, its name, rarity and type; back, pin and close. */
	private int drawHeader(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int y, int mouseX, int mouseY) {
		int rarity = entry == null ? HudTheme.text() : entry.rarityColor();
		int x = left;
		backButton = new int[]{x, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON};
		iconButton(ui, backButton, "chevron", true, mouseX, mouseY);
		x += BUTTON + 4;
		ui.softLight(x + ICON_TILE / 2f, y + ICON_TILE / 2f, ICON_TILE * 0.7f, Draw.withOpacity(rarity, 0.4f));
		ui.rounded(x, y, ICON_TILE, ICON_TILE, Radius.MD, Draw.withOpacity(rarity, 0.12f));
		graphics.item(stackOf(id), x + (ICON_TILE - 16) / 2, y + (ICON_TILE - 16) / 2);
		int textX = x + ICON_TILE + 7;
		int buttonsLeft = left + width - 2 * BUTTON - 4;
		pinButton = new int[]{buttonsLeft, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON};
		closeButton = new int[]{buttonsLeft + BUTTON + 4, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON};
		iconButton(ui, pinButton, "pin", false, mouseX, mouseY);
		if (feature.isFavorite(id)) {
			ui.ring(pinButton[0], pinButton[1], BUTTON, BUTTON, Radius.SM, 1, ui.accent());
		}
		iconButton(ui, closeButton, "close", false, mouseX, mouseY);
		float room = buttonsLeft - 6 - textX;
		ui.textAtScaledBold(ui.fitScaled(nameOf(id), room, TextScale.BODY), textX, y + 1f, TextScale.BODY, HudTheme.readable(rarity));
		String label = info == null ? "" : info.label();
		if (!label.isEmpty()) {
			ui.textAtScaledBold(ui.fitScaled(label.toUpperCase(Locale.ROOT), room, TextScale.SMALL), textX, y + 12f, TextScale.SMALL, Draw.withOpacity(HudTheme.readable(rarity), 0.85f));
		}
		return y + HEADER;
	}

	private static void iconButton(Canvas ui, int[] button, String icon, boolean pointsBack, int mouseX, int mouseY) {
		boolean over = PanelKit.inside(button, mouseX, mouseY);
		ui.rounded(button[0], button[1], button[2], button[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.16f : 0.08f));
		if (pointsBack) {
			var pose = ui.graphics().pose();
			pose.pushMatrix();
			pose.translate(button[0] + button[2] / 2f, button[1] + button[3] / 2f);
			pose.rotate((float) (Math.PI / 2.0));
			ui.icon("chevron", -3, -3, 6, HudTheme.text());
			pose.popMatrix();
		} else {
			ui.icon(icon, button[0] + (button[2] - 7) / 2, button[1] + (button[3] - 7) / 2, 7, HudTheme.text());
		}
	}

	/** The tabs as a quiet row of words, smaller where they do not fit, with a line under the open one. @return the y below them */
	private int drawTabs(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		float wide = 0f;
		for (ItemTabs.Tab each : tabs) {
			wide += ui.widthScaled(each.kind().label, 1f);
		}
		float scale = Math.min(TextScale.SMALL, (width - (tabs.size() - 1f) * TAB_GAP - 2f * ARROW - 30f) / Math.max(1f, wide));
		float x = left;
		for (int i = 0; i < tabs.size(); i++) {
			String label = tabs.get(i).kind().label;
			float labelWidth = ui.widthScaled(label, scale);
			int[] hit = {Math.round(x) - 3, y - 2, Math.round(labelWidth) + 6, TABS};
			tabBoxes.add(hit);
			boolean chosen = i == tab;
			boolean over = PanelKit.inside(hit, mouseX, mouseY);
			int color = chosen ? 0xFFFFFFFF : over ? Draw.mix(HudTheme.secondary(), 0xFFFFFFFF, 0.5f) : HudTheme.secondary();
			if (chosen) {
				ui.textAtScaledBold(label, x, y, scale, color);
				ui.roundedSmooth(x + (labelWidth - TAB_UNDERLINE) / 2f, y + 9f, TAB_UNDERLINE, 1.5f, 0.75f, ui.accent());
			} else {
				ui.textAtScaled(label, x, y, scale, color);
			}
			x += labelWidth + TAB_GAP;
		}
		return y + TABS;
	}

	/** The arrows and "1 / 2" at the right of the tabs' row, when there are several pages. */
	private void drawPageArrows(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		pages = Math.max(1, pages);
		if (pages <= 1) {
			return;
		}
		String counter = (page + 1) + " / " + pages;
		float counterWidth = ui.widthScaled(counter, TextScale.SMALL);
		float from = left + width - 2f * ARROW - counterWidth - 4f;
		prevPage = new int[]{Math.round(from), y - TABS - 1, ARROW, ARROW};
		nextPage = new int[]{Math.round(from + ARROW + counterWidth + 4f), y - TABS - 1, ARROW, ARROW};
		arrow(ui, prevPage, true, mouseX, mouseY);
		ui.textAtScaled(counter, from + ARROW + 2f, y - TABS, TextScale.SMALL, HudTheme.secondary());
		arrow(ui, nextPage, false, mouseX, mouseY);
	}

	private void drawBody(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		ItemTabs.Kind kind = currentKind();
		if (kind == ItemTabs.Kind.USED_IN) {
			drawUsedIn(ui, graphics, left, width, top, bottom, mouseX, mouseY);
		} else if (listTab()) {
			drawRows(ui, graphics, left, width, top, bottom, mouseX, mouseY);
		} else {
			pages = shownRecipes().size();
			drawRecipe(ui, graphics, left, width, top, bottom, mouseX, mouseY);
		}
		drawPageArrows(ui, left, width, top, mouseX, mouseY);
	}

	/** A recipe: its grid with green or red borders, what it needs beside it with have/need, what it makes and where, and quick craft. */
	private void drawRecipe(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		ItemRepo.Recipe shown = shownRecipe();
		if (shown == null) {
			return;
		}
		for (int i = 0; i < 9; i++) {
			ItemRepo.Stack stack = i < shown.inputs().size() ? shown.inputs().get(i) : null;
			drawCell(ui, graphics, stack, left + i % 3 * (CELL + CELL_GAP), top + i / 3 * (CELL + CELL_GAP));
		}
		int listX = left + 3 * CELL + 2 * CELL_GAP + 8;
		int listWidth = left + width - listX;
		int fit = Math.max(1, (3 * CELL + 2 * CELL_GAP) / NEED_ROW);
		for (int i = 0; i < needs.size() && i < fit; i++) {
			Need need = needs.get(i);
			int rowY = top + i * NEED_ROW;
			ui.icon(need.enough() ? "check" : "close", listX, rowY + 1, 6, need.enough() ? GOOD : BAD);
			String count = PanelKit.count(need.have()) + "/" + PanelKit.count(need.need());
			float countWidth = ui.widthScaled(count, TextScale.SMALL);
			ui.textAtScaled(count, listX + listWidth - countWidth, rowY, TextScale.SMALL, need.enough() ? HudTheme.text() : HudTheme.secondary());
			ui.textAtScaled(ui.fitScaled(need.name(), listWidth - countWidth - 14, TextScale.SMALL), listX + 9, rowY, TextScale.SMALL, HudTheme.readable(need.color()));
		}
		int y = top + 3 * CELL + 2 * CELL_GAP + 4;
		String where = shown.crafting() ? shown.note() : shown.source() + (shown.note().isEmpty() ? "" : " · " + shown.note());
		if (!where.isEmpty() && y + NOTE_ROW <= bottom) {
			boolean link = !shown.sourceId().isEmpty() && ItemRepo.get(shown.sourceId()) != null;
			String shownWhere = ui.fitScaled(where, width, TextScale.SMALL);
			sourceLink = link ? new int[]{left, y - 1, Math.round(ui.widthScaled(shownWhere, TextScale.SMALL)), NOTE_ROW} : new int[4];
			boolean over = link && PanelKit.inside(sourceLink, mouseX, mouseY);
			ui.textAtScaled(shownWhere, left, y, TextScale.SMALL, over ? ui.accent() : HudTheme.secondary());
			y += NOTE_ROW + 2;
		}
		if (craftable() && y + CRAFT_BUTTON <= bottom) {
			String missing = missingCount == 0 ? "" : missingCount + " missing";
			float missingWidth = missing.isEmpty() ? 0f : ui.widthScaled(missing, TextScale.SMALL) + 8;
			craftButton = new int[]{left, y, Math.round(width - missingWidth), CRAFT_BUTTON};
			if (PanelKit.inside(craftButton, mouseX, mouseY)) {
				ui.rounded(craftButton[0], craftButton[1], craftButton[2], CRAFT_BUTTON, Radius.MD, Draw.withOpacity(HudTheme.text(), 0.08f));
			}
			String label = "Quick craft";
			float labelWidth = ui.boldWidthScaled(label, TextScale.SMALL);
			float contentX = craftButton[0] + (craftButton[2] - CRAFT_ICON - CRAFT_ICON_GAP - labelWidth) / 2f;
			ui.icon("pickaxe", Math.round(contentX), y + (CRAFT_BUTTON - CRAFT_ICON) / 2, CRAFT_ICON, HudTheme.text());
			ui.textAtScaledBold(label, contentX + CRAFT_ICON + CRAFT_ICON_GAP, y + CRAFT_BUTTON / 2f - 3.5f, TextScale.SMALL, HudTheme.text());
			if (!missing.isEmpty()) {
				ui.textAtScaled(missing, left + width - missingWidth + 8, y + CRAFT_BUTTON / 2f - 3.5f, TextScale.SMALL, BAD);
			}
		}
	}

	/** A list: a mob's drops with their chances, an NPC's shop, or who drops the item. A click on a row opens it. */
	private void drawRows(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		int y = top;
		if (!rowsNote.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(rowsNote, width, TextScale.SMALL), left, y, TextScale.SMALL, HudTheme.secondary());
			y += NOTE_ROW + 2;
		}
		int perPage = Math.max(1, (bottom - y) / ROW);
		pages = ItemTabs.pages(rows.size(), perPage);
		page = Math.floorMod(page, pages);
		int first = page * perPage;
		for (int i = first; i < rows.size() && i < first + perPage; i++) {
			Row row = rows.get(i);
			int rowY = y + (i - first) * ROW;
			int[] hit = {left, rowY, width, ROW - 1};
			boolean known = row.target() != null && ItemRepo.get(row.target()) != null;
			if (known && PanelKit.inside(hit, mouseX, mouseY)) {
				ui.rounded(left - 3, rowY, width + 6, ROW - 1, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.08f));
			}
			graphics.item(stackOf(row.icon()), left, rowY);
			float rightWidth = row.right().isEmpty() ? 0f : Math.min(width * 0.5f, ui.widthScaled(row.right(), TextScale.SMALL));
			float textRoom = width - TILE - rightWidth - 6f;
			ui.textAtScaled(ui.fitScaled(row.text(), textRoom, TextScale.SMALL), left + TILE, rowY + 5f, TextScale.SMALL, HudTheme.text());
			if (rightWidth > 0f) {
				String right = ui.fitScaled(row.right(), rightWidth, TextScale.SMALL);
				ui.textAtScaled(right, left + width - ui.widthScaled(right, TextScale.SMALL), rowY + 5f, TextScale.SMALL, HudTheme.secondary());
			}
			if (known) {
				hitBoxes.add(hit);
				hitIds.add(row.target());
			}
		}
	}

	/** What it is used in: its items in a grid, by pages. */
	private void drawUsedIn(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		int columns = Math.max(1, (width + TILE_GAP) / (TILE + TILE_GAP));
		int lines = Math.max(1, (bottom - top + TILE_GAP) / (TILE + TILE_GAP));
		int perPage = columns * lines;
		pages = ItemTabs.pages(usedIn.size(), perPage);
		page = Math.floorMod(page, pages);
		int first = page * perPage;
		for (int i = first; i < usedIn.size() && i < first + perPage; i++) {
			String itemId = usedIn.get(i);
			int x = left + (i - first) % columns * (TILE + TILE_GAP);
			int y = top + (i - first) / columns * (TILE + TILE_GAP);
			boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
			ui.rounded(x, y, TILE, TILE, Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.14f : 0.06f));
			graphics.item(stackOf(itemId), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
			hitBoxes.add(new int[]{x, y, TILE, TILE});
			hitIds.add(itemId);
		}
	}

	/** The price big in gold with its label, one quiet line of other values below, and the trend chip on the right. */
	private int drawPrice(Canvas ui, int left, int width, int y) {
		String big = coins(priceValue);
		ui.textAtScaledBold(big, left, y, TextScale.HEADING, GOLD);
		float bigWidth = ui.boldWidthScaled(big, TextScale.HEADING);
		ui.textAtScaled(priceLabel, left + bigWidth + 5, y + 6f, TextScale.SMALL, HudTheme.secondary());
		float extraRoom = width;
		float lineRight = left + width;
		if (!trend.isEmpty()) {
			float chipWidth = ui.boldWidthScaled(trend, TextScale.SMALL) + 8;
			float chipX = left + width - chipWidth;
			lineRight = chipX - PRICE_LINE_GAP;
			ui.roundedSmooth(chipX, y + 3f, chipWidth, 10f, Radius.XS, Draw.withOpacity(trendColor, 0.2f));
			ui.textAtScaledBold(trend, chipX + 4, y + 4.5f, TextScale.SMALL, trendColor);
			extraRoom = width - chipWidth - 6;
		}
		if (!priceExtra.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(priceExtra, extraRoom, TextScale.SMALL), left, y + 18f, TextScale.SMALL, HudTheme.secondary());
		}
		drawPriceLine(ui, lineRight - PRICE_LINE_WIDTH, y + 1f, PRICE_LINE_WIDTH, PRICE_LINE_HEIGHT);
		return y + PRICE_HEIGHT;
	}

	/** The price over time in a soft gold line, with the room under it faintly filled: what was kept of this item so far, ending in the price now. */
	private void drawPriceLine(Canvas ui, float x, float y, float width, float height) {
		if (priceLine.length < 2) {
			return;
		}
		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (double point : priceLine) {
			low = Math.min(low, point);
			high = Math.max(high, point);
		}
		double range = high - low;
		if (range <= 0) {
			// no change kept yet: a flat line would say the price never moves
			return;
		}
		float last = priceLine.length - 1f;
		for (float px = 0f; px <= width; px += PRICE_LINE_STEP) {
			float at = px / width * last;
			int index = Math.min((int) at, priceLine.length - 2);
			double value = Mth.lerp(at - index, priceLine[index], priceLine[index + 1]);
			float share = (float) ((value - low) / range);
			float lineY = y + (height - 1f) * (1f - share);
			ui.roundedSmooth(x + px, lineY, PRICE_LINE_STEP, y + height - lineY, 0f, Draw.withOpacity(GOLD, PRICE_LINE_FILL));
			ui.roundedSmooth(x + px, lineY, PRICE_LINE_STEP, PRICE_LINE_THICKNESS, 0f, GOLD);
		}
	}

	/** "You have N" with where, cut with "…" if long, and the Find link. */
	private int drawHave(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		String count = "You have " + have;
		ui.textAtScaledBold(count, left, y, TextScale.SMALL, HudTheme.text());
		float x = left + ui.boldWidthScaled(count, TextScale.SMALL) + 5;
		findButton = new int[4];
		float room = left + width - x;
		if (have > 0) {
			String find = "Find";
			float findWidth = ui.boldWidthScaled(find, TextScale.SMALL);
			findButton = new int[]{Math.round(left + width - findWidth), y - 2, Math.round(findWidth), 11};
			boolean over = PanelKit.inside(findButton, mouseX, mouseY);
			ui.textAtScaledBold(find, findButton[0], y, TextScale.SMALL, over ? Draw.mix(ui.accent(), 0xFFFFFFFF, 0.3f) : ui.accent());
			room -= findWidth + 8;
		}
		if (!places.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(places, room, TextScale.SMALL), x, y, TextScale.SMALL, HudTheme.secondary());
		}
		return y + HAVE_HEIGHT;
	}


	/** Draws a cell of the recipe grid: empty, or the ingredient with its count and a green or red border. */
	private void drawCell(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Stack stack, int x, int y) {
		if (stack == null) {
			ui.rounded(x, y, CELL, CELL, Radius.SM, Draw.withOpacity(HudTheme.slot(), 0.8f));
			return;
		}
		int ring = ringOf(stack);
		ui.rounded(x, y, CELL, CELL, Radius.SM, Draw.withOpacity(ring == 0 ? HudTheme.slot() : ring, ring == 0 ? 0.8f : 0.14f));
		graphics.item(stackOf(stack.id()), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
		long count = Math.round(stack.count());
		if (count > 1) {
			String text = PanelKit.count(count);
			float w = ui.boldWidthScaled(text, TextScale.SMALL);
			ui.textAtScaledBold(text, x + CELL - w - 1f, y + CELL - 8f, TextScale.SMALL, 0xFFFFFFFF);
		}
		if (ring != 0) {
			ui.ring(x, y, CELL, CELL, Radius.SM, 1, ring);
		}
		if (!stack.id().equals("SKYBLOCK_COIN")) {
			hitBoxes.add(new int[]{x, y, CELL, CELL});
			hitIds.add(stack.id());
		}
	}

	/** @return the border of an ingredient: green when the player has enough of it, red when not, none for coins */
	private int ringOf(ItemRepo.Stack stack) {
		for (Need need : needs) {
			if (need.id().equals(stack.id())) {
				return need.enough() ? GOOD : BAD;
			}
		}
		return 0;
	}

	private static void arrow(Canvas ui, int[] box, boolean previous, int mouseX, int mouseY) {
		boolean over = PanelKit.inside(box, mouseX, mouseY);
		ui.rounded(box[0], box[1], box[2], box[3], Radius.XS, Draw.withOpacity(HudTheme.text(), over ? 0.16f : 0.07f));
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(box[0] + box[2] / 2f, box[1] + box[3] / 2f);
		pose.rotate((float) (previous ? Math.PI / 2.0 : -Math.PI / 2.0));
		ui.icon("chevron", -3, -3, 6, HudTheme.text());
		pose.popMatrix();
	}


	private static ItemStack stackOf(String itemId) {
		ItemRepo.Entry item = ItemRepo.get(itemId);
		if (item != null) {
			return item.stack();
		}
		return new ItemStack(itemId != null && itemId.equals(COIN) ? Items.GOLD_NUGGET : Items.BARRIER);
	}

	/** @return a compact but clear price: 856, 1.4k, 12.5k, 4.75M, 1.2B */
	static String coins(double value) {
		if (value < 1_000) {
			return String.valueOf(Math.round(value));
		}
		String[] units = {"k", "M", "B"};
		double shown = value;
		int unit = -1;
		while (shown >= 1_000 && unit < units.length - 1) {
			shown /= 1_000;
			unit++;
		}
		String number = shown < 10 ? String.format(Locale.ROOT, "%.2f", shown) : shown < 100 ? String.format(Locale.ROOT, "%.1f", shown)
			: String.valueOf(Math.round(shown));
		return Draw.trimZeros(number) + units[unit];
	}

	// --------------------------------------------------------------------------------------------------- input

	/** @return whether a click was on the window or the inventory's place; a click elsewhere is for the caller */
	boolean click(double mouseX, double mouseY) {
		if (id == null || !contains(mouseX, mouseY)) {
			return false;
		}
		if (PanelKit.inside(backButton, mouseX, mouseY)) {
			back();
			return true;
		}
		if (PanelKit.inside(pinButton, mouseX, mouseY)) {
			feature.toggleFavorite(id);
			return true;
		}
		if (PanelKit.inside(closeButton, mouseX, mouseY)) {
			close();
			return true;
		}
		for (int i = 0; i < tabBoxes.size(); i++) {
			if (PanelKit.inside(tabBoxes.get(i), mouseX, mouseY)) {
				tab = i;
				page = 0;
				refreshedAt = 0L;
				return true;
			}
		}
		if (PanelKit.inside(prevPage, mouseX, mouseY) || PanelKit.inside(nextPage, mouseX, mouseY)) {
			page = ItemTabs.stepped(page, PanelKit.inside(prevPage, mouseX, mouseY) ? -1 : 1, pages);
			refreshedAt = 0L;
			return true;
		}
		if (PanelKit.inside(craftButton, mouseX, mouseY)) {
			feature.quickCraft(id);
			return true;
		}
		if (PanelKit.inside(findButton, mouseX, mouseY)) {
			feature.findInStorage(id);
			return true;
		}
		if (PanelKit.inside(sourceLink, mouseX, mouseY)) {
			ItemRepo.Recipe shown = shownRecipe();
			if (shown != null) {
				open(shown.sourceId());
			}
			return true;
		}
		for (int i = 0; i < hitBoxes.size(); i++) {
			if (PanelKit.inside(hitBoxes.get(i), mouseX, mouseY) && !hitIds.get(i).equals(id)) {
				open(hitIds.get(i));
				return true;
			}
		}
		return true;
	}
}
