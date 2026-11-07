package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.config.Feature;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
		assertEquals(8, SkyBlockFeatures.SKYBLOCK_CATEGORIES.size());
	}

	@Test
	void everySkyBlockFeatureBuildsItsSettings() {
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			assertDoesNotThrow(feature::shownSettings, feature.id());
		}
	}
}
