package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one cache of pet icons. A pet is known by what its item's {@code petInfo} says ({@link PetIdentity}): its type and rarity, its skin, the
 * skin's variant and, for a real pet, its UUID. The icon is, in this order: the head of the real item as a Hypixel menu showed it (remembered by
 * the pet's UUID, and dropped when that pet's skin changes); the skin's item, of an animated skin its first frame, of a seasonal, day and night or faction skin the variant of
 * now; the pet's own item without a skin. The heads are kept as their textures, also on disk, so the icons are right straight after a restart.
 * The idea is SkyHanni's; the code is this mod's own.
 */
public final class PetIcons {
	/** How often an icon is made again for the season or the time of day. */
	private static final long RECHECK_MILLIS = 5000L;
	private static final int MOST_ICONS = 512;
	private static final int MOST_TEXT = 8192;

	/** A made icon (its stack null until first wanted, when it was kept from the last start), the look it was made for and when. */
	private record Icon(String look, String variantNow, ItemStack stack, String texture, long madeAt) {
	}

	private static final Map<String, Icon> ICONS = new LinkedHashMap<>();
	/** The heads of real pets as a Hypixel menu showed them, by key, with the look they had then. */
	private static final Map<String, String[]> REAL = new LinkedHashMap<>();
	/** Identities read per custom data instance: the same data is the same pet, and its JSON is parsed once. */
	private static final Map<CustomData, PetIdentity> READ = new IdentityHashMap<>();
	private static int version;
	/** The last pet asked for by the panels, so a frame asks nothing new. */
	private static Object lastAsked;
	private static ItemStack lastIcon = ItemStack.EMPTY;
	private static long lastAskedAt;

	private PetIcons() {
	}

	/** @return the pet an item is, or null if it is not a pet */
	public static PetIdentity of(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		if (READ.containsKey(data)) {
			return READ.get(data);
		}
		if (READ.size() > MOST_ICONS) {
			READ.clear();
		}
		PetIdentity identity = null;
		String raw = data.copyTag().getStringOr("petInfo", "");
		if (!raw.isEmpty() && raw.length() <= MOST_TEXT) {
			try {
				if (JsonParser.parseString(raw) instanceof JsonObject info) {
					identity = PetIdentity.read(info, ItemRepo.petSkins());
				}
			} catch (RuntimeException ignored) {
				// not a pet's data after all
			}
		}
		READ.put(data, identity);
		return identity;
	}

	// ------------------------------------------------------------------------------------------------ icons

	/** A Hypixel menu showed a real pet: its own head is remembered by its UUID, and its icon is made again if its skin changed. */
	public static void seen(ItemStack stack) {
		PetIdentity pet = of(stack);
		String texture = SkullTextures.propertyOf(stack);
		if (pet == null || pet.uuid().isEmpty() || texture.isEmpty() || texture.length() > MOST_TEXT) {
			return;
		}
		String[] before = REAL.get(pet.key());
		if (before != null && before[0].equals(pet.look()) && before[1].equals(texture)) {
			return;
		}
		REAL.remove(pet.key());
		REAL.put(pet.key(), new String[]{pet.look(), texture});
		trim(REAL);
		ICONS.remove(pet.key());
		version++;
	}

	/** @return the icon of a pet item, made as for any pet; the item itself if it is no pet or no icon can be made */
	public static ItemStack icon(ItemStack stack) {
		PetIdentity pet = of(stack);
		ItemStack icon = pet == null ? ItemStack.EMPTY : icon(pet);
		return icon.isEmpty() ? stack : icon;
	}

	/**
	 * The icon of a pet the panels show, asked every frame: the same pet object gets the same icon without asking again, for a few seconds.
	 *
	 * @param owner what the icon is for (the pet object), compared by identity
	 * @param item  the pet's item from a menu, or empty
	 * @param name  the pet's kind by name, for a pet known only from a message
	 * @param color the pet's rarity color
	 * @return the icon, or empty when none can be made
	 */
	public static ItemStack forPet(Object owner, ItemStack item, String name, int color) {
		long now = System.currentTimeMillis();
		if (owner == lastAsked && now - lastAskedAt < RECHECK_MILLIS) {
			return lastIcon;
		}
		PetIdentity pet = item.isEmpty() ? null : of(item);
		if (pet == null) {
			pet = PetIdentity.ofName(name, PetTracker.rarityName(color));
		}
		ItemStack icon = pet == null ? ItemStack.EMPTY : icon(pet);
		lastAsked = owner;
		lastAskedAt = now;
		lastIcon = icon.isEmpty() ? item : icon;
		return lastIcon;
	}

	/** @return the icon of a pet, made once per key and look, again for the season or the time of day now and then */
	public static ItemStack icon(PetIdentity pet) {
		String key = pet.key();
		Icon cached = ICONS.get(key);
		if (cached != null && cached.stack() == null) {
			// kept from the last start: its head is made now, when it is first wanted, as items can only be made once the game is running
			cached = new Icon(cached.look(), cached.variantNow(), SkullTextures.head(cached.texture()), cached.texture(), cached.madeAt());
			ICONS.put(key, cached);
		}
		long now = System.currentTimeMillis();
		if (cached != null && cached.look().equals(pet.look()) && (pet.skin().isEmpty() || now - cached.madeAt() < RECHECK_MILLIS)) {
			return cached.stack();
		}
		String variant = pet.variantNow(ItemRepo.petSkins(), PetIdentity.seasonAt(now), isDayNow());
		if (cached != null && cached.look().equals(pet.look()) && cached.variantNow().equals(variant)) {
			ICONS.put(key, new Icon(cached.look(), variant, cached.stack(), cached.texture(), now));
			return cached.stack();
		}
		Icon made = make(pet, variant, now);
		if (made.stack().isEmpty()) {
			return cached != null && cached.look().equals(pet.look()) ? cached.stack() : ItemStack.EMPTY;
		}
		ICONS.remove(key);
		ICONS.put(key, made);
		trim(ICONS);
		version++;
		return made.stack();
	}

	private static Icon make(PetIdentity pet, String variant, long now) {
		String[] real = REAL.get(pet.key());
		String texture = real != null && real[0].equals(pet.look()) ? real[1] : "";
		if (texture.isEmpty() && !variant.isEmpty()) {
			texture = ItemRepo.petSkins().firstFrame(variant);
			if (texture.isEmpty()) {
				texture = repoTexture(pet.skinItem());
			}
		}
		if (texture.isEmpty()) {
			texture = repoTexture(pet.petItem());
		}
		if (!texture.isEmpty()) {
			return new Icon(pet.look(), variant, SkullTextures.head(texture), texture, now);
		}
		ItemRepo.loadIfNeeded();
		ItemRepo.Entry entry = ItemRepo.get(pet.petItem());
		return new Icon(pet.look(), variant, entry == null ? ItemStack.EMPTY : entry.stack(), "", now);
	}

	private static String repoTexture(String id) {
		if (id.isEmpty()) {
			return "";
		}
		ItemRepo.loadIfNeeded();
		ItemRepo.Entry entry = ItemRepo.get(id);
		return entry == null ? "" : SkullTextures.propertyOf(entry.stack());
	}

	private static boolean isDayNow() {
		var level = Minecraft.getInstance().level;
		return level == null || PetIdentity.isDay(level.getOverworldClockTime());
	}

	private static <V> void trim(Map<String, V> map) {
		while (map.size() > MOST_ICONS) {
			map.remove(map.keySet().iterator().next());
		}
	}

	// ------------------------------------------------------------------------------------------------ on disk

	/** @return a counter that goes up whenever an icon was made or a real head seen, to save then */
	public static int version() {
		return version;
	}

	/** @return the icons as "look, variant, texture" by key, and the real heads as "look, texture" by "real|" key, for the settings file */
	public static Map<String, String[]> export() {
		Map<String, String[]> out = new HashMap<>();
		ICONS.forEach((key, icon) -> {
			if (!icon.texture().isEmpty()) {
				out.put(key, new String[]{icon.look(), icon.variantNow(), icon.texture()});
			}
		});
		REAL.forEach((key, real) -> out.put("real|" + key, real.clone()));
		return out;
	}

	/**
	 * Puts back what {@link #export} wrote. It runs while the settings load, before items can be made, so only the textures are kept here; each head
	 * is made when its icon is first wanted. Odd entries are left out.
	 */
	public static void restore(Map<String, String[]> kept) {
		for (Map.Entry<String, String[]> entry : kept.entrySet()) {
			String[] value = entry.getValue();
			if (entry.getKey().startsWith("real|") && value.length == 2 && value[1].length() <= MOST_TEXT) {
				REAL.putIfAbsent(entry.getKey().substring("real|".length()), value.clone());
			} else if (value.length == 3 && !value[2].isEmpty() && value[2].length() <= MOST_TEXT) {
				ICONS.putIfAbsent(entry.getKey(), new Icon(value[0], value[1], null, value[2], 0L));
			}
		}
		trim(REAL);
		trim(ICONS);
	}
}
