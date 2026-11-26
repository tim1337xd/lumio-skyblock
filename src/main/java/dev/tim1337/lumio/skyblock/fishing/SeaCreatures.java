package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.experimental.util.Logs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hypixel SkyBlock's sea creatures: each with its rarity, the chat line it surfaces with, and where it is fished. The tracker counts them and the
 * fishing alerts tell of the rare ones, both from this one list.
 * <p>
 * The list is SkyHanni's data file (SkyHanni-REPO, {@code constants/SeaCreatures.json}, MIT license, Copyright (c) 2022 hannibal2), bundled
 * unchanged beside its license ({@code skyblock/fishing/sea-creatures.json}, {@code LICENSE-skyhanni-repo.txt}): the names, the rarities, the
 * lines of the game, which creatures count as rare, and the names a creature had before. Ours are the places the alert list is sorted by and
 * the names counted under before (see {@link #named}).
 */
public final class SeaCreatures {
	/** Where a sea creature is fished, as the alert list groups them. */
	public enum Place {
		WATER("Water"), MINES("Mines"), CRIMSON_ISLE("Crimson Isle"), SPOOKY_FESTIVAL("Spooky Festival"), WORKSHOP("Jerry's Workshop"),
		FISHING_FESTIVAL("Fishing Festival"), BAYOU("Backwater Bayou"), HOTSPOT("Hotspots"), GALATEA("Galatea"), LOTUS_ATOLL("Lotus Atoll"),
		TORRHUS_CANYON("Torrhus Canyon");

		private final String label;

		Place(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** A sea creature: name, rarity (0 common to 5 mythic), its catch message (or a part of it), where it is fished, and whether it counts as a rare one. */
	public record Creature(String name, int rarity, String message, Place place, boolean rare) {
		/** @return the name as part of a setting's key: lower case, with underscores */
		public String key() {
			return name.toLowerCase(Locale.ROOT).replace(' ', '_');
		}
	}

	/** The line before a catch when it is a double hook. */
	public static final String DOUBLE_HOOK = "Double Hook!";
	/** From this rarity on (legendary) a creature is a rare one for the alerts, beside those the data marks as rare. */
	public static final int RARE_FROM = 4;
	private static final String DATA = "/assets/lumio-skyblock/skyblock/fishing/sea-creatures.json";
	private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
	/** The data's groups, by the place of ours they belong to; a group not named here is fished in water. */
	private static final Map<String, Place> PLACES = Map.ofEntries(Map.entry("WINTER_ISLAND", Place.WORKSHOP), Map.entry("SPOOKY", Place.SPOOKY_FESTIVAL),
		Map.entry("SHARK", Place.FISHING_FESTIVAL), Map.entry("ABANDONED_QUARRY", Place.MINES), Map.entry("MAGMA_FIELDS", Place.MINES),
		Map.entry("LAVA_PRECURSOR", Place.MINES), Map.entry("GOBLIN_BURROWS", Place.MINES), Map.entry("WATER_CRYSTAL_HOLLOWS", Place.MINES),
		Map.entry("LAVA_CRIMSON_ISLE", Place.CRIMSON_ISLE), Map.entry("PLHLEGBLAST", Place.CRIMSON_ISLE), Map.entry("BACKWATER_BAYOU", Place.BAYOU),
		Map.entry("LAVA_HOTSPOT", Place.HOTSPOT), Map.entry("WATER_HOTSPOT", Place.HOTSPOT), Map.entry("MOONGLADE_MARSH", Place.GALATEA),
		Map.entry("LOTUS_ATOLL", Place.LOTUS_ATOLL), Map.entry("TORRHUS_CANYON", Place.TORRHUS_CANYON));

	private static final List<Creature> ALL = new ArrayList<>();
	private static final Map<String, Creature> BY_NAME = new HashMap<>();

	static {
		load();
	}

	/** Reads the bundled list: every group's creatures, in the order of our places and from common to rare within each. */
	private static void load() {
		try (InputStream in = SeaCreatures.class.getResourceAsStream(DATA)) {
			if (in == null) {
				Logs.warn("Sea creatures: the list is missing from the jar", new IllegalStateException(DATA));
				return;
			}
			JsonObject groups = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> group : groups.entrySet()) {
				Place place = PLACES.getOrDefault(group.getKey(), Place.WATER);
				JsonObject creatures = group.getValue().getAsJsonObject().getAsJsonObject("sea_creatures");
				for (Map.Entry<String, JsonElement> each : creatures.entrySet()) {
					JsonObject data = each.getValue().getAsJsonObject();
					// a creature without a line of its own (one that only comes out of another) cannot be told from chat
					if (!data.has("chat_message")) {
						continue;
					}
					int rarity = Math.max(0, RARITIES.indexOf(data.has("rarity") ? data.get("rarity").getAsString() : ""));
					boolean rare = rarity >= RARE_FROM || data.has("rare") && data.get("rare").getAsBoolean();
					Creature creature = new Creature(each.getKey(), rarity, data.get("chat_message").getAsString(), place, rare);
					ALL.add(creature);
					BY_NAME.put(creature.name(), creature);
					if (data.has("old_names")) {
						for (JsonElement old : data.getAsJsonArray("old_names")) {
							BY_NAME.putIfAbsent(old.getAsString(), creature);
						}
					}
				}
			}
			ALL.sort(java.util.Comparator.comparing((Creature creature) -> creature.place().ordinal()).thenComparing(Creature::rarity));
		} catch (IOException | RuntimeException e) {
			Logs.warn("Sea creatures: the list could not be read", e);
		}
	}

	private SeaCreatures() {
	}

	/** @return every sea creature, in the order of the places and from common to rare within each */
	public static List<Creature> all() {
		return List.copyOf(ALL);
	}

	/** @return the creature of a name, also of one it had before ("Sea Emperor"), or null */
	public static Creature named(String name) {
		return BY_NAME.get(name);
	}

	/** @return the name a creature has today for a name it was saved under (a creature that was renamed), or the name itself */
	public static String nameNow(String name) {
		Creature creature = BY_NAME.get(name);
		return creature == null ? name : creature.name();
	}

	/** @return the names creatures had before, each with the name it has today */
	public static Map<String, String> renamed() {
		Map<String, String> renamed = new java.util.TreeMap<>();
		BY_NAME.forEach((name, creature) -> {
			if (!name.equals(creature.name())) {
				renamed.put(name, creature.name());
			}
		});
		return renamed;
	}

	/** @return the creature a chat line (without colors) says surfaced, or null when it is no catch line */
	public static Creature caughtIn(String line) {
		for (Creature creature : ALL) {
			if (line.contains(creature.message())) {
				return creature;
			}
		}
		return null;
	}

	/** @return whether a creature is one of the rare ones, which the alerts tell of unless the player chose otherwise */
	public static boolean rare(Creature creature) {
		return creature.rare();
	}
}
