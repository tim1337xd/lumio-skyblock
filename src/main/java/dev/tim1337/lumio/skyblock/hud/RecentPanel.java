package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** The Recent panel: the items the player last looked at, newest first, as many as fit and as the setting allows. It folds up to its header bar. */
final class RecentPanel {
	private final ItemListFeature feature;
	private final int[] hoveredTile = new int[4];
	private String hoveredId;
	/** The header's count, made again only when it changes. */
	private int countFor = -1;
	private String count = "";

	RecentPanel(ItemListFeature feature) {
		this.feature = feature;
	}

	int[] hoveredTile() {
		return hoveredTile;
	}

	/** @return the item under the mouse (to drag it), or null */
	String hoveredId() {
		return hoveredId;
	}

	/** @return the item under the mouse, or null */
	ItemRepo.Entry draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, float highlight, boolean lifted, int mouseX, int mouseY) {
		List<String> ids = feature.recent().items();
		boolean collapsed = feature.collapsed(ItemPanelKind.RECENT);
		int shown = Math.min(ids.size(), feature.recentShown());
		if (countFor != shown) {
			countFor = shown;
			count = shown == 0 ? "" : String.valueOf(shown);
		}
		PanelFrame.draw(ui, box, "clock", HudTheme.text(), "Recent", count, highlight, lifted, !collapsed);
		PanelFrame.collapseArrow(ui, box, collapsed);
		hoveredId = null;
		hoveredTile[2] = 0;
		if (collapsed) {
			return null;
		}
		int left = box[0] + PanelFrame.PAD;
		int top = box[1] + PanelFrame.HEADER;
		int width = box[2] - 2 * PanelFrame.PAD;
		int height = box[3] - PanelFrame.HEADER - PanelFrame.PAD + ItemGrid.TILE_GAP;
		if (ids.isEmpty()) {
			ui.textAtScaled(ui.fitScaled("Items you open show here", width, TextScale.SMALL), left, top + 4, TextScale.SMALL, HudTheme.secondary());
			PanelFrame.noteContent(top + PanelFrame.TEXT_LINE);
			return null;
		}
		float scale = feature.recentItemSize();
		int tile = ItemGrid.tile(scale);
		int cell = ItemGrid.cell(scale);
		int columns = Math.max(1, ItemGrid.columns(width, scale));
		int rows = Math.max(1, ItemGrid.rows(height, scale));
		int count = Math.min(Math.min(ids.size(), feature.recentShown()), columns * rows);
		ItemRepo.Entry hovered = null;
		for (int i = 0; i < count; i++) {
			ItemRepo.Entry entry = ItemRepo.get(ids.get(i));
			if (entry == null) {
				continue;
			}
			int x = left + (i % columns) * cell;
			int y = top + (i / columns) * cell;
			boolean over = mouseX >= x && mouseX < x + tile && mouseY >= y && mouseY < y + tile;
			graphics.nextStratum();
			ItemTiles.drawInPanel(ui, graphics, entry, x, y, tile, over ? 1f : 0f, feature.lights(entry) ? 1f : 0f);
			if (over) {
				hovered = entry;
				hoveredId = entry.id;
				hoveredTile[0] = x;
				hoveredTile[1] = y;
				hoveredTile[2] = tile;
				hoveredTile[3] = tile;
			}
		}
		return hovered;
	}
}
