package dev.tim1337.lumio.skyblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.PageOutline;
import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The add-on's page outlines name real features and keys, and a page with an outline leaves no row out: every setting that shows has its place, on the page, under Advanced or in the Look sheet. */
class SkyBlockOutlinesTest {
	/** Rows made for the page that hold no value of their own. */
	private static final List<String> ONLY_OPENS = List.of("look_row", "bars_row", "lines_row", "lines_chips");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void outlinesNameRealFeaturesAndKeysAndLeaveNoRowOut() {
		Map<String, PageOutline> outlines = PageOutlines.of("lumio-skyblock");
		Map<String, Feature> features = new HashMap<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			features.put(feature.id(), feature);
		}
		List<String> wrong = new ArrayList<>();
		outlines.forEach((id, outline) -> {
			Feature feature = features.get(id);
			assertNotNull(feature, "no feature " + id);
			feature.useOutline(outline);
			List<String> placed = new ArrayList<>();
			for (PageOutline.Row row : outline.rows()) {
				placed.add(row.key());
				// the search goes through a feature's settings: what a row shows must be among them, or be a row that only opens or shows others
				Setting<?> shown = feature.settingOf(row.key());
				if (shown != null && !feature.settings().contains(shown) && !ONLY_OPENS.contains(row.key())) {
					wrong.add(id + "." + row.key() + " cannot be found by the search");
				}
				// keys are made when the game registers them
				if (feature.settingOf(row.key()) == null && !row.key().startsWith("key.")) {
					wrong.add(id + "." + row.key() + " does not exist");
				}
			}
			if (outline.onlyNames()) {
				return;
			}
			for (Setting<?> setting : feature.settings()) {
				if (setting.isVisible() && !setting.key().equals("enabled") && !setting.key().equals("reset_page") && !placed.contains(setting.key()) && feature.viewOf(setting) == null) {
					wrong.add(id + "." + setting.key() + " has no place");
				}
			}
		});
		assertEquals(List.of(), wrong);
	}

	/**
	 * A sub-page card stands on exactly one tab: on a page with tabs (two or more named groups, the keys and the cards' own tabs counted), every
	 * sub-page row is in the overview of one named group, never in the untitled one, which shows under every tab.
	 */
	@Test
	void aSubPageCardIsOnExactlyOneTab() {
		Map<String, PageOutline> outlines = PageOutlines.of("lumio-skyblock");
		List<String> wrong = new ArrayList<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			PageOutline outline = outlines.get(feature.id());
			if (outline == null || outline.pages().isEmpty()) {
				continue;
			}
			feature.useOutline(outline);
			java.util.Set<String> tabs = new java.util.HashSet<>();
			for (PageOutline.Row row : outline.main()) {
				tabs.add(row.group());
			}
			// a key the outline does not place gets a Keys group of its own in the game, where the keys are registered
			if (addon.getClass().getName().contains("Fishing") || outline.main().stream().anyMatch(row -> row.key().startsWith("key."))) {
				tabs.add(Feature.KEYS_GROUP);
			}
			Map<String, Integer> seen = new HashMap<>();
			for (Setting<?> setting : feature.shownSettings()) {
				if (setting instanceof dev.tim1337.lumio.config.setting.NavListSetting overview) {
					String tab = feature.sectionOf(overview);
					tabs.add(tab == null ? "" : tab);
					for (var item : overview.items()) {
						seen.merge(item.title(), 1, Integer::sum);
						if ((tab == null || tab.isEmpty()) && outline.pages().keySet().stream().anyMatch(page -> page.title().equals(item.title()))) {
							wrong.add(feature.id() + ": card " + item.title() + " in the untitled group");
						}
					}
				}
			}
			tabs.remove("");
			tabs.remove("Reset");
			if (tabs.size() < 2) {
				wrong.removeIf(line -> line.startsWith(feature.id() + ":"));
			}
			for (PageOutline.SubPage page : outline.pages().keySet()) {
				if (seen.getOrDefault(page.title(), 0) != 1) {
					wrong.add(feature.id() + ": card " + page.title() + " shown " + seen.getOrDefault(page.title(), 0) + " times");
				}
			}
		}
		assertEquals(List.of(), wrong);
	}
}
