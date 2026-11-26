package dev.tim1337.lumio.skyblock.misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The slot binds: pairs of the player's own inventory slots, one of them on the hotbar, kept in named sets; one set is in use. A set may be used
 * automatically in an area (dungeons, the mines, the Garden, or everywhere else); a set the player chose stays until the area changes. Slots are
 * the inventory's own numbers, 0–8 the hotbar and 9–35 the inventory above it; armor, the off hand and the crafting grid cannot be bound. Plain
 * numbers, so it is tested without the game.
 */
final class SlotBinds {
	/** How a press of the bind key on a slot turned out. */
	enum Result {
		/** The first slot of a new bind is picked. */
		STARTED,
		/** The second slot was picked: the two are bound. */
		BOUND,
		/** The slot was bound: its bind is gone. */
		UNBOUND,
		/** The second slot was the first one again. */
		SAME_SLOT,
		/** Neither slot is on the hotbar. */
		NO_HOTBAR,
		/** The second slot already has a bind of its own. */
		ALREADY_BOUND,
		/** A slot that cannot be bound: not of the inventory or the hotbar, or the SkyBlock Menu. */
		NOT_BINDABLE
	}

	/** Where a set is used by itself. */
	enum Area {
		OFF("Only when chosen"), EVERYWHERE("Everywhere else"), DUNGEONS("Dungeons"), MINING("Mining areas"), GARDEN("Garden");

		final String label;

		Area(String label) {
			this.label = label;
		}
	}

	/** One named set of binds. */
	static final class Set {
		String name;
		Area area;
		final int[] partners = new int[SLOTS];

		Set(String name, Area area) {
			this.name = name;
			this.area = area;
			Arrays.fill(partners, NONE);
		}
	}

	static final int SLOTS = 36;
	static final int HOTBAR = 9;
	static final int MOST_SETS = 12;
	static final int MAX_NAME = 24;
	static final String DEFAULT_NAME = "Default";
	/** Where the slots are in the inventory screen's menu: the inventory from 9 to 35 at its own numbers, the hotbar from 36 to 44. */
	static final int MENU_MAIN_FROM = 9;
	static final int MENU_HOTBAR = 36;
	static final int MENU_HOTBAR_END = 44;
	private static final int NONE = -1;

	private final List<Set> sets = new ArrayList<>();
	private int active;
	private int pending = NONE;
	private Area lastArea;

	SlotBinds() {
		reset();
	}

	/** One empty "Default" set, used everywhere. */
	void reset() {
		sets.clear();
		sets.add(new Set(DEFAULT_NAME, Area.EVERYWHERE));
		active = 0;
		pending = NONE;
	}

	/** @return whether a slot is on the hotbar */
	static boolean hotbar(int slot) {
		return slot >= 0 && slot < HOTBAR;
	}

	static boolean valid(int slot) {
		return slot >= 0 && slot < SLOTS;
	}

	/**
	 * The bind key on a slot: the first press picks a slot (or removes its bind), the second picks its partner.
	 *
	 * @param blocked whether the slot holds what must not be bound (the SkyBlock Menu)
	 */
	Result press(int slot, boolean blocked) {
		if (!valid(slot) || blocked) {
			pending = NONE;
			return Result.NOT_BINDABLE;
		}
		int[] current = sets.get(active).partners;
		if (pending == NONE) {
			if (current[slot] != NONE) {
				current[current[slot]] = NONE;
				current[slot] = NONE;
				return Result.UNBOUND;
			}
			pending = slot;
			return Result.STARTED;
		}
		int first = pending;
		pending = NONE;
		if (slot == first) {
			return Result.SAME_SLOT;
		}
		if (current[slot] != NONE) {
			return Result.ALREADY_BOUND;
		}
		if (!hotbar(slot) && !hotbar(first)) {
			return Result.NO_HOTBAR;
		}
		current[first] = slot;
		current[slot] = first;
		return Result.BOUND;
	}

	/** Forgets a first slot picked but not yet bound (the inventory was closed). */
	void cancel() {
		pending = NONE;
	}

	/** @return the first slot of a bind being made, or -1 */
	int pending() {
		return pending;
	}

	/** @return the slot bound to this one in the set in use, or -1 */
	int partner(int slot) {
		return partner(active, slot);
	}

	int partner(int set, int slot) {
		return valid(slot) && set >= 0 && set < sets.size() ? sets.get(set).partners[slot] : NONE;
	}

	/** Removes the bind of a slot in a set. @return whether it had one */
	boolean unbind(int set, int slot) {
		int other = partner(set, slot);
		if (other == NONE) {
			return false;
		}
		sets.get(set).partners[slot] = NONE;
		sets.get(set).partners[other] = NONE;
		return true;
	}

	/** Removes every bind of a set. */
	void clearBinds(int set) {
		if (set >= 0 && set < sets.size()) {
			Arrays.fill(sets.get(set).partners, NONE);
			pending = NONE;
		}
	}

	// ------------------------------------------------------------------------------------------------------ sets

	int count() {
		return sets.size();
	}

	String name(int set) {
		return sets.get(set).name;
	}

	Area area(int set) {
		return sets.get(set).area;
	}

	/** @return the set in use */
	int active() {
		return active;
	}

	/** The player chose a set: it is used until the area changes; a bind being made is forgotten. */
	void choose(int set) {
		if (set >= 0 && set < sets.size()) {
			active = set;
			pending = NONE;
		}
	}

	/** @return the new set's index, or -1 when there are as many as can be */
	int add(String name) {
		if (sets.size() >= MOST_SETS) {
			return NONE;
		}
		sets.add(new Set(cleanName(name, "Set " + (sets.size() + 1)), Area.OFF));
		return sets.size() - 1;
	}

	void rename(int set, String name) {
		if (set >= 0 && set < sets.size()) {
			sets.get(set).name = cleanName(name, sets.get(set).name);
		}
	}

	/** Deletes a set; the last one stays. @return whether it was deleted */
	boolean delete(int set) {
		if (sets.size() <= 1 || set < 0 || set >= sets.size()) {
			return false;
		}
		sets.remove(set);
		if (active >= set && active > 0) {
			active--;
		}
		pending = NONE;
		return true;
	}

	void setArea(int set, Area area) {
		if (set >= 0 && set < sets.size()) {
			sets.get(set).area = area == null ? Area.OFF : area;
		}
	}

	/**
	 * Where the player is now: when the area changed, the set for it is used (the first set of that area, else the first of "everywhere else");
	 * a set the player chose stays until then.
	 *
	 * @return whether the set in use changed
	 */
	boolean areaNow(Area here) {
		if (here == lastArea) {
			return false;
		}
		lastArea = here;
		int chosen = first(here);
		if (chosen == NONE) {
			chosen = first(Area.EVERYWHERE);
		}
		if (chosen == NONE || chosen == active) {
			return false;
		}
		active = chosen;
		pending = NONE;
		return true;
	}

	private int first(Area area) {
		for (int i = 0; i < sets.size(); i++) {
			if (sets.get(i).area == area) {
				return i;
			}
		}
		return NONE;
	}

	private static String cleanName(String name, String fallback) {
		String clean = name == null ? "" : name.strip();
		if (clean.length() > MAX_NAME) {
			clean = clean.substring(0, MAX_NAME);
		}
		return clean.isEmpty() ? fallback : clean;
	}

	// ------------------------------------------------------------------------------------------------------ the menu

	/** @return where a bindable slot is in the inventory screen's menu: 9–35 the inventory, 36–44 the hotbar; -1 for any other */
	static int menuSlot(int slot) {
		if (hotbar(slot)) {
			return MENU_HOTBAR + slot;
		}
		return valid(slot) ? slot : NONE;
	}

	/** @return the inventory slot at a place of the inventory screen's menu, or -1 for the crafting slots, the armor and the off hand */
	static int fromMenuSlot(int menuSlot) {
		if (menuSlot >= MENU_HOTBAR && menuSlot <= MENU_HOTBAR_END) {
			return menuSlot - MENU_HOTBAR;
		}
		return menuSlot >= MENU_MAIN_FROM && menuSlot < MENU_HOTBAR ? menuSlot : NONE;
	}

	/**
	 * @return the one swap click a shift-click on a bound slot becomes: the menu slot to click and the hotbar number (0–8) to swap it with, or
	 *         null when the slot has no bind
	 */
	int[] swap(int slot) {
		int other = partner(slot);
		if (other == NONE) {
			return null;
		}
		if (hotbar(other)) {
			return new int[] {menuSlot(slot), other};
		}
		return new int[] {menuSlot(other), slot};
	}

	/** @return a set's binds, for the config: its pairs as [slot, partner] with the lower slot first */
	int[][] pairs(int set) {
		int[] partners = sets.get(set).partners;
		List<int[]> pairs = new ArrayList<>();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (partners[slot] > slot) {
				pairs.add(new int[] {slot, partners[slot]});
			}
		}
		return pairs.toArray(new int[0][]);
	}

	/** Takes a set from the config, replacing the default one the first time. @return its index, or -1 when there are as many as can be */
	int load(String name, Area area, boolean first) {
		if (first) {
			sets.clear();
			active = 0;
		}
		if (sets.size() >= MOST_SETS) {
			return NONE;
		}
		sets.add(new Set(cleanName(name, "Set " + (sets.size() + 1)), area == null ? Area.OFF : area));
		return sets.size() - 1;
	}

	/** Takes a bind of a set from the config; one that breaks the rules (no hotbar slot, a slot bound twice, out of range) is left out. */
	void load(int set, int slot, int other) {
		if (set < 0 || set >= sets.size() || !valid(slot) || !valid(other) || slot == other || !hotbar(slot) && !hotbar(other)) {
			return;
		}
		int[] current = sets.get(set).partners;
		if (current[slot] == NONE && current[other] == NONE) {
			current[slot] = other;
			current[other] = slot;
		}
	}

	/** @return whether a set has any bind */
	boolean hasBinds(int set) {
		for (int partner : sets.get(set).partners) {
			if (partner != NONE) {
				return true;
			}
		}
		return false;
	}
}
