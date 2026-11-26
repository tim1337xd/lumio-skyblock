package dev.tim1337.lumio.skyblock.dungeon.partyfinder;

import dev.tim1337.lumio.skyblock.dungeon.DungeonClass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The player's own Catacombs level and class levels, read from the Dungeons card of SkyBlock's player list ("Catacombs 60", "Tank 50") when
 * it is there, and remembered per profile, so the Party Finder Helper can tell a party whose requirement the player does not meet. Levels
 * that were never seen stay unknown, and an unknown level never counts against a party. Plain data, tested without the game.
 */
public final class DungeonLevels {
	/** No level known. */
	public static final int UNKNOWN = -1;
	private static final int MOST_LEVEL = 999;
	private static final int MOST_PROFILES = 16;
	private static final String CARD = "dungeons:";
	private static final String CATACOMBS = "catacombs";
	/** "Catacombs 60", "Tank 50: 35.2%", also with a colon before the number. */
	private static final Pattern LEVEL = Pattern.compile("^(Catacombs|Healer|Mage|Berserk|Archer|Tank):? (\\d{1,3})(?:\\D.*)?$");

	/** The levels of one profile. */
	public static final class Levels {
		private int catacombs = UNKNOWN;
		private final Map<DungeonClass, Integer> classes = new EnumMap<>(DungeonClass.class);

		public int catacombs() {
			return catacombs;
		}

		/** @return the level of a class, or {@link #UNKNOWN} */
		public int of(DungeonClass dungeonClass) {
			return classes.getOrDefault(dungeonClass, UNKNOWN);
		}
	}

	private final Map<String, Levels> byProfile = new HashMap<>();

	/** @return the levels remembered for a profile; all unknown for one never seen */
	public Levels of(String profile) {
		Levels levels = byProfile.get(key(profile));
		return levels == null ? new Levels() : levels;
	}

	/**
	 * Reads the Dungeons card out of the player list's lines (without colors, in their order).
	 *
	 * @return whether a level changed
	 */
	public boolean read(String profile, List<String> lines) {
		boolean inCard = false;
		boolean changed = false;
		for (String raw : lines) {
			String line = raw.strip();
			if (line.toLowerCase(Locale.ROOT).equals(CARD)) {
				inCard = true;
				continue;
			}
			if (!inCard) {
				continue;
			}
			Matcher matcher = LEVEL.matcher(line);
			if (!matcher.matches()) {
				if (line.isEmpty() || line.endsWith(":")) {
					// the card is over: a blank line, or the next card's heading
					break;
				}
				continue;
			}
			int level = Math.min(MOST_LEVEL, Integer.parseInt(matcher.group(2)));
			if (byProfile.size() >= MOST_PROFILES && !byProfile.containsKey(key(profile))) {
				byProfile.clear();
			}
			Levels levels = byProfile.computeIfAbsent(key(profile), each -> new Levels());
			if (matcher.group(1).equalsIgnoreCase(CATACOMBS)) {
				changed |= levels.catacombs != level;
				levels.catacombs = level;
			} else {
				Integer before = levels.classes.put(DungeonClass.of(matcher.group(1)), level);
				changed |= before == null || before != level;
			}
		}
		return changed;
	}

	private static String key(String profile) {
		return profile == null || profile.isBlank() ? "unknown" : profile;
	}

	/** Writes the remembered levels, by profile. */
	public JsonObject save() {
		JsonObject json = new JsonObject();
		byProfile.forEach((profile, levels) -> {
			JsonObject each = new JsonObject();
			if (levels.catacombs >= 0) {
				each.addProperty(CATACOMBS, levels.catacombs);
			}
			levels.classes.forEach((dungeonClass, level) -> each.addProperty(dungeonClass.label().toLowerCase(Locale.ROOT), level));
			json.add(profile, each);
		});
		return json;
	}

	/** Reads what {@link #save} wrote; anything else is left out. */
	public void load(JsonElement element) {
		byProfile.clear();
		if (!(element instanceof JsonObject json)) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (!(entry.getValue() instanceof JsonObject each) || byProfile.size() >= MOST_PROFILES) {
				continue;
			}
			Levels levels = new Levels();
			for (Map.Entry<String, JsonElement> value : each.entrySet()) {
				int level;
				try {
					level = Math.clamp(value.getValue().getAsInt(), 0, MOST_LEVEL);
				} catch (RuntimeException e) {
					continue;
				}
				if (value.getKey().equals(CATACOMBS)) {
					levels.catacombs = level;
				} else {
					DungeonClass dungeonClass = DungeonClass.of(value.getKey());
					if (dungeonClass != DungeonClass.NONE) {
						levels.classes.put(dungeonClass, level);
					}
				}
			}
			byProfile.put(entry.getKey(), levels);
		}
	}
}
