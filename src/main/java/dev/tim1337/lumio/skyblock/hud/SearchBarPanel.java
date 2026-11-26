package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The search bar, one calm box in the panels' style: a grip on the left that moves it, the field (its icon, the text, the result count or a calculation's result, and a
 * clear ×) and, behind a thin divider, the one Settings button (a gear) that opens the item search's settings window.
 */
final class SearchBarPanel {
	/** The grip's room at the left of the bar, and the room of the gear at its right behind the divider. */
	private static final int GRIP_ROOM = 13;
	private static final int GEAR_ROOM = 20;
	private static final float GRIP_HOVER_OPACITY = 0.12f;
	private static final int ICON = 10;
	private static final int TEXT_X = 23;
	private static final int LIGHT_SIZE = 13;
	private static final int LIGHT_ICON = 9;
	private static final String LIGHT_TOOLTIP = "Highlight mode: matches light up. Double-click to switch it off";

	private final ItemListFeature feature;
	private final int[] field = new int[4];
	private final int[] settingsButton = new int[4];
	private final int[] grip = new int[4];
	private final int[] clearButton = new int[4];

	SearchBarPanel(ItemListFeature feature) {
		this.feature = feature;
	}

	int[] field() {
		return field;
	}

	int[] settingsButton() {
		return settingsButton;
	}

	/** @return whether a point is on the grip, which moves the bar */
	boolean onGrip(double mouseX, double mouseY) {
		return PanelKit.inside(grip, mouseX, mouseY);
	}

	/**
	 * @param settingsOpen whether the settings window is open: its button shows it
	 * @param movable      whether the layout may be moved: without, the grip is left out
	 */
	void draw(Canvas ui, GuiGraphicsExtractor graphics, int[] box, boolean settingsOpen, boolean movable, float highlight, int mouseX, int mouseY) {
		int size = box[3];
		int gripRoom = movable ? GRIP_ROOM : 0;
		grip[0] = box[0];
		grip[1] = box[1];
		grip[2] = gripRoom;
		grip[3] = size;
		settingsButton[0] = box[0] + box[2] - GEAR_ROOM;
		settingsButton[1] = box[1];
		settingsButton[2] = GEAR_ROOM;
		settingsButton[3] = size;
		field[0] = box[0] + gripRoom;
		field[1] = box[1];
		field[2] = settingsButton[0] - field[0];
		field[3] = size;
		drawBox(ui, box, highlight, mouseX, mouseY);
		if (movable) {
			drawGrip(ui, graphics, mouseX, mouseY);
		}
		drawField(ui, mouseX, mouseY);
		drawGear(ui, settingsOpen, mouseX, mouseY);
		if (feature.lightOn()) {
			drawLightOn(ui, graphics, mouseX, mouseY);
		}
	}

	/** The bar's calm box: ringed in the accent with a soft glow while the search has focus. */
	private void drawBox(Canvas ui, int[] box, float highlight, int mouseX, int mouseY) {
		boolean focused = feature.searchFocused();
		boolean over = PanelKit.inside(box, mouseX, mouseY);
		float radius = Radius.box(box[3]);
		if (focused) {
			ui.softLight(box[0] + box[2] / 2f, box[1] + box[3] / 2f, box[2] / 2f + 6f, box[3], Draw.withOpacity(ui.accent(), 0.25f));
		}
		PanelFrame.body(ui, box[0], box[1], box[2], box[3], radius, PanelFrame.bodyOpacity());
		if (focused || highlight > 0.01f) {
			ui.ring(box[0], box[1], box[2], box[3], radius, 1, ui.accent());
		} else if (over) {
			ui.ring(box[0], box[1], box[2], box[3], radius, 1, Draw.withOpacity(HudTheme.text(), 0.25f));
		}
	}

	/** The grip of six dots; under the mouse it brightens on a soft fill and the cursor shows that it moves the bar. */
	private void drawGrip(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (PanelKit.inside(grip, mouseX, mouseY)) {
			ui.rounded(grip[0] + 2, grip[1] + 3, grip[2] - 3, grip[3] - 6, Radius.XS, Draw.withOpacity(HudTheme.text(), GRIP_HOVER_OPACITY));
			graphics.requestCursor(CursorTypes.RESIZE_ALL);
		}
		PanelFrame.grip(ui, grip[0] + (grip[2] - PanelFrame.GRIP_WIDTH) / 2 + 1, grip[1] + (grip[3] - PanelFrame.GRIP_HEIGHT) / 2);
	}

	/** The gear behind a thin divider. */
	private void drawGear(Canvas ui, boolean open, int mouseX, int mouseY) {
		boolean over = PanelKit.inside(settingsButton, mouseX, mouseY);
		ui.fill(settingsButton[0], settingsButton[1] + 5, 1, settingsButton[3] - 10, Draw.withOpacity(HudTheme.text(), 0.15f));
		int color = open ? ui.accent() : over ? HudTheme.text() : HudTheme.secondary();
		ui.icon("gear", settingsButton[0] + (settingsButton[2] - ICON) / 2 + 1, settingsButton[1] + (settingsButton[3] - ICON) / 2, ICON, color);
	}

	/** The field: its icon, the text or the hint, and what it shows on its right. */
	private void drawField(Canvas ui, int mouseX, int mouseY) {
		boolean focused = feature.searchFocused();
		int middle = field[1] + field[3] / 2;
		ui.icon("search", field[0] + 8, middle - ICON / 2, ICON, HudTheme.secondary());
		String text = feature.search();
		boolean empty = text.isEmpty();
		int rightRoom = 0;
		clearButton[2] = 0;
		if (!empty) {
			clearButton[0] = field[0] + field[2] - 15;
			clearButton[1] = middle - 6;
			clearButton[2] = 12;
			clearButton[3] = 12;
			boolean onClear = PanelKit.inside(clearButton, mouseX, mouseY);
			if (onClear) {
				ui.rounded(clearButton[0], clearButton[1], 12, 12, Radius.XS, Draw.withOpacity(HudTheme.text(), 0.15f));
			}
			ui.icon("close", clearButton[0] + 3, clearButton[1] + 3, 6, onClear ? HudTheme.text() : HudTheme.secondary());
			rightRoom = 20;
		}
		String right = rightText();
		if (!right.isEmpty()) {
			boolean math = right.startsWith("=");
			float width = math ? ui.boldWidthScaled(right, TextScale.SMALL) : ui.widthScaled(right, TextScale.SMALL);
			float x = field[0] + field[2] - 8 - rightRoom - width;
			if (empty) {
				// a key hint, in a small key cap
				ui.roundedSmooth(x - 4, middle - 6f, width + 8, 12f, Radius.XS, Draw.withOpacity(HudTheme.text(), 0.1f));
			}
			if (math) {
				ui.textAtScaledBold(right, x, middle - 3.5f, TextScale.SMALL, ui.accent());
			} else {
				ui.textAtScaled(right, x, middle - 3.5f, TextScale.SMALL, HudTheme.secondary());
			}
			rightRoom += Math.round(width) + 10;
		}
		int textX = field[0] + TEXT_X;
		int room = field[2] - TEXT_X - 8 - rightRoom;
		String placeholder = ItemRepo.ready() ? "Search items" : ItemRepo.state();
		String shown = ui.fit(empty && !focused ? placeholder : text, room);
		ui.textMiddle(shown, textX, middle, empty ? HudTheme.secondary() : HudTheme.text());
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			ui.fill(textX + (empty ? 0 : ui.width(shown)) + 1, field[1] + 5, 1, field[3] - 10, HudTheme.text());
		}
	}

	/** @return what the field shows on its right: a calculation's result, the count, or the key that focuses the search */
	private String rightText() {
		String text = feature.search();
		if (text.isEmpty()) {
			return feature.searchKeyHint();
		}
		String math = feature.calculation();
		if (!math.isEmpty()) {
			return math;
		}
		int count = feature.results().size();
		return count == 1 ? "1 item" : PanelKit.count(count) + " items";
	}

	/** While highlight mode is on: the accent sign over the field's icon, which says how to switch it off. */
	private void drawLightOn(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int center = field[0] + 13;
		int middle = field[1] + field[3] / 2;
		ui.rounded(center - LIGHT_SIZE / 2, middle - LIGHT_SIZE / 2, LIGHT_SIZE, LIGHT_SIZE, Radius.XS, ui.accent());
		ui.icon("sun", center - LIGHT_ICON / 2, middle - LIGHT_ICON / 2, LIGHT_ICON, 0xFFFFFFFF);
		if (Math.abs(mouseX - center) <= LIGHT_SIZE / 2 && Math.abs(mouseY - middle) <= LIGHT_SIZE / 2) {
			graphics.setTooltipForNextFrame(Minecraft.getInstance().font, Component.literal(LIGHT_TOOLTIP), mouseX, mouseY);
		}
	}

	/** @return whether a point is on the bar: the field or its button */
	boolean contains(double mouseX, double mouseY) {
		return PanelKit.inside(field, mouseX, mouseY) || PanelKit.inside(settingsButton, mouseX, mouseY);
	}

	/** @return whether a point is on the clear button */
	boolean onClear(double mouseX, double mouseY) {
		return clearButton[2] > 0 && PanelKit.inside(clearButton, mouseX, mouseY);
	}
}
