package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player can be doing, and the one place that says which features and HUD elements belong to which activity and which islands are
 * good for which activities. The HUD editor simulates activities with it, and the home page shows the features for the island the player is
 * on. Only activities the mod has features for are listed.
 */
public enum Activity {
	ALWAYS("Always", "", List.of()),
	FISHING("Fishing", "Fishing", List.of()),
	LAVA_FISHING("Lava and trophy fishing", "Fishing", List.of()),
	DWARVEN_MINES("Dwarven Mines", "Mining", Options.MINING),
	CRYSTAL_HOLLOWS("Crystal Hollows", "Mining", Options.MINING),
	MINESHAFT("Glacite Mineshaft", "Mining", Options.MINING),
	/** Any slayer: the one the HUD editor simulates, with every slayer element. */
	SLAYER("Slayer", "Slayer", List.of()),
	// the single slayers, for the islands they are done on
	REVENANT("Revenant Horror", "Slayer", List.of()),
	TARANTULA("Tarantula Broodfather", "Slayer", List.of()),
	SVEN("Sven Packmaster", "Slayer", List.of()),
	VOIDGLOOM("Voidgloom Seraph", "Slayer", List.of()),
	INFERNO("Inferno Demonlord", "Slayer", List.of()),
	RIFTSTALKER("Riftstalker Bloodfiend", "Slayer", List.of()),
	FARMING("Garden", "Farming", List.of()),
	EXPERIMENTS("Experimentation Table", "Experiments", List.of()),
	DRAGONS("Dragon fight", "Dragons", List.of());

	/** The choices within an activity that change what the HUD shows. */
	private static final class Options {
		static final List<String> MINING = List.of("Pickaxe ability ready", "Pickaxe ability on cooldown");
	}

	// the mining options
	public static final int ABILITY_READY = 0;
	public static final int ABILITY_COOLDOWN = 1;

	private final String displayName;
	private final String group;
	private final List<String> options;

	Activity(String displayName, String group, List<String> options) {
		this.displayName = displayName;
		this.group = group;
		this.options = options;
	}

	public String displayName() {
		return displayName;
	}

	/** @return the kind of activity, which is also the name of its category page: "Slayer", "Fishing", "Mining", "Farming"; empty for always */
	public String group() {
		return group;
	}

	/** @return the choices within the activity that change the HUD, such as a slayer boss's phases; empty if there are none */
	public List<String> options() {
		return options;
	}

	public boolean isSlayer() {
		return group.equals("Slayer");
	}

	/** @return whether the HUD editor offers to simulate the activity: one entry for all slayers, not one per slayer */
	public boolean simulatable() {
		return this != ALWAYS && (!isSlayer() || this == SLAYER);
	}

	public boolean isMining() {
		return group.equals("Mining");
	}

	// ------------------------------------------------------------------------------------------------------------ tags

	private static final Set<Activity> ALL_SLAYERS = EnumSet.of(SLAYER, REVENANT, TARANTULA, SVEN, VOIDGLOOM, INFERNO, RIFTSTALKER);
	private static final Set<Activity> ALL_MINING = EnumSet.of(DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT);
	private static final Set<Activity> ALL_FISHING = EnumSet.of(FISHING, LAVA_FISHING);

	/**
	 * The ids of the main mod's features that are on screen whatever the player does: the HUD, the music, the chat. By id, since the add-on
	 * sees the main mod's features only through the API.
	 */
	private static final List<String> ALWAYS_SHOWN = List.of("hotbar", "status_bars", "action_bar_hud", "info_bar", "boss_bars", "armor_hud",
		"inventory_hud", "scoreboard_hud", "tab_list_hud", "music", "chat", "held_item_transform");

	/** The activities of every feature by its id, in the order they are tagged, built once on first use, after all features exist. */
	private static Map<String, Set<Activity>> tags;

	private static Map<String, Set<Activity>> tags() {
		if (tags == null) {
			Map<String, Set<Activity>> map = new LinkedHashMap<>();
			// always on screen
			for (String id : ALWAYS_SHOWN) {
				map.put(id, EnumSet.of(ALWAYS));
			}
			for (AddonFeature feature : List.of(SkyBlockFeatures.PET, SkyBlockFeatures.EQUIPMENT, SkyBlockFeatures.DEPLOYABLES)) {
				map.put(feature.id(), EnumSet.of(ALWAYS));
			}
			map.put(SkyBlockFeatures.EXPERIMENT_PROFIT.id(), EnumSet.of(EXPERIMENTS));
			map.put(SkyBlockFeatures.DRAGON_PROFIT.id(), EnumSet.of(DRAGONS));
			map.put(SkyBlockFeatures.LAST_DRAGONS.id(), EnumSet.of(DRAGONS));
			map.put(SkyBlockFeatures.GRAPPLING_HOOK.id(), EnumSet.of(ALWAYS));
			map.put(SkyBlockFeatures.SEA_CREATURES.id(), ALL_FISHING);
			map.put(SkyBlockFeatures.FISHING_PROFIT.id(), ALL_FISHING);
			map.put(SkyBlockFeatures.MINING_PROFIT.id(), ALL_MINING);
			map.put(SkyBlockFeatures.MINING_ABILITY.id(), ALL_MINING);
			map.put(SkyBlockFeatures.HOTM_TREE.id(), ALL_MINING);
			map.put(SkyBlockFeatures.TICK_GLIDE.id(), ALL_MINING);
			map.put(SkyBlockFeatures.COMMISSIONS.id(), EnumSet.of(DWARVEN_MINES, CRYSTAL_HOLLOWS));
			map.put(SkyBlockFeatures.MINESHAFT_PITY.id(), EnumSet.of(DWARVEN_MINES, MINESHAFT));
			map.put(SkyBlockFeatures.CORPSE_TRACKER.id(), EnumSet.of(MINESHAFT));
			map.put(SkyBlockFeatures.CORPSE_PROFIT.id(), EnumSet.of(MINESHAFT));
			map.put(SkyBlockFeatures.SLAYER_PHASE.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.SLAYER_HELPERS.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.MINIBOSSES.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.VOIDGLOOM_HELPER.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.TARANTULA_HELPER.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.SLAYER_PROFIT.id(), ALL_SLAYERS);
			map.put(SkyBlockFeatures.PESTS.id(), EnumSet.of(FARMING));
			map.put(SkyBlockFeatures.MOUSE_LOCK.id(), EnumSet.of(FARMING));
			map.put(SkyBlockFeatures.VISITORS.id(), EnumSet.of(FARMING));
			map.put(SkyBlockFeatures.FARMING_HUD.id(), EnumSet.of(FARMING));
			map.put(SkyBlockFeatures.FARMING_PROFIT.id(), EnumSet.of(FARMING));
			tags = map;
		}
		return tags;
	}

	/** @return the activities a feature belongs to; empty for features that belong to none, such as the world effects */
	public static Set<Activity> of(FeatureView feature) {
		// a part of a feature (the corpse highlight of the corpse tracker) belongs where the feature does
		FeatureView whole = feature == SkyBlockFeatures.CORPSES ? SkyBlockFeatures.CORPSE_TRACKER : feature;
		Set<Activity> found = whole == null ? null : tags().get(whole.id());
		return found == null ? Set.of() : found;
	}

	/** @return whether the feature belongs to the activity, or is always there */
	public static boolean belongs(FeatureView feature, Activity activity) {
		Set<Activity> found = of(feature);
		return found.contains(ALWAYS) || found.contains(activity);
	}

	/** @return the features of an activity, in the order they are tagged */
	public static List<AddonFeature> featuresOf(Activity activity) {
		List<AddonFeature> found = new ArrayList<>();
		for (Map.Entry<String, Set<Activity>> entry : tags().entrySet()) {
			if (entry.getValue().contains(activity)) {
				AddonFeature feature = SkyBlockFeatures.byId(entry.getKey());
				if (feature != null) {
					found.add(feature);
				}
			}
		}
		return found;
	}

	// --------------------------------------------------------------------------------------------------------- islands

	/** The activities of every island the mod detects, by the name the player list shows after "Area:". */
	private static final Map<String, List<Activity>> ISLANDS = new HashMap<>();

	static {
		ISLANDS.put("Hub", List.of(REVENANT, SVEN, FISHING));
		ISLANDS.put("Spider's Den", List.of(TARANTULA));
		ISLANDS.put("The Park", List.of(SVEN, FISHING));
		ISLANDS.put("The End", List.of(VOIDGLOOM));
		ISLANDS.put("Crimson Isle", List.of(INFERNO, LAVA_FISHING));
		ISLANDS.put("The Rift", List.of(RIFTSTALKER));
		ISLANDS.put("Dwarven Mines", List.of(DWARVEN_MINES));
		ISLANDS.put("Crystal Hollows", List.of(CRYSTAL_HOLLOWS, LAVA_FISHING));
		ISLANDS.put("Mineshaft", List.of(MINESHAFT));
		ISLANDS.put("Garden", List.of(FARMING));
		ISLANDS.put("Backwater Bayou", List.of(FISHING));
		ISLANDS.put("Jerry's Workshop", List.of(FISHING));
		ISLANDS.put("Galatea", List.of(FISHING));
		ISLANDS.put("Private Island", List.of(FISHING));
	}

	/** @return the activities of an island, in the order the home page shows them; empty for islands without any */
	public static List<Activity> atIsland(String island) {
		List<Activity> found = island == null ? null : ISLANDS.get(island.strip());
		return found == null ? List.of() : found;
	}
}
