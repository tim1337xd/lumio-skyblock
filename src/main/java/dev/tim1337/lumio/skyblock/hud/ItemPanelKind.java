package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.PanelKit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Set;

/**
 * The panels of the item search, in the order they claim room on the screen: the search bar first, then the items, Pinned, Recent and the docked
 * details window. Each has a least size, below which it is not drawn.
 */
enum ItemPanelKind {
	SEARCH("search", "Search", 108, PanelKit.FIELD_HEIGHT),
	ITEMS("items", "Items", 96, 92),
	PINNED("pinned", "Pinned", 112, 70),
	RECENT("recent", "Recent", 96, 48),
	DETAILS("details", "Details window", 176, 150);

	/** Its name in the saved layout. */
	final String key;
	final String title;
	final int leastWidth;
	final int leastHeight;

	ItemPanelKind(String key, String title, int leastWidth, int leastHeight) {
		this.key = key;
		this.title = title;
		this.leastWidth = leastWidth;
		this.leastHeight = leastHeight;
	}

	/** @return whether the player can pull its height: the search bar's is fixed */
	boolean tall() {
		return this != SEARCH;
	}

	/** @return whether it folds up to its header bar: Pinned and Recent */
	boolean collapses() {
		return this == PINNED || this == RECENT;
	}

	/** @return whether its area for the mouse ends where its content ends: Pinned, Recent and Items, which have no body of their own */
	boolean fitsContent() {
		return this == PINNED || this == RECENT || this == ITEMS;
	}

	static ItemPanelKind byKey(String key) {
		for (ItemPanelKind kind : values()) {
			if (kind.key.equals(key)) {
				return kind;
			}
		}
		return null;
	}

	/** @return folded-up panels by their saved names */
	static JsonArray collapsedToJson(Set<ItemPanelKind> kinds) {
		JsonArray array = new JsonArray();
		for (ItemPanelKind kind : kinds) {
			array.add(kind.key);
		}
		return array;
	}

	/** Reads folded-up panels back: only those that fold, and nothing from anything broken. */
	static void collapsedFromJson(JsonElement json, Set<ItemPanelKind> into) {
		into.clear();
		if (json == null || !json.isJsonArray()) {
			return;
		}
		for (JsonElement element : json.getAsJsonArray()) {
			ItemPanelKind kind = element.isJsonPrimitive() ? byKey(element.getAsString()) : null;
			if (kind != null && kind.collapses()) {
				into.add(kind);
			}
		}
	}
}
