package dev.tim1337.lumio.skyblock.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How good a SkyBlock enchant's level is, from the item repository: how many levels it has, by the highest book of it and its enchanting costs, and how far an enchanting table goes. At the most levels it is perfect, above
 * the table's level great, at it good, below it poor. Ultimate enchants are always ultimate; an enchant nothing knows has no tier. Plain data, so it is unit tested.
 */
public final class EnchantTiers {
	/** The tiers, from the best. */
	public enum Tier {
		ULTIMATE, PERFECT, GREAT, GOOD, POOR
	}

	/** Nothing known yet. */
	public static final EnchantTiers EMPTY = new EnchantTiers();
	private static final String ULTIMATE_PREFIX = "ultimate_";
	private static final int MOST_LEVEL = 100;
	/**
	 * The enchants that grow with use. The repository lists only their first level, so what it says about them cannot be used: these go up to ten, and half of that is good.
	 */
	private static final Set<String> GROW_WITH_USE = Set.of("compact", "cultivating", "expertise", "hecatomb", "champion", "toxophilite", "absorb");
	private static final int GROWN_MOST = 10;
	private static final int GROWN_GOOD = 5;
	/** The enchants the repository does not know, with their most levels: the one list by hand. When the repository has them, it decides. */
	private static final Map<String, Integer> BY_HAND = Map.of("gravity", 6, "drain", 5, "prismatic", 5);
	private static final int MOST_UNKNOWN_REMEMBERED = 256;

	private final Map<String, int[]> levels = new HashMap<>();
	private final Set<String> ultimates = new HashSet<>();
	private final Map<String, String> sameAs = new HashMap<>();
	private final Set<String> unknownSeen = new HashSet<>();

	private EnchantTiers() {
	}

	/** @return the tiers of the repository's enchant file; what it cannot read is left out */
	public static EnchantTiers of(JsonObject root) {
		EnchantTiers tiers = new EnchantTiers();
		JsonObject costs = object(root, "enchants_xp_cost");
		JsonObject table = object(root, "max_xp_table_levels");
		for (Map.Entry<String, JsonElement> cost : costs.entrySet()) {
			if (cost.getValue().isJsonArray()) {
				String key = cost.getKey().toLowerCase(Locale.ROOT);
				int most = cost.getValue().getAsJsonArray().size();
				int good = table.has(cost.getKey()) && table.get(cost.getKey()).isJsonPrimitive() ? table.get(cost.getKey()).getAsInt() : most - 1;
				tiers.levels.put(key, new int[]{most, good});
			}
		}
		BY_HAND.forEach((name, most) -> tiers.levels.putIfAbsent(name, new int[]{most, most - 1}));
		for (String grown : GROW_WITH_USE) {
			tiers.levels.put(grown, new int[]{GROWN_MOST, GROWN_GOOD});
		}
		// some enchants have another name in the lore than in the file
		List<JsonElement> ids = root.has("enchant_mapping_id") && root.get("enchant_mapping_id").isJsonArray() ? root.getAsJsonArray("enchant_mapping_id").asList() : List.of();
		List<JsonElement> items = root.has("enchant_mapping_item") && root.get("enchant_mapping_item").isJsonArray() ? root.getAsJsonArray("enchant_mapping_item").asList() : List.of();
		for (int i = 0; i < ids.size() && i < items.size(); i++) {
			String item = items.get(i).getAsString().toLowerCase(Locale.ROOT);
			String id = ids.get(i).getAsString().toLowerCase(Locale.ROOT);
			tiers.sameAs.put(id, item);
			int[] known = tiers.levels.get(item);
			if (known != null) {
				tiers.levels.put(id, known);
			}
		}
		for (Map.Entry<String, JsonElement> group : object(root, "enchants").entrySet()) {
			if (group.getValue().isJsonArray()) {
				for (JsonElement enchant : group.getValue().getAsJsonArray()) {
					String name = enchant.getAsString().toLowerCase(Locale.ROOT);
					if (name.startsWith(ULTIMATE_PREFIX)) {
						tiers.ultimates.add(name);
						tiers.ultimates.add(name.substring(ULTIMATE_PREFIX.length()));
					}
				}
			}
		}
		return tiers;
	}

	/**
	 * Takes the most levels from the repository's books: its enchanting costs leave out many enchants and the levels no table gives.
	 *
	 * @param books the highest book of each enchant by its id in lower case ("smite" to 7); what grows with use has only its first level there and stays as it is
	 */
	public void addBooks(Map<String, Integer> books) {
		for (Map.Entry<String, Integer> book : books.entrySet()) {
			String name = book.getKey();
			int most = book.getValue();
			if (GROW_WITH_USE.contains(name) || name.startsWith(ULTIMATE_PREFIX) || most < 1 || most > MOST_LEVEL) {
				continue;
			}
			int[] known = levels.get(name);
			int good = known != null ? known[1] : most - 1;
			int top = known != null && !BY_HAND.containsKey(name) ? Math.max(known[0], most) : most;
			levels.put(name, new int[]{top, good});
		}
		sameAs.forEach((id, item) -> {
			int[] known = levels.get(item);
			if (known != null) {
				levels.put(id, known);
			}
		});
	}

	/** @return whether a name is not known and this is the first time it is seen, to note it once; the names kept are bounded */
	public boolean firstUnknown(String name) {
		if (levels.containsKey(key(name)) || ultimates.contains(key(name)) || unknownSeen.size() >= MOST_UNKNOWN_REMEMBERED) {
			return false;
		}
		return unknownSeen.add(key(name));
	}

	private static JsonObject object(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}

	/**
	 * @param name  the enchant's name as in the lore ("Ender Slayer")
	 * @param level its level
	 * @return its tier, or null when the enchant is not known
	 */
	public Tier tier(String name, int level) {
		String key = key(name);
		if (ultimates.contains(key)) {
			return Tier.ULTIMATE;
		}
		int[] known = levels.get(key);
		if (known == null) {
			return null;
		}
		if (level >= known[0]) {
			return Tier.PERFECT;
		}
		return level > known[1] ? Tier.GREAT : level == known[1] ? Tier.GOOD : Tier.POOR;
	}

	static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replace("'", "").replace(' ', '_').replace('-', '_');
	}

	/** @return a level written in Roman numerals or digits, or -1 */
	public static int level(String text) {
		if (text.isEmpty()) {
			return -1;
		}
		if (Character.isDigit(text.charAt(0))) {
			try {
				int value = Integer.parseInt(text);
				return value <= MOST_LEVEL ? value : -1;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		int total = 0;
		int last = 0;
		for (int i = text.length() - 1; i >= 0; i--) {
			int value = switch (text.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> -1;
			};
			if (value < 0) {
				return -1;
			}
			total += value < last ? -value : value;
			last = Math.max(last, value);
		}
		return total;
	}
}
