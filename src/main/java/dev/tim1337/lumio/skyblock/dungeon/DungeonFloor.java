package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The floors of the Catacombs: the Entrance, F1 to F7 and the Master Mode floors M1 to M7, with the share of the secrets a floor needs for the
 * full secret score (all of them on F7 and in Master Mode), as SkyBlock counts them.
 */
public enum DungeonFloor {
	E(0.3), F1(0.3), F2(0.4), F3(0.5), F4(0.6), F5(0.7), F6(0.85), F7(1.0),
	M1(1.0), M2(1.0), M3(1.0), M4(1.0), M5(1.0), M6(1.0), M7(1.0);

	private final double secretShare;

	DungeonFloor(double secretShare) {
		this.secretShare = secretShare;
	}

	/** @return the share of the secrets needed for the full secret score */
	public double secretShare() {
		return secretShare;
	}

	/** @return the floor's number: 0 for the Entrance, 1 to 7 for the others */
	public int number() {
		return this == E ? 0 : ordinal() <= F7.ordinal() ? ordinal() : ordinal() - F7.ordinal();
	}

	public boolean master() {
		return ordinal() > F7.ordinal();
	}

	/** @return whether the floor has a Mimic (F6, F7, M6, M7) */
	public boolean hasMimic() {
		return number() >= 6;
	}

	/** @return the floor of a name as the scoreboard writes it ("F7", "M3", "E"), or null */
	public static DungeonFloor of(String name) {
		for (DungeonFloor floor : values()) {
			if (floor.name().equals(name)) {
				return floor;
			}
		}
		return null;
	}
}
