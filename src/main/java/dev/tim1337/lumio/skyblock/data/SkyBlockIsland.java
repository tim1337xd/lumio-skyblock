package dev.tim1337.lumio.skyblock.data;

import java.util.List;
import java.util.Locale;

/**
 * The SkyBlock islands a player can pick, for features that work only on some of them (custom keybinds). Each knows the names the player list
 * shows after "Area:" (or "Dungeon:" in a dungeon), in lower case.
 */
public enum SkyBlockIsland {
	HUB("hub", "Hub", "hub"),
	PRIVATE_ISLAND("private_island", "Private Island", "private island"),
	CRIMSON_ISLE("crimson_isle", "Crimson Isle", "crimson isle"),
	KUUDRA("kuudra", "Kuudra", "kuudra", "kuudra's hollow"),
	DWARVEN_MINES("dwarven_mines", "Dwarven Mines", "dwarven mines"),
	CRYSTAL_HOLLOWS("crystal_hollows", "Crystal Hollows", "crystal hollows"),
	GARDEN("garden", "The Garden", "garden", "the garden"),
	THE_END("the_end", "The End", "the end"),
	SPIDERS_DEN("spiders_den", "Spider's Den", "spider's den"),
	THE_PARK("the_park", "The Park", "the park"),
	GALATEA("galatea", "Galatea", "galatea"),
	DUNGEONS("dungeons", "Dungeons", "catacombs", "the catacombs"),
	DUNGEON_HUB("dungeon_hub", "Dungeon Hub", "dungeon hub"),
	MINESHAFT("mineshaft", "Glacite Mineshafts", "mineshaft", "glacite mineshafts"),
	DEEP_CAVERNS("deep_caverns", "Deep Caverns", "deep caverns"),
	GOLD_MINE("gold_mine", "Gold Mine", "gold mine"),
	FARMING_ISLANDS("farming_islands", "The Farming Islands", "the farming islands", "the barn", "mushroom desert"),
	JERRYS_WORKSHOP("jerrys_workshop", "Jerry's Workshop", "jerry's workshop"),
	THE_RIFT("the_rift", "The Rift", "the rift"),
	BACKWATER_BAYOU("backwater_bayou", "Backwater Bayou", "backwater bayou"),
	TORRHUS_CANYON("torrhus_canyon", "Torrhus Canyon", "torrhus canyon"),
	SAFARI("safari", "Safari", "safari"),
	LOTUS_ATOLL("lotus_atoll", "Lotus Atoll", "lotus atoll");

	private final String key;
	private final String label;
	private final String[] areas;

	SkyBlockIsland(String key, String label, String... areas) {
		this.key = key;
		this.label = label;
		this.areas = areas;
	}

	/** @return whether the mining trackers work on an island named so: not where the player only builds, or farms */
	public static boolean miningHappensOn(String area) {
		SkyBlockIsland island = ofArea(area);
		return island != PRIVATE_ISLAND && island != GARDEN;
	}

	/** @return the island's name in configs */
	public String key() {
		return key;
	}

	/** @return the names the player list shows for the island, in lower case */
	public List<String> areas() {
		return List.of(areas);
	}

	/** @return the island's name as shown to the player */
	public String label() {
		return label;
	}

	/** @return the island of a configured name, or null */
	public static SkyBlockIsland byKey(String key) {
		for (SkyBlockIsland island : values()) {
			if (island.key.equals(key)) {
				return island;
			}
		}
		return null;
	}

	/** @return the island of an area name from the player list ("Dwarven Mines", "Catacombs"), or null when it is none of these */
	public static SkyBlockIsland ofArea(String area) {
		if (area == null || area.isEmpty()) {
			return null;
		}
		String name = area.strip().toLowerCase(Locale.ROOT);
		for (SkyBlockIsland island : values()) {
			for (String known : island.areas) {
				if (known.equals(name)) {
					return island;
				}
			}
		}
		return null;
	}
}
