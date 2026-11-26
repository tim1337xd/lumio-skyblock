package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * No setting of the add-on gets lost: every saved key of the list taken before the settings were tidied up is still there, or is named here with where its value went. The list itself never changes;
 * {@code ./gradlew test -PupdateSettingKeys} only writes it when it does not exist yet.
 */
class SkyBlockSettingKeysTest {
	private static final Path BASELINE = Path.of("src/test/resources/settings-keys.txt");
	/** What left, on purpose, and where its value went. */
	private static final Map<String, String> GONE = new TreeMap<>(Map.of("pest_hud.show_spray", "removed: the Pests panel has no spray line any more; the Farming module shows it", "pest_hud.show_visitors", "removed: the Pests panel has no visitors line any more; the Visitors module shows them", "item_tooltip_lines.stack_price", "the row with a fixed stack size is gone: Shift prices the real stack",
		"item_tooltip_lines.dungeon_data", "moved: the private debug add-on shows a dungeon item's data", "minibosses.drop_debug",
		"moved: the private debug add-on logs every dropped item", "experiments.debug_overlay", "moved: the private debug add-on shows the experiments' state",
		"break_reset_fix.debug_log", "moved: the private debug add-on's break trace", "item_list.favorites",
		"moved: the item list's favorites are Pinned now, and Favorite items keeps them as its starred kinds (item_protection.kinds)",
		"item_list.favorites_first", "renamed: its value is item_list.pinned_first"));

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	private static TreeSet<String> keysNow() {
		TreeSet<String> keys = new TreeSet<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			for (Map.Entry<String, JsonElement> entry : feature.save().entrySet()) {
				keys.add(feature.id() + "." + entry.getKey());
			}
		}
		return keys;
	}

	/** The item search's settings removed on 2026-11-24: always None, off and the inventory only, Both sides the one layout. */
	private static final List<String> ITEM_SEARCH_REMOVED = List.of("item_list.panel_background", "item_list.rarity_line", "item_list.slot_background",
		"item_list.show_chests", "item_list.show_menus", "item_list.layout_preset");

	@Test
	void autoIsAlwaysTheLastOption() {
		List<String> wrong = new ArrayList<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			for (dev.tim1337.lumio.config.setting.Setting<?> setting : feature.settings()) {
				if (setting instanceof dev.tim1337.lumio.config.setting.EnumSetting<?> choice) {
					Enum<?>[] options = choice.get().getDeclaringClass().getEnumConstants();
					for (int i = 0; i < options.length - 1; i++) {
						if (options[i].name().equals("AUTO")) {
							wrong.add(feature.id() + "." + setting.key());
						}
					}
				}
			}
		}
		assertEquals(List.of(), wrong, "Auto is not the last option");
	}

	@Test
	void theRemovedItemSearchSettingsAreGoneAndASavedOneIsDroppedSilently() {
		TreeSet<String> now = keysNow();
		for (String key : ITEM_SEARCH_REMOVED) {
			assertFalse(now.contains(key), key);
		}
		Feature items = Adapters.featureOf(SkyBlockFeatures.ITEM_LIST);
		JsonObject saved = items.save();
		saved.addProperty("panel_background", "SOLID");
		saved.addProperty("rarity_line", true);
		saved.addProperty("slot_background", true);
		saved.addProperty("show_chests", true);
		saved.addProperty("show_menus", true);
		saved.addProperty("layout_preset", "RIGHT_SIDE");
		items.load(saved);
		JsonObject again = items.save();
		for (String key : List.of("panel_background", "rarity_line", "slot_background", "show_chests", "show_menus", "layout_preset")) {
			assertFalse(again.has(key), key + " is dropped");
		}
	}

	@Test
	void everyKeyOfTheListIsStillThereOrNamedAsGone() throws IOException {
		TreeSet<String> now = keysNow();
		if (Boolean.getBoolean("lumio.updateSettingKeys") && !Files.exists(BASELINE)) {
			Files.createDirectories(BASELINE.getParent());
			Files.write(BASELINE, now, StandardCharsets.UTF_8);
		}
		List<String> lost = new ArrayList<>();
		for (String key : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
			if (!now.contains(key) && !GONE.containsKey(key)) {
				lost.add(key);
			}
		}
		assertEquals(List.of(), lost, "settings lost without a word");
	}
}
