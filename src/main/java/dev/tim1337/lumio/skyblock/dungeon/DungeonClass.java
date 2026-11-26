package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The dungeon classes, with the colors the game gives them, the letter shown for them, the quadrant of the leap overlay each calls home and how strongly
 * it claims it when two classes share a home, so the cards stand where players used to Odin's leap menu expect them.
 */
public enum DungeonClass {
	ARCHER("Archer", 0xFFAA00, 0, 2),
	BERSERK("Berserk", 0xFF5555, 1, 0),
	HEALER("Healer", 0xFF55FF, 2, 2),
	MAGE("Mage", 0x55FFFF, 3, 2),
	TANK("Tank", 0x55FF55, 3, 1),
	NONE("Unknown", 0xAAAAAA, 0, 3);

	private final String label;
	private final int rgb;
	private final int homeQuadrant;
	private final int claim;

	DungeonClass(String label, int rgb, int homeQuadrant, int claim) {
		this.label = label;
		this.rgb = rgb;
		this.homeQuadrant = homeQuadrant;
		this.claim = claim;
	}

	public String label() {
		return label;
	}

	public int rgb() {
		return rgb;
	}

	public char letter() {
		return label.charAt(0);
	}

	/** @return the quadrant of the leap overlay the class calls home: 0 top left, 1 top right, 2 bottom left, 3 bottom right */
	public int homeQuadrant() {
		return homeQuadrant;
	}

	/** @return how strongly the class claims its home quadrant: lower is stronger */
	public int claim() {
		return claim;
	}

	/** @return the class of a name in the player list ("Mage", "Berserk"), or NONE */
	public static DungeonClass of(String name) {
		for (DungeonClass each : values()) {
			if (each.label.equalsIgnoreCase(name)) {
				return each;
			}
		}
		return NONE;
	}
}
