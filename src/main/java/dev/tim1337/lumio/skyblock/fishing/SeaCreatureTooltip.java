package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;

import java.util.List;

/**
 * The sea creature panel's tooltip, in the look of the menu's tooltips (a dark box with a soft shadow and a faint edge, the same in every theme):
 * a title, then one line per creature with its name in its color and its numbers in columns. Drawn in the panel's own units, beside the mouse.
 */
final class SeaCreatureTooltip {
	private static final int BACK = 0xF2161618;
	private static final int EDGE = 0x40FFFFFF;
	private static final int SHADOW = 0x66000000;
	private static final int TEXT = 0xFFF5F5F7;
	private static final int SECONDARY = 0xFFA8A8B0;
	private static final float PAD = 7f;
	private static final float LINE = 11f;
	private static final float COLUMN_GAP = 10f;
	private static final float FROM_MOUSE = 10f;
	private static final float SHADOW_SPREAD = 10f;

	/** One creature: its name and color, and its numbers in the order of the columns. */
	record Line(String name, int rgb, String[] values) {
	}

	private SeaCreatureTooltip() {
	}

	/**
	 * @param title  the first line, such as "Rare sea creatures"
	 * @param note   a quiet line under the title, or null
	 * @param lines  the creatures; with none, {@code empty} stands alone
	 * @param mouseX the mouse in the panel's units: the box stands to its right and below, or to its left where the panel is far right
	 */
	static void draw(Canvas ui, String title, String note, List<Line> lines, String empty, float mouseX, float mouseY, boolean toTheLeft) {
		int columns = 0;
		for (Line line : lines) {
			columns = Math.max(columns, line.values().length);
		}
		float nameWidth = ui.boldWidthScaled(title, PanelKit.META);
		float[] widths = new float[columns];
		for (Line line : lines) {
			nameWidth = Math.max(nameWidth, ui.widthScaled(line.name(), PanelKit.META));
			for (int c = 0; c < line.values().length; c++) {
				widths[c] = Math.max(widths[c], ui.widthScaled(line.values()[c], PanelKit.SMALL));
			}
		}
		float valuesWidth = 0f;
		for (float width : widths) {
			valuesWidth += COLUMN_GAP + width;
		}
		float noteWidth = note == null ? 0f : ui.widthScaled(note, PanelKit.SMALL);
		float emptyWidth = lines.isEmpty() ? ui.widthScaled(empty, PanelKit.SMALL) : 0f;
		float inner = Math.max(Math.max(nameWidth + valuesWidth, noteWidth), emptyWidth);
		int rows = 1 + (note == null ? 0 : 1) + Math.max(1, lines.size());
		int width = Math.round(inner + 2 * PAD);
		int height = Math.round(rows * LINE + 2 * PAD - 2f);
		int x = Math.round(toTheLeft ? mouseX - FROM_MOUSE - width : mouseX + FROM_MOUSE);
		int y = Math.round(mouseY + FROM_MOUSE);
		ui.softLight(x + width / 2f, y + height / 2f + 2f, width / 2f + SHADOW_SPREAD, height / 2f + SHADOW_SPREAD, SHADOW);
		ui.rounded(x, y, width, height, Radius.SM, BACK);
		ui.ring(x, y, width, height, Radius.SM, 1, EDGE);
		float textY = y + PAD - 1f;
		ui.textAtScaledBold(title, x + PAD, textY, PanelKit.META, TEXT);
		textY += LINE;
		if (note != null) {
			ui.textAtScaled(note, x + PAD, textY, PanelKit.SMALL, SECONDARY);
			textY += LINE;
		}
		if (lines.isEmpty()) {
			ui.textAtScaled(empty, x + PAD, textY, PanelKit.SMALL, SECONDARY);
			return;
		}
		for (Line line : lines) {
			ui.textAtScaled(line.name(), x + PAD, textY, PanelKit.META, PanelKit.readable(0xFF000000 | line.rgb()));
			// the numbers right-aligned in their columns, after the widest name
			float columnRight = x + PAD + nameWidth;
			for (int c = 0; c < line.values().length; c++) {
				columnRight += COLUMN_GAP + widths[c];
				String value = line.values()[c];
				ui.textAtScaled(value, columnRight - ui.widthScaled(value, PanelKit.SMALL), textY + 0.5f, PanelKit.SMALL, SECONDARY);
			}
			textY += LINE;
		}
	}

	/** @return how long ago, in the largest whole unit: "now", "12 s ago", "4 min ago", "3 h ago", "2 d ago" */
	static String ago(long millis) {
		long seconds = Math.max(0, millis / 1000L);
		if (seconds < 5) {
			return "now";
		}
		if (seconds < 60) {
			return seconds + " s ago";
		}
		if (seconds < 3600) {
			return seconds / 60 + " min ago";
		}
		if (seconds < 86_400) {
			return seconds / 3600 + " h ago";
		}
		return seconds / 86_400 + " d ago";
	}
}
