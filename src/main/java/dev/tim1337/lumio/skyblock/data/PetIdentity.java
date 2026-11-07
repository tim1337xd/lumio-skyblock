package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * What tells one pet from another, as its item's {@code petInfo} says, and which textures its skin shows now. Plain data, so it is tested without
 * the game; {@link PetIcons} makes and keeps the icons. The idea is SkyHanni's; the code is this mod's own.
 *
 * @param type    the pet's type, "GOLDEN_DRAGON"
 * @param rarity  the repository's rarity index, 0 common to 5 mythic
 * @param skin    the skin's own name without "PET_SKIN_", "" without a skin
 * @param variant the skin's variant index, -1 without one
 * @param uuid    the real pet's UUID, "" when it is not one (a shop, a message)
 */
public record PetIdentity(String type, int rarity, String skin, int variant, String uuid) {
	static final String SKIN_PREFIX = "PET_SKIN_";
	private static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};
	/** The faction a faction skin shows when the player's is unknown, as SkyHanni does. */
	private static final String DEFAULT_FACTION = "BARBARIAN";
	/** Day 1 of year 1 of SkyBlock's calendar, and its year of 124 hours. */
	private static final long SKYBLOCK_EPOCH_MILLIS = 1_559_829_300_000L;
	private static final long SKYBLOCK_YEAR_MILLIS = 124L * 60 * 60 * 1000;
	private static final long SKYBLOCK_SEASON_MILLIS = SKYBLOCK_YEAR_MILLIS / 4;
	private static final int TICKS_PER_DAY = 24000;
	private static final int DAY_ENDS_TICKS = 12000;
	private static final int MOST_VARIANT = 512;

	/** @return the cache key: the UUID when known, else type, rarity, skin and variant; never only a name or an item id */
	public String key() {
		return uuid.isEmpty() ? type + ";" + rarity + "|" + skin + "|" + variant : "uuid:" + uuid;
	}

	/** @return what the icon is made of: when it changes for one UUID (a skin put on or taken off), its icon is made again */
	public String look() {
		return skin + "|" + variant;
	}

	/** @return the repository id of the pet's own item: "GOLDEN_DRAGON;4" */
	public String petItem() {
		return type + ";" + rarity;
	}

	/** @return the repository id of the skin's item, "" without a skin */
	public String skinItem() {
		return skin.isEmpty() ? "" : SKIN_PREFIX + skin;
	}

	/** @return the pet {@code petInfo} describes, or null without a type */
	public static PetIdentity read(JsonObject info, RepoPetSkins skins) {
		String type = text(info, "type").toUpperCase(Locale.ROOT);
		if (type.isEmpty()) {
			return null;
		}
		String skin = text(info, "skin");
		if (skin.equalsIgnoreCase("null") || skin.equalsIgnoreCase("none")) {
			skin = "";
		}
		String uuid = text(info, "uniqueId");
		if (uuid.isEmpty()) {
			uuid = text(info, "uuid");
		}
		int variant = -1;
		if (info.get("extraData") instanceof JsonObject extra) {
			for (String key : skins.variantKeys()) {
				JsonElement value = extra.get(key);
				if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
					variant = Math.clamp(value.getAsInt(), -1, MOST_VARIANT);
					break;
				}
			}
		}
		return new PetIdentity(type, rarityIndex(text(info, "tier")), skin.toUpperCase(Locale.ROOT), variant, uuid.toLowerCase(Locale.ROOT));
	}

	/** @return a pet known only by its name and rarity, as a message names it ("Golden Dragon"): no skin, no UUID; null without a name */
	public static PetIdentity ofName(String name, String tier) {
		String type = name.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
		return type.isEmpty() ? null : new PetIdentity(type, rarityIndex(tier), "", -1, "");
	}

	private static String text(JsonObject info, String key) {
		JsonElement value = info.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
	}

	/** @return the repository's rarity index of a tier name, 0 for common or unknown */
	public static int rarityIndex(String tier) {
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> 0;
		};
	}

	/** @return the season of SkyBlock's calendar at a time: 0 spring to 3 winter */
	public static int seasonAt(long millis) {
		long intoYear = Math.floorMod(millis - SKYBLOCK_EPOCH_MILLIS, SKYBLOCK_YEAR_MILLIS);
		return (int) (intoYear / SKYBLOCK_SEASON_MILLIS);
	}

	/** @return whether it is day at this time of day in ticks, as SkyBlock's day and night skins tell it */
	public static boolean isDay(long dayTime) {
		long tick = Math.floorMod(dayTime, TICKS_PER_DAY);
		return tick >= 1 && tick <= DAY_ENDS_TICKS;
	}

	/**
	 * @return the name of the textures the skin shows now: the variant the pet chose (by its index), else the season's, the day's or the
	 *         night's, the faction's, else the skin itself; "" without a skin
	 */
	public String variantNow(RepoPetSkins skins, int season, boolean day) {
		String item = skinItem();
		if (item.isEmpty()) {
			return "";
		}
		String chosen = skins.variant(item, variant);
		if (!chosen.isEmpty()) {
			return chosen;
		}
		for (String suffix : new String[]{SEASONS[Math.floorMod(season, SEASONS.length)], day ? "DAY" : "NIGHT", DEFAULT_FACTION}) {
			if (skins.textures().containsKey(item + "_" + suffix)) {
				return item + "_" + suffix;
			}
		}
		return item;
	}
}
