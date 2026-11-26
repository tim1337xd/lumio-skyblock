package dev.tim1337.lumio.skyblock.dungeon;

import net.minecraft.world.item.DyeColor;

/**
 * Which Livid is the real one: the color of the wool at the arena's known spot names it, and nothing is known (no default) until that wool shows
 * one of the nine colors. The real Livid is the player entity named "<Name> Livid"; its entity is kept until it is gone (data updates keep the same
 * entity), then found again. Plain values, so it is tested without the game.
 */
final class LividState {
	static final String SUFFIX = " Livid";
	private static final String[] NAMES = {"Vendetta", "Crossed", "Arcade", "Smile", "Doctor", "Purple", "Scream", "Frog", "Hockey"};
	private static final DyeColor[] WOOLS = {DyeColor.WHITE, DyeColor.MAGENTA, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GRAY, DyeColor.PURPLE,
		DyeColor.BLUE, DyeColor.GREEN, DyeColor.RED};
	private static final int NONE = -1;

	private String name = "";
	private DyeColor color;
	private int realId = NONE;

	/** @return the Livid a wool color names, or null for any other color */
	static String nameFor(DyeColor wool) {
		for (int i = 0; i < WOOLS.length; i++) {
			if (WOOLS[i] == wool) {
				return NAMES[i];
			}
		}
		return null;
	}

	/** The wool at the arena's spot, read when it changes: one of the nine colors names the real Livid; anything else changes nothing. */
	void wool(DyeColor wool) {
		String named = wool == null ? null : nameFor(wool);
		if (named != null && !named.equals(name)) {
			name = named;
			color = wool;
			realId = NONE;
		}
	}

	/** @return the real Livid's name ("Hockey"), or "" while it is not known */
	String name() {
		return name;
	}

	/** @return the wool color of the real Livid, or null while it is not known */
	DyeColor color() {
		return color;
	}

	boolean known() {
		return !name.isEmpty();
	}

	/** @return the entity name the real Livid has ("Hockey Livid"), or "" while it is not known */
	String entityName() {
		return known() ? name + SUFFIX : "";
	}

	/** @return the real Livid's entity id, or -1 while none is kept */
	int realId() {
		return realId;
	}

	/** The real Livid's entity was found. */
	void found(int id) {
		realId = id;
	}

	/** The kept entity is gone (removed, or a respawn made a new one): it is looked for again. */
	void lost() {
		realId = NONE;
	}

	/** @return whether the kept entity is still the real Livid: it exists and has the right name (a data update changes neither) */
	boolean keeps(boolean present, String entityName) {
		return realId != NONE && present && entityName.equals(entityName());
	}

	/**
	 * @return whether an entity is a fake Livid to hide: named "<Name> Livid", the real one known, and not the real one (by id or by name), so the
	 *         real one is never hidden
	 */
	boolean fake(int id, String entityName) {
		return known() && entityName.endsWith(SUFFIX) && id != realId && !entityName.equals(entityName());
	}

	/**
	 * @param on        whether the module and its tracer switch are on
	 * @param inFight   whether the fight of F5 or M5 goes on
	 * @param realAlive whether the real Livid's entity is kept and alive
	 * @param blind     whether the player is blind: the start of the fight, before the wool's color can be read
	 * @return whether the tracer to the real Livid is drawn: only while he is known and alive in the fight, never in the start phase
	 */
	boolean tracerActive(boolean on, boolean inFight, boolean realAlive, boolean blind) {
		return on && inFight && known() && realId != NONE && realAlive && !blind;
	}

	/**
	 * @param own       the player's own tracer color, as 0xRRGGBB
	 * @param useLivids whether the tracer takes the real Livid's color
	 * @return the tracer's color: the real Livid's wool color, or the player's own where that is asked for or the color is not known
	 */
	int tracerRgb(int own, boolean useLivids) {
		return useLivids && color != null ? color.getTextColor() & 0xFFFFFF : own & 0xFFFFFF;
	}

	/** The fight is over or the world changed: nothing is known. */
	void reset() {
		name = "";
		color = null;
		realId = NONE;
	}
}
