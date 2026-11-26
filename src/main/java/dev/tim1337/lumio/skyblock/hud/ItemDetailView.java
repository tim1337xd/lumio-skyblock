package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.data.ItemHoldings;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An item's own window: next to what was clicked, docked as a panel of its own, or (without room for either) in the inventory's place, whose slots
 * are then away. At the top the item with its name in its rarity color, its rarity and type, back, pin and close, and one number below: the lowest
 * BIN (or the Bazaar's buy price). Then tabs: Recipe and the other ways to make it (forge, shops, trades, Kat), Market, Drops (who drops it) and
 * what it is used in; a mob has its drops, an NPC what it sells. It opens on Recipe when there is one, else on its first tab. At the bottom the
 * action bar: Quick craft, the market and Wiki.
 * <p>
 * An entry clicked in the window opens in the same window, at the same place; Back, Esc and Backspace go back step by step to the inventory. A tab
 * without data is left out. Everything shown is worked out when the item opens and again once a second, never per frame. Quick craft only opens
 * SkyBlock's own crafting menu for the item, one command per click; it never moves, crafts or clicks anything itself.
 */
final class ItemDetailView {
	private static final int PAD = 10;
	private static final int HEADER = 22;
	private static final int ICON_TILE = 22;
	private static final int BUTTON = 13;
	private static final int BUTTON_ICON = 7;
	private static final int TOP_GAP = 6;
	private static final int PRICE_ROW = 13;
	private static final int TABS = 15;
	private static final int TAB_GAP = 10;
	private static final int TAB_PAD = 2;
	private static final float TAB_LINE = 1.5f;
	private static final int BODY_GAP = 7;
	private static final int ARROW = 10;
	private static final float TEXT_HALF = 3.5f;
	private static final float BODY_HALF = 4f;
	private static final float RADIUS = 10f;
	private static final float OPEN_SECONDS = 0.15f;
	private static final long REFRESH_MILLIS = 1000L;
	private static final int MOST_BACK = 30;
	private static final int COVER = 0xFF12131A;
	private static final float COVER_OPACITY = 0.94f;
	private static final float ICON_GLOW = 0.4f;
	private static final float ICON_FILL = 0.12f;
	private static final float LABEL_OPACITY = 0.85f;
	private static final float EDGE_OPACITY = 0.1f;
	private static final float BUTTON_FILL = 0.08f;
	private static final float BUTTON_HOVER = 0.16f;
	private static final String NOTHING = "Nothing is known of how to get this";
	/** A name in two rows starts this far above the header's top, and its rows are this far apart. */
	private static final float TITLE_TWO_ROWS_TOP = -2f;
	private static final float TITLE_ROW = 8.5f;
	/** A name that does not fit is first set this much smaller. */
	static final float TITLE_SHRINK = 0.85f;

	/** What Back returns to. */
	private record View(String id, ItemTabs.Kind tab) {
	}

	/** A window title as it is set: one row, or two, and how large (a share of the usual size). */
	record Title(String first, String second, float scale) {
	}

	private final ItemListFeature feature;
	private final Deque<View> back = new ArrayDeque<>();
	private final Spring opening = Spring.smooth(OPEN_SECONDS);
	private final ItemWindowHits hits = new ItemWindowHits();
	private final ItemWindowMarket market = new ItemWindowMarket();
	private final ItemWindowActions actions = new ItemWindowActions();
	private final List<int[]> tabBoxes = new ArrayList<>();
	private String id;
	private ItemTabs.Kind wantedTab;
	/** Whether the item was just opened: it then opens on its first tab (Recipe when it has one). */
	private boolean fresh;
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
	private final List<ItemWindowBodies.Need> needs = new ArrayList<>();
	private final List<String> usedIn = new ArrayList<>();
	private final List<ItemWindowBodies.Row> rows = new ArrayList<>();
	private String rowsNote = "";
	private int missingCount;
	private String wiki = "";
	// last drawn boxes, for clicks
	private final int[] backButton = new int[4];
	private final int[] pinButton = new int[4];
	private final int[] closeButton = new int[4];
	private final int[] prevPage = new int[4];
	private final int[] nextPage = new int[4];
	private final int[] sourceLink = new int[4];

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
		fresh = true;
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
		wiki = WikiLink.of(entry == null ? ItemWindowBodies.nameOf(id) : entry.name, source);
		readUsedIn();
		List<ItemRepo.Recipe> making = ItemRepo.recipesFor(id);
		market.read(id, source ? 0 : craftCost(firstCrafting(making)));
		tabs = ItemTabs.of(making, usedIn.size(), ItemRepo.ofSource(id), !source && market.hasContent());
		if (fresh) {
			tab = ItemTabs.opening(tabs);
			fresh = false;
		}
		if (wantedTab != null) {
			int wanted = ItemTabs.indexOf(tabs, wantedTab);
			tab = wanted >= 0 ? wanted : tab;
			wantedTab = null;
		}
		tab = tabs.isEmpty() ? 0 : Math.min(tab, tabs.size() - 1);
		readRows();
		ItemRepo.Recipe shown = shownRecipe();
		readNeeds(shown != null ? shown : firstCrafting(making));
	}

	private static ItemRepo.Recipe firstCrafting(List<ItemRepo.Recipe> making) {
		for (ItemRepo.Recipe recipe : making) {
			if (recipe.crafting()) {
				return recipe;
			}
		}
		return null;
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

	/** @return whether the open tab shows recipes (Recipe, Forge, Shop, Trades, Kat) */
	private boolean recipeTab() {
		ItemTabs.Kind kind = currentKind();
		return kind == ItemTabs.Kind.RECIPE || kind == ItemTabs.Kind.FORGE || kind == ItemTabs.Kind.SHOP || kind == ItemTabs.Kind.TRADE
			|| kind == ItemTabs.Kind.KAT;
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
						rows.add(new ItemWindowBodies.Row(drop.id(), ItemWindowBodies.nameOf(drop.id()) + times(drop.count()), drop.note(), drop.id()));
					}
				}
			}
		} else if (kind == ItemTabs.Kind.SELLS) {
			for (ItemRepo.Recipe recipe : shownRecipes()) {
				ItemRepo.Stack sold = recipe.outputs().getFirst();
				rows.add(new ItemWindowBodies.Row(sold.id(), ItemWindowBodies.nameOf(sold.id()) + times(sold.count()), costOf(recipe), sold.id()));
			}
		} else if (kind == ItemTabs.Kind.DROPPED_BY) {
			Set<String> seen = new LinkedHashSet<>();
			for (ItemRepo.Recipe recipe : shownRecipes()) {
				if (seen.add(recipe.sourceId().isEmpty() ? recipe.source() : recipe.sourceId())) {
					rows.add(new ItemWindowBodies.Row(recipe.sourceId(), recipe.source(), chanceHere(recipe), recipe.sourceId()));
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
			cost.append(input.id().equals(ItemWindowBodies.COIN) ? coins(input.count()) + " coins" : ItemWindowBodies.nameOf(input.id()) + times(input.count()));
		}
		return cost.toString();
	}

	private static String times(double count) {
		return count > 1 ? " ×" + PanelKit.count(Math.round(count)) : "";
	}

	/** @return the recipe the open tab shows now, or null */
	private ItemRepo.Recipe shownRecipe() {
		List<ItemRepo.Recipe> recipes = shownRecipes();
		if (recipes.isEmpty() || !recipeTab()) {
			return null;
		}
		page = Math.floorMod(page, recipes.size());
		return recipes.get(page);
	}

	/** The ingredients of a recipe, added up over its slots, with how many the player has. */
	private void readNeeds(ItemRepo.Recipe recipe) {
		needs.clear();
		missingCount = 0;
		if (recipe == null) {
			return;
		}
		Map<String, Long> wanted = new LinkedHashMap<>();
		for (ItemRepo.Stack stack : recipe.inputs()) {
			if (stack != null && !stack.id().equals(ItemWindowBodies.COIN)) {
				wanted.merge(stack.id(), Math.round(stack.count()), Long::sum);
			}
		}
		for (Map.Entry<String, Long> want : wanted.entrySet()) {
			ItemRepo.Entry ingredient = ItemRepo.get(want.getKey());
			int color = ingredient == null ? HudTheme.text() : ingredient.rarityColor();
			ItemWindowBodies.Need need = new ItemWindowBodies.Need(want.getKey(), ItemWindowBodies.nameOf(want.getKey()), color, want.getValue(),
				ItemHoldings.count(want.getKey()));
			needs.add(need);
			if (!need.enough()) {
				missingCount++;
			}
		}
	}

	/** @return what a recipe costs in coins at the prices known, 0 without one */
	private static double craftCost(ItemRepo.Recipe recipe) {
		if (recipe == null) {
			return 0;
		}
		double cost = 0;
		for (ItemRepo.Stack stack : recipe.inputs()) {
			if (stack != null) {
				cost += stack.id().equals(ItemWindowBodies.COIN) ? stack.count() : SkyBlockPrices.price(stack.id()) * stack.count();
			}
		}
		return cost;
	}

	// --------------------------------------------------------------------------------------------------- drawing

	/**
	 * Draws the window: beside what was clicked, docked in its own panel, or in the inventory's place when there is no room for it elsewhere.
	 *
	 * @param where    the window's box
	 * @param coverGui whether it takes the inventory's place: the inventory is covered (its slots are away meanwhile)
	 * @param gui      the inventory or chest window
	 * @return the item under the mouse (an ingredient, a drop, a use), or null
	 */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, int[] where, boolean coverGui, int[] gui, float dt, int mouseX, int mouseY) {
		if (id == null) {
			return null;
		}
		refresh();
		hits.clear();
		tabBoxes.clear();
		System.arraycopy(where, 0, box, 0, 4);
		if (coverGui) {
			System.arraycopy(gui, 0, covered, 0, 4);
		} else {
			Arrays.fill(covered, 0);
		}
		float open = Mth.clamp(opening.step(1f, dt), 0f, 1f);
		float alphaBefore = Draw.alpha();
		graphics.nextStratum();
		if (coverGui) {
			// the inventory's own background stays under it: it is covered at once, the window itself fades in
			ui.rounded(gui[0], gui[1], gui[2], gui[3], Radius.XL, COVER);
		}
		ui.rounded(box[0], box[1], box[2], box[3], RADIUS, Draw.withOpacity(COVER, COVER_OPACITY));
		Draw.setAlpha(alphaBefore * open);
		try {
			HudTheme.panel(ui, box[0], box[1], box[2], box[3], RADIUS, ItemSearchLook.opacity(), feature.glow());
			ui.ring(box[0], box[1], box[2], box[3], RADIUS, 1, Draw.withOpacity(HudTheme.text(), EDGE_OPACITY));
			int left = box[0] + PAD;
			int width = box[2] - 2 * PAD;
			int actionsTop = box[1] + box[3] - PAD - ItemWindowActions.HEIGHT;
			int top = drawHeader(ui, graphics, left, width, box[1] + PAD, mouseX, mouseY);
			if (!source && market.topValue() > 0) {
				top = drawPrice(ui, left, top + TOP_GAP);
			}
			Arrays.fill(prevPage, 0);
			Arrays.fill(nextPage, 0);
			Arrays.fill(sourceLink, 0);
			market.hide();
			top += TOP_GAP;
			if (tabs.isEmpty()) {
				ui.textAtScaled(ui.fitScaled(NOTHING, width, TextScale.SMALL), left, top + 4f, TextScale.SMALL, HudTheme.secondary());
			} else {
				top = drawTabs(ui, left, width, top, mouseX, mouseY);
				drawBody(ui, graphics, left, width, top + BODY_GAP, actionsTop - BODY_GAP, mouseX, mouseY);
			}
			actions.draw(ui, left, width, actionsTop, !source, feature.canQuickCraft(id), missingCount, source ? MarketSearch.Market.NONE : market.market(),
				!wiki.isEmpty(), mouseX, mouseY);
		} finally {
			Draw.setAlpha(alphaBefore);
		}
		String under = hits.at(mouseX, mouseY);
		return under == null ? null : ItemRepo.get(under);
	}

	/** The item on a soft glow in its rarity color, its name, rarity and type; back, pin and close. @return the y below it */
	private int drawHeader(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int y, int mouseX, int mouseY) {
		int rarity = entry == null ? HudTheme.text() : entry.rarityColor();
		int x = left;
		place(backButton, x, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON);
		iconButton(ui, backButton, "chevron", true, mouseX, mouseY);
		x += BUTTON + 4;
		ui.softLight(x + ICON_TILE / 2f, y + ICON_TILE / 2f, ICON_TILE * 0.7f, Draw.withOpacity(rarity, ICON_GLOW));
		ui.rounded(x, y, ICON_TILE, ICON_TILE, Radius.MD, Draw.withOpacity(rarity, ICON_FILL));
		ItemTiles.item(graphics, ItemWindowBodies.stackOf(id), x, y, ICON_TILE);
		int textX = x + ICON_TILE + 7;
		int buttonsLeft = left + width - 2 * BUTTON - 4;
		place(pinButton, buttonsLeft, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON);
		place(closeButton, buttonsLeft + BUTTON + 4, y + (HEADER - BUTTON) / 2, BUTTON, BUTTON);
		iconButton(ui, pinButton, "pin", false, mouseX, mouseY);
		if (feature.isPinned(id)) {
			ui.ring(pinButton[0], pinButton[1], BUTTON, BUTTON, Radius.SM, 1, ui.accent());
		}
		iconButton(ui, closeButton, "close", false, mouseX, mouseY);
		float room = buttonsLeft - 6 - textX;
		// the name whole: on one row, a little smaller, or on two rows, before anything of it is cut; measured at both sizes, as text is not drawn
		// exactly in proportion
		Title title = title(ItemWindowBodies.nameOf(id), room, text -> Math.max(ui.boldWidthScaled(text, TextScale.BODY),
			ui.boldWidthScaled(text, TextScale.BODY * TITLE_SHRINK) / TITLE_SHRINK));
		float size = TextScale.BODY * title.scale();
		boolean two = title.second() != null;
		ui.textAtScaledBold(fitBold(ui, title.first(), room, size), textX, y + (two ? TITLE_TWO_ROWS_TOP : 1f), size, HudTheme.readable(rarity));
		if (two) {
			ui.textAtScaledBold(fitBold(ui, title.second(), room, size), textX, y + TITLE_TWO_ROWS_TOP + TITLE_ROW, size, HudTheme.readable(rarity));
		}
		String label = info == null ? "" : info.label();
		if (!label.isEmpty()) {
			ui.textAtScaledBold(ui.fitScaled(label.toUpperCase(Locale.ROOT), room, TextScale.SMALL), textX, y + (two ? TITLE_TWO_ROWS_TOP + 2 * TITLE_ROW : 12f),
				TextScale.SMALL, Draw.withOpacity(HudTheme.readable(rarity), LABEL_OPACITY));
		}
		return y + HEADER;
	}

	private static void place(int[] target, int x, int y, int width, int height) {
		target[0] = x;
		target[1] = y;
		target[2] = width;
		target[3] = height;
	}

	/** The one number at the top: the lowest BIN large in gold, with its label. @return the y below it */
	private int drawPrice(Canvas ui, int left, int y) {
		String big = coins(market.topValue());
		ui.textAtScaledBold(big, left, y, TextScale.HEADING, ItemWindowMarket.GOLD);
		float bigWidth = ui.boldWidthScaled(big, TextScale.HEADING);
		ui.textAtScaled(market.topLabel(), left + bigWidth + 5, y + 3f, TextScale.SMALL, HudTheme.secondary());
		return y + PRICE_ROW;
	}

	/**
	 * Fits a name into the title's room without cutting it: at full size when it fits; else a little smaller; else in two rows, broken at the
	 * space that leaves the rows most alike. Only a name too long even for that (or one long word) is left to be cut by the caller.
	 *
	 * @param width the width of a text at the title's full size
	 */
	static Title title(String name, float room, java.util.function.ToDoubleFunction<String> width) {
		if (width.applyAsDouble(name) <= room) {
			return new Title(name, null, 1f);
		}
		if (width.applyAsDouble(name) * TITLE_SHRINK <= room) {
			return new Title(name, null, TITLE_SHRINK);
		}
		int best = -1;
		double bestWidth = Double.MAX_VALUE;
		for (int i = name.indexOf(' '); i > 0; i = name.indexOf(' ', i + 1)) {
			double widest = Math.max(width.applyAsDouble(name.substring(0, i)), width.applyAsDouble(name.substring(i + 1)));
			if (widest < bestWidth) {
				bestWidth = widest;
				best = i;
			}
		}
		return best < 0 ? new Title(name, null, TITLE_SHRINK) : new Title(name.substring(0, best), name.substring(best + 1), TITLE_SHRINK);
	}

	/** @return a bold text as it is, or cut with "…" only when it is wider than the room at its size */
	private static String fitBold(Canvas ui, String text, float room, float size) {
		return ui.boldWidthScaled(text, size) <= room ? text : ui.fitScaled(text, room * 0.94f, size);
	}

	private static void iconButton(Canvas ui, int[] button, String icon, boolean pointsBack, int mouseX, int mouseY) {
		boolean over = PanelKit.inside(button, mouseX, mouseY);
		ui.rounded(button[0], button[1], button[2], button[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? BUTTON_HOVER : BUTTON_FILL));
		if (pointsBack) {
			chevron(ui, button, (float) (Math.PI / 2.0));
		} else {
			ui.icon(icon, button[0] + (button[2] - BUTTON_ICON) / 2, button[1] + (button[3] - BUTTON_ICON) / 2, BUTTON_ICON, HudTheme.text());
		}
	}

	private static void chevron(Canvas ui, int[] box, float turn) {
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(box[0] + box[2] / 2f, box[1] + box[3] / 2f);
		pose.rotate(turn);
		ui.icon("chevron", -3, -3, 6, HudTheme.text());
		pose.popMatrix();
	}

	/** The tabs as a row of words with a line under the open one and a hairline under the row; smaller where they do not fit. @return the y below them */
	private int drawTabs(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		float wide = 0f;
		for (ItemTabs.Tab each : tabs) {
			wide += ui.boldWidthScaled(each.kind().label, 1f);
		}
		float room = width - (tabs.size() - 1f) * TAB_GAP - pageRoom(ui);
		float scale = Math.min(TextScale.BODY, room / Math.max(1f, wide));
		float textY = y + TABS / 2f - BODY_HALF * scale;
		float x = left;
		for (int i = 0; i < tabs.size(); i++) {
			String label = tabs.get(i).kind().label;
			float labelWidth = ui.boldWidthScaled(label, scale);
			int[] hit = {Math.round(x) - TAB_PAD, y, Math.round(labelWidth) + 2 * TAB_PAD, TABS};
			tabBoxes.add(hit);
			boolean chosen = i == tab;
			boolean over = PanelKit.inside(hit, mouseX, mouseY);
			if (chosen) {
				ui.textAtScaledBold(label, x, textY, scale, HudTheme.text());
				ui.roundedSmooth(x - TAB_PAD, y + TABS - TAB_LINE, labelWidth + 2 * TAB_PAD, TAB_LINE, TAB_LINE / 2f, ui.accent());
			} else {
				ui.textAtScaled(label, x, textY, scale, over ? HudTheme.text() : HudTheme.secondary());
			}
			x += labelWidth + TAB_GAP;
		}
		ui.fill(left, y + TABS, width, 1, HudTheme.line(EDGE_OPACITY));
		drawPageArrows(ui, left, width, y, mouseX, mouseY);
		return y + TABS + 1;
	}

	/** @return the room the page arrows take at the right of the tabs' row */
	private float pageRoom(Canvas ui) {
		return pages > 1 ? 2f * ARROW + ui.widthScaled("00 / 00", TextScale.SMALL) + 12f : 0f;
	}

	/** The arrows and "1 / 2" at the right of the tabs' row, when there are several pages. */
	private void drawPageArrows(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		if (pages <= 1) {
			return;
		}
		String counter = (page + 1) + " / " + pages;
		float counterWidth = ui.widthScaled(counter, TextScale.SMALL);
		float from = left + width - 2f * ARROW - counterWidth - 4f;
		int arrowY = y + (TABS - ARROW) / 2;
		place(prevPage, Math.round(from), arrowY, ARROW, ARROW);
		place(nextPage, Math.round(from + ARROW + counterWidth + 4f), arrowY, ARROW, ARROW);
		arrow(ui, prevPage, true, mouseX, mouseY);
		ui.textAtScaled(counter, from + ARROW + 2f, y + TABS / 2f - TEXT_HALF, TextScale.SMALL, HudTheme.secondary());
		arrow(ui, nextPage, false, mouseX, mouseY);
	}

	private static void arrow(Canvas ui, int[] box, boolean previous, int mouseX, int mouseY) {
		boolean over = PanelKit.inside(box, mouseX, mouseY);
		ui.rounded(box[0], box[1], box[2], box[3], Radius.XS, Draw.withOpacity(HudTheme.text(), over ? BUTTON_HOVER : BUTTON_FILL));
		chevron(ui, box, (float) (previous ? Math.PI / 2.0 : -Math.PI / 2.0));
	}

	private void drawBody(Canvas ui, GuiGraphicsExtractor graphics, int left, int width, int top, int bottom, int mouseX, int mouseY) {
		ItemTabs.Kind kind = currentKind();
		if (kind == ItemTabs.Kind.MARKET) {
			pages = 1;
			market.draw(ui, left, width, top, bottom, mouseX, mouseY);
		} else if (kind == ItemTabs.Kind.USED_IN) {
			pages = ItemWindowBodies.usedIn(ui, graphics, usedIn, page, left, width, top, bottom, hits, mouseX, mouseY);
		} else if (recipeTab()) {
			pages = shownRecipes().size();
			ItemRepo.Recipe shown = shownRecipe();
			if (shown != null) {
				ItemWindowBodies.recipe(ui, graphics, shown, needs, left, width, top, bottom, hits, sourceLink, mouseX, mouseY);
			}
		} else {
			pages = ItemWindowBodies.rows(ui, graphics, rows, rowsNote, page, left, width, top, bottom, hits, mouseX, mouseY);
		}
		page = Math.floorMod(page, Math.max(1, pages));
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

	/** @return whether the window is in the inventory's place now */
	boolean coversGui() {
		return id != null && covered[2] > 0;
	}

	/** @return whether a point is on one of the window's own controls (back, pin, close, tabs, arrows, the source, the market, the action bar), not on an item */
	boolean onControl(double mouseX, double mouseY) {
		for (int[] control : new int[][]{backButton, pinButton, closeButton, prevPage, nextPage, sourceLink}) {
			if (PanelKit.inside(control, mouseX, mouseY)) {
				return true;
			}
		}
		for (int[] tabBox : tabBoxes) {
			if (PanelKit.inside(tabBox, mouseX, mouseY)) {
				return true;
			}
		}
		return market.onMarket(mouseX, mouseY) || market.onLink(mouseX, mouseY) || actions.at(mouseX, mouseY) != ItemWindowActions.Action.NONE;
	}

	/** @return whether a click was on the window or the inventory's place; a click elsewhere is for the caller */
	boolean click(double mouseX, double mouseY) {
		if (id == null || !contains(mouseX, mouseY)) {
			return false;
		}
		if (PanelKit.inside(backButton, mouseX, mouseY)) {
			back();
		} else if (PanelKit.inside(pinButton, mouseX, mouseY)) {
			feature.togglePin(id);
		} else if (PanelKit.inside(closeButton, mouseX, mouseY)) {
			close();
		} else if (PanelKit.inside(prevPage, mouseX, mouseY) || PanelKit.inside(nextPage, mouseX, mouseY)) {
			page = ItemTabs.stepped(page, PanelKit.inside(prevPage, mouseX, mouseY) ? -1 : 1, pages);
			refreshedAt = 0L;
		} else if (market.onMarket(mouseX, mouseY)) {
			feature.openMarket(id);
		} else if (market.onLink(mouseX, mouseY)) {
			market.openLink();
		} else if (PanelKit.inside(sourceLink, mouseX, mouseY)) {
			ItemRepo.Recipe shown = shownRecipe();
			if (shown != null) {
				open(shown.sourceId());
			}
		} else if (!clickTab(mouseX, mouseY) && !clickAction(mouseX, mouseY)) {
			String clicked = hits.at(mouseX, mouseY);
			if (clicked != null && !clicked.equals(id)) {
				open(clicked);
			}
		}
		return true;
	}

	private boolean clickTab(double mouseX, double mouseY) {
		for (int i = 0; i < tabBoxes.size(); i++) {
			if (PanelKit.inside(tabBoxes.get(i), mouseX, mouseY)) {
				tab = i;
				page = 0;
				refreshedAt = 0L;
				return true;
			}
		}
		return false;
	}

	private boolean clickAction(double mouseX, double mouseY) {
		switch (actions.at(mouseX, mouseY)) {
			case CRAFT -> feature.quickCraft(id);
			case MARKET -> feature.openMarket(id);
			case WIKI -> ItemWindowActions.openLink(wiki);
			case NONE -> {
				return false;
			}
		}
		return true;
	}
}
