package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The item window's tab contents besides the market: a recipe (its 3×3 grid in the middle, where it is made, and its ingredients below with have
 * / need, red while missing and green when enough), a list (a mob's drops, an NPC's shop, who drops the item) and what it is used in. Every item
 * is drawn in the exact middle of its slot. What a click on an entry opens goes into the window's hits.
 */
final class ItemWindowBodies {
	/** An ingredient of the shown recipe: what, its name and color, how many the recipe needs, and how many the player has. */
	record Need(String id, String name, int color, long need, long have) {
		boolean enough() {
			return have >= need;
		}
	}

	/** One row of a list: its icon, words on the left and the right, and what a click opens, or null. */
	record Row(String icon, String text, String right, String target) {
	}

	static final String COIN = "SKYBLOCK_COIN";
	static final int GOOD = SystemColors.GREEN;
	static final int BAD = SystemColors.RED;
	private static final int CELL = 20;
	private static final int CELL_GAP = 3;
	private static final int GRID = 3 * CELL + 2 * CELL_GAP;
	private static final int GRID_GAP = 6;
	private static final int ROW = 18;
	private static final int ROW_ICON = ItemTiles.ITEM;
	private static final int ROW_TEXT_GAP = 6;
	private static final int NOTE_ROW = 11;
	private static final int TILE = 20;
	private static final int TILE_GAP = 4;
	private static final float TEXT_HALF = 3.5f;
	private static final int COUNT_SHADOW = 0xC0000000;
	private static final float COUNT_SCALE = 0.62f;
	private static final float SLOT_OPACITY = 0.8f;
	private static final float RING_FILL = 0.14f;
	private static final float ROW_HOVER = 0.08f;
	private static final float ROW_LINE = 0.07f;

	private ItemWindowBodies() {
	}

	/**
	 * A recipe: its grid in the middle, the line of where it is made (a link where that is a known mob or NPC), and its ingredients below.
	 *
	 * @param sourceLink filled with the where-line's box while it is a link, else emptied
	 */
	static void recipe(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Recipe shown, List<Need> needs, int left, int width, int top, int bottom,
		ItemWindowHits hits, int[] sourceLink, int mouseX, int mouseY) {
		sourceLink[2] = 0;
		int gridX = left + (width - GRID) / 2;
		for (int i = 0; i < 9; i++) {
			ItemRepo.Stack stack = i < shown.inputs().size() ? shown.inputs().get(i) : null;
			cell(ui, graphics, stack, needs, gridX + i % 3 * (CELL + CELL_GAP), top + i / 3 * (CELL + CELL_GAP), hits, mouseX, mouseY);
		}
		int y = top + GRID + GRID_GAP;
		String where = shown.crafting() ? shown.note() : shown.source() + (shown.note().isEmpty() ? "" : " · " + shown.note());
		if (!where.isEmpty() && y + NOTE_ROW <= bottom) {
			boolean link = !shown.sourceId().isEmpty() && ItemRepo.get(shown.sourceId()) != null;
			String text = ui.fitScaled(where, width, TextScale.SMALL);
			float textWidth = ui.widthScaled(text, TextScale.SMALL);
			float x = left + (width - textWidth) / 2f;
			if (link) {
				sourceLink[0] = Math.round(x);
				sourceLink[1] = y - 1;
				sourceLink[2] = Math.round(textWidth);
				sourceLink[3] = NOTE_ROW;
			}
			boolean over = link && PanelKit.inside(sourceLink, mouseX, mouseY);
			ui.textAtScaled(text, x, y, TextScale.SMALL, over ? ui.accent() : HudTheme.secondary());
			y += NOTE_ROW;
		}
		int fit = Math.max(0, (bottom - y) / ROW);
		boolean cut = needs.size() > fit;
		int shownNeeds = cut ? Math.max(0, fit - 1) : needs.size();
		for (int i = 0; i < shownNeeds; i++) {
			need(ui, graphics, needs.get(i), left, width, y + i * ROW, hits, mouseX, mouseY);
		}
		if (cut && fit > 0) {
			String more = "+" + (needs.size() - shownNeeds) + " more in the grid";
			ui.textAtScaled(more, left, y + shownNeeds * ROW + ROW / 2f - TEXT_HALF, TextScale.SMALL, HudTheme.secondary());
		}
	}

	/** An ingredient's row: its icon, its name in its rarity color, and have / need on the right, red while missing, green when enough. */
	private static void need(Canvas ui, GuiGraphicsExtractor graphics, Need need, int left, int width, int y, ItemWindowHits hits, int mouseX, int mouseY) {
		int[] hit = {left, y, width, ROW};
		if (PanelKit.inside(hit, mouseX, mouseY)) {
			ui.rounded(left - 3, y, width + 6, ROW, Radius.SM, Draw.withOpacity(HudTheme.text(), ROW_HOVER));
		}
		ItemTiles.item(graphics, stackOf(need.id()), left, y + (ROW - ROW_ICON) / 2, ROW_ICON);
		String count = PanelKit.count(need.have()) + " / " + PanelKit.count(need.need());
		float countWidth = ui.widthScaled(count, TextScale.SMALL);
		float middle = y + ROW / 2f - TEXT_HALF;
		ui.textAtScaled(count, left + width - countWidth, middle, TextScale.SMALL, need.enough() ? GOOD : BAD);
		float textX = left + ROW_ICON + ROW_TEXT_GAP;
		ui.textAtScaled(ui.fitScaled(need.name(), left + width - countWidth - 6 - textX, TextScale.SMALL), textX, middle, TextScale.SMALL,
			HudTheme.readable(need.color()));
		ui.fill(left, y + ROW - 1, width, 1, HudTheme.line(ROW_LINE));
		hits.add(hit, need.id());
	}

	/** A cell of the grid: empty, or the ingredient in its middle with its count and a green or red edge. */
	private static void cell(Canvas ui, GuiGraphicsExtractor graphics, ItemRepo.Stack stack, List<Need> needs, int x, int y, ItemWindowHits hits, int mouseX,
		int mouseY) {
		if (stack == null) {
			ui.rounded(x, y, CELL, CELL, Radius.SM, Draw.withOpacity(HudTheme.slot(), SLOT_OPACITY));
			return;
		}
		int ring = ringOf(stack, needs);
		boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
		ui.rounded(x, y, CELL, CELL, Radius.SM, Draw.withOpacity(ring == 0 ? HudTheme.slot() : ring, ring == 0 ? SLOT_OPACITY : RING_FILL));
		if (over) {
			ui.rounded(x, y, CELL, CELL, Radius.SM, Draw.withOpacity(ui.accent(), 0.3f));
		}
		if (ring != 0) {
			ui.ring(x, y, CELL, CELL, Radius.SM, 1, ring);
		}
		ItemTiles.item(graphics, stackOf(stack.id()), x, y, CELL);
		long count = Math.round(stack.count());
		if (count > 1) {
			graphics.nextStratum();
			String text = PanelKit.count(count);
			float width = ui.boldWidthScaled(text, COUNT_SCALE);
			ui.textAtScaledBold(text, x + CELL - 2f - width + 0.5f, y + CELL - 6.5f + 0.5f, COUNT_SCALE, COUNT_SHADOW);
			ui.textAtScaledBold(text, x + CELL - 2f - width, y + CELL - 6.5f, COUNT_SCALE, 0xFFFFFFFF);
		}
		if (!stack.id().equals(COIN)) {
			hits.add(new int[]{x, y, CELL, CELL}, stack.id());
		}
	}

	/** @return the edge of an ingredient: green when the player has enough of it, red when not, none for coins */
	private static int ringOf(ItemRepo.Stack stack, List<Need> needs) {
		for (Need need : needs) {
			if (need.id().equals(stack.id())) {
				return need.enough() ? GOOD : BAD;
			}
		}
		return 0;
	}

	/**
	 * A list by pages: a mob's drops with their chances, an NPC's shop, or who drops the item. A click on a row opens it.
	 *
	 * @return how many pages it takes
	 */
	static int rows(Canvas ui, GuiGraphicsExtractor graphics, List<Row> rows, String note, int page, int left, int width, int top, int bottom, ItemWindowHits hits,
		int mouseX, int mouseY) {
		int y = top;
		if (!note.isEmpty()) {
			ui.textAtScaled(ui.fitScaled(note, width, TextScale.SMALL), left, y, TextScale.SMALL, HudTheme.secondary());
			y += NOTE_ROW + 2;
		}
		int perPage = Math.max(1, (bottom - y) / ROW);
		int pages = ItemTabs.pages(rows.size(), perPage);
		int first = Math.floorMod(page, pages) * perPage;
		for (int i = first; i < rows.size() && i < first + perPage; i++) {
			Row row = rows.get(i);
			int rowY = y + (i - first) * ROW;
			int[] hit = {left, rowY, width, ROW};
			boolean known = row.target() != null && ItemRepo.get(row.target()) != null;
			if (known && PanelKit.inside(hit, mouseX, mouseY)) {
				ui.rounded(left - 3, rowY, width + 6, ROW, Radius.SM, Draw.withOpacity(HudTheme.text(), ROW_HOVER));
			}
			ItemTiles.item(graphics, stackOf(row.icon()), left, rowY + (ROW - ROW_ICON) / 2, ROW_ICON);
			float middle = rowY + ROW / 2f - TEXT_HALF;
			float rightWidth = row.right().isEmpty() ? 0f : Math.min(width * 0.5f, ui.widthScaled(row.right(), TextScale.SMALL));
			float textX = left + ROW_ICON + ROW_TEXT_GAP;
			ui.textAtScaled(ui.fitScaled(row.text(), left + width - rightWidth - 6f - textX, TextScale.SMALL), textX, middle, TextScale.SMALL, HudTheme.text());
			if (rightWidth > 0f) {
				String right = ui.fitScaled(row.right(), rightWidth, TextScale.SMALL);
				ui.textAtScaled(right, left + width - ui.widthScaled(right, TextScale.SMALL), middle, TextScale.SMALL, HudTheme.secondary());
			}
			ui.fill(left, rowY + ROW - 1, width, 1, HudTheme.line(ROW_LINE));
			if (known) {
				hits.add(hit, row.target());
			}
		}
		return pages;
	}

	/** What it is used in: its items as tiles, by pages. @return how many pages it takes */
	static int usedIn(Canvas ui, GuiGraphicsExtractor graphics, List<String> ids, int page, int left, int width, int top, int bottom, ItemWindowHits hits,
		int mouseX, int mouseY) {
		int columns = Math.max(1, (width + TILE_GAP) / (TILE + TILE_GAP));
		int lines = Math.max(1, (bottom - top + TILE_GAP) / (TILE + TILE_GAP));
		int perPage = columns * lines;
		int pages = ItemTabs.pages(ids.size(), perPage);
		int first = Math.floorMod(page, pages) * perPage;
		for (int i = first; i < ids.size() && i < first + perPage; i++) {
			ItemRepo.Entry entry = ItemRepo.get(ids.get(i));
			if (entry == null) {
				continue;
			}
			int x = left + (i - first) % columns * (TILE + TILE_GAP);
			int y = top + (i - first) / columns * (TILE + TILE_GAP);
			boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
			ItemTiles.draw(ui, graphics, entry, x, y, TILE, over ? 1f : 0f, 0f);
			hits.add(new int[]{x, y, TILE, TILE}, entry.id);
		}
		return pages;
	}

	static ItemStack stackOf(String itemId) {
		ItemRepo.Entry item = ItemRepo.get(itemId);
		if (item != null) {
			return item.stack();
		}
		return new ItemStack(itemId != null && itemId.equals(COIN) ? Items.GOLD_NUGGET : Items.BARRIER);
	}

	static String nameOf(String itemId) {
		ItemRepo.Entry item = ItemRepo.get(itemId);
		return item == null ? SkyBlockPrices.nameOf(itemId) : item.plainName();
	}
}
