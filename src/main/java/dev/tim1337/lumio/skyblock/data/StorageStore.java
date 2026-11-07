package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What is kept of the storage in the config folder, a file per SkyBlock profile, and only there: the file's name and form, and how a profile is told. Plain data, so it is unit tested.
 */
final class StorageStore {
	/** A page as it is kept: its items as written by {@link PetTracker#encodeIcon}. */
	record Saved(String title, int rows, long seen, List<String> items) {
	}

	/** What the server says when a profile starts. Only this form of an id ever becomes part of a file's name. */
	private static final Pattern PROFILE_LINE = Pattern.compile("^Profile ID: ([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");
	/** Before any profile is known, and what older versions wrote. */
	static final String NO_PROFILE_FILE = "storage.json";
	private static final String FILE_PREFIX = "storage-";
	private static final String FILE_SUFFIX = ".json";
	/** Where the file keeps the storage menu's items that open the pages, beside the pages. */
	private static final String MENU = "menu";
	/** Where the storage menu has its pages, as NotEnoughUpdates reads it too. */
	private static final int MENU_FIRST_ENDER = 9;
	private static final int MENU_ENDER_PAGES = 9;
	private static final int MENU_FIRST_BACKPACK = 27;
	private static final int MENU_BACKPACKS = 18;
	private static final String LOCKED = "locked";

	private StorageStore() {
	}

	/** @return the id of the profile a line of the chat names, in lower case, or null */
	static String profileOf(String line) {
		Matcher matcher = PROFILE_LINE.matcher(line.strip());
		return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
	}

	/** @return the name of a profile's file */
	static String fileName(String profile) {
		return profile == null ? NO_PROFILE_FILE : FILE_PREFIX + profile + FILE_SUFFIX;
	}

	/**
	 * @param slot a slot of the storage menu
	 * @param name the plain name of the item in it
	 * @return the page it stands for: by its name ("Ender Chest Page 3", "Empty Backpack Slot 4"), or, for "Locked Page", which has no number, by its place; or null
	 */
	static String keyOfMenuSlot(int slot, String name) {
		String key = StorageTracker.keyOfMenuItem(name);
		if (key != null || !name.toLowerCase(Locale.ROOT).contains(LOCKED)) {
			return key;
		}
		if (slot >= MENU_FIRST_ENDER && slot < MENU_FIRST_ENDER + MENU_ENDER_PAGES) {
			return "e" + (slot - MENU_FIRST_ENDER + 1);
		}
		return slot >= MENU_FIRST_BACKPACK && slot < MENU_FIRST_BACKPACK + MENU_BACKPACKS ? "b" + (slot - MENU_FIRST_BACKPACK + 1) : null;
	}

	/** @return the pages and the storage menu's items, by page, in the form of the file */
	static JsonObject toJson(Map<String, Saved> pages, Map<String, String> menu) {
		JsonObject root = new JsonObject();
		JsonObject menuJson = new JsonObject();
		menu.forEach(menuJson::addProperty);
		root.add(MENU, menuJson);
		pages.forEach((key, page) -> {
			JsonObject json = new JsonObject();
			json.addProperty("title", page.title() == null ? "" : page.title());
			json.addProperty("rows", page.rows());
			json.addProperty("seen", page.seen());
			JsonArray items = new JsonArray();
			page.items().forEach(items::add);
			json.add("items", items);
			root.add(key, json);
		});
		return root;
	}

	/** @return the pages of a file; a page it cannot read is left out */
	static Map<String, Saved> fromJson(JsonObject root) {
		Map<String, Saved> pages = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (entry.getKey().equals(MENU)) {
				continue;
			}
			try {
				JsonObject json = entry.getValue().getAsJsonObject();
				List<String> items = new ArrayList<>();
				if (json.has("items")) {
					for (JsonElement item : json.getAsJsonArray("items")) {
						items.add(item.getAsString());
					}
				}
				pages.put(entry.getKey(), new Saved(json.has("title") ? json.get("title").getAsString() : "", json.has("rows") ? json.get("rows").getAsInt() : 0,
					json.has("seen") ? json.get("seen").getAsLong() : 0L, items));
			} catch (RuntimeException e) {
				// from a file: a broken page is one page less
				continue;
			}
		}
		return pages;
	}

	/** @return the storage menu's items of a file, by page; a file from before they were kept has none, and what cannot be read is left out */
	static Map<String, String> menuFromJson(JsonObject root) {
		Map<String, String> menu = new LinkedHashMap<>();
		if (root.get(MENU) instanceof JsonObject json) {
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					menu.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
		}
		return menu;
	}
}
