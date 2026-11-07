package dev.tim1337.lumio.skyblock.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The protected items, by their SkyBlock UUIDs, kept per profile. An item is looked up over all profiles: a UUID belongs to one item only, and the server
 * names the profile some time after joining, when an item must already be safe.
 */
final class ProtectedItems {
	/** Where items go until the server has named the profile. */
	static final String NO_PROFILE = "unknown";
	private static final int ITEMS_MOST = 10_000;
	private static final int PROFILES_MOST = 64;
	private static final int TEXT_MOST = 64;
	private static final String PROFILES = "profiles";

	private final Map<String, Set<String>> byProfile = new HashMap<>();
	private final Set<String> all = new HashSet<>();

	/** @return whether nothing is protected, to be asked first where it is asked often */
	boolean isEmpty() {
		return all.isEmpty();
	}

	boolean has(String uuid) {
		return all.contains(uuid);
	}

	/** @return whether it was added: the number of items and profiles is bounded, as they come from a file */
	boolean add(String profile, String uuid) {
		String key = profile == null ? NO_PROFILE : profile;
		if (all.size() >= ITEMS_MOST || !byProfile.containsKey(key) && byProfile.size() >= PROFILES_MOST) {
			return false;
		}
		byProfile.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(uuid);
		all.add(uuid);
		return true;
	}

	void remove(String uuid) {
		for (Set<String> items : byProfile.values()) {
			items.remove(uuid);
		}
		byProfile.values().removeIf(Set::isEmpty);
		all.remove(uuid);
	}

	/** Takes over what a file kept, next to what was protected in the meantime. */
	void adopt(ProtectedItems kept) {
		kept.byProfile.forEach((profile, items) -> items.forEach(uuid -> add(profile, uuid)));
	}

	JsonObject toJson() {
		JsonObject profiles = new JsonObject();
		byProfile.forEach((profile, items) -> {
			JsonArray list = new JsonArray();
			items.forEach(list::add);
			profiles.add(profile, list);
		});
		JsonObject root = new JsonObject();
		root.add(PROFILES, profiles);
		return root;
	}

	/** @return what a file holds; whatever does not look right in it is left out */
	static ProtectedItems fromJson(JsonObject root) {
		ProtectedItems items = new ProtectedItems();
		if (!root.has(PROFILES) || !root.get(PROFILES).isJsonObject()) {
			return items;
		}
		for (Map.Entry<String, JsonElement> profile : root.getAsJsonObject(PROFILES).entrySet()) {
			if (profile.getKey().length() > TEXT_MOST || !profile.getValue().isJsonArray()) {
				continue;
			}
			for (JsonElement item : profile.getValue().getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && !item.getAsString().isBlank() && item.getAsString().length() <= TEXT_MOST) {
					items.add(profile.getKey(), item.getAsString());
				}
			}
		}
		return items;
	}
}
