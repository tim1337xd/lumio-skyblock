package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.draw.Canvas;

/**
 * Numbers that never move sideways: every digit sits in a cell as wide as the widest digit, and a number is aligned at its right end, so a value that
 * counts up or a clock that runs keeps every character in its place. Other characters (":", ".", "/", letters) keep their own width.
 */
public final class TabularText {
	private static final String DIGITS = "0123456789";
	/** One string per character up to 0x2FFF, made on first use, so drawing makes no new strings. */
	private static final String[] ONE = new String[0x3000];
	private static float cachedScale = Float.NaN;
	private static boolean cachedBold;
	private static float cell;

	private TabularText() {
	}

	/** @return the width the text takes when drawn tabular */
	public static float width(Canvas ui, String text, float scale, boolean bold) {
		float digit = digitCell(ui, scale, bold);
		float width = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			width += Character.isDigit(c) ? digit : charWidth(ui, c, scale, bold);
		}
		return width;
	}

	/** Draws the text with its right end at a place. */
	public static void drawRight(Canvas ui, String text, float right, float y, float scale, boolean bold, int color) {
		draw(ui, text, right - width(ui, text, scale, bold), y, scale, bold, color);
	}

	/** Draws the text from a place on its left. */
	public static void draw(Canvas ui, String text, float left, float y, float scale, boolean bold, int color) {
		float digit = digitCell(ui, scale, bold);
		float x = left;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			String one = one(c);
			if (Character.isDigit(c)) {
				// the digit in the middle of its cell
				float own = charWidth(ui, c, scale, bold);
				text(ui, one, x + (digit - own) / 2f, y, scale, bold, color);
				x += digit;
			} else {
				text(ui, one, x, y, scale, bold, color);
				x += charWidth(ui, c, scale, bold);
			}
		}
	}

	private static String one(char c) {
		if (c >= ONE.length) {
			return String.valueOf(c);
		}
		String made = ONE[c];
		if (made == null) {
			made = Character.toString(c);
			ONE[c] = made;
		}
		return made;
	}

	private static void text(Canvas ui, String text, float x, float y, float scale, boolean bold, int color) {
		if (bold) {
			ui.textAtScaledBold(text, x, y, scale, color);
		} else {
			ui.textAtScaled(text, x, y, scale, color);
		}
	}

	private static float charWidth(Canvas ui, char c, float scale, boolean bold) {
		String one = one(c);
		return bold ? ui.boldWidthScaled(one, scale) : ui.widthScaled(one, scale);
	}

	/** @return the width of the widest digit at a scale, worked out once per scale and weight */
	private static float digitCell(Canvas ui, float scale, boolean bold) {
		if (scale != cachedScale || bold != cachedBold) {
			float widest = 0;
			for (int i = 0; i < DIGITS.length(); i++) {
				widest = Math.max(widest, charWidth(ui, DIGITS.charAt(i), scale, bold));
			}
			cachedScale = scale;
			cachedBold = bold;
			cell = widest;
		}
		return cell;
	}
}
