package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * New modules start off, also for a config from 1.0.0 Beta 1 (which has none of them), except the ones named: the dungeon announcements and the
 * Highlights (for the starred mobs). What the old config holds stays as it was.
 */
class NewModulesOffTest {
	/** A config of 1.0.0 Beta 1, as it was saved: three modules of then, each set the other way than their default. */
	private static final String BETA_1 = """
		{
		  "arrows": {"enabled": false},
		  "corpse_highlight": {"enabled": true},
		  "cocoons": {"enabled": false}
		}
		""";
	/** The modules that came after Beta 1 and still start on, by name. */
	private static final Set<String> NAMED_ON = Set.of("dungeon_announcements", "dungeon_highlights");
	/** A module without a switch of its own: there is nothing to turn on or off. */
	private static final Set<String> NO_SWITCH = Set.of("dungeon_practice");
	private static final Set<String> NEW_SINCE_BETA_1 = Set.of("hotspot_helper", "bite_timer", "dungeon_abilities", "dungeon_announcements", "dungeon_blood_camp", "dungeon_boss",
		"dungeon_chests", "dungeon_doors", "dungeon_f7", "dungeon_f7_devices", "dungeon_highlights", "dungeon_invincibility", "dungeon_leap", "dungeon_livid",
		"dungeon_map", "dungeon_puzzles", "dungeon_score", "dungeon_splits", "dungeon_terminal_status", "dungeon_terminals", "dungeon_terracotta",
		"dungeon_waypoints", "party_commands", "powder_helper", "crystal_hollows_map", "inventory_buttons", "dungeon_alerts", "precision_mining", "slot_binds",
		"pickup_log", "loadout_keys", "dungeon_auto_requeue", "dungeon_party_finder", "fishing_alerts", "farming_keys", "pest_highlight");
	/** The modules of 1.0.0 Beta 1 (the add-on as released on 2026-11-07): together with the new ones, every module there is. */
	private static final Set<String> IN_BETA_1 = Set.of("arrows", "boss_label", "break_reset_fix", "celebrations", "cocoons", "command_suggestions",
		"commissions_hud", "corpse_highlight", "corpse_profit", "corpses", "custom_scoreboard", "deployables", "dragon_profit", "drop_toasts", "enchant_colors",
		"equipment_hud", "etherwarp", "experiment_profit", "experiments", "explosions_hider", "farming_hud", "farming_profit", "fishing_profit", "grappling_hook",
		"hotm_tree_hud", "item_list", "item_protection", "item_tooltip_lines", "keep_terrain", "last_dragons", "mineshaft_pity", "minibosses", "mining_ability",
		"mining_profit", "mouse_lock", "pest_hud", "pet_candy", "pet_hud", "sea_creatures", "skyblock_chat_tab", "slayer_helpers", "slayer_phase", "slayer_profit",
		"storage_overlay", "tarantula_helper", "tick_glide", "visitor_hud", "voidgloom_helper", "wardrobe_overlay");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void aBeta1ConfigGetsTheNewModulesOffAndKeepsItsValues() {
		JsonObject beta1 = JsonParser.parseString(BETA_1).getAsJsonObject();
		Map<String, Feature> features = new HashMap<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			feature.settings().forEach(Setting::reset);
			feature.load(beta1.has(feature.id()) ? beta1.getAsJsonObject(feature.id()) : new JsonObject());
			features.put(feature.id(), feature);
		}
		for (String id : NEW_SINCE_BETA_1) {
			Feature feature = features.get(id);
			assertEquals(NAMED_ON.contains(id), feature.isEnabled(), id);
		}
		assertFalse(features.get("arrows").isEnabled());
		assertTrue(features.get("corpse_highlight").isEnabled());
		assertFalse(features.get("cocoons").isEnabled());
		assertTrue(NO_SWITCH.stream().allMatch(features::containsKey));
	}

	/** A module added later must be named in {@link #NEW_SINCE_BETA_1}, so that its default is checked here: none is left out by mistake. */
	@Test
	void everyModuleIsKnownFromBeta1OrNamedAsNew() {
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			String id = Adapters.featureOf(addon).id();
			assertTrue(IN_BETA_1.contains(id) || NEW_SINCE_BETA_1.contains(id) || NO_SWITCH.contains(id), id + " is in neither list: add it to NEW_SINCE_BETA_1");
		}
	}
}
