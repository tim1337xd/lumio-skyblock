package dev.tim1337.lumio.skyblock.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The item search's saved layout: per kind of window (the inventory, a chest of a number of rows, other menus) where the player put each panel. A kind
 * the player has not arranged yet takes the inventory's arrangement, and without that the chosen starting layout. Plain data, so it is unit tested.
 */
final class PanelPlaces {
	/** The window kind whose arrangement the others start from. */
	static final String INVENTORY = "inventory";
	/** How many window kinds are read from a file at most; there are only a few. */
	private static final int MOST_KINDS = 32;

	private final Map<String, EnumMap<ItemPanelKind, PanelPlace>> byWindow = new HashMap<>();

	/** @return where the player put a panel in a kind of window, or null where it follows the starting layout */
	PanelPlace get(String window, ItemPanelKind kind) {
		EnumMap<ItemPanelKind, PanelPlace> own = byWindow.get(window);
		if (own == null) {
			own = byWindow.get(INVENTORY);
		}
		return own == null ? null : own.get(kind);
	}

	/** Keeps where the player put a panel in a kind of window; the window's other panels keep what they had (also when that came from the inventory). */
	void put(String window, ItemPanelKind kind, PanelPlace place) {
		EnumMap<ItemPanelKind, PanelPlace> own = byWindow.get(window);
		if (own == null) {
			EnumMap<ItemPanelKind, PanelPlace> inventory = byWindow.get(INVENTORY);
			own = inventory == null ? new EnumMap<>(ItemPanelKind.class) : new EnumMap<>(inventory);
			byWindow.put(window, own);
		}
		own.put(kind, place);
	}

	/** Forgets every arrangement: all windows follow the starting layout again. */
	void clear() {
		byWindow.clear();
	}

	/**
	 * Forgets where the player put one panel, in every kind of window: it follows the starting layout again, the other panels stay where they were
	 * put. For a panel whose starting place changed (the search bar, now at the bottom of the screen).
	 */
	void forget(ItemPanelKind kind) {
		byWindow.values().forEach(places -> places.remove(kind));
		byWindow.values().removeIf(EnumMap::isEmpty);
	}

	boolean isEmpty() {
		return byWindow.isEmpty();
	}

	/** @return the kind of a window for its layout: the inventory, "chest" with its rows, or "menu" for any other */
	static String windowKind(boolean inventory, int chestRows) {
		if (inventory) {
			return INVENTORY;
		}
		return chestRows > 0 ? "chest" + chestRows : "menu";
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		byWindow.forEach((window, places) -> {
			JsonObject panels = new JsonObject();
			places.forEach((kind, place) -> panels.add(kind.key, place.toJson()));
			json.add(window, panels);
		});
		return json;
	}

	/** Reads a saved layout; broken parts are left out. */
	void load(JsonObject json) {
		byWindow.clear();
		if (json == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> window : json.entrySet()) {
			if (byWindow.size() >= MOST_KINDS || !window.getValue().isJsonObject()) {
				continue;
			}
			EnumMap<ItemPanelKind, PanelPlace> places = new EnumMap<>(ItemPanelKind.class);
			for (Map.Entry<String, JsonElement> panel : window.getValue().getAsJsonObject().entrySet()) {
				ItemPanelKind kind = ItemPanelKind.byKey(panel.getKey());
				PanelPlace place = kind != null && panel.getValue().isJsonObject() ? PanelPlace.fromJson(panel.getValue().getAsJsonObject()) : null;
				if (place != null) {
					places.put(kind, place);
				}
			}
			if (!places.isEmpty()) {
				byWindow.put(window.getKey(), places);
			}
		}
	}
}
