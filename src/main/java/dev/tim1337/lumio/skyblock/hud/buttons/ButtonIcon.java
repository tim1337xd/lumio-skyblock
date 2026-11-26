package dev.tim1337.lumio.skyblock.hud.buttons;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A button's icon: an item drawn as the real item (a SkyBlock item by its id, such as "HYPERION", or a game item such as "minecraft:diamond"), or one
 * of Lumio's simple symbols in a color. Saved as a short text: "item:HYPERION", "symbol:paw:ff6ec7".
 */
record ButtonIcon(Kind kind, String value, int rgb) {
	enum Kind {
		ITEM, SYMBOL
	}

	/** Lumio's symbols a button may use. */
	static final List<String> SYMBOLS = List.of("star", "navigate", "skull", "leaf", "pickaxe", "gem", "sun", "eye", "coins", "crown", "paw", "shield",
		"chest", "grid", "drop", "layers", "sword", "fish", "wheat", "map", "portal", "tree", "bolt", "heart", "bell", "clock", "backpack", "people",
		"person", "orb", "sparkle", "moon", "snow", "bug", "terminal", "note");
	static final ButtonIcon DEFAULT = new ButtonIcon(Kind.SYMBOL, "star", 0xFFD60A);
	/** An item id: a SkyBlock id or a namespaced game id. */
	private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_:;.\\-]{1,64}");

	static ButtonIcon item(String id) {
		return new ButtonIcon(Kind.ITEM, id, 0xFFFFFF);
	}

	static ButtonIcon symbol(String name, int rgb) {
		return new ButtonIcon(Kind.SYMBOL, name, rgb & 0xFFFFFF);
	}

	String toText() {
		return kind == Kind.ITEM ? "item:" + value : "symbol:" + value + ":" + String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF);
	}

	/** @return the icon of a saved text, or null for text that is no icon */
	static ButtonIcon parse(String text) {
		if (text == null) {
			return null;
		}
		if (text.startsWith("item:")) {
			String id = text.substring(5);
			return ITEM_ID.matcher(id).matches() ? item(id) : null;
		}
		if (text.startsWith("symbol:")) {
			String[] parts = text.substring(7).split(":", -1);
			if (parts.length != 2 || !SYMBOLS.contains(parts[0]) || parts[1].length() != 6) {
				return null;
			}
			try {
				return symbol(parts[0], Integer.parseInt(parts[1], 16));
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}
}
