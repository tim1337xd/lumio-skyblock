package dev.tim1337.lumio.skyblock.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The rarity and type of a SkyBlock item, read from the last line of its lore ("§6§lLEGENDARY DUNGEON SWORD"), and the group it belongs to for the
 * item search's filter chips. Read once per item and kept.
 */
public final class ItemTypes {
	/** The item search's groups, in the order of their chips. */
	public enum Category {
		ALL("All"),
		WEAPONS("Weapons"),
		ARMOR("Armor"),
		ACCESSORIES("Accessories"),
		PETS("Pets"),
		TOOLS("Tools"),
		MATERIALS("Materials");

		public final String label;

		Category(String label) {
			this.label = label;
		}
	}

	/**
	 * What an item is.
	 *
	 * @param rank   its rarity's rank, from 0 (common) upwards, -1 without a rarity, for sorting
	 * @param rarity its rarity as written ("LEGENDARY"), or empty
	 * @param type   its type as written ("DUNGEON SWORD"), or empty
	 */
	public record Info(int rank, String rarity, String type, Category category) {
		/** @return "LEGENDARY · DUNGEON SWORD", or whichever part there is */
		public String label() {
			return rarity.isEmpty() ? type : type.isEmpty() ? rarity : rarity + " · " + type;
		}
	}

	private static final Pattern CODES = Pattern.compile("§.");
	/** The rarities, lowest first; two words before one, so "VERY SPECIAL" is not read as "SPECIAL". */
	private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE",
		"ADMIN"};
	private static final String[] WEAPONS = {"SWORD", "LONGSWORD", "BOW", "WAND", "FISHING WEAPON", "WEAPON"};
	private static final String[] ARMOR = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET", "GAUNTLET"};
	private static final String[] ACCESSORIES = {"ACCESSORY", "HATCESSORY"};
	private static final String[] TOOLS = {"PICKAXE", "DRILL", "AXE", "HOE", "SHOVEL", "SHEARS", "FISHING ROD", "ROD", "VACUUM", "CHISEL", "TOOL"};
	private static final Info NOTHING = new Info(-1, "", "", Category.MATERIALS);
	private static final Map<String, Info> KNOWN = new HashMap<>();

	private ItemTypes() {
	}

	/** @return what an item of the repository is, read once and then kept */
	public static Info of(ItemRepo.Entry entry) {
		Info known = KNOWN.get(entry.id);
		if (known == null) {
			known = parse(lastLine(entry), entry.id);
			KNOWN.put(entry.id, known);
		}
		return known;
	}

	private static String lastLine(ItemRepo.Entry entry) {
		for (int i = entry.lore.size() - 1; i >= 0; i--) {
			String line = CODES.matcher(entry.lore.get(i)).replaceAll("").strip();
			if (!line.isEmpty()) {
				return line;
			}
		}
		return "";
	}

	/**
	 * @param line the last lore line without colors ("LEGENDARY DUNGEON SWORD", "a EPIC ACCESSORY a" for a recombobulated one)
	 * @param id   the item's id; pets have a level after a semicolon ("BEE;4")
	 * @return what the item is; an item without a rarity line is a material
	 */
	static Info parse(String line, String id) {
		String text = line.toUpperCase(Locale.ROOT).strip();
		// the recombobulator's marks around the line
		if (text.startsWith("A ")) {
			text = text.substring(2).strip();
		}
		if (text.endsWith(" A")) {
			text = text.substring(0, text.length() - 2).strip();
		}
		int rank = -1;
		String rarity = "";
		for (int i = RARITIES.length - 1; i >= 0; i--) {
			String candidate = RARITIES[i];
			if ((text.equals(candidate) || text.startsWith(candidate + " ")) && candidate.length() > rarity.length()) {
				rank = i;
				rarity = candidate;
			}
		}
		if (rank < 0) {
			return id.contains(";") ? new Info(-1, "", "PET", Category.PETS) : NOTHING;
		}
		String type = text.substring(rarity.length()).strip();
		return new Info(rank, rarity, type, categoryOf(type, id));
	}

	private static Category categoryOf(String type, String id) {
		if (id.contains(";") || type.equals("PET") || type.endsWith(" PET")) {
			return Category.PETS;
		}
		if (endsWithAny(type, ACCESSORIES)) {
			return Category.ACCESSORIES;
		}
		if (endsWithAny(type, WEAPONS)) {
			return Category.WEAPONS;
		}
		if (endsWithAny(type, ARMOR)) {
			return Category.ARMOR;
		}
		if (endsWithAny(type, TOOLS)) {
			return Category.TOOLS;
		}
		return Category.MATERIALS;
	}

	private static boolean endsWithAny(String type, String[] words) {
		for (String word : words) {
			if (type.equals(word) || type.endsWith(" " + word)) {
				return true;
			}
		}
		return false;
	}
}
