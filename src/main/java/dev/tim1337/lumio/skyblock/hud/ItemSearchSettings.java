package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

/**
 * The "Item search" settings window, opened by the Settings button next to the search bar, in the panels' own calm style: a grip that moves it, the
 * title and a ×. PANELS (Items, Pinned, Recent), LOOK (slot size), BEHAVIOUR (the key that focuses the search, highlighting matches
 * in the inventory) and LAYOUT (Reset of the positions). Every row has one height and every control one width (segmented choices, the key
 * and Reset line up at the right). It opens over the button, closes on a click outside or its ×, and every change applies at once. The same settings, and more (theme,
 * accent, background strength, snapping, Lock layout, the details window), are in the module's settings sheet.
 */
final class ItemSearchSettings {
	static final int WIDTH = 264;
	private static final String TITLE = "Item search";
	private static final int PAD = 10;
	/** The header: a bar as the panels' own, this far below the window's top. */
	private static final int HEADER_TOP = 4;
	private static final int TITLE_ROW = 22;
	private static final int ROW = 20;
	/** The one width of the controls at the right of the rows: segmented choices share it evenly, the key and Reset fill it. */
	static final int CONTROL_WIDTH = 120;
	private static final int SECTION = 13;
	/** The room over a section's title, after the rows before it. */
	private static final int SECTION_GAP = 5;
	private static final int SWITCH_WIDTH = 18;
	private static final int SWITCH_HEIGHT = 10;
	private static final int SWITCH_KNOB_INSET = 2;
	private static final int CLOSE = 12;
	private static final int CLOSE_ICON = 6;
	private static final float CLOSE_HOVER = 0.15f;
	/** The window's body is at least this opaque, so its rows read over the panels, and its faint outline. */
	private static final float LEAST_OPACITY = 0.94f;
	private static final float OUTLINE = 0.1f;
	private static final float ROW_LINE = 0.05f;
	private static final float ROW_HOVER = 0.06f;
	private static final float SWITCH_OFF = 0.2f;
	private static final float TEXT_DROP = 4.5f;
	/** How far above the Settings button it opens. */
	private static final int ABOVE_BUTTON = 6;
	/** The window fades in and out over this time, like a switch of tabs in the menu; a frame counts at most this long. */
	private static final float FADE_SECONDS = 0.18f;
	private static final float LONGEST_FRAME_SECONDS = 0.1f;
	private static final float NANOS_PER_SECOND = 1_000_000_000f;
	private static final ItemPanelKind[] PANELS = {ItemPanelKind.ITEMS, ItemPanelKind.PINNED, ItemPanelKind.RECENT};
	private static final String[] SIZES = {"Small", "Normal", "Large"};
	/** The item sizes Small, Normal and Large stand for. */
	static final float[] SIZE_VALUES = {0.8f, 1f, 1.25f};
	private static final String RESET = "Reset";
	/** The rows of each section, for the window's height. */
	private static final int LOOK_ROWS = 1;
	private static final int BEHAVIOUR_ROWS = 2;
	private static final int LAYOUT_ROWS = 1;

	private final ItemListFeature feature;
	private final int[] box = new int[4];
	private final int[] header = new int[4];
	private final int[] closeButton = new int[4];
	private final int[][] panelRows = new int[PANELS.length][4];
	private final int[][] sizeBoxes = new int[SIZES.length][];
	private final int[] focusChip = new int[4];
	private final int[] highlightRow = new int[4];
	private final int[] resetButton = new int[4];
	private boolean open;
	/** Where the window was moved by its grip, from its place over the button; kept while the game runs. */
	private int offsetX;
	private int offsetY;
	/** Whether the grip is held, and where it was pressed, less the offset then. */
	private boolean moving;
	private double moveFromX;
	private double moveFromY;
	private float shown;
	private long lastFrame;

	ItemSearchSettings(ItemListFeature feature) {
		this.feature = feature;
	}

	boolean open() {
		return open;
	}

	void toggle() {
		open = !open;
		moving = false;
	}

	void close() {
		open = false;
		moving = false;
	}

	/** @return the window's box while it is open, else null */
	int[] box() {
		return open ? box.clone() : null;
	}

	boolean contains(double mouseX, double mouseY) {
		return open && PanelKit.inside(box, mouseX, mouseY);
	}

	/** @return the window's height: its header, the four sections with their rows, and the room around them */
	static int height() {
		int rows = PANELS.length + LOOK_ROWS + BEHAVIOUR_ROWS + LAYOUT_ROWS;
		return 2 * PAD + TITLE_ROW + 4 * SECTION + rows * ROW + 3 * SECTION_GAP - PAD / 2;
	}

	/** Draws it over the Settings button (or where its grip moved it), kept on the screen; it fades in when opened and out when closed. */
	void draw(Canvas ui, int[] button, int screenWidth, int screenHeight, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0L ? 0f : Math.min(LONGEST_FRAME_SECONDS, (now - lastFrame) / NANOS_PER_SECOND);
		lastFrame = now;
		shown = Math.clamp(shown + (open ? seconds : -seconds) / FADE_SECONDS, 0f, 1f);
		if (shown <= 0f) {
			return;
		}
		if (moving) {
			offsetX = (int) Math.round(mouseX - moveFromX);
			offsetY = (int) Math.round(mouseY - moveFromY);
		}
		float before = Draw.alpha();
		Draw.setAlpha(before * shown * shown * (3f - 2f * shown));
		try {
			drawWindow(ui, button, screenWidth, screenHeight, open ? mouseX : -1, open ? mouseY : -1);
		} finally {
			Draw.setAlpha(before);
		}
	}

	private void drawWindow(Canvas ui, int[] button, int screenWidth, int screenHeight, int mouseX, int mouseY) {
		ui.graphics().nextStratum();
		int height = height();
		box[2] = Math.min(WIDTH, screenWidth - 2 * ItemPanelLayout.MARGIN);
		box[3] = height;
		int above = button[1] - height - ABOVE_BUTTON;
		int x = button[0] + button[2] - box[2] + offsetX;
		int y = (above >= ItemPanelLayout.MARGIN ? above : ItemPanelLayout.MARGIN) + offsetY;
		box[0] = Math.clamp(x, 0, Math.max(0, screenWidth - box[2]));
		box[1] = Math.clamp(y, 0, Math.max(0, screenHeight - height));
		PanelFrame.body(ui, box[0], box[1], box[2], box[3], Radius.LG, Math.max(LEAST_OPACITY, PanelFrame.bodyOpacity()));
		ui.ring(box[0], box[1], box[2], box[3], Radius.LG, 1, Draw.withOpacity(HudTheme.text(), OUTLINE));
		int left = box[0] + PAD;
		int width = box[2] - 2 * PAD;
		drawHeader(ui, mouseX, mouseY);
		int top = box[1] + TITLE_ROW;

		section(ui, "PANELS", left, top);
		top += SECTION;
		for (int i = 0; i < PANELS.length; i++) {
			row(panelRows[i], left, top, width);
			switchRow(ui, panelRows[i], PANELS[i].title, feature.panelOn(PANELS[i]), i < PANELS.length - 1, mouseX, mouseY);
			top += ROW;
		}

		section(ui, "LOOK", left, top + SECTION_GAP);
		top += SECTION + SECTION_GAP;
		label(ui, "Slot size", left, top);
		segmented(ui, left + width, top, SIZES, sizeIndex(feature.itemSize()), sizeBoxes, mouseX, mouseY);
		top += ROW;

		section(ui, "BEHAVIOUR", left, top + SECTION_GAP);
		top += SECTION + SECTION_GAP;
		label(ui, "Focus search", left, top);
		chip(ui, focusChip, feature.searchKeyHint(), left + width, top, mouseX, mouseY);
		rowLine(ui, left, top, width);
		top += ROW;
		row(highlightRow, left, top, width);
		switchRow(ui, highlightRow, "Highlight matches in the inventory", feature.highlightMatches(), false, mouseX, mouseY);
		top += ROW;

		section(ui, "LAYOUT", left, top + SECTION_GAP);
		top += SECTION + SECTION_GAP;
		label(ui, "Positions", left, top);
		chip(ui, resetButton, RESET, left + width, top, mouseX, mouseY);
	}

	/** The header, as a panel's: the grip that moves the window, the title, and the × that closes it. */
	private void drawHeader(Canvas ui, int mouseX, int mouseY) {
		header[0] = box[0] + PAD - PanelFrame.PAD;
		header[1] = box[1] + HEADER_TOP;
		header[2] = box[2] - 2 * (PAD - PanelFrame.PAD);
		header[3] = PanelFrame.BAR;
		int middle = PanelFrame.middle(header);
		PanelFrame.headerGrip(ui, header);
		ui.textAtScaledBold(TITLE, PanelFrame.afterGrip(header), middle - PanelFrame.BODY_HALF, TextScale.BODY, HudTheme.text());
		closeButton[0] = box[0] + box[2] - PAD - CLOSE;
		closeButton[1] = middle - CLOSE / 2;
		closeButton[2] = CLOSE;
		closeButton[3] = CLOSE;
		boolean over = PanelKit.inside(closeButton, mouseX, mouseY);
		if (over) {
			ui.rounded(closeButton[0], closeButton[1], CLOSE, CLOSE, Radius.XS, Draw.withOpacity(HudTheme.text(), CLOSE_HOVER));
		}
		int inset = (CLOSE - CLOSE_ICON) / 2;
		ui.icon("close", closeButton[0] + inset, closeButton[1] + inset, CLOSE_ICON, over ? HudTheme.text() : HudTheme.secondary());
	}

	private static void row(int[] row, int x, int y, int width) {
		row[0] = x;
		row[1] = y;
		row[2] = width;
		row[3] = ROW;
	}

	private static void section(Canvas ui, String title, int x, int y) {
		ui.textAtScaledBold(title, x, y, TextScale.SMALL, HudTheme.secondary());
	}

	private static void label(Canvas ui, String text, int x, int y) {
		ui.textAtScaled(text, x, y + TEXT_DROP, TextScale.SMALL, HudTheme.text());
	}

	/** The faint line under a row that has another one of its section below it. */
	private static void rowLine(Canvas ui, int x, int y, int width) {
		ui.fill(x, y + ROW - 1, width, 1, HudTheme.line(ROW_LINE));
	}

	private static void switchRow(Canvas ui, int[] row, String label, boolean on, boolean lineUnder, int mouseX, int mouseY) {
		if (PanelKit.inside(row, mouseX, mouseY)) {
			ui.rounded(row[0] - 3, row[1], row[2] + 6, row[3], Radius.XS, Draw.withOpacity(HudTheme.text(), ROW_HOVER));
		}
		label(ui, label, row[0], row[1]);
		if (lineUnder) {
			rowLine(ui, row[0], row[1], row[2]);
		}
		int x = row[0] + row[2] - SWITCH_WIDTH;
		int y = row[1] + (row[3] - SWITCH_HEIGHT) / 2;
		// switches keep their rounded look
		ui.roundedSmooth(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2f, on ? ui.accent() : Draw.withOpacity(HudTheme.text(), SWITCH_OFF));
		float knob = SWITCH_HEIGHT - 2 * SWITCH_KNOB_INSET;
		float knobX = on ? x + SWITCH_WIDTH - SWITCH_KNOB_INSET - knob / 2f : x + SWITCH_KNOB_INSET + knob / 2f;
		ui.discAt(knobX, y + SWITCH_HEIGHT / 2f, knob, 0xFFFFFFFF);
	}

	/** A segmented control of the one control width, right-aligned and centered in its row. */
	private static void segmented(Canvas ui, int right, int y, String[] labels, int chosen, int[][] boxes, int mouseX, int mouseY) {
		PanelKit.segmented(ui, right - CONTROL_WIDTH, y + (ROW - PanelKit.SEGMENT_HEIGHT) / 2, CONTROL_WIDTH, labels, chosen, mouseX, mouseY, boxes);
	}

	/** A box with a word (the focus key, Reset) of the one control width, right-aligned and centered in its row. */
	private static void chip(Canvas ui, int[] chip, String text, int right, int y, int mouseX, int mouseY) {
		int width = CONTROL_WIDTH;
		chip[0] = right - width;
		chip[1] = y + (ROW - PanelKit.CHIP_HEIGHT) / 2;
		chip[2] = width;
		chip[3] = PanelKit.CHIP_HEIGHT;
		PanelKit.chip(ui, chip, text, false, mouseX, mouseY);
	}

	/** @return the size segment (Small, Normal, Large) nearest to an item size */
	static int sizeIndex(float size) {
		int best = 0;
		for (int i = 1; i < SIZE_VALUES.length; i++) {
			if (Math.abs(SIZE_VALUES[i] - size) < Math.abs(SIZE_VALUES[best] - size)) {
				best = i;
			}
		}
		return best;
	}

	/** The mouse button let go: the window stays where its grip was let go. */
	void release() {
		moving = false;
	}

	/** @return whether a click was on the window; a click outside closes it (and is the caller's) */
	boolean click(double mouseX, double mouseY) {
		if (!open) {
			return false;
		}
		if (!PanelKit.inside(box, mouseX, mouseY)) {
			close();
			return false;
		}
		if (PanelKit.inside(closeButton, mouseX, mouseY)) {
			close();
			return true;
		}
		if (!feature.layoutLocked() && PanelFrame.onGrip(header, mouseX, mouseY)) {
			moving = true;
			moveFromX = mouseX - offsetX;
			moveFromY = mouseY - offsetY;
			return true;
		}
		for (int i = 0; i < PANELS.length; i++) {
			if (PanelKit.inside(panelRows[i], mouseX, mouseY)) {
				feature.setPanelOn(PANELS[i], !feature.panelOn(PANELS[i]));
				return true;
			}
		}
		int size = hit(sizeBoxes, mouseX, mouseY);
		if (size >= 0) {
			feature.setItemSize(SIZE_VALUES[size]);
			return true;
		}
		if (PanelKit.inside(focusChip, mouseX, mouseY)) {
			feature.setTabFocuses(!feature.tabFocuses());
			return true;
		}
		if (PanelKit.inside(highlightRow, mouseX, mouseY)) {
			feature.setHighlightMatches(!feature.highlightMatches());
			return true;
		}
		if (PanelKit.inside(resetButton, mouseX, mouseY)) {
			feature.resetLayout();
			offsetX = 0;
			offsetY = 0;
		}
		return true;
	}

	/** @return the segment of a segmented control under a point, or -1 */
	private static int hit(int[][] boxes, double mouseX, double mouseY) {
		for (int i = 0; i < boxes.length; i++) {
			if (boxes[i] != null && PanelKit.inside(boxes[i], mouseX, mouseY)) {
				return i;
			}
		}
		return -1;
	}
}
