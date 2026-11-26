package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The Items panel: its header (title, count, Sort) and the grid of results with a scrollbar. Only the rows
 * in view are drawn. With no room for Pinned of its own, the header has a Pinned tab instead of the title, and the panel shows the shelf.
 */
final class ItemsPanel {
	private static final float SCROLL_RATE = 14f;
	private static final int SCROLL_ROWS = 2;
	private static final int SCROLLBAR_WIDTH = 2;
	private static final int LEAST_THUMB = 12;
	private static final int TAB_GAP = 10;
	/** The open tab's line: this far above the bar's bottom. */
	private static final float TAB_LINE_FROM_BOTTOM = 2f;
	/** The sort control: a small box on the header's bar, its height and its text's room at the sides. */
	private static final int SORT_HEIGHT = 11;
	private static final int SORT_PAD = 5;
	private static final int SORT_CHEVRON = 5;
	private static final int SORT_CHEVRON_GAP = 3;
	private static final float SORT_FILL = 0.07f;
	private static final float SORT_HOVER = 0.14f;
	/** The dark backing of a slot while the panel has no background. */
	private static final int SLOT_BACKING = 0x8C000000;
	private static final String[] TABS = {"Items", "Pinned"};

	private final ItemListFeature feature;
	private final int[] sortButton = new int[4];
	private final int[][] tabBoxes = new int[2][4];
	private final int[] gridBox = new int[4];
	/** The box the shelf is drawn in while its tab is open. */
	private final int[] inner = new int[4];
	private float scroll;
	private float scrollTarget;
	private int cell;
	private int columns;
	private int resultsFor = -1;
	/** The hovered result's tile, for the drag and the details' place. */
	private final int[] hoveredTile = new int[4];
	/** Whether the shelf is shown in place of the results (Pinned as a tab). */
	private boolean pinnedTab;

	ItemsPanel(ItemListFeature feature) {
		this.feature = feature;
	}

	boolean pinnedTab() {
		return pinnedTab;
	}

	void toTop() {
		scroll = 0f;
		scrollTarget = 0f;
	}

	int[] hoveredTile() {
		return hoveredTile;
	}

	/**
	 * @param asTab  whether Pinned is a tab of this panel
	 * @param pinned the shelf, drawn here while its tab is open
	 * @return the item under the mouse, or null
	 */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, boolean asTab, PinnedPanel pinned, float highlight, boolean lifted, float dt,
		int mouseX, int mouseY) {
		if (!asTab) {
			pinnedTab = false;
		}
		List<ItemRepo.Entry> results = feature.results();
		String count = PanelKit.count(results.size());
		// with the background "None" no box is behind the grid: the items float over the game in their own slots, under the header as a small bar
		PanelFrame.draw(ui, box, "cube", HudTheme.text(), asTab ? null : "Items", count, highlight, lifted, true);
		if (asTab) {
			drawTabs(ui, box, mouseX, mouseY);
		}
		if (pinnedTab) {
			sortButton[2] = 0;
			inner[0] = box[0];
			inner[1] = box[1] + PanelFrame.HEADER - PinnedPanel.HEADER_IN_TAB;
			inner[2] = box[2];
			inner[3] = box[3] - PanelFrame.HEADER + PinnedPanel.HEADER_IN_TAB;
			return pinned.drawContent(ui, graphics, inner, dt, mouseX, mouseY);
		}
		drawSort(ui, box, mouseX, mouseY);
		gridBox[0] = box[0] + PanelFrame.PAD;
		gridBox[1] = box[1] + PanelFrame.HEADER;
		gridBox[2] = box[2] - 2 * PanelFrame.PAD - ItemGrid.SCROLLBAR_ROOM;
		gridBox[3] = box[1] + box[3] - PanelFrame.PAD - gridBox[1];
		return drawGrid(ui, graphics, results, dt, mouseX, mouseY);
	}

	private void drawTabs(Canvas ui, int[] box, int mouseX, int mouseY) {
		int middle = PanelFrame.middle(box);
		float x = PanelFrame.afterGrip(box);
		float y = middle - PanelFrame.BODY_HALF;
		for (int i = 0; i < TABS.length; i++) {
			boolean on = (i == 1) == pinnedTab;
			String label = TABS[i];
			float width = ui.boldWidthScaled(label, TextScale.BODY);
			tabBoxes[i][0] = Math.round(x) - 2;
			tabBoxes[i][1] = box[1];
			tabBoxes[i][2] = Math.round(width) + 4;
			tabBoxes[i][3] = PanelFrame.BAR;
			boolean over = PanelKit.inside(tabBoxes[i], mouseX, mouseY);
			ui.textAtScaledBold(label, x, y, TextScale.BODY, on ? HudTheme.text() : over ? Draw.mix(HudTheme.secondary(), HudTheme.text(), 0.5f) : HudTheme.secondary());
			if (on) {
				ui.roundedSmooth(x, box[1] + PanelFrame.BAR - TAB_LINE_FROM_BOTTOM, width, 1.5f, 0.75f, ui.accent());
			}
			x += width + TAB_GAP;
		}
	}

	/** The sort chip ("Price ▾"): a small box at the header's right end, centered on its bar; a click takes the next order. */
	private void drawSort(Canvas ui, int[] box, int mouseX, int mouseY) {
		String label = feature.sort().label;
		int width = Math.round(ui.widthScaled(label, TextScale.SMALL)) + 2 * SORT_PAD + SORT_CHEVRON + SORT_CHEVRON_GAP;
		PanelFrame.headerControl(box, PanelFrame.controlsRight(box, false), width, SORT_HEIGHT, sortButton);
		boolean over = PanelKit.inside(sortButton, mouseX, mouseY);
		int color = over ? HudTheme.text() : HudTheme.secondary();
		ui.rounded(sortButton[0], sortButton[1], width, SORT_HEIGHT, Radius.XS, Draw.withOpacity(HudTheme.text(), over ? SORT_HOVER : SORT_FILL));
		ui.textAtScaled(label, sortButton[0] + SORT_PAD, PanelFrame.middle(box) - PanelFrame.SMALL_HALF, TextScale.SMALL, color);
		ui.icon("chevron", sortButton[0] + width - SORT_PAD - SORT_CHEVRON, PanelFrame.middle(box) - SORT_CHEVRON / 2, SORT_CHEVRON, color);
	}

	/** The grid of results: only the rows in view, a light ring on the first result while searching, and the scrollbar. */
	private ItemRepo.Entry drawGrid(Canvas ui, GuiGraphicsExtractor graphics, List<ItemRepo.Entry> results, float dt, int mouseX, int mouseY) {
		float scale = feature.itemSize();
		int tile = ItemGrid.tile(scale);
		cell = ItemGrid.cell(scale);
		columns = Math.max(1, ItemGrid.columns(gridBox[2], scale));
		hoveredTile[2] = 0;
		if (resultsFor != feature.resultsVersion()) {
			resultsFor = feature.resultsVersion();
			// new results start at the top
			toTop();
		}
		String empty = emptyText(results);
		if (empty != null) {
			ui.textAtScaled(ui.fitScaled(empty, gridBox[2], TextScale.SMALL), gridBox[0], gridBox[1] + 4, TextScale.SMALL, HudTheme.secondary());
			PanelFrame.noteContent(gridBox[1] + PanelFrame.TEXT_LINE);
			return null;
		}
		int rows = (results.size() + columns - 1) / columns;
		float most = Math.max(0, rows * cell - ItemGrid.TILE_GAP - gridBox[3]);
		scrollTarget = Math.max(0f, Math.min(most, scrollTarget));
		scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * SCROLL_RATE));
		int firstRow = Math.max(0, (int) (scroll / cell));
		int lastRow = Math.min(rows - 1, (int) ((scroll + gridBox[3]) / cell));
		int marked = feature.markedResult();
		ItemRepo.Entry hovered = null;
		ui.graphics().enableScissor(gridBox[0] - 2, gridBox[1] - 2, gridBox[0] + gridBox[2] + 2, gridBox[1] + gridBox[3] + 2);
		try {
			for (int row = firstRow; row <= lastRow; row++) {
				// a stratum per row: the game scans every element of a stratum for each new one
				graphics.nextStratum();
				for (int column = 0; column < columns; column++) {
					int index = row * columns + column;
					if (index >= results.size()) {
						break;
					}
					int x = gridBox[0] + column * cell;
					int y = Math.round(gridBox[1] + row * cell - scroll);
					ItemRepo.Entry entry = results.get(index);
					boolean over = mouseX >= x && mouseX < x + tile && mouseY >= y && mouseY < y + tile && mouseY >= gridBox[1] && mouseY < gridBox[1] + gridBox[3];
					if (over) {
						hovered = entry;
						hoveredTile[0] = x;
						hoveredTile[1] = y;
						hoveredTile[2] = tile;
						hoveredTile[3] = tile;
					}
					// without a panel behind them the slots get a dark backing of their own, so they read over a bright world
					ui.rounded(x, y, tile, tile, Radius.SM, SLOT_BACKING);
					ItemTiles.drawInPanel(ui, graphics, entry, x, y, tile, over ? 1f : 0f, index == marked ? 1f : 0f);
				}
			}
		} finally {
			ui.graphics().disableScissor();
		}
		if (most > 0) {
			float share = gridBox[3] / (float) (gridBox[3] + most);
			int thumb = Math.max(LEAST_THUMB, Math.round(gridBox[3] * share));
			int thumbY = gridBox[1] + Math.round((gridBox[3] - thumb) * (scroll / most));
			int barX = gridBox[0] + gridBox[2] + ItemGrid.SCROLLBAR_ROOM - SCROLLBAR_WIDTH;
			ui.rounded(barX, gridBox[1], SCROLLBAR_WIDTH, gridBox[3], Radius.XXS, Draw.withOpacity(HudTheme.text(), 0.06f));
			ui.rounded(barX, thumbY, SCROLLBAR_WIDTH, thumb, Radius.XXS, Draw.withOpacity(HudTheme.text(), 0.3f));
		}
		return hovered;
	}

	/** @return what the grid says instead of items: loading, type to search, nothing found; null while there are results */
	private String emptyText(List<ItemRepo.Entry> results) {
		if (!ItemRepo.ready()) {
			return ItemRepo.state();
		}
		if (!results.isEmpty()) {
			return null;
		}
		if (feature.searchBusy() && !feature.resultsEverDone()) {
			return "Loading items…";
		}
		if (feature.search().isBlank() && feature.onlyWhileSearching()) {
			return "Type to search";
		}
		return feature.searchBusy() ? "Searching…" : "Nothing found";
	}

	/** @return whether a click hit the panel's own controls (sort, tabs) */
	boolean click(double mouseX, double mouseY, boolean asTab) {
		if (asTab) {
			for (int i = 0; i < TABS.length; i++) {
				if (PanelKit.inside(tabBoxes[i], mouseX, mouseY)) {
					pinnedTab = i == 1;
					return true;
				}
			}
		}
		if (pinnedTab) {
			return false;
		}
		if (sortButton[2] > 0 && PanelKit.inside(sortButton, mouseX, mouseY)) {
			feature.nextSort();
			return true;
		}
		return false;
	}

	/** Scrolls the grid. @return whether the mouse was over it */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (pinnedTab) {
			return false;
		}
		if (!PanelKit.inside(gridBox, mouseX, mouseY)) {
			return false;
		}
		scrollTarget -= (float) (amount * cell * SCROLL_ROWS);
		return true;
	}

	/** @return how many items show in the grid now, for the badge */
	int columns() {
		return columns;
	}
}
