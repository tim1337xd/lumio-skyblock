package dev.tim1337.lumio.skyblock.slayer;

import java.util.List;

/**
 * The slayer minibosses by their name tags, and whose quest one belongs to. A miniboss carries a name tag like "☠ Voidling Devotee 12M❤"; it
 * belongs to the player whose "Spawned by" tag is next to it, or else to the player clearly nearest to where it spawned (it spawns where its
 * quest's mob died). Plain strings and numbers, so it is unit tested with recorded name tags.
 */
final class Minibosses {
	/** Every slayer's minibosses, as their name tags spell them. The Riftstalker has none. */
	static final List<String> NAMES = List.of(
		"Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant",
		"Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula",
		"Pack Enforcer", "Sven Follower", "Sven Alpha",
		"Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac",
		"Flare Demon", "Kindleheart Demon", "Burningsoul Demon");
	/** How far from the nearest player a miniboss may spawn to count as theirs, in blocks. */
	static final double OWN_RANGE = 8.0;
	/** How much nearer one player has to be than the next to tell them apart, in blocks. */
	static final double TIE_BLOCKS = 1.5;

	/** A player in the world: their name and where they stand. */
	record Player(String name, double x, double y, double z) {
	}

	private Minibosses() {
	}

	/** @return the miniboss a name tag line (without color codes) names, or null when it names none, such as a boss or another mob */
	static String named(String line) {
		// a mob's name tag shows its health or the skull; a chat line or a sign with the name does not count
		if (line.indexOf('❤') < 0 && line.indexOf('☠') < 0) {
			return null;
		}
		for (String name : NAMES) {
			int at = line.indexOf(name);
			if (at >= 0 && wordEdge(line, at - 1) && wordEdge(line, at + name.length())) {
				return name;
			}
		}
		return null;
	}

	private static boolean wordEdge(String line, int at) {
		return at < 0 || at >= line.length() || !Character.isLetter(line.charAt(at));
	}

	/**
	 * @param spawnedBy the name on a "Spawned by" tag next to it, or null when it has none
	 * @return the name of the player whose quest the miniboss belongs to, or null when it can't be told
	 */
	static String ownerOf(String spawnedBy, double x, double y, double z, List<Player> players) {
		if (spawnedBy != null) {
			return spawnedBy;
		}
		Player nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		double second = Double.MAX_VALUE;
		for (Player player : players) {
			double dx = player.x() - x;
			double dy = player.y() - y;
			double dz = player.z() - z;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (distance < nearestDistance) {
				second = nearestDistance;
				nearestDistance = distance;
				nearest = player;
			} else if (distance < second) {
				second = distance;
			}
		}
		if (nearest == null || nearestDistance > OWN_RANGE || second - nearestDistance < TIE_BLOCKS) {
			return null;
		}
		return nearest.name();
	}

	/**
	 * @param owner     whose quest it belongs to, or null
	 * @param ownQuest  whether the player's own slayer quest runs
	 * @param carried   the carried player's name, or "" when not carrying
	 * @return whether a miniboss is one of the player's own quest's, or of the carried player's
	 */
	static boolean watched(String owner, String me, boolean ownQuest, String carried) {
		if (owner == null) {
			return false;
		}
		return ownQuest && owner.equalsIgnoreCase(me) || !carried.isEmpty() && owner.equalsIgnoreCase(carried);
	}
}
