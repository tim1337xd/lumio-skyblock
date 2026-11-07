package dev.tim1337.lumio.skyblock.slayer;

/**
 * Which mob near a boss's name tag is the boss itself, by the kind of boss: helpers are never it, such as the invisible guardian of the Voidgloom's laser. Plain text, so it is
 * unit tested.
 */
final class BossBodies {
	private static final String[][] KINDS = {
		{"voidgloom", "enderman"},
		{"tarantula", "spider", "cave_spider"},
		{"broodfather", "spider", "cave_spider"},
		{"revenant", "zombie"},
		{"atoned", "zombie"},
		{"sven", "wolf"},
		{"inferno", "blaze"}
	};

	private BossBodies() {
	}

	/**
	 * @param bossName  the boss's name, lower case ("voidgloom seraph iv")
	 * @param type      the id of the mob's type, without its namespace ("enderman")
	 * @param invisible whether the mob is invisible
	 * @param riding    whether it rides something
	 * @return whether it can be the boss. A boss of a known kind is that mob even while invisible, so its highlight pauses and does not move on to a helper, and also while it rides something, as a Voidgloom in its laser does; an unknown one
	 *         is any mob that can be seen, and rides nothing.
	 */
	static boolean isBody(String bossName, String type, boolean invisible, boolean riding) {
		for (String[] kind : KINDS) {
			if (bossName.contains(kind[0])) {
				for (int i = 1; i < kind.length; i++) {
					if (kind[i].equals(type)) {
						return true;
					}
				}
				return false;
			}
		}
		return !invisible && !riding;
	}
}
