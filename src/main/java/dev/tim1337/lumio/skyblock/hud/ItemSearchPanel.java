package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The item search beside the inventory, without a panel: the search field as a glass pill with the result count and a clear button (at the
 * bottom of the screen beside the window, see {@link SearchBarPlace}, or at the top of the results when it has no room there), the groups as a quiet row of text (the chosen one white with a short accent line under it, the sort at the end), and the results floating right on the
 * screen, each item with a soft shadow and a thin line in its rarity color, the hovered one on a light rounded highlight. Every text has a
 * soft shadow, so it stays readable on a bright world. With "only while searching" an empty search shows just the field and the favorites.
 */
final class ItemSearchPanel {
	// the sign in the bar while the slots light up
	private static final int LIGHT_CENTER = 14;
	private static final int LIGHT_SIZE = 13;
	private static final int LIGHT_ICON_SIZE = 9;
	private static final String LIGHT_ICON = "sun";
	private static final String LIGHT_TOOLTIP = "Double-click to stop highlighting";
	static final int PAD = 10;
	private static final int GAP = 8;
	private static final int CATEGORY_ROW = 14;
	private static final int CATEGORY_GAP = 8;
	private static final int UNDERLINE = 8;
	private static final int TILE_GAP = 6;
	private static final int HINT_ROW = 12;
	/** The rarity line under an item, as a share of the tile, and the item's soft shadow. */
	private static final float RARITY_LINE = 0.55f;
	private static final int ITEM_SHADOW = 0x59000000;
	private static final int TEXT_SHADOW = 0x99000000;
	private static final float HOVER_FILL = 0.14f;
	private static final float SCROLL_RATE = 14f;
	private static final int SCROLL_STEP = 2;
	/** The room between the last row of items, which is clipped a little below the grid, and the hint. */
	private static final int HINT_GAP = 9;
	private static final String HINT = "Enter opens the first item · Esc clears";
	/** The end of the row of groups when not all fit, and the padding of its menu. */
	private static final String MORE = "More…";
	private static final int MENU_PAD = 6;

	private final ItemListFeature feature;
	private final Spring reveal = Spring.smooth(Spring.DEFAULT);
	private final int[][] categoryBoxes = new int[ItemTypes.Category.values().length][];
	private int[] field = new int[4];
	/** The widest the search field gets: on a wide panel it stays centered over the results instead of spanning them. */
	static final int MOST_FIELD_WIDTH = 220;
	private int[] sortButton = new int[4];
	/** The "More" button (null while every group fits), whether its menu is open, and the first group in it. */
	private int[] moreButton;
	private boolean moreOpen;
	private int moreFrom;
	private int gridY;
	private int gridHeight;
	private int cell;
	private float scroll;
	private float scrollTarget;

	ItemSearchPanel(ItemListFeature feature) {
		this.feature = feature;
	}

	/** @return the search field's box, as last drawn */
	int[] field() {
		return field;
	}

	/** Back to the top of the results, after the search changed. */
	void toTop() {
		scrollTarget = 0f;
		scroll = 0f;
	}

	/** @return whether the results are shown, not just the field */
	boolean open() {
		return reveal.value() > 0.5f;
	}

	/** @return the item under the mouse, or null; the field goes in its place at the bottom (bar), or at the top of the box when that is null */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, boolean room, int[] bar, float dt, int mouseX, int mouseY) {
		// without room for the results the field stays
		boolean wantOpen = room && (!feature.onlyWhileSearching() || !feature.search().isBlank());
		float open = reveal.step(wantOpen ? 1f : 0f, dt);
		int left = box[0] + PAD;
		int width = box[2] - 2 * PAD;
		field = bar;
		int top = box[1];
		ItemRepo.Entry hovered = null;
		if (open > 0.01f) {
			float alphaBefore = Draw.alpha();
			Draw.setAlpha(alphaBefore * open);
			int rowBottom = drawCategories(ui, left, width, top, mouseX, mouseY);
			int hintY = box[1] + box[3] - PAD - HINT_ROW;
			hovered = drawGrid(ui, graphics, feature.filtered(), left, width, rowBottom + GAP, hintY - HINT_GAP, dt, mouseX, mouseY);
			shadowed(ui, ui.fitScaled(HINT, width, TextScale.SMALL), left, Math.min(hintY, gridBottom + 6), TextScale.SMALL, HudTheme.secondary());
			drawMoreMenu(ui, mouseX, mouseY);
			Draw.setAlpha(alphaBefore);
		}
		List<ItemRepo.Entry> favorites = feature.favoriteEntries();
		if (room && open < 0.99f && !favorites.isEmpty()) {
			float alphaBefore = Draw.alpha();
			Draw.setAlpha(alphaBefore * (1f - open));
			ItemRepo.Entry favorite = drawGrid(ui, graphics, favorites, left, width, top, top + tileSize() + 4, 0f, mouseX, mouseY);
			if (hovered == null && open < 0.5f) {
				hovered = favorite;
			}
			Draw.setAlpha(alphaBefore);
		}
		int count = feature.filtered().size();
		String right = feature.search().isEmpty() ? feature.searchKeyHint() : count == 1 ? "1 item" : count + " items";
		String placeholder = ItemRepo.ready() ? "Search items" : ItemRepo.state();
		PanelKit.searchField(ui, field, feature.search(), placeholder, right, feature.searchFocused(), mouseX, mouseY);
		if (feature.lightOn()) {
			drawLightOn(ui, graphics, field, mouseX, mouseY);
		}
		return hovered;
	}

	/** The bar while the slots light up: an outline in the accent, and a small sign over the search's icon that says how to stop. */
	private static void drawLightOn(Canvas ui, GuiGraphicsExtractor graphics, int[] field, int mouseX, int mouseY) {
		ui.ring(field[0], field[1], field[2], field[3], field[3] / 2f, 1, ui.accent());
		int center = field[0] + LIGHT_CENTER;
		int middle = field[1] + field[3] / 2;
		ui.discAt(center, middle, LIGHT_SIZE, ui.accent());
		ui.icon(LIGHT_ICON, center - LIGHT_ICON_SIZE / 2, middle - LIGHT_ICON_SIZE / 2, LIGHT_ICON_SIZE, 0xFFFFFFFF);
		if (Math.abs(mouseX - center) <= LIGHT_SIZE / 2 && Math.abs(mouseY - middle) <= LIGHT_SIZE / 2) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(LIGHT_TOOLTIP), mouseX, mouseY);
		}
	}

	/** @return how wide the area must be for so many columns of items at a size, with its padding */
	static int areaWidthFor(int columns, float itemSize) {
		int cellSize = Math.round(20 * itemSize) + TILE_GAP;
		return columns * cellSize - TILE_GAP + 2 * PAD;
	}

	private int tileSize() {
		return Math.round(20 * feature.itemSize());
	}

	/** Draws a text with a soft shadow, so it reads on a bright world. */
	private static void shadowed(Canvas ui, String text, float x, float y, float scale, int color) {
		ui.textAtScaled(text, x + 0.5f, y + 0.6f, scale, Draw.withOpacity(TEXT_SHADOW, 1f));
		ui.textAtScaled(text, x, y, scale, color);
	}

	/**
	 * Draws the groups as one quiet row of text with the sort at its end. The groups that do not fit go into a "More" menu at the row's end, which
	 * opens below it. @return the y below the row
	 */
	private int drawCategories(Canvas ui, int left, int width, int y, int mouseX, int mouseY) {
		float moreWidth = ui.widthScaled(MORE, TextScale.SMALL);
		String sort = "Sort: " + feature.sort().label;
		if (moreWidth + CATEGORY_GAP + ui.widthScaled(sort, TextScale.SMALL) > width) {
			// a very narrow area: the sort says only what it sorts by
			sort = feature.sort().label;
		}
		float sortWidth = ui.widthScaled(sort, TextScale.SMALL);
		ItemTypes.Category[] all = ItemTypes.Category.values();
		int right = Math.round(left + width - sortWidth - CATEGORY_GAP);
		// how many fit: all of them, or as many as leave room for "More"
		int fitting = all.length;
		float x = left;
		for (int i = 0; i < all.length; i++) {
			float labelWidth = ui.widthScaled(all[i].label, TextScale.SMALL);
			boolean last = i == all.length - 1;
			if (x + labelWidth > (last ? right : right - moreWidth - CATEGORY_GAP)) {
				fitting = i;
				break;
			}
			x += labelWidth + CATEGORY_GAP;
		}
		x = left;
		boolean chosenInMore = false;
		for (int i = 0; i < all.length; i++) {
			if (i >= fitting) {
				categoryBoxes[i] = null;
				chosenInMore |= feature.category() == all[i];
				continue;
			}
			x = drawCategory(ui, all[i].label, x, y, feature.category() == all[i], i, mouseX, mouseY) + CATEGORY_GAP;
		}
		moreFrom = fitting;
		if (fitting < all.length) {
			moreButton = new int[]{Math.round(x) - 3, y - 2, Math.round(moreWidth) + 6, CATEGORY_ROW};
			boolean over = PanelKit.inside(moreButton, mouseX, mouseY);
			int color = chosenInMore || moreOpen ? 0xFFFFFFFF : over ? Draw.mix(HudTheme.secondary(), 0xFFFFFFFF, 0.5f) : HudTheme.secondary();
			shadowed(ui, MORE, x, y, TextScale.SMALL, color);
			if (chosenInMore) {
				ui.roundedSmooth(x + (moreWidth - UNDERLINE) / 2f, y + 9f, UNDERLINE, 1.5f, 0.75f, ui.accent());
			}
		} else {
			moreButton = null;
			moreOpen = false;
		}
		float sortX = left + width - sortWidth;
		sortButton = new int[]{Math.round(sortX) - 3, y - 2, Math.round(sortWidth) + 6, CATEGORY_ROW};
		boolean overSort = PanelKit.inside(sortButton, mouseX, mouseY);
		shadowed(ui, sort, sortX, y, TextScale.SMALL, overSort ? 0xFFFFFFFF : HudTheme.secondary());
		return y + CATEGORY_ROW;
	}

	/** Draws one group's label, underlined when chosen, and keeps where a click hits it. @return the x at its end */
	private float drawCategory(Canvas ui, String label, float x, int y, boolean chosen, int index, int mouseX, int mouseY) {
		float labelWidth = ui.widthScaled(label, TextScale.SMALL);
		int[] hit = {Math.round(x) - 3, y - 2, Math.round(labelWidth) + 6, CATEGORY_ROW};
		categoryBoxes[index] = hit;
		boolean over = PanelKit.inside(hit, mouseX, mouseY);
		int color = chosen ? 0xFFFFFFFF : over ? Draw.mix(HudTheme.secondary(), 0xFFFFFFFF, 0.5f) : HudTheme.secondary();
		if (chosen) {
			ui.textAtScaledBold(label, x + 0.5f, y + 0.6f, TextScale.SMALL, TEXT_SHADOW);
			ui.textAtScaledBold(label, x, y, TextScale.SMALL, color);
			ui.roundedSmooth(x + (labelWidth - UNDERLINE) / 2f, y + 9f, UNDERLINE, 1.5f, 0.75f, ui.accent());
		} else {
			shadowed(ui, label, x, y, TextScale.SMALL, color);
		}
		return x + labelWidth;
	}

	/** The "More" menu, over the results: the groups that did not fit in the row, one per line. */
	private void drawMoreMenu(Canvas ui, int mouseX, int mouseY) {
		if (!moreOpen || moreButton == null) {
			return;
		}
		ItemTypes.Category[] all = ItemTypes.Category.values();
		float widest = 0f;
		for (int i = moreFrom; i < all.length; i++) {
			widest = Math.max(widest, ui.widthScaled(all[i].label, TextScale.SMALL));
		}
		int menuWidth = Math.round(widest) + 2 * MENU_PAD;
		int menuX = moreButton[0];
		int menuY = moreButton[1] + moreButton[3] + 2;
		int menuHeight = (all.length - moreFrom) * CATEGORY_ROW + 2 * MENU_PAD - 4;
		PanelKit.card(ui, menuX, menuY, menuWidth, menuHeight, 0f);
		int rowY = menuY + MENU_PAD;
		for (int i = moreFrom; i < all.length; i++) {
			drawCategory(ui, all[i].label, menuX + MENU_PAD, rowY, feature.category() == all[i], i, mouseX, mouseY);
			categoryBoxes[i] = new int[]{menuX, rowY - 2, menuWidth, CATEGORY_ROW};
			rowY += CATEGORY_ROW;
		}
	}

	/** The bottom of the results as drawn last, so the hint follows right under them. */
	private int gridBottom;

	/** Draws items floating in a grid: a soft shadow and a rarity line under each, a light highlight under the hovered one. */
	private ItemRepo.Entry drawGrid(Canvas ui, GuiGraphicsExtractor graphics, List<ItemRepo.Entry> list, int left, int width, int top, int bottom, float dt,
		int mouseX, int mouseY) {
		int size = tileSize();
		cell = size + TILE_GAP;
		int columns = Math.max(1, (width + TILE_GAP) / cell);
		gridY = top;
		gridHeight = Math.max(0, bottom - top);
		gridBottom = top;
		if (!ItemRepo.ready()) {
			shadowed(ui, ItemRepo.state(), left, top + 4, TextScale.SMALL, HudTheme.secondary());
			gridBottom = top + 14;
			return null;
		}
		if (list.isEmpty()) {
			shadowed(ui, feature.search().isBlank() ? "Type to search" : "Nothing found", left, top + 4, TextScale.SMALL, HudTheme.secondary());
			gridBottom = top + 14;
			return null;
		}
		int rows = (list.size() + columns - 1) / columns;
		float most = Math.max(0, rows * cell - gridHeight);
		scrollTarget = Math.max(0f, Math.min(most, scrollTarget));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * SCROLL_RATE));
		int firstRow = Math.max(0, (int) (scroll / cell));
		int lastRow = Math.min(rows - 1, (int) ((scroll + gridHeight) / cell));
		gridBottom = Math.min(bottom, Math.round(top + rows * cell - scroll));
		ItemRepo.Entry hovered = null;
		ui.graphics().enableScissor(left - 4, top - 4, left + width + 4, bottom + 4);
		try {
			for (int row = firstRow; row <= lastRow; row++) {
				// a stratum per row: the game scans every element of a stratum for each new one
				graphics.nextStratum();
				for (int column = 0; column < columns; column++) {
					int index = row * columns + column;
					if (index >= list.size()) {
						break;
					}
					int x = left + column * cell;
					int y = Math.round(gridY + row * cell - scroll);
					ItemRepo.Entry entry = list.get(index);
					boolean over = mouseX >= x - 2 && mouseX < x + size + 2 && mouseY >= y - 2 && mouseY < y + size + 2 && mouseY >= top && mouseY < bottom;
					if (over) {
						hovered = entry;
						ui.rounded(x - 3, y - 3, size + 6, size + 6, Radius.MD, Draw.withOpacity(0xFFFFFFFF, HOVER_FILL));
					}
					drawItem(ui, graphics, entry, x, y, size);
				}
			}
		} finally {
			ui.graphics().disableScissor();
		}
		return hovered;
	}

	/** Draws an item floating: a soft shadow under it, the item, and a thin line in its rarity color below. */
	private static void drawItem(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Entry entry, int x, int y, int size) {
		float center = x + size / 2f;
		ui.softLight(center, y + size * 0.62f, size * 0.42f, size * 0.32f, ITEM_SHADOW);
		var pose = graphics.pose();
		pose.pushMatrix();
		float itemScale = size / 20f;
		float itemSize = 16f * itemScale;
		float itemX = x + (size - itemSize) / 2f;
		float itemY = y + (size - itemSize) / 2f - 1f;
		pose.translate(itemX, itemY);
		pose.scale(itemScale, itemScale);
		graphics.item(entry.stack(), 0, 0);
		pose.popMatrix();
		SkyBlockFeatures.PET_CANDY.drawOn(ui, entry.stack(), itemX, itemY, itemSize);
		if (ItemTypes.of(entry).rank() >= 0) {
			float line = size * RARITY_LINE;
			ui.roundedSmooth(center - line / 2f, y + size - 1f, line, 1f, 0.5f, Draw.withOpacity(0xFF000000 | entry.rarityColor(), Draw.alpha()));
		}
	}

	// --------------------------------------------------------------------------------------------------- input

	/** A click on the groups or the sort. @return whether it hit one */
	boolean click(double mouseX, double mouseY) {
		if (!open()) {
			return false;
		}
		if (moreButton != null && PanelKit.inside(moreButton, mouseX, mouseY)) {
			moreOpen = !moreOpen;
			return true;
		}
		if (PanelKit.inside(sortButton, mouseX, mouseY)) {
			feature.nextSort();
			return true;
		}
		ItemTypes.Category[] all = ItemTypes.Category.values();
		for (int i = 0; i < all.length; i++) {
			if (categoryBoxes[i] != null && PanelKit.inside(categoryBoxes[i], mouseX, mouseY)) {
				feature.setCategory(all[i]);
				moreOpen = false;
				toTop();
				return true;
			}
		}
		// a click anywhere else closes the menu
		if (moreOpen) {
			moreOpen = false;
			return true;
		}
		return false;
	}

	/** Scrolls the results. @return whether the mouse was over them */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (!open() || mouseY < gridY || mouseY > gridY + gridHeight) {
			return false;
		}
		scrollTarget -= (float) (amount * cell * SCROLL_STEP);
		return true;
	}
}
