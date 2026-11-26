package dev.tim1337.lumio.skyblock.hud.buttons;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * The buttons as clipboard text, to share a layout: a header line and the buttons as JSON, readable as it is ("command": "/warp hub"). Pasting reads
 * only text with the header; anything else, broken JSON, or a list without a valid button is refused, and nothing changes. Plain text, so it is unit
 * tested.
 */
final class ButtonClipboard {
	static final String HEADER = "Lumio inventory buttons 1";
	/** Text longer than this is not a layout of 28 buttons. */
	private static final int MOST_TEXT = 64 * 1024;

	/** What was read: the buttons, or why there are none. */
	record Read(List<ButtonLayout.Placed> buttons, String problem) {
		boolean ok() {
			return problem == null;
		}
	}

	private ButtonClipboard() {
	}

	static String write(ButtonLayout layout) {
		return HEADER + "\n" + layout.toJson();
	}

	static Read read(String text) {
		if (text == null || text.isBlank()) {
			return new Read(List.of(), "The clipboard is empty");
		}
		if (text.length() > MOST_TEXT) {
			return new Read(List.of(), "The clipboard holds too much text");
		}
		String trimmed = text.strip();
		if (!trimmed.startsWith(HEADER)) {
			return new Read(List.of(), "The clipboard holds no inventory buttons");
		}
		try {
			JsonElement json = JsonParser.parseString(trimmed.substring(HEADER.length()).strip());
			if (!json.isJsonArray()) {
				return new Read(List.of(), "The buttons could not be read");
			}
			List<ButtonLayout.Placed> buttons = ButtonLayout.fromJson(json.getAsJsonArray());
			return buttons.isEmpty() ? new Read(List.of(), "There is no valid button in it") : new Read(buttons, null);
		} catch (RuntimeException e) {
			return new Read(List.of(), "The buttons could not be read");
		}
	}
}
