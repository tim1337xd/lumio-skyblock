package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.Place;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;
import dev.tim1337.lumio.skyblock.data.StorageTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;

/**
 * Where the player is on SkyBlock, for Lumio: the island from the player list or the scoreboard, and the islands a keybind can be
 * limited to. Every answer comes from values the SkyBlock data keeps up to date; nothing is scanned when asked.
 */
public final class SkyBlockLocation implements LocationProvider {
	/** The server's ids of the islands (its location "mode"), as lists of waypoints from other mods name them. */
	private static final Map<SkyBlockIsland, String> SERVER_IDS = serverIds();
	/** The Crystal Hollows lie in this square, and no other island reaches it: points there are the Hollows'. */
	private static final int HOLLOWS_LOW = 202;
	private static final int HOLLOWS_HIGH = 823;
	/** The islands as places, made once. */
	static final List<Place> PLACES = islandPlaces();

	private static Map<SkyBlockIsland, String> serverIds() {
		Map<SkyBlockIsland, String> ids = new EnumMap<>(SkyBlockIsland.class);
		ids.put(SkyBlockIsland.HUB, "hub");
		ids.put(SkyBlockIsland.PRIVATE_ISLAND, "dynamic");
		ids.put(SkyBlockIsland.CRIMSON_ISLE, "crimson_isle");
		ids.put(SkyBlockIsland.KUUDRA, "kuudra");
		ids.put(SkyBlockIsland.DWARVEN_MINES, "mining_3");
		ids.put(SkyBlockIsland.CRYSTAL_HOLLOWS, "crystal_hollows");
		ids.put(SkyBlockIsland.GARDEN, "garden");
		ids.put(SkyBlockIsland.THE_END, "combat_3");
		ids.put(SkyBlockIsland.SPIDERS_DEN, "combat_1");
		ids.put(SkyBlockIsland.THE_PARK, "foraging_1");
		ids.put(SkyBlockIsland.GALATEA, "foraging_2");
		ids.put(SkyBlockIsland.DUNGEONS, "dungeon");
		ids.put(SkyBlockIsland.DUNGEON_HUB, "dungeon_hub");
		ids.put(SkyBlockIsland.MINESHAFT, "mineshaft");
		ids.put(SkyBlockIsland.DEEP_CAVERNS, "mining_2");
		ids.put(SkyBlockIsland.GOLD_MINE, "mining_1");
		ids.put(SkyBlockIsland.FARMING_ISLANDS, "farming_1");
		ids.put(SkyBlockIsland.JERRYS_WORKSHOP, "winter");
		ids.put(SkyBlockIsland.THE_RIFT, "rift");
		ids.put(SkyBlockIsland.BACKWATER_BAYOU, "fishing_1");
		ids.put(SkyBlockIsland.TORRHUS_CANYON, "foraging_3");
		ids.put(SkyBlockIsland.SAFARI, "safari");
		ids.put(SkyBlockIsland.LOTUS_ATOLL, "lotus_atoll");
		return ids;
	}

	private static List<Place> islandPlaces() {
		List<Place> places = new ArrayList<>();
		for (SkyBlockIsland island : SkyBlockIsland.values()) {
			List<String> aliases = new ArrayList<>(island.areas());
			String serverId = SERVER_IDS.get(island);
			if (serverId != null) {
				// the server's own id of the island, which other mods' waypoint lists name it by
				aliases.add("id:" + serverId);
			}
			places.add(new Place(island.key(), island.label(), aliases));
		}
		return List.copyOf(places);
	}

	@Override
	public String worldName() {
		return "SkyBlock";
	}

	@Override
	public boolean inWorld() {
		return SkyBlock.inSkyBlock();
	}

	@Override
	public String placeName() {
		// in a run of the Catacombs, the floor: "You are in F7"
		if (dev.tim1337.lumio.skyblock.dungeon.Dungeon.in() && dev.tim1337.lumio.skyblock.dungeon.Dungeon.floor() != null) {
			return dev.tim1337.lumio.skyblock.dungeon.Dungeon.floor().name();
		}
		return SkyBlockArea.island();
	}

	@Override
	public String zoneName() {
		return SkyBlockArea.zone();
	}

	@Override
	public String profileId() {
		// the profile the server named when it started, which the storage keeps its pages by too
		String profile = StorageTracker.profile();
		return profile == null ? "" : profile;
	}

	@Override
	public String placeAt(int x, int y, int z) {
		boolean hollows = x >= HOLLOWS_LOW && x <= HOLLOWS_HIGH && z >= HOLLOWS_LOW && z <= HOLLOWS_HIGH;
		return hollows ? SkyBlockIsland.CRYSTAL_HOLLOWS.key() : "";
	}

	@Override
	public boolean knownArea(String name) {
		return SkyBlockIsland.ofArea(name) != null || KNOWN_ZONES.contains(name.strip().toLowerCase(Locale.ROOT));
	}

	/** Areas within the islands that are often the first to be read, before the location source has one: the player's own island and the Hub's. */
	private static final Set<String> KNOWN_ZONES = Set.of("your island", "village", "auction house", "bazaar alley", "bank", "community center", "wilderness", "forest", "farm", "graveyard", "coal mine", "ruins", "colosseum", "fishing outpost");

	@Override
	public List<Place> places() {
		return PLACES;
	}

	@Override
	public boolean has(String property) {
		return switch (property) {
			case "mines" -> SkyBlockArea.inMines();
			case "garden" -> SkyBlockArea.inGarden();
			case "slayer" -> SkyBlockArea.slayerQuest();
			default -> SkyBlockAmbience.has(property, SkyBlockIsland.ofArea(SkyBlockArea.island()), SkyBlockAmbience.seasonAt(System.currentTimeMillis()));
		};
	}
}
