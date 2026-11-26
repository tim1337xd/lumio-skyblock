package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.PriceHistory;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Pinned panel, the player's own shelf: groups as small boxes at the top (All, their own, and +), then the pinned items as a list (icon, name in
 * its rarity color, its price and the trend of the last day) or a grid (the icon with its price under it). While an item is dragged over it, it shows
 * where the item lands: a line in the list, or the group under the mouse. Right-click on a group renames or deletes it.
 */
final class PinnedPanel {
	/** The views. */
	enum View {
		LIST("List"),
		GRID("Grid");

		final String label;

		View(String label) {
			this.label = label;
		}

		Component displayName() {
			return Component.literal(label);
		}
	}

	/** How much of the header the tab of the Items panel takes over. */
	static final int HEADER_IN_TAB = 0;
	private static final int PIN_COLOR = 0xFFFFC93C;
	private static final int ROW = 23;
	private static final int ROW_TILE = 18;
	private static final int GROUP_GAP = 4;
	private static final int GROUPS_ROW = 18;
	private static final int VIEW_BUTTON = 11;
	private static final int VIEW_ICON = 7;
	private static final String[] VIEW_ICONS = {"list", "grid"};
	private static final int DROP_LINE = 2;
	private static final float SCROLL_RATE = 14f;
	/** How often the shown pinned items' prices are noted for their trend. */
	private static final long NOTE_MILLIS = 60_000L;
	private static final String ADD_GROUP = "+";
	/** The group menu's rows. */
	private static final String[] MENU = {"Rename", "Delete"};
	private static final int MENU_ROW = 13;
	private static final int MENU_WIDTH = 62;

	private final ItemListFeature feature;
	private String group = PinnedItems.ALL;
	private final List<String> shownIds = new ArrayList<>();
	private final List<int[]> itemBoxes = new ArrayList<>();
	private final List<String> groupNames = new ArrayList<>();
	private final List<int[]> groupBoxes = new ArrayList<>();
	private final int[] addBox = new int[4];
	private final int[][] viewBoxes = new int[2][4];
	private final int[] content = new int[4];
	private final int[] hoveredTile = new int[4];
	/** The header's count, made again only when it changes. */
	private int countFor = -1;
	private String count = "";
	private float scroll;
	private float scrollTarget;
	private long notedAt;
	/** The group whose menu is open, where, and the group being renamed with what is typed so far. */
	private String menuFor;
	private final int[] menuBox = new int[4];
	private String renaming;
	private String typed = "";
	/** Where a dragged item would land: before this item (null for the end) or into this group (null for none); set by the panels' controller. */
	private boolean dropping;
	private String dropBefore;
	private String dropGroup;

	/** The hovered row's market button: its box (no width while none is shown), the item it opens, and its size in GUI pixels. */
	private final int[] marketBox = new int[4];
	private String marketId;
	private static final int MARKET_BUTTON = 10;

	PinnedPanel(ItemListFeature feature) {
		this.feature = feature;
	}

	String group() {
		return group;
	}

	int[] hoveredTile() {
		return hoveredTile;
	}

	/** @return whether a group's name is being typed: keys go to it */
	boolean renaming() {
		return renaming != null;
	}

	/** Draws the whole panel with its frame and header. @return the item under the mouse, or null */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, float highlight, boolean lifted, float dt, int mouseX, int mouseY) {
		PinnedItems shelf = feature.pinned();
		boolean collapsed = feature.collapsed(ItemPanelKind.PINNED);
		if (countFor != shelf.size()) {
			countFor = shelf.size();
			count = String.valueOf(countFor);
		}
		PanelFrame.draw(ui, box, "pin", PIN_COLOR, "Pinned", count, Math.max(highlight, dropping ? 1f : 0f), lifted, !collapsed);
		PanelFrame.collapseArrow(ui, box, collapsed);
		// the view's buttons show only while the mouse is over the open panel: at rest the header stays calm
		viewBoxes[0][2] = 0;
		viewBoxes[1][2] = 0;
		if (collapsed) {
			hoveredTile[2] = 0;
			return null;
		}
		if (PanelKit.inside(box, mouseX, mouseY)) {
			drawViewToggle(ui, box, mouseX, mouseY);
		}
		return drawContent(ui, graphics, box, dt, mouseX, mouseY);
	}

	/** Draws the groups and the items in a box below a header (its own, or the Items panel's when it is a tab there). */
	ItemRepo.Entry drawContent(Canvas ui, GuiGraphicsExtractor graphics, int[] box, float dt, int mouseX, int mouseY) {
		PinnedItems shelf = feature.pinned();
		if (!PinnedItems.ALL.equals(group) && !shelf.groups().contains(group)) {
			group = PinnedItems.ALL;
		}
		int left = box[0] + PanelFrame.PAD;
		int width = box[2] - 2 * PanelFrame.PAD;
		int y = box[1] + PanelFrame.HEADER;
		drawGroups(ui, shelf, left, width, y, mouseX, mouseY);
		content[0] = left;
		content[1] = y + GROUPS_ROW + 2;
		content[2] = width;
		content[3] = box[1] + box[3] - PanelFrame.PAD - content[1];
		shownIds.clear();
		shownIds.addAll(shelf.ids(group));
		notePrices();
		hoveredTile[2] = 0;
		if (shownIds.isEmpty()) {
			String empty = shelf.isEmpty() ? "Drag items here, or press the Pin key over one" : "Nothing in this group yet";
			ui.textAtScaled(ui.fitScaled(empty, width, TextScale.SMALL), left, content[1] + 4, TextScale.SMALL, HudTheme.secondary());
			PanelFrame.noteContent(content[1] + PanelFrame.TEXT_LINE);
			itemBoxes.clear();
			drawMenu(ui, mouseX, mouseY);
			return null;
		}
		ItemRepo.Entry hovered = feature.pinnedView() == View.LIST ? drawList(ui, graphics, dt, mouseX, mouseY) : drawGrid(ui, graphics, dt, mouseX, mouseY);
		drawMenu(ui, mouseX, mouseY);
		return hovered;
	}

	/** Notes the shown items' prices now and then, so their trend of the last day can be told later. */
	private void notePrices() {
		long now = System.currentTimeMillis();
		if (!feature.showTrend() || now - notedAt < NOTE_MILLIS) {
			return;
		}
		notedAt = now;
		for (String id : shownIds) {
			String priced = SkyBlockPrices.priceIdOfListed(id);
			PriceHistory.note(priced, SkyBlockPrices.listedPrice(priced));
		}
	}

	private void drawViewToggle(Canvas ui, int[] box, int mouseX, int mouseY) {
		int x = PanelFrame.controlsRight(box, true) - 2 * VIEW_BUTTON - 2;
		int y = PanelFrame.middle(box) - VIEW_BUTTON / 2;
		ui.rounded(x - 1, y - 1, 2 * VIEW_BUTTON + 4, VIEW_BUTTON + 2, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.06f));
		for (int i = 0; i < 2; i++) {
			int[] button = viewBoxes[i];
			button[0] = x + i * (VIEW_BUTTON + 2);
			button[1] = y;
			button[2] = VIEW_BUTTON;
			button[3] = VIEW_BUTTON;
			boolean on = feature.pinnedView().ordinal() == i;
			boolean over = PanelKit.inside(button, mouseX, mouseY);
			if (on || over) {
				ui.rounded(button[0], button[1], VIEW_BUTTON, VIEW_BUTTON, Radius.XS, Draw.withOpacity(HudTheme.text(), on ? 0.16f : 0.08f));
			}
			ui.icon(VIEW_ICONS[i], button[0] + (VIEW_BUTTON - VIEW_ICON) / 2, button[1] + (VIEW_BUTTON - VIEW_ICON) / 2, VIEW_ICON, on ? HudTheme.text() : HudTheme.secondary());
		}
	}

	/** The groups as small boxes with their counts: All, the player's own, and + to add one. */
	private void drawGroups(Canvas ui, PinnedItems shelf, int left, int width, int y, int mouseX, int mouseY) {
		groupNames.clear();
		groupBoxes.clear();
		float x = left;
		x = drawGroup(ui, PinnedItems.ALL, "All", shelf.count(PinnedItems.ALL), x, y, mouseX, mouseY);
		for (String name : shelf.groups()) {
			if (x > left + width - 20) {
				break;
			}
			x = drawGroup(ui, name, name, shelf.count(name), x, y, mouseX, mouseY);
		}
		addBox[2] = 0;
		if (shelf.groups().size() < PinnedItems.MOST_GROUPS && x + 16 <= left + width) {
			addBox[0] = Math.round(x);
			addBox[1] = y;
			addBox[2] = 16;
			addBox[3] = PanelKit.CHIP_HEIGHT;
			PanelKit.chip(ui, addBox, ADD_GROUP, false, mouseX, mouseY);
		}
	}

	private float drawGroup(Canvas ui, String name, String label, int count, float x, int y, int mouseX, int mouseY) {
		boolean editing = name.equals(renaming);
		String shown = editing ? typed + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : " ") : label + "  " + count;
		int width = PanelKit.chipWidth(ui, shown);
		int[] chip = {Math.round(x), y, width, PanelKit.CHIP_HEIGHT};
		boolean target = dropping && name.equals(dropGroup);
		PanelKit.chip(ui, chip, shown, group.equals(name) || editing, mouseX, mouseY);
		if (target || editing) {
			ui.ring(chip[0], chip[1], chip[2], chip[3], Radius.SM, 1, ui.accent());
		}
		groupNames.add(name);
		groupBoxes.add(chip);
		return x + width + GROUP_GAP;
	}

	/** The list: a row per item with its icon, name, where the price is from, the price and the trend. */
	private ItemRepo.Entry drawList(Canvas ui, GuiGraphicsExtractor graphics, float dt, int mouseX, int mouseY) {
		int total = shownIds.size() * ROW;
		scrollTo(total, dt);
		ItemRepo.Entry hovered = null;
		itemBoxes.clear();
		marketBox[2] = 0;
		marketId = null;
		ui.graphics().enableScissor(content[0] - 2, content[1], content[0] + content[2] + 2, content[1] + content[3]);
		try {
			for (int i = 0; i < shownIds.size(); i++) {
				int y = Math.round(content[1] + i * ROW - scroll);
				int[] row = {content[0], y, content[2], ROW - 2};
				itemBoxes.add(row);
				if (y + ROW < content[1] || y > content[1] + content[3]) {
					continue;
				}
				String id = shownIds.get(i);
				ItemRepo.Entry entry = ItemRepo.get(id);
				boolean over = PanelKit.inside(row, mouseX, mouseY) && mouseY >= content[1] && mouseY < content[1] + content[3];
				if (over) {
					ui.rounded(row[0] - 3, row[1] - 1, row[2] + 6, row[3] + 2, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.07f));
				}
				if (entry != null) {
					graphics.nextStratum();
					ItemTiles.draw(ui, graphics, entry, row[0], y + (ROW - 2 - ROW_TILE) / 2, ROW_TILE, over ? 1f : 0f, feature.lights(entry) ? 1f : 0f);
					PanelFrame.noteContent(y + ROW);
					if (over) {
						hovered = entry;
						setHovered(row[0], y + (ROW - 2 - ROW_TILE) / 2, ROW_TILE);
					}
				}
				boolean marketButton = over && MarketSearch.of(id) != MarketSearch.Market.NONE;
				drawRowText(ui, id, entry, row, y, !marketButton);
				if (marketButton) {
					drawMarketButton(ui, id, row, y, mouseX, mouseY);
				}
				drawDropLine(ui, id, row);
			}
			if (dropping && dropBefore == null && dropGroup == null) {
				int y = Math.round(content[1] + shownIds.size() * ROW - scroll) - 2;
				ui.rounded(content[0], y, content[2], DROP_LINE, Radius.XXS, ui.accent());
			}
		} finally {
			ui.graphics().disableScissor();
		}
		return hovered;
	}

	/** The hovered row's market button, where its trend is: the coins, opening the item's market with its name searched. */
	private void drawMarketButton(Canvas ui, String id, int[] row, int y, int mouseX, int mouseY) {
		int x = row[0] + row[2] - MARKET_BUTTON;
		int top = y + ROW - 2 - MARKET_BUTTON - 1;
		marketBox[0] = x;
		marketBox[1] = top;
		marketBox[2] = MARKET_BUTTON;
		marketBox[3] = MARKET_BUTTON;
		marketId = id;
		boolean over = PanelKit.inside(marketBox, mouseX, mouseY);
		ui.rounded(x, top, MARKET_BUTTON, MARKET_BUTTON, Radius.XS, Draw.withOpacity(HudTheme.text(), over ? 0.2f : 0.1f));
		ui.icon("coins", x + 2, top + 2, MARKET_BUTTON - 4, over ? HudTheme.text() : HudTheme.secondary());
	}

	private void drawRowText(Canvas ui, String id, ItemRepo.Entry entry, int[] row, int y, boolean withTrend) {
		int textX = row[0] + ROW_TILE + 6;
		// a book of the item list is its enchantment's Bazaar product, a shard its own: on the Bazaar first, as in the tooltip
		String priced = SkyBlockPrices.priceIdOfListed(id);
		double buy = SkyBlockPrices.bazaarBuy(priced);
		double price = buy > 0 ? buy : SkyBlockPrices.listedLowest(priced);
		String priceText = feature.showPinnedPrice() && price > 0 ? ItemDetailView.coins(price) : "";
		double change = feature.showTrend() && price > 0 ? PriceHistory.dayChange(priced, price) : Double.NaN;
		String trend = Double.isNaN(change) ? "" : String.format(Locale.ROOT, "%+.1f %%", change);
		float priceWidth = priceText.isEmpty() ? 0f : ui.boldWidthScaled(priceText, TextScale.SMALL);
		float trendWidth = trend.isEmpty() ? 0f : ui.widthScaled(trend, TextScale.SMALL);
		float room = row[2] - ROW_TILE - 6 - Math.max(priceWidth, trendWidth) - 6;
		int rarity = entry == null ? HudTheme.text() : HudTheme.readable(entry.rarityColor());
		String name = entry == null ? SkyBlockPrices.nameOf(id) : entry.plainName();
		ui.textAtScaledBold(ui.fitScaled(name, room, TextScale.SMALL), textX, y + 2, TextScale.SMALL, rarity);
		String source = price <= 0 ? "" : buy > 0 ? bazaarLine(priced) : "Lowest BIN";
		if (feature.showPinnedPrice() && !source.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(source, room, TextScale.SMALL), textX, y + 11, TextScale.SMALL, HudTheme.secondary());
		}
		int right = row[0] + row[2];
		if (!priceText.isEmpty()) {
			ui.textAtScaledBold(priceText, right - priceWidth, y + 2, TextScale.SMALL, HudTheme.text());
		}
		if (!trend.isEmpty() && withTrend) {
			int color = change > 0.05 ? PanelKit.GREEN_TEXT : change < -0.05 ? PanelKit.RED_TEXT : HudTheme.secondary();
			ui.textAtScaled(trend, right - trendWidth, y + 11, TextScale.SMALL, color);
		}
	}

	/** @return the line under a Bazaar item's name: the buy price stands at the right, the sell price and the week's volume here */
	private static String bazaarLine(String priced) {
		StringBuilder line = new StringBuilder("Bazaar buy");
		double sell = SkyBlockPrices.bazaarSell(priced);
		if (sell > 0) {
			line.append(" · sell ").append(ItemDetailView.coins(sell));
		}
		double volume = SkyBlockPrices.bazaarVolumeWeek(priced);
		if (!Double.isNaN(volume) && volume > 0) {
			line.append(" · ").append(ItemDetailView.coins(volume)).append(" / 7 days");
		}
		return line.toString();
	}

	private void drawDropLine(Canvas ui, String id, int[] row) {
		if (dropping && dropGroup == null && id.equals(dropBefore)) {
			ui.rounded(row[0], row[1] - 2, row[2], DROP_LINE, Radius.XXS, ui.accent());
		}
	}

	/** The grid: each item with its price under it. */
	private ItemRepo.Entry drawGrid(Canvas ui, GuiGraphicsExtractor graphics, float dt, int mouseX, int mouseY) {
		float scale = feature.pinnedItemSize();
		int tile = ItemGrid.tile(scale);
		int cellWidth = Math.max(tile + ItemGrid.TILE_GAP, Math.round(tile * 1.6f));
		int cellHeight = tile + (feature.showPinnedPrice() ? 11 : 0) + ItemGrid.TILE_GAP;
		int columns = Math.max(1, (content[2] + ItemGrid.TILE_GAP) / cellWidth);
		int rows = (shownIds.size() + columns - 1) / columns;
		scrollTo(rows * cellHeight, dt);
		ItemRepo.Entry hovered = null;
		itemBoxes.clear();
		ui.graphics().enableScissor(content[0] - 2, content[1], content[0] + content[2] + 2, content[1] + content[3]);
		try {
			for (int i = 0; i < shownIds.size(); i++) {
				int column = i % columns;
				int row = i / columns;
				int x = content[0] + column * cellWidth + (cellWidth - tile) / 2;
				int y = Math.round(content[1] + row * cellHeight - scroll);
				int[] cellBox = {content[0] + column * cellWidth, y, cellWidth, cellHeight};
				itemBoxes.add(cellBox);
				if (y + cellHeight < content[1] || y > content[1] + content[3]) {
					continue;
				}
				String id = shownIds.get(i);
				ItemRepo.Entry entry = ItemRepo.get(id);
				boolean over = mouseX >= x && mouseX < x + tile && mouseY >= y && mouseY < y + tile && mouseY >= content[1] && mouseY < content[1] + content[3];
				if (dropping && dropGroup == null && id.equals(dropBefore)) {
					ui.rounded(cellBox[0] - 1, y, DROP_LINE, tile, Radius.XXS, ui.accent());
				}
				if (entry == null) {
					continue;
				}
				graphics.nextStratum();
				ItemTiles.drawInPanel(ui, graphics, entry, x, y, tile, over ? 1f : 0f, feature.lights(entry) ? 1f : 0f);
				if (over) {
					hovered = entry;
					setHovered(x, y, tile);
				}
				if (feature.showPinnedPrice()) {
					double price = SkyBlockPrices.listedPrice(SkyBlockPrices.priceIdOfListed(id));
					String text = price > 0 ? ItemDetailView.coins(price) : "–";
					float width = ui.widthScaled(text, TextScale.SMALL);
					ui.textAtScaled(text, x + (tile - width) / 2f, y + tile + 2, TextScale.SMALL, HudTheme.secondary());
				}
			}
		} finally {
			ui.graphics().disableScissor();
		}
		return hovered;
	}

	private void setHovered(int x, int y, int size) {
		hoveredTile[0] = x;
		hoveredTile[1] = y;
		hoveredTile[2] = size;
		hoveredTile[3] = size;
	}

	private void scrollTo(int total, float dt) {
		float most = Math.max(0, total - content[3]);
		scrollTarget = Math.max(0f, Math.min(most, scrollTarget));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * SCROLL_RATE));
	}

	/** The small menu of a group: Rename, Delete. */
	private void drawMenu(Canvas ui, int mouseX, int mouseY) {
		if (menuFor == null) {
			return;
		}
		ui.graphics().nextStratum();
		HudTheme.panel(ui, menuBox[0], menuBox[1], menuBox[2], menuBox[3], Radius.SM, Math.max(0.9f, HudTheme.opacity()));
		for (int i = 0; i < MENU.length; i++) {
			int[] row = {menuBox[0] + 2, menuBox[1] + 2 + i * MENU_ROW, menuBox[2] - 4, MENU_ROW};
			boolean over = PanelKit.inside(row, mouseX, mouseY);
			if (over) {
				ui.rounded(row[0], row[1], row[2], row[3], Radius.XS, Draw.withOpacity(HudTheme.text(), 0.1f));
			}
			ui.textAtScaled(MENU[i], row[0] + 5, row[1] + 3, TextScale.SMALL, i == 1 ? PanelKit.RED_TEXT : HudTheme.text());
		}
	}

	// ------------------------------------------------------------------------------------------------ drops, for the panels' controller

	/** Shows where a dragged item would land at a point over the panel, or nothing when it is not over it. */
	void dragOver(boolean over, double mouseX, double mouseY) {
		dropping = over;
		dropGroup = null;
		dropBefore = null;
		if (!over) {
			return;
		}
		for (int i = 0; i < groupBoxes.size(); i++) {
			if (PanelKit.inside(groupBoxes.get(i), mouseX, mouseY)) {
				dropGroup = groupNames.get(i);
				return;
			}
		}
		for (int i = 0; i < itemBoxes.size() && i < shownIds.size(); i++) {
			int[] box = itemBoxes.get(i);
			boolean grid = feature.pinnedView() == View.GRID;
			boolean before = grid ? mouseY >= box[1] && mouseY < box[1] + box[3] && mouseX < box[0] + box[2] / 2f && mouseX >= box[0]
				: mouseY < box[1] + box[3] / 2f && mouseY >= box[1] - ROW / 2f;
			if (before) {
				dropBefore = shownIds.get(i);
				return;
			}
		}
	}

	/** @return the label at the cursor while dragging over the panel: "Add to Mining", "Move here" */
	String dropLabel(boolean pinnedAlready) {
		if (!dropping) {
			return null;
		}
		String target = dropGroup != null ? dropGroup : group;
		String name = PinnedItems.ALL.equals(target) ? "Pinned" : target;
		return pinnedAlready && dropGroup == null ? "Move here" : "Add to " + name;
	}

	/** Drops an item where it was shown to land. @return whether it was taken */
	boolean drop(String id) {
		PinnedItems shelf = feature.pinned();
		boolean changed;
		if (dropGroup != null) {
			changed = shelf.has(id) ? shelf.move(id, dropGroup) : shelf.add(id, dropGroup, null);
		} else {
			String into = shelf.has(id) ? shelf.groupOf(id) : group;
			changed = shelf.add(id, PinnedItems.ALL.equals(group) ? into : group, dropBefore);
		}
		dropping = false;
		dropGroup = null;
		dropBefore = null;
		if (changed) {
			feature.pinnedChanged();
		}
		return changed;
	}

	// ------------------------------------------------------------------------------------------------ input

	/** @return the pinned item at a point (to drag it), or null */
	String idAt(double mouseX, double mouseY) {
		if (mouseY < content[1] || mouseY >= content[1] + content[3]) {
			return null;
		}
		for (int i = 0; i < itemBoxes.size() && i < shownIds.size(); i++) {
			if (PanelKit.inside(itemBoxes.get(i), mouseX, mouseY)) {
				return shownIds.get(i);
			}
		}
		return null;
	}

	/** @return whether a click hit the panel's own controls: the view, the groups, +, the group menu */
	boolean click(double mouseX, double mouseY, int button) {
		if (marketId != null && marketBox[2] > 0 && PanelKit.inside(marketBox, mouseX, mouseY)) {
			feature.openMarket(marketId);
			return true;
		}
		PinnedItems shelf = feature.pinned();
		if (menuFor != null) {
			String target = menuFor;
			menuFor = null;
			if (PanelKit.inside(menuBox, mouseX, mouseY)) {
				int row = (int) ((mouseY - menuBox[1] - 2) / MENU_ROW);
				if (row == 0) {
					renaming = target;
					typed = target;
				} else if (row == 1 && shelf.deleteGroup(target)) {
					feature.pinnedChanged();
				}
				return true;
			}
		}
		if (renaming != null) {
			finishRename(true);
		}
		for (int i = 0; i < 2; i++) {
			if (PanelKit.inside(viewBoxes[i], mouseX, mouseY)) {
				feature.setPinnedView(View.values()[i]);
				return true;
			}
		}
		if (addBox[2] > 0 && PanelKit.inside(addBox, mouseX, mouseY)) {
			String made = shelf.addGroup("Group");
			if (made != null) {
				group = made;
				renaming = made;
				typed = "";
				feature.pinnedChanged();
			}
			return true;
		}
		for (int i = 0; i < groupBoxes.size(); i++) {
			if (PanelKit.inside(groupBoxes.get(i), mouseX, mouseY)) {
				String name = groupNames.get(i);
				if (button == 1 && !PinnedItems.ALL.equals(name)) {
					menuFor = name;
					int[] chip = groupBoxes.get(i);
					menuBox[0] = chip[0];
					menuBox[1] = chip[1] + chip[3] + 2;
					menuBox[2] = MENU_WIDTH;
					menuBox[3] = MENU.length * MENU_ROW + 4;
				} else {
					group = name;
					scrollTarget = 0f;
				}
				return true;
			}
		}
		return false;
	}

	/** Ends typing a group's name: kept when wanted and not empty or taken. */
	void finishRename(boolean keep) {
		if (renaming == null) {
			return;
		}
		String target = renaming;
		renaming = null;
		if (keep && !typed.isBlank() && !typed.equals(target) && feature.pinned().renameGroup(target, typed)) {
			if (group.equals(target)) {
				group = PinnedItems.cleanName(typed);
			}
			feature.pinnedChanged();
		}
	}

	/** A key while a group's name is typed. @return whether it was used */
	boolean renameKey(int key, boolean enter, boolean escape, boolean backspace) {
		if (renaming == null) {
			return false;
		}
		if (enter || escape) {
			finishRename(enter);
		} else if (backspace && !typed.isEmpty()) {
			typed = typed.substring(0, typed.length() - 1);
		}
		return true;
	}

	/** A typed character while a group's name is typed. @return whether it was used */
	boolean renameChar(int codepoint) {
		if (renaming == null) {
			return false;
		}
		if (typed.length() < PinnedItems.MOST_GROUP_NAME && codepoint >= ' ' && codepoint != '§') {
			typed += new String(Character.toChars(codepoint));
		}
		return true;
	}

	/** Scrolls the items. @return whether the mouse was over them */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!PanelKit.inside(content, mouseX, mouseY)) {
			return false;
		}
		scrollTarget -= (float) (amount * ROW * 2);
		return true;
	}
}
