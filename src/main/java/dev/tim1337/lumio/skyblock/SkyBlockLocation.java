package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.Place;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Where the player is on SkyBlock, for Lumio: the island from the player list or the scoreboard, and the islands a keybind can be
 * limited to. Every answer comes from values the SkyBlock data keeps up to date; nothing is scanned when asked.
 */
public final class SkyBlockLocation implements LocationProvider {
	/** The islands as places, made once. */
	static final List<Place> PLACES = islandPlaces();

	private static List<Place> islandPlaces() {
		List<Place> places = new ArrayList<>();
		for (SkyBlockIsland island : SkyBlockIsland.values()) {
			places.add(new Place(island.key(), island.label(), island.areas()));
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
		return SkyBlockArea.island();
	}

	@Override
	public String zoneName() {
		return SkyBlockArea.zone();
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
