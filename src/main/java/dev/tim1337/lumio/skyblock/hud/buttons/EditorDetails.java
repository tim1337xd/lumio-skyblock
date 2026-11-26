package dev.tim1337.lumio.skyblock.hud.buttons;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The editor's panel for the selected button: its command (with suggestions from the ready-made ones while typing), its name, its icon, where it
 * shows, whether only on SkyBlock and whether the inventory closes after its click, and Duplicate and Delete. A free spot gets the same panel; its
 * button is made as soon as a valid command is entered.
 */
final class EditorDetails {
	/** What a click in the panel asks the editor to do. */
	enum Action {
		NONE, DUPLICATE, DELETE, PICK_ICON, HELD_ICON, NEXT_COLOR, ICON, SHOW_IN, ONLY_SKYBLOCK, CLOSE_AFTER, SUGGESTION, FIELD
	}

	private static final int PAD = 10;
	private static final int HEADER_ICON = 20;
	private static final int FIELD_HEIGHT = 18;
	private static final int SUGGESTION_HEIGHT = 13;
	private static final int ICON_CELL = 16;
	private static final int MOST_ICONS = 8;
	private static final String[] SHOW_IN = {"Inventory", "Chests", "Both"};
	private static final float HINT_SCALE = TextScale.SMALL * 0.9f;
	private static final int FIRST_HEIGHT = 300;

	final TextField command = new TextField(InventoryButton.MOST_COMMAND - 1, "/warp hub", ">_");
	final TextField name = new TextField(InventoryButton.MOST_NAME, "Name", "");
	final int[] panel = new int[4];
	private final int[] duplicateBox = new int[4];
	private final int[] deleteBox = new int[4];
	private final int[] pickBox = new int[4];
	private final int[] heldBox = new int[4];
	private final int[] colorBox = new int[4];
	private final int[] onlyBox = new int[4];
	private final int[] closeBox = new int[4];
	private final int[][] showInBoxes = new int[SHOW_IN.length][4];
	private final int[][] iconBoxes = new int[MOST_ICONS][4];
	private final int[][] suggestionBoxes = new int[5][4];
	private List<ButtonCatalog.Ready> suggestions = List.of();
	private List<ButtonIcon> icons = List.of();
	private int measured = FIRST_HEIGHT;
	private int chipX;
	private int chipY;
	/** What the last click chose, for the editor to take: an icon, a place to show in, a suggestion. */
	ButtonIcon clickedIcon;
	InventoryButton.ShowIn clickedShowIn;
	ButtonCatalog.Ready clickedSuggestion;

	void place(int x, int y, int width) {
		EditorWidgets.set(panel, x, y, width, 0);
	}

	void icons(List<ButtonIcon> choices) {
		icons = choices.size() > MOST_ICONS ? choices.subList(0, MOST_ICONS) : choices;
	}

	/** The command field changed: its suggestions follow it. */
	void suggest() {
		String typed = command.value();
		suggestions = command.focused ? ButtonCatalog.suggestions(typed) : List.of();
		if (suggestions.size() == 1 && suggestions.getFirst().command().equalsIgnoreCase(InventoryButton.clean(typed))) {
			suggestions = List.of();
		}
	}

	/**
	 * @param button the selected button, or null for a free spot
	 * @param icon   the icon shown (the button's, or the draft's)
	 */
	void draw(Canvas ui, GuiGraphicsExtractor graphics, InventoryButton button, ButtonIcon icon, InventoryButton.ShowIn showIn, boolean onlySkyBlock,
		boolean closeAfter, int spot, int mouseX, int mouseY) {
		int x = panel[0] + PAD;
		int width = panel[2] - 2 * PAD;
		int top = panel[1];
		int height = measure();
		panel[3] = height;
		HudTheme.panel(ui, panel[0], top, panel[2], height, Radius.LG, HudTheme.opacity());
		ui.ring(panel[0], top, panel[2], height, Radius.LG, 1, Draw.withOpacity(HudTheme.text(), 0.1f));
		int y = top + PAD;
		ButtonLook.draw(ui, graphics, icon, x, y, HEADER_ICON, ButtonLook.State.NORMAL, true, true);
		String title = button == null ? "New button" : button.name();
		ui.textAtScaledBold(ui.fitScaled(title, width - HEADER_ICON - 46, TextScale.BODY), x + HEADER_ICON + 6, y + 1, TextScale.BODY, HudTheme.text());
		ui.textAtScaled(ui.fitScaled(ButtonSpots.label(spot), width - HEADER_ICON - 46, TextScale.SMALL), x + HEADER_ICON + 6, y + 12, TextScale.SMALL,
			HudTheme.secondary());
		EditorWidgets.set(deleteBox, x + width - 14, y + 3, 14, 14);
		EditorWidgets.set(duplicateBox, x + width - 32, y + 3, 14, 14);
		if (button != null) {
			EditorWidgets.iconButton(ui, duplicateBox, "layers", mouseX, mouseY);
			EditorWidgets.iconButton(ui, deleteBox, "trash", mouseX, mouseY);
		}
		y += HEADER_ICON + 10;
		EditorWidgets.label(ui, "COMMAND", x, y);
		y += 10;
		command.place(x, y, width, FIELD_HEIGHT);
		command.draw(ui, mouseX, mouseY);
		y += FIELD_HEIGHT + 2;
		for (int[] box : suggestionBoxes) {
			box[2] = 0;
		}
		if (!suggestions.isEmpty()) {
			int listHeight = suggestions.size() * SUGGESTION_HEIGHT + 4;
			ui.rounded(x, y, width, listHeight, Radius.SM, Draw.withOpacity(0xFF000000, 0.45f));
			for (int i = 0; i < suggestions.size(); i++) {
				ButtonCatalog.Ready ready = suggestions.get(i);
				int[] box = suggestionBoxes[i];
				EditorWidgets.set(box, x + 2, y + 2 + i * SUGGESTION_HEIGHT, width - 4, SUGGESTION_HEIGHT);
				if (EditorWidgets.inside(box, mouseX, mouseY)) {
					ui.rounded(box[0], box[1], box[2], box[3], Radius.XS, Draw.withOpacity(ui.accent(), 0.3f));
				}
				ui.textAtScaled(ready.command(), box[0] + 4, box[1] + 3, TextScale.SMALL, HudTheme.text());
				float nameWidth = ui.widthScaled(ready.name(), TextScale.SMALL);
				ui.textAtScaled(ready.name(), box[0] + box[2] - 4 - nameWidth, box[1] + 3, TextScale.SMALL, HudTheme.secondary());
			}
			y += listHeight + 2;
		}
		y += 6;
		EditorWidgets.label(ui, "NAME", x, y);
		y += 10;
		name.place(x, y, width, FIELD_HEIGHT);
		name.draw(ui, mouseX, mouseY);
		y += FIELD_HEIGHT + 3;
		ui.textAtScaled(ui.fitScaled("Shown on hover.", width, HINT_SCALE), x, y, HINT_SCALE, HudTheme.secondary());
		ui.textAtScaled(ui.fitScaled("Filled in by itself for known commands.", width, HINT_SCALE), x, y + 8, HINT_SCALE, HudTheme.secondary());
		y += 20;
		EditorWidgets.label(ui, "ICON", x, y);
		y += 10;
		for (int i = 0; i < MOST_ICONS; i++) {
			iconBoxes[i][2] = 0;
		}
		int perRow = Math.max(1, Math.min(MOST_ICONS, (width + 2) / (ICON_CELL + 2)));
		for (int i = 0; i < Math.min(perRow, icons.size()); i++) {
			ButtonIcon each = icons.get(i);
			int[] box = iconBoxes[i];
			EditorWidgets.set(box, x + i * (ICON_CELL + 2), y, ICON_CELL, ICON_CELL);
			boolean over = EditorWidgets.inside(box, mouseX, mouseY);
			ButtonLook.draw(ui, graphics, each, box[0], y, ICON_CELL, over ? ButtonLook.State.HOVER : ButtonLook.State.NORMAL, true, false);
			if (each.equals(icon)) {
				ui.ring(box[0] - 1, y - 1, ICON_CELL + 2, ICON_CELL + 2, Radius.SM, 1, ui.accent());
			}
		}
		y += ICON_CELL + 4;
		chipX = x;
		chipY = y;
		chip(ui, pickBox, "Search items…", mouseX, mouseY);
		chip(ui, heldBox, "Item in my hand", mouseX, mouseY);
		if (icon.kind() == ButtonIcon.Kind.SYMBOL) {
			chip(ui, colorBox, "Color", mouseX, mouseY);
		} else {
			colorBox[2] = 0;
		}
		y = chipY + PanelKit.CHIP_HEIGHT + 8;
		ui.icon("cube", x, y + 3, 9, HudTheme.secondary());
		ui.textAtScaled("Show in", x + 13, y + 4, TextScale.SMALL, HudTheme.text());
		int segmentedWidth = EditorWidgets.segmentedWidth(ui, SHOW_IN);
		EditorWidgets.segmented(ui, x + width - segmentedWidth, y, SHOW_IN, showIn.ordinal(), mouseX, mouseY, showInBoxes);
		y += EditorWidgets.SEGMENT_HEIGHT + 5;
		measured = y + 2 * 16 + PAD - top;
		y = switchRow(ui, onlyBox, "lock", "Only on SkyBlock", onlySkyBlock, x, y, width);
		switchRow(ui, closeBox, "close", "Close the inventory after the click", closeAfter, x, y, width);
	}

	/** @return the panel's height: as tall as its content was in the last frame (the first frame guesses) */
	private int measure() {
		return measured;
	}

	/** A chip after the last one; one that does not fit any more goes on the next line. */
	private void chip(Canvas ui, int[] box, String label, int mouseX, int mouseY) {
		int width = PanelKit.chipWidth(ui, label);
		int right = panel[0] + panel[2] - PAD;
		if (chipX + width > right && chipX > panel[0] + PAD) {
			chipX = panel[0] + PAD;
			chipY += PanelKit.CHIP_HEIGHT + 3;
		}
		EditorWidgets.set(box, chipX, chipY, width, PanelKit.CHIP_HEIGHT);
		PanelKit.chip(ui, box, label, false, mouseX, mouseY);
		chipX += width + 4;
	}

	private static int switchRow(Canvas ui, int[] box, String icon, String label, boolean on, int x, int y, int width) {
		ui.icon(icon, x, y + 3, 9, HudTheme.secondary());
		ui.textAtScaled(ui.fitScaled(label, width - 13 - EditorWidgets.SWITCH_WIDTH - 6, TextScale.SMALL), x + 13, y + 4, TextScale.SMALL, HudTheme.text());
		EditorWidgets.set(box, x, y, width, 16);
		EditorWidgets.toggle(ui, x + width - EditorWidgets.SWITCH_WIDTH, y + 3, on);
		return y + 16;
	}

	/** @return what a click in the panel does; the fields take their focus here */
	Action click(double mouseX, double mouseY, boolean hasButton) {
		for (int i = 0; i < suggestions.size(); i++) {
			if (EditorWidgets.inside(suggestionBoxes[i], mouseX, mouseY)) {
				clickedSuggestion = suggestions.get(i);
				return Action.SUGGESTION;
			}
		}
		command.focused = command.inside(mouseX, mouseY);
		name.focused = name.inside(mouseX, mouseY);
		suggest();
		if (command.focused || name.focused) {
			return Action.FIELD;
		}
		if (hasButton && EditorWidgets.inside(duplicateBox, mouseX, mouseY)) {
			return Action.DUPLICATE;
		}
		if (hasButton && EditorWidgets.inside(deleteBox, mouseX, mouseY)) {
			return Action.DELETE;
		}
		for (int i = 0; i < icons.size() && i < MOST_ICONS; i++) {
			if (iconBoxes[i][2] > 0 && EditorWidgets.inside(iconBoxes[i], mouseX, mouseY)) {
				clickedIcon = icons.get(i);
				return Action.ICON;
			}
		}
		if (EditorWidgets.inside(pickBox, mouseX, mouseY)) {
			return Action.PICK_ICON;
		}
		if (EditorWidgets.inside(heldBox, mouseX, mouseY)) {
			return Action.HELD_ICON;
		}
		if (colorBox[2] > 0 && EditorWidgets.inside(colorBox, mouseX, mouseY)) {
			return Action.NEXT_COLOR;
		}
		for (int i = 0; i < SHOW_IN.length; i++) {
			if (EditorWidgets.inside(showInBoxes[i], mouseX, mouseY)) {
				clickedShowIn = InventoryButton.ShowIn.values()[i];
				return Action.SHOW_IN;
			}
		}
		if (EditorWidgets.inside(onlyBox, mouseX, mouseY)) {
			return Action.ONLY_SKYBLOCK;
		}
		if (EditorWidgets.inside(closeBox, mouseX, mouseY)) {
			return Action.CLOSE_AFTER;
		}
		return Action.NONE;
	}

	boolean inside(double mouseX, double mouseY) {
		return mouseX >= panel[0] && mouseX < panel[0] + panel[2] && mouseY >= panel[1] && mouseY < panel[1] + panel[3];
	}
}
