package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's own shelf of items (Pinned), in their order, each in at most one of the player's groups. This is not the Favorite items module, which
 * stars and protects items; the item list's old favorites were moved here once. Kept per SkyBlock profile ({@link Profiles}). Plain data, so it is
 * unit tested.
 */
final class PinnedItems {
	/** The group every pinned item is in: shown as "All". */
	static final String ALL = "";
	static final int MOST_PINS = 300;
	static final int MOST_GROUPS = 12;
	static final int MOST_GROUP_NAME = 16;
	private static final int MOST_ID = 96;

	/** A pinned item and its group (ALL for none). */
	record Pin(String id, String group) {
	}

	private final List<String> groups = new ArrayList<>();
	private final List<Pin> pins = new ArrayList<>();
	/** Counts up with every change, so a view built from it knows when to build again. */
	private int version;

	int version() {
		return version;
	}

	int size() {
		return pins.size();
	}

	boolean isEmpty() {
		return pins.isEmpty();
	}

	/** @return the player's own groups, in their order (not All) */
	List<String> groups() {
		return groups;
	}

	boolean has(String id) {
		return indexOf(id) >= 0;
	}

	/** @return the group of a pinned item, or null when it is not pinned */
	String groupOf(String id) {
		int index = indexOf(id);
		return index < 0 ? null : pins.get(index).group();
	}

	/** @return the items of a group in their order; ALL for every item */
	List<String> ids(String group) {
		List<String> ids = new ArrayList<>();
		for (Pin pin : pins) {
			if (ALL.equals(group) || pin.group().equals(group)) {
				ids.add(pin.id());
			}
		}
		return ids;
	}

	int count(String group) {
		if (ALL.equals(group)) {
			return pins.size();
		}
		int count = 0;
		for (Pin pin : pins) {
			if (pin.group().equals(group)) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Pins an item into a group, or moves it there when it is pinned already.
	 *
	 * @param before the item it goes before, or null for the end
	 * @return whether anything changed (false when full or unknown)
	 */
	boolean add(String id, String group, String before) {
		if (id == null || id.isEmpty() || id.length() > MOST_ID || !knownGroup(group)) {
			return false;
		}
		int at = indexOf(id);
		if (at < 0 && pins.size() >= MOST_PINS) {
			return false;
		}
		if (at >= 0) {
			pins.remove(at);
		}
		int target = before == null || before.equals(id) ? -1 : indexOf(before);
		Pin pin = new Pin(id, group);
		if (target < 0) {
			pins.add(pin);
		} else {
			pins.add(target, pin);
		}
		version++;
		return true;
	}

	/** Moves a pinned item into a group, keeping its place in the order. @return whether it changed */
	boolean move(String id, String group) {
		int at = indexOf(id);
		if (at < 0 || !knownGroup(group) || pins.get(at).group().equals(group)) {
			return false;
		}
		pins.set(at, new Pin(id, group));
		version++;
		return true;
	}

	/** Takes an item off the shelf. @return whether it was pinned */
	boolean remove(String id) {
		int at = indexOf(id);
		if (at < 0) {
			return false;
		}
		pins.remove(at);
		version++;
		return true;
	}

	/** Pins an item at the end of All, or takes it off when it is pinned: the Pin key. @return whether it is pinned now */
	boolean toggle(String id) {
		if (remove(id)) {
			return false;
		}
		return add(id, ALL, null);
	}

	/** Adds a group with a free name made from the wanted one. @return its name, or null when there are too many */
	String addGroup(String wanted) {
		if (groups.size() >= MOST_GROUPS) {
			return null;
		}
		String base = cleanName(wanted);
		if (base.isEmpty()) {
			base = "Group";
		}
		String name = base;
		for (int n = 2; groups.contains(name); n++) {
			String suffix = " " + n;
			name = base.substring(0, Math.min(base.length(), MOST_GROUP_NAME - suffix.length())) + suffix;
		}
		groups.add(name);
		version++;
		return name;
	}

	/** Renames a group; its items follow. @return whether it was renamed (not to an empty or taken name) */
	boolean renameGroup(String from, String to) {
		String name = cleanName(to);
		int at = groups.indexOf(from);
		if (at < 0 || name.isEmpty() || groups.contains(name)) {
			return false;
		}
		groups.set(at, name);
		for (int i = 0; i < pins.size(); i++) {
			if (pins.get(i).group().equals(from)) {
				pins.set(i, new Pin(pins.get(i).id(), name));
			}
		}
		version++;
		return true;
	}

	/** Deletes a group; its items stay pinned, in All. @return whether it existed */
	boolean deleteGroup(String name) {
		if (!groups.remove(name)) {
			return false;
		}
		for (int i = 0; i < pins.size(); i++) {
			if (pins.get(i).group().equals(name)) {
				pins.set(i, new Pin(pins.get(i).id(), ALL));
			}
		}
		version++;
		return true;
	}

	private boolean knownGroup(String group) {
		return ALL.equals(group) || groups.contains(group);
	}

	private int indexOf(String id) {
		for (int i = 0; i < pins.size(); i++) {
			if (pins.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	/** @return a group name as typed: without line breaks or section signs, trimmed, at most its length */
	static String cleanName(String name) {
		if (name == null) {
			return "";
		}
		StringBuilder clean = new StringBuilder();
		name.strip().codePoints().filter(c -> c >= ' ' && c != 0x7F).forEach(clean::appendCodePoint);
		String text = TextColors.noColors(clean.toString()).strip();
		return text.length() > MOST_GROUP_NAME ? text.substring(0, MOST_GROUP_NAME).strip() : text;
	}

	PinnedItems copy() {
		PinnedItems copy = new PinnedItems();
		copy.groups.addAll(groups);
		copy.pins.addAll(pins);
		return copy;
	}

	JsonObject toJson() {
		JsonObject json = new JsonObject();
		JsonArray groupList = new JsonArray();
		groups.forEach(groupList::add);
		json.add("groups", groupList);
		JsonArray items = new JsonArray();
		for (Pin pin : pins) {
			JsonObject item = new JsonObject();
			item.addProperty("id", pin.id());
			if (!pin.group().isEmpty()) {
				item.addProperty("group", pin.group());
			}
			items.add(item);
		}
		json.add("items", items);
		return json;
	}

	/** @return the shelf read from the file; broken parts are left out, and it is bounded like one made in the game */
	static PinnedItems fromJson(JsonObject json) {
		PinnedItems shelf = new PinnedItems();
		if (json == null) {
			return shelf;
		}
		if (json.has("groups") && json.get("groups").isJsonArray()) {
			for (JsonElement group : json.getAsJsonArray("groups")) {
				String name = group.isJsonPrimitive() ? cleanName(group.getAsString()) : "";
				if (!name.isEmpty() && !shelf.groups.contains(name) && shelf.groups.size() < MOST_GROUPS) {
					shelf.groups.add(name);
				}
			}
		}
		if (json.has("items") && json.get("items").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("items")) {
				if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) {
					continue;
				}
				JsonObject item = element.getAsJsonObject();
				String group = item.has("group") ? cleanName(item.get("group").getAsString()) : ALL;
				shelf.add(item.get("id").getAsString(), shelf.knownGroup(group) ? group : ALL, null);
			}
		}
		shelf.version = 0;
		return shelf;
	}

	/** @return a shelf with the item list's old favorites, in their order, ungrouped */
	static PinnedItems migrated(Collection<String> favorites) {
		PinnedItems shelf = new PinnedItems();
		for (String id : favorites) {
			shelf.add(id, ALL, null);
		}
		return shelf;
	}

	/**
	 * The shelves per SkyBlock profile. Until the server names the profile, the shelf of the last profile played is shown; a profile without a shelf of
	 * its own starts with what was pinned before any profile was known, or else with the moved favorites.
	 */
	static final class Profiles {
		/** Bounds the file: more profiles than any player has. */
		static final int MOST_PROFILES = 32;
		/** The shelf for when no profile was ever named. */
		static final String UNKNOWN = "";

		private final Map<String, PinnedItems> byProfile = new HashMap<>();
		/** The moved favorites, given to each profile that has no shelf yet. */
		private PinnedItems migrated = new PinnedItems();
		private String last = UNKNOWN;

		/** @return the shelf of a profile (null while the server has not named one: the last profile's) */
		PinnedItems of(String profile) {
			boolean named = profile != null && !profile.isEmpty();
			String key = named ? profile : last;
			PinnedItems shelf = byProfile.get(key);
			if (shelf == null) {
				// what was pinned before the server named a profile belongs to the first one named
				PinnedItems before = named ? byProfile.remove(UNKNOWN) : null;
				shelf = before != null && !before.isEmpty() ? before : migrated.copy();
				if (byProfile.size() < MOST_PROFILES) {
					byProfile.put(key, shelf);
				}
			}
			if (named) {
				last = key;
			}
			return shelf;
		}

		/** Takes the item list's old favorites in, for every profile that has no shelf yet. */
		void migrate(Collection<String> favorites) {
			migrated = PinnedItems.migrated(favorites);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			JsonObject profiles = new JsonObject();
			byProfile.forEach((profile, shelf) -> profiles.add(profile, shelf.toJson()));
			json.add("profiles", profiles);
			if (!migrated.isEmpty()) {
				json.add("migrated", migrated.toJson());
			}
			json.addProperty("last", last);
			return json;
		}

		void load(JsonObject json) {
			byProfile.clear();
			migrated = new PinnedItems();
			last = UNKNOWN;
			if (json == null) {
				return;
			}
			if (json.has("profiles") && json.get("profiles").isJsonObject()) {
				for (Map.Entry<String, JsonElement> profile : json.getAsJsonObject("profiles").entrySet()) {
					if (byProfile.size() < MOST_PROFILES && profile.getValue().isJsonObject() && profile.getKey().length() <= MOST_ID) {
						byProfile.put(profile.getKey(), fromJson(profile.getValue().getAsJsonObject()));
					}
				}
			}
			if (json.has("migrated") && json.get("migrated").isJsonObject()) {
				migrated = fromJson(json.getAsJsonObject("migrated"));
			}
			if (json.has("last") && json.get("last").isJsonPrimitive() && byProfile.containsKey(json.get("last").getAsString())) {
				last = json.get("last").getAsString();
			}
		}
	}
}
