package dev.tim1337.lumio.skyblock.mining.hollows;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The places of the Crystal Hollows the map marks: their names, a short name for the map, a color, and how the player's own visit is recognized
 * (the scoreboard's area, a line of a non-player character or the crystal found there). Places without an area of their own are found only
 * by those lines or by coordinates shared in chat.
 */
enum HollowsStructure {
	JUNGLE_TEMPLE("Jungle Temple", "Temple", 0x7ED957, "Jungle Temple", List.of("[NPC] Kalhuiki Door Guardian:"), "Amethyst",
		List.of("jungle temple", "temple")),
	MINES_OF_DIVAN("Mines of Divan", "Divan", 0x2EE6A6, "Mines of Divan", List.of("[NPC] Keeper of "), "Jade", List.of("mines of divan", "divan")),
	GOBLIN_QUEENS_DEN("Goblin Queen's Den", "Queen", 0xFFB340, "Goblin Queen's Den", List.of(), "Amber", List.of("goblin queen", "queen's den", "queen")),
	LOST_PRECURSOR_CITY("Lost Precursor City", "City", 0x64A8FF, "Lost Precursor City", List.of("[NPC] Professor Robot:"), "Sapphire",
		List.of("lost precursor city", "precursor city", "city")),
	KHAZAD_DUM("Khazad-dûm", "Khazad", 0xFF5E57, "Khazad-dûm", List.of(), "Topaz", List.of("khazad-dûm", "khazad-dum", "khazad", "bal")),
	FAIRY_GROTTO("Fairy Grotto", "Grotto", 0xFF7AD9, "Fairy Grotto", List.of(), "", List.of("fairy grotto", "grotto", "fairy")),
	DRAGONS_LAIR("Dragon's Lair", "Lair", 0xC77DFF, "Dragon's Lair", List.of(), "", List.of("dragon's lair", "dragons lair", "lair")),
	GOBLIN_KING("Goblin King", "King", 0xFFD60A, "", List.of("[NPC] King Yolkar:"), "", List.of("goblin king", "king yolkar", "yolkar", "king")),
	ODAWA("Odawa", "Odawa", 0x9BE564, "", List.of("[NPC] Odawa:"), "", List.of("odawa")),
	KEY_GUARDIAN("Key Guardian", "Guardian", 0xB0B8C8, "", List.of(), "", List.of("key guardian", "guardian")),
	CORLEONE("Corleone", "Corleone", 0xE0E0E0, "", List.of("[NPC] Boss Corleone:"), "", List.of("boss corleone", "corleone"));

	/** What is not part of a name: punctuation and digits, so "Divan:" reads as "divan". */
	private static final Pattern NOT_IN_NAMES = Pattern.compile("[^\\p{L}'\\- ]");

	final String title;
	final String shortName;
	final int defaultRgb;
	/** The scoreboard's area inside the structure, or "" when it has none. */
	final String area;
	/** The starts of server lines that come only from the structure's characters, near them. */
	final List<String> lines;
	/** The crystal found inside it ("Jade"), or "". */
	final String crystal;
	/** What players call it in chat, lower case, the longest first. */
	final List<String> aliases;

	HollowsStructure(String title, String shortName, int defaultRgb, String area, List<String> lines, String crystal, List<String> aliases) {
		this.title = title;
		this.shortName = shortName;
		this.defaultRgb = defaultRgb;
		this.area = area;
		this.lines = lines;
		this.crystal = crystal;
		this.aliases = aliases;
	}

	/** @return the key of the structure in saved data and settings: "mines_of_divan" */
	String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	static HollowsStructure byKey(String key) {
		for (HollowsStructure structure : values()) {
			if (structure.key().equals(key)) {
				return structure;
			}
		}
		return null;
	}

	/** @return the structure whose area the scoreboard shows ("Mines of Divan"), or null */
	static HollowsStructure byArea(String area) {
		if (area == null || area.isEmpty()) {
			return null;
		}
		for (HollowsStructure structure : values()) {
			if (!structure.area.isEmpty() && structure.area.equalsIgnoreCase(area.strip())) {
				return structure;
			}
		}
		return null;
	}

	/** @return the structure a server line comes from (its character speaking, or its crystal found), or null */
	static HollowsStructure byServerLine(String line) {
		for (HollowsStructure structure : values()) {
			for (String start : structure.lines) {
				if (line.startsWith(start)) {
					return structure;
				}
			}
		}
		if (!line.contains("CRYSTAL FOUND")) {
			return null;
		}
		for (HollowsStructure structure : values()) {
			if (!structure.crystal.isEmpty() && line.contains(structure.crystal + " Crystal")) {
				return structure;
			}
		}
		return null;
	}

	/** @return the structure a name from chat stands for ("divan", "Mines of Divan", "Khazad-dûm"), or null: whole words only, longest first */
	static HollowsStructure byName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		String text = " " + NOT_IN_NAMES.matcher(name.toLowerCase(Locale.ROOT)).replaceAll(" ") + " ";
		HollowsStructure best = null;
		int bestLength = 0;
		for (HollowsStructure structure : values()) {
			for (String alias : structure.aliases) {
				if (alias.length() > bestLength && text.contains(" " + alias + " ")) {
					best = structure;
					bestLength = alias.length();
				}
			}
		}
		return best;
	}
}
