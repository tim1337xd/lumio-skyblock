package dev.tim1337.lumio.skyblock.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * A short list of the latest things, newest first, each once: the items the player last looked at (Recent) and the last searches (the arrow keys in
 * the search). Bounded. Plain data, so it is unit tested.
 */
final class RecentList {
	/** The longest entry kept; longer ones are not noted. */
	private static final int MOST_LENGTH = 96;

	private final int most;
	private final List<String> items = new ArrayList<>();
	private int version;

	RecentList(int most) {
		this.most = most;
	}

	/** Notes something as the newest; one noted before moves to the front. */
	void note(String value) {
		if (value == null || value.isBlank() || value.length() > MOST_LENGTH) {
			return;
		}
		if (!items.isEmpty() && items.getFirst().equals(value)) {
			return;
		}
		items.remove(value);
		items.addFirst(value);
		while (items.size() > most) {
			items.removeLast();
		}
		version++;
	}

	/** @return newest first */
	List<String> items() {
		return items;
	}

	int size() {
		return items.size();
	}

	int version() {
		return version;
	}

	void clear() {
		if (!items.isEmpty()) {
			items.clear();
			version++;
		}
	}

	/**
	 * Steps through the list from the field: -1 is what is typed, 0 the newest, upward older.
	 *
	 * @param older whether to go to an older one (arrow up), else a newer one (arrow down)
	 * @return the index stepped to, kept within the list
	 */
	int step(int index, boolean older) {
		if (older) {
			return Math.min(index + 1, items.size() - 1);
		}
		return Math.max(-1, index - 1);
	}

	JsonArray toJson() {
		JsonArray json = new JsonArray();
		items.forEach(json::add);
		return json;
	}

	/** Reads the list, oldest last as saved; broken parts are left out. */
	void load(JsonArray json) {
		items.clear();
		version++;
		if (json == null) {
			return;
		}
		for (JsonElement element : json) {
			if (items.size() >= most) {
				break;
			}
			if (element.isJsonPrimitive()) {
				String value = element.getAsString();
				if (!value.isBlank() && value.length() <= MOST_LENGTH && !items.contains(value)) {
					items.add(value);
				}
			}
		}
	}
}
