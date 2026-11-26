package dev.tim1337.lumio.skyblock.hud.buttons;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A one-line text field of the editor, as a box (not a pill): typing at the end, Backspace, Ctrl+Backspace for a word, Ctrl+V to paste, Ctrl+A to
 * clear and type anew; a ring in the accent and a blinking caret while focused.
 */
final class TextField {
	private static final int TEXT_PAD = 6;
	private static final long BLINK_MILLIS = 500;

	private final int most;
	private final String placeholder;
	private final String prefix;
	private String value = "";
	boolean focused;
	final int[] box = new int[4];

	/** @param prefix a quiet mark before the text (such as "›_" for a command), or "" */
	TextField(int most, String placeholder, String prefix) {
		this.most = most;
		this.placeholder = placeholder;
		this.prefix = prefix;
	}

	String value() {
		return value;
	}

	void set(String text) {
		value = text == null ? "" : text.length() > most ? text.substring(0, most) : text;
	}

	void place(int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	boolean inside(double x, double y) {
		return x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
	}

	void draw(Canvas ui, int mouseX, int mouseY) {
		boolean over = inside(mouseX, mouseY);
		ui.rounded(box[0], box[1], box[2], box[3], Radius.SM, Draw.withOpacity(0xFF000000, 0.35f));
		int ring = focused ? ui.accent() : Draw.withOpacity(HudTheme.text(), over ? 0.25f : 0.12f);
		ui.ring(box[0], box[1], box[2], box[3], Radius.SM, 1, ring);
		int middle = box[1] + box[3] / 2;
		int x = box[0] + TEXT_PAD;
		if (!prefix.isEmpty()) {
			ui.textMiddle(prefix, x, middle, HudTheme.secondary());
			x += ui.width(prefix) + 4;
		}
		boolean empty = value.isEmpty();
		int room = box[0] + box[2] - TEXT_PAD - x;
		String shown = empty ? placeholder : value;
		// the end of a long text stays in view, where the caret is
		String fitted = empty || ui.width(shown) <= room ? ui.fit(shown, room) : tail(ui, shown, room);
		ui.textMiddle(fitted, x, middle, empty ? HudTheme.secondary() : HudTheme.text());
		if (focused && (System.currentTimeMillis() / BLINK_MILLIS) % 2 == 0) {
			ui.fill(x + (empty ? 0 : ui.width(fitted)) + 1, box[1] + 4, 1, box[3] - 8, HudTheme.text());
		}
	}

	private static String tail(Canvas ui, String text, int room) {
		int start = 0;
		while (start < text.length() && ui.width(text.substring(start)) > room) {
			start++;
		}
		return text.substring(start);
	}

	/** @return whether the key was the field's */
	boolean key(KeyEvent event) {
		if (!focused) {
			return false;
		}
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (control) {
				String trimmed = value.stripTrailing();
				int space = trimmed.lastIndexOf(' ');
				set(space < 0 ? "" : trimmed.substring(0, space + 1));
			} else if (!value.isEmpty()) {
				set(value.substring(0, value.length() - 1));
			}
			return true;
		}
		if (control && ScreenInput.letter(event.key(), 'v')) {
			String pasted = Minecraft.getInstance().keyboardHandler.getClipboard();
			if (pasted != null) {
				// one line only: a field never takes a line break
				int lineBreak = pasted.indexOf('\n');
				set(value + (lineBreak < 0 ? pasted : pasted.substring(0, lineBreak)).strip());
			}
			return true;
		}
		if (control && ScreenInput.letter(event.key(), 'a')) {
			set("");
			return true;
		}
		return event.key() != InputConstants.KEY_ESCAPE && event.key() != InputConstants.KEY_RETURN && event.key() != InputConstants.KEY_NUMPADENTER
			&& event.key() != InputConstants.KEY_TAB;
	}

	/** @return whether the character was typed into the field */
	boolean typed(CharacterEvent event) {
		if (!focused || !event.isAllowedChatCharacter() || value.length() >= most) {
			return false;
		}
		set(value + new String(Character.toChars(event.codepoint())));
		return true;
	}
}
