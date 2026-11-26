package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.menu.PageGroups;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The SkyBlock page, its category pages and every SkyBlock feature build their settings with rows, as the main mod's pages do. */
class SkyBlockPagesTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void theSkyBlockPageAndItsCategoriesHaveRows() {
		PageRows.buildsWithRows(Adapters.featureOf(SkyBlockFeatures.SKYBLOCK_PAGE));
		for (AddonPage category : SkyBlockFeatures.SKYBLOCK_CATEGORIES) {
			PageRows.buildsWithRows(Adapters.featureOf(category));
		}
		assertEquals(9, SkyBlockFeatures.SKYBLOCK_CATEGORIES.size());
	}

	/** A long page (eight or more features) lists them under named groups: every feature in exactly one, none under "Other", in the order of the code. */
	@Test
	void everyFeatureOfAGroupedPageIsInExactlyOneGroup() {
		java.util.Map<String, java.util.List<String>> grouped = new java.util.LinkedHashMap<>();
		for (AddonPage category : SkyBlockFeatures.SKYBLOCK_CATEGORIES) {
			FeatureListFeature page = (FeatureListFeature) Adapters.featureOf(category);
			java.util.Map<String, java.util.List<Feature>> groups = page.groups();
			int features = page.featureCount();
			if (page.isTabbed()) {
				// a page shown as tabs (Dungeons) is split by its tabs, not into groups
				continue;
			}
			if (features < PageGroups.FROM) {
				assertEquals(1, groups.size(), page.nameText() + " is short and stays one plain list");
				continue;
			}
			java.util.List<Feature> seen = new java.util.ArrayList<>();
			groups.forEach((name, list) -> {
				assertFalse(name.isBlank() || name.equals(PageGroups.OTHER), page.nameText() + ": a feature without a group, " + list.get(0).nameText());
				for (Feature feature : list) {
					assertEquals(name, feature.pageGroupName(), feature.nameText());
					assertFalse(seen.contains(feature), feature.nameText() + " is in two groups");
					seen.add(feature);
				}
			});
			assertEquals(features, seen.size(), page.nameText());
			grouped.put(page.nameText(), java.util.List.copyOf(groups.keySet()));
		}
		assertEquals(java.util.List.of("On screen", "Inventory and menus"), grouped.get("Panels"));
		assertEquals(java.util.List.of("Panels", "While mining", "Mineshafts and Hollows"), grouped.get("Mining"));
		assertEquals(java.util.List.of("Items and inventory", "Chat and party", "Helpers", "Visual"), grouped.get("Misc"));
		assertEquals(3, grouped.size());
	}

	@Test
	void everySkyBlockFeatureBuildsItsSettings() {
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			assertDoesNotThrow(feature::shownSettings, feature.id());
		}
	}
}
