package dev.tim1337.lumio.skyblock.slayer;

import java.util.Locale;
import java.util.Set;

/**
 * Where each slayer's boss can spawn, and with it where what is only for that slayer shows: with its quest active and on one of its islands, or with its boss near. The one place of this list. The names are as the player list writes them after "Area:".
 */
final class SlayerPlaces {
	/** A slayer: a word of its boss's name, and its islands, in lower case. */
	private enum Slayer {
		REVENANT("revenant", Set.of("hub")),
		TARANTULA("tarantula", Set.of("spider's den")),
		SVEN("sven", Set.of("the park", "hub")),
		VOIDGLOOM("voidgloom", Set.of("the end")),
		INFERNO("inferno", Set.of("crimson isle")),
		RIFTSTALKER("riftstalker", Set.of("the rift"));

		private final String word;
		private final Set<String> islands;

		Slayer(String word, Set<String> islands) {
			this.word = word;
			this.islands = islands;
		}
	}

	private SlayerPlaces() {
	}

	/**
	 * @param questBoss the quest's boss as the scoreboard writes it, "Tarantula Broodfather III"; empty without a quest
	 * @param island    the island the player is on; empty while that is not known
	 * @param bossNear  whether the boss is spawned near the player
	 * @return whether what is only for slayers shows now
	 */
	static boolean shows(String questBoss, String island, boolean bossNear) {
		if (bossNear) {
			return true;
		}
		if (questBoss == null || questBoss.isBlank() || island == null || island.isBlank()) {
			return false;
		}
		String boss = questBoss.toLowerCase(Locale.ROOT);
		String place = island.strip().toLowerCase(Locale.ROOT);
		for (Slayer slayer : Slayer.values()) {
			if (boss.contains(slayer.word)) {
				return slayer.islands.contains(place);
			}
		}
		return false;
	}
}
