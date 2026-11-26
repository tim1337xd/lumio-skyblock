package dev.tim1337.lumio.skyblock.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The head each of the player's pets really has (its skin's, not the default of its type), remembered per profile and pet, so the pet panel shows
 * the right one also for a pet known only from a message or the player list, after a restart, and when autopet switches pets. Where a head comes
 * from, best first: the pet's own item in the Pets menu (as that menu showed it last), then the pet's head floating beside the player in the world,
 * then the default of its type from the item list (which is not kept here). A head from the menu is never replaced by one from the world; a newer
 * one from the same or a better source is. Only textures the game already has are kept; nothing is asked of anybody. Plain data, so it is unit
 * tested.
 */
public final class PetHeads {
	/** Where a head was seen, best first. */
	public enum Source {
		MENU, WORLD
	}

	/** A remembered head: its skull texture and where it was seen. */
	public record Head(String texture, Source source) {
	}

	private static final int MOST_PETS = 256;
	private static final int MOST_TEXT = 8192;
	private static final String SEPARATOR = "|";

	private final Map<String, Head> heads = new LinkedHashMap<>();
	private int version;

	/** @return the key of a pet of a profile: its kind by name and its rarity color; two pets of one kind and rarity share it, the one out last wins */
	static String key(String profile, String petName, int rarityColor) {
		return (profile == null ? "" : profile) + SEPARATOR + petName.strip().toLowerCase(Locale.ROOT) + SEPARATOR + Integer.toHexString(rarityColor & 0xFFFFFF);
	}

	/**
	 * A head was seen for a pet.
	 *
	 * @return whether it is kept: it has a texture, and no head from a better source is known for the pet
	 */
	public boolean seen(String profile, String petName, int rarityColor, String texture, Source source) {
		if (texture == null || texture.isEmpty() || texture.length() > MOST_TEXT || petName == null || petName.isBlank()) {
			return false;
		}
		String key = key(profile, petName, rarityColor);
		Head before = heads.get(key);
		if (before != null && before.source().ordinal() < source.ordinal()) {
			return false;
		}
		if (before != null && before.texture().equals(texture) && before.source() == source) {
			return true;
		}
		heads.remove(key);
		heads.put(key, new Head(texture, source));
		while (heads.size() > MOST_PETS) {
			heads.remove(heads.keySet().iterator().next());
		}
		version++;
		return true;
	}

	/** @return the texture remembered for a pet, or "" while none was seen: the default of its type shows then */
	public String texture(String profile, String petName, int rarityColor) {
		Head head = petName == null ? null : heads.get(key(profile, petName, rarityColor));
		return head == null ? "" : head.texture();
	}

	/** @return where the remembered head of a pet was seen, or null */
	public Source source(String profile, String petName, int rarityColor) {
		Head head = petName == null ? null : heads.get(key(profile, petName, rarityColor));
		return head == null ? null : head.source();
	}

	/** @return a number that changes whenever a head is kept, to save then */
	public int version() {
		return version;
	}

	/** @return the heads as "texture, source" by key, for the settings file */
	public Map<String, String[]> export() {
		Map<String, String[]> out = new LinkedHashMap<>();
		heads.forEach((key, head) -> out.put(key, new String[] {head.texture(), head.source().name()}));
		return out;
	}

	/** Puts back what {@link #export} wrote; odd entries are left out. */
	public void restore(Map<String, String[]> kept) {
		for (Map.Entry<String, String[]> entry : kept.entrySet()) {
			String[] value = entry.getValue();
			if (heads.size() >= MOST_PETS || value == null || value.length != 2 || value[0].isEmpty() || value[0].length() > MOST_TEXT) {
				continue;
			}
			try {
				heads.put(entry.getKey(), new Head(value[0], Source.valueOf(value[1])));
			} catch (IllegalArgumentException odd) {
				// a source this version does not know
			}
		}
	}
}
