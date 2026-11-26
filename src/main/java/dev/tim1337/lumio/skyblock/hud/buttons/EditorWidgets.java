package dev.tim1337.lumio.skyblock.hud.buttons;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

/** The editor's small parts: dashed free spots, switches, small buttons and section labels, in Lumio's look (boxes, not pills). */
final class EditorWidgets {
	static final int SWITCH_WIDTH = 18;
	static final int SWITCH_HEIGHT = 10;
	static final int SEGMENT_HEIGHT = 14;
	private static final int SEGMENT_PAD = 4;
	private static final float SEGMENT_SCALE = TextScale.SMALL * 0.9f;
	private static final int DASH = 2;
	private static final int DASH_STEP = 4;

	private EditorWidgets() {
	}

	static boolean inside(int[] box, double x, double y) {
		return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}

	static void set(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	/** A free spot: a dashed outline, lit while something can be dropped on it. */
	static void dashed(Canvas ui, int x, int y, int width, int height, boolean lit) {
		int color = lit ? ui.accent() : Draw.withOpacity(HudTheme.text(), 0.28f);
		if (lit) {
			ui.rounded(x, y, width, height, Radius.SM, Draw.withOpacity(ui.accent(), 0.15f));
		}
		for (int i = 2; i < width - 2; i += DASH_STEP) {
			int length = Math.min(DASH, width - 2 - i);
			ui.fill(x + i, y, length, 1, color);
			ui.fill(x + i, y + height - 1, length, 1, color);
		}
		for (int i = 2; i < height - 2; i += DASH_STEP) {
			int length = Math.min(DASH, height - 2 - i);
			ui.fill(x, y + i, 1, length, color);
			ui.fill(x + width - 1, y + i, 1, length, color);
		}
	}

	/** A switch; switches keep their rounded look. */
	static void toggle(Canvas ui, int x, int y, boolean on) {
		float radius = SWITCH_HEIGHT / 2f;
		ui.roundedSmooth(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, radius, on ? ui.accent() : Draw.withOpacity(HudTheme.text(), 0.2f));
		float knob = SWITCH_HEIGHT - 3;
		float knobX = on ? x + SWITCH_WIDTH - knob - 1.5f : x + 1.5f;
		ui.roundedSmooth(knobX, y + 1.5f, knob, knob, knob / 2f, 0xFFFFFFFF);
	}

	/** A small button with a label: the main one in the accent, a quiet one in glass. */
	static void button(Canvas ui, int[] box, String label, boolean primary, int mouseX, int mouseY) {
		boolean over = inside(box, mouseX, mouseY);
		if (primary) {
			ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, over ? Draw.mix(ui.accent(), 0xFFFFFFFF, 0.15f) : ui.accent());
		} else {
			ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.16f : 0.08f));
		}
		float width = ui.widthScaled(label, TextScale.SMALL);
		ui.textAtScaled(label, box[0] + (box[2] - width) / 2f, box[1] + box[3] / 2f - 3.5f, TextScale.SMALL, primary ? 0xFFFFFFFF : HudTheme.text());
	}

	/** @return the width of a small button with this label */
	static int buttonWidth(Canvas ui, String label) {
		return Math.round(ui.widthScaled(label, TextScale.SMALL)) + 12;
	}

	/** A small square button with an icon (duplicate, delete). */
	static void iconButton(Canvas ui, int[] box, String icon, int mouseX, int mouseY) {
		boolean over = inside(box, mouseX, mouseY);
		ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(HudTheme.text(), over ? 0.16f : 0.07f));
		int size = box[2] - 6;
		ui.icon(icon, box[0] + 3, box[1] + 3, size, over ? HudTheme.text() : HudTheme.secondary());
	}

	/** A compact segmented control: the choices side by side in one box, the chosen one lit. */
	static void segmented(Canvas ui, int x, int y, String[] labels, int chosen, int mouseX, int mouseY, int[][] boxes) {
		int width = segmentedWidth(ui, labels);
		ui.rounded(x, y, width, SEGMENT_HEIGHT, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.08f));
		int cursor = x + 2;
		for (int i = 0; i < labels.length; i++) {
			int part = Math.round(ui.widthScaled(labels[i], SEGMENT_SCALE)) + 2 * SEGMENT_PAD;
			set(boxes[i], cursor, y, part, SEGMENT_HEIGHT);
			boolean over = inside(boxes[i], mouseX, mouseY);
			if (i == chosen) {
				ui.rounded(cursor, y + 2, part, SEGMENT_HEIGHT - 4, Radius.XS, Draw.withOpacity(HudTheme.text(), 0.2f));
			}
			ui.textAtScaled(labels[i], cursor + SEGMENT_PAD, y + SEGMENT_HEIGHT / 2f - 3f, SEGMENT_SCALE,
				i == chosen || over ? HudTheme.text() : HudTheme.secondary());
			cursor += part;
		}
	}

	static int segmentedWidth(Canvas ui, String[] labels) {
		int width = 4;
		for (String label : labels) {
			width += Math.round(ui.widthScaled(label, SEGMENT_SCALE)) + 2 * SEGMENT_PAD;
		}
		return width;
	}

	/** A section's small label in capitals: "COMMAND". */
	static void label(Canvas ui, String text, int x, int y) {
		ui.sectionLabel(text, x, y, HudTheme.secondary());
	}
}
