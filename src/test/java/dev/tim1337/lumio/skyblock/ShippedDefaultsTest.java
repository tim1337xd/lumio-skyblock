package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.ConfigPart;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The add-on's shipped default settings ({@code assets/lumio-skyblock/defaults.json}, which Lumio takes before the player's own) hold only settings:
 * every key is a setting of the add-on, never a private value, game state, a HUD position or a key bind, and no module that the project keeps off
 * by default (it sends or clicks on the player's input) is switched on.
 */
class ShippedDefaultsTest {
	/** Modules that start off by the project's rules, whatever the player who made the defaults uses. */
	private static final Set<String> OFF_BY_RULE = Set.of("party_commands", "loadout_keys", "slot_binds", "farming_keys", "dungeon_auto_requeue", "inventory_buttons",
		// shares a rare catch in party chat by itself (on inside the module)
		"fishing_alerts");
	private static final String DEFAULTS = "/assets/lumio-skyblock/defaults.json";
	private static final String FRESH = "/assets/lumio-skyblock/defaults-fresh.json";

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void onlySettingsThatMayBeShipped() throws IOException {
		for (String name : List.of(DEFAULTS, FRESH)) {
			checkFile(name);
		}
	}

	@Test
	void thePestCooldownKeepsItsDefault() throws IOException {
		for (String name : List.of(DEFAULTS, FRESH)) {
			JsonObject pests = shipped(name).getAsJsonObject("pest_hud");
			assertTrue(pests == null || !pests.has("custom_cooldown"), name + ": the pest cooldown keeps its old default");
		}
	}

	private static JsonObject shipped(String name) throws IOException {
		try (InputStream in = ShippedDefaultsTest.class.getResourceAsStream(name)) {
			assertNotNull(in, name + " is in the jar");
			return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void checkFile(String name) throws IOException {
		JsonObject file = shipped(name);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, JsonElement> part : file.entrySet()) {
			Feature feature = null;
			for (AddonFeature addon : SkyBlockFeatures.ALL) {
				if (Adapters.featureOf(addon).id().equals(part.getKey())) {
					feature = Adapters.featureOf(addon);
				}
			}
			if (feature == null) {
				wrong.add(part.getKey() + " is no feature of the add-on");
				continue;
			}
			if (OFF_BY_RULE.contains(feature.id())) {
				wrong.add(feature.id() + " starts off by the project's rules");
				continue;
			}
			for (Map.Entry<String, JsonElement> entry : part.getValue().getAsJsonObject().entrySet()) {
				String id = part.getKey() + "." + entry.getKey();
				Setting<?> setting = feature.settings().stream().filter(s -> s.key().equals(entry.getKey())).findFirst().orElse(null);
				if (setting == null) {
					wrong.add(id + " is no setting");
				} else if (FeatureManager.isPrivate(feature.id(), setting.key())) {
					wrong.add(id + " is private");
				} else if (ConfigPart.of(feature, setting.key(), setting) == ConfigPart.LAYOUT) {
					wrong.add(id + " is a HUD position or size");
				} else if (setting.key().matches(".*(^|_)keys?($|_).*") || entry.getValue().toString().contains("key_name")) {
					wrong.add(id + " is a key bind");
				} else if (entry.getValue().toString().matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*")) {
					wrong.add(id + " holds a profile or player id");
				} else if (id.equals("experiments.block_wrong_clicks") && entry.getValue().getAsBoolean()) {
					wrong.add(id + " starts off by the project's rules");
				}
			}
		}
		assertTrue(wrong.isEmpty(), name + ":\n" + String.join("\n", wrong));
	}
}
