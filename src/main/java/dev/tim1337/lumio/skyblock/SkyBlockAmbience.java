package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;

/** What the ambience's Auto shows on an island in a SkyBlock season. The calendar follows the real time, so the season is known without reading anything. */
final class SkyBlockAmbience {
	/** The seasons, as {@link #seasonAt} gives them. */
	static final int SPRING = 0;
	static final int SUMMER = 1;
	static final int AUTUMN = 2;
	static final int WINTER = 3;

	private static final long YEAR_1_START_MILLIS = 1559829300000L;
	private static final long YEAR_MILLIS = 124L * 60L * 60L * 1000L;
	private static final long SEASON_MILLIS = YEAR_MILLIS / 4L;

	private SkyBlockAmbience() {
	}

	/** @return the SkyBlock season at a real time */
	static int seasonAt(long millis) {
		return (int) (Math.floorMod(millis - YEAR_1_START_MILLIS, YEAR_MILLIS) / SEASON_MILLIS);
	}

	/** @return whether a property of the ambience holds for an island, which is null when it is not known yet, in a season */
	static boolean has(String property, SkyBlockIsland island, int season) {
		if (island == null) {
			return false;
		}
		return switch (property) {
			case "ambience:snow" -> island == SkyBlockIsland.JERRYS_WORKSHOP || island == SkyBlockIsland.MINESHAFT || outdoors(island) && season == WINTER;
			case "ambience:fireflies" -> outdoors(island) && season != WINTER || island == SkyBlockIsland.BACKWATER_BAYOU;
			case "ambience:mist" -> misty(island);
			case "ambience:no_leaves" -> !outdoors(island) && !misty(island);
			case "ambience:autumn" -> season == AUTUMN;
			case "mood:ash", "mood:sparks" -> island == SkyBlockIsland.CRIMSON_ISLE || island == SkyBlockIsland.KUUDRA;
			case "mood:dust" -> dusty(island);
			case "mood:webs" -> island == SkyBlockIsland.SPIDERS_DEN;
			default -> false;
		};
	}

	/** The green islands, which have seasons, trees and meadows. */
	private static boolean outdoors(SkyBlockIsland island) {
		return switch (island) {
			case HUB, PRIVATE_ISLAND, GARDEN, THE_PARK, GALATEA, FARMING_ISLANDS -> true;
			default -> false;
		};
	}

	private static boolean misty(SkyBlockIsland island) {
		return switch (island) {
			case BACKWATER_BAYOU, GALATEA, SPIDERS_DEN, THE_PARK, LOTUS_ATOLL -> true;
			default -> false;
		};
	}

	private static boolean dusty(SkyBlockIsland island) {
		return switch (island) {
			case DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT, DEEP_CAVERNS, GOLD_MINE, DUNGEONS, DUNGEON_HUB, THE_END, THE_RIFT, TORRHUS_CANYON, SAFARI -> true;
			default -> false;
		};
	}
}
