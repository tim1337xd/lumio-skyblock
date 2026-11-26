package dev.tim1337.lumio.skyblock.hud.buttons;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

/**
 * The editor's Quick add panel: a search that also takes a command, the ready-made buttons in Warps and Menus, and "Own command…". A ready-made
 * button is dragged onto a spot or clicked onto the next free one.
 */
final class EditorQuickAdd {
	private static final int PAD = 8;
	private static final int TILE_HEIGHT = 17;
	private static final int TILE_GAP = 3;
	private static final int ICON = 12;
	private static final float NAME_SCALE = TextScale.SMALL * 0.88f;

	final TextField search = new TextField(InventoryButton.MOST_COMMAND, "Search, or type a command", "");
	final int[] panel = new int[4];
	final int[] ownBox = new int[4];
	private List<ButtonCatalog.Ready> shown = ButtonCatalog.ALL;
	private final int[][] tileBoxes = new int[ButtonCatalog.ALL.size()][4];

	void place(int x, int y, int width, int screenHeight) {
		EditorWidgets.set(panel, x, y, width, Math.min(screenHeight - y - 8, height()));
	}

	private int height() {
		int rows = 0;
		String group = "";
		int inGroup = 0;
		for (ButtonCatalog.Ready ready : shown) {
			if (!ready.group().equals(group)) {
				group = ready.group();
				rows += (inGroup + 1) / 2;
				inGroup = 0;
				rows++;
			}
			inGroup++;
		}
		rows += (inGroup + 1) / 2;
		return PAD + 12 + 22 + rows * (TILE_HEIGHT + TILE_GAP) + 26 + PAD;
	}

	/** The search changed: the tiles follow it. */
	void refilter() {
		shown = ButtonCatalog.search(search.value());
	}

	void draw(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		HudTheme.panel(ui, panel[0], panel[1], panel[2], panel[3], Radius.LG, HudTheme.opacity());
		ui.ring(panel[0], panel[1], panel[2], panel[3], Radius.LG, 1, Draw.withOpacity(HudTheme.text(), 0.1f));
		int x = panel[0] + PAD;
		int y = panel[1] + PAD;
		int width = panel[2] - 2 * PAD;
		ui.icon("plus", x, y, 9, HudTheme.text());
		ui.textAtScaledBold("Quick add", x + 13, y + 1, TextScale.BODY, HudTheme.text());
		String hint = "Drag onto a spot, or click";
		float hintWidth = ui.widthScaled(hint, TextScale.SMALL);
		if (hintWidth < width - 70) {
			ui.textAtScaled(hint, x + width - hintWidth, y + 2, TextScale.SMALL, HudTheme.secondary());
		}
		y += 14;
		search.place(x, y, width, 18);
		search.draw(ui, mouseX, mouseY);
		y += 24;
		int column = (width - TILE_GAP) / 2;
		String group = "";
		int inGroup = 0;
		for (int i = 0; i < tileBoxes.length; i++) {
			tileBoxes[i][2] = 0;
		}
		for (ButtonCatalog.Ready ready : shown) {
			if (!ready.group().equals(group)) {
				if (inGroup % 2 == 1) {
					y += TILE_HEIGHT + TILE_GAP;
				}
				group = ready.group();
				inGroup = 0;
				EditorWidgets.label(ui, group.toUpperCase(Locale.ROOT), x, y + 2);
				y += 12;
			}
			int tileX = x + (inGroup % 2) * (column + TILE_GAP);
			int[] box = tileBoxes[ButtonCatalog.ALL.indexOf(ready)];
			EditorWidgets.set(box, tileX, y, column, TILE_HEIGHT);
			drawTile(ui, graphics, ready, box, mouseX, mouseY);
			inGroup++;
			if (inGroup % 2 == 0) {
				y += TILE_HEIGHT + TILE_GAP;
			}
		}
		if (inGroup % 2 == 1) {
			y += TILE_HEIGHT + TILE_GAP;
		}
		y += 3;
		String typed = InventoryButton.clean(search.value());
		String own = typed != null && search.value().strip().startsWith("/") ? "Add " + typed : "Own command…";
		EditorWidgets.set(ownBox, x, y, width, 18);
		boolean over = EditorWidgets.inside(ownBox, mouseX, mouseY);
		EditorWidgets.dashed(ui, x, y, width, 18, over);
		String fitted = ui.fitScaled(own, width - 16, TextScale.SMALL);
		float ownWidth = ui.widthScaled(fitted, TextScale.SMALL);
		ui.textAtScaled(fitted, x + (width - ownWidth) / 2f, y + 5.5f, TextScale.SMALL, over ? HudTheme.text() : HudTheme.secondary());
	}

	private static void drawTile(Canvas ui, GuiGraphicsExtractor graphics, ButtonCatalog.Ready ready, int[] box, int mouseX, int mouseY) {
		boolean over = EditorWidgets.inside(box, mouseX, mouseY);
		ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.12f : 0.05f));
		ui.ring(box[0], box[1], box[2], box[3], Radius.SM, 1, over ? Draw.withOpacity(ui.accent(), 0.9f) : Draw.withOpacity(HudTheme.text(), 0.07f));
		ButtonLook.drawIcon(ui, graphics, ready.icon(), box[0] + 2, box[1] + (box[3] - ICON) / 2, ICON, ButtonLook.colorOf(ready.icon()));
		String name = ui.fitScaled(ready.name(), box[2] - ICON - 7, NAME_SCALE);
		ui.textAtScaled(name, box[0] + ICON + 4, box[1] + box[3] / 2f - 3.2f, NAME_SCALE, HudTheme.text());
	}

	/** @return the ready-made button under the mouse, or null */
	ButtonCatalog.Ready readyAt(double mouseX, double mouseY) {
		for (ButtonCatalog.Ready ready : shown) {
			int[] box = tileBoxes[ButtonCatalog.ALL.indexOf(ready)];
			if (box[2] > 0 && EditorWidgets.inside(box, mouseX, mouseY)) {
				return ready;
			}
		}
		return null;
	}

	boolean inside(double mouseX, double mouseY) {
		return EditorWidgets.inside(panel, mouseX, mouseY);
	}
}
