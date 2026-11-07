package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The repository's animated pet skins and the skins with variants ({@code constants/animatedskulls.json}): the head textures of each, which
 * variants a skin has, and under which names a pet's extra data holds the variant it shows. Downloaded data: sizes are bounded, odd entries left out.
 *
 * @param textures    the head textures of a skin or variant, by its name ("PET_SKIN_WOLF_DOGE"), each a Base64 texture property
 * @param variants    a skin's variants, by the skin's name, in the order the variant index counts
 * @param variantKeys the names of the extra data fields that hold a pet's variant index
 */
public record RepoPetSkins(Map<String, List<String>> textures, Map<String, List<String>> variants, Set<String> variantKeys) {
	public static final RepoPetSkins EMPTY = new RepoPetSkins(Map.of(), Map.of(), Set.of());
	private static final int MOST_ENTRIES = 8192;
	private static final int MOST_FRAMES = 128;
	private static final int MOST_TEXT = 8192;

	/** @return the skins of the file; whatever is odd in it is left out */
	public static RepoPetSkins of(JsonObject json) {
		Map<String, List<String>> textures = new HashMap<>();
		if (json.get("skins") instanceof JsonObject skins) {
			for (Map.Entry<String, JsonElement> skin : skins.entrySet()) {
				if (textures.size() >= MOST_ENTRIES || !(skin.getValue() instanceof JsonObject entry) || !entry.has("textures") || !entry.get("textures").isJsonArray()) {
					continue;
				}
				List<String> frames = new ArrayList<>();
				for (JsonElement frame : entry.getAsJsonArray("textures")) {
					String text = frame.isJsonPrimitive() ? property(frame.getAsString()) : "";
					if (!text.isEmpty() && frames.size() < MOST_FRAMES) {
						frames.add(text);
					}
				}
				if (!frames.isEmpty()) {
					textures.put(skin.getKey(), List.copyOf(frames));
				}
			}
		}
		Map<String, List<String>> variants = new HashMap<>();
		if (json.get("pet_skin_variant") instanceof JsonObject all) {
			for (Map.Entry<String, JsonElement> skin : all.entrySet()) {
				if (variants.size() >= MOST_ENTRIES || !skin.getValue().isJsonArray()) {
					continue;
				}
				List<String> names = new ArrayList<>();
				for (JsonElement name : skin.getValue().getAsJsonArray()) {
					if (name.isJsonPrimitive() && names.size() < MOST_FRAMES) {
						names.add(name.getAsString());
					}
				}
				variants.put(skin.getKey(), List.copyOf(names));
			}
		}
		Set<String> keys = new HashSet<>();
		if (json.has("pet_skin_nbt_name") && json.get("pet_skin_nbt_name").isJsonArray()) {
			for (JsonElement key : json.getAsJsonArray("pet_skin_nbt_name")) {
				if (key.isJsonPrimitive() && keys.size() < MOST_FRAMES) {
					keys.add(key.getAsString());
				}
			}
		}
		return new RepoPetSkins(Map.copyOf(textures), Map.copyOf(variants), Set.copyOf(keys));
	}

	/** @return the Base64 texture of an entry, which the file writes as "profile id:texture"; "" when it is odd */
	static String property(String entry) {
		if (entry.length() > MOST_TEXT) {
			return "";
		}
		int colon = entry.indexOf(':');
		return (colon >= 0 ? entry.substring(colon + 1) : entry).strip();
	}

	/** @return the first frame of a skin or variant, which is enough for a small icon; "" if the file has none for it */
	public String firstFrame(String name) {
		List<String> frames = textures.get(name);
		return frames == null || frames.isEmpty() ? "" : frames.getFirst();
	}

	/** @return the name of a skin's variant by its index, or "" if it has no such variant */
	public String variant(String skin, int index) {
		List<String> names = variants.get(skin);
		return names == null || index < 0 || index >= names.size() ? "" : names.get(index);
	}
}
