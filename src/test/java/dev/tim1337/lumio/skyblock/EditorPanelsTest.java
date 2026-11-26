package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.menu.FeatureListFeature;
import dev.tim1337.lumio.skyblock.data.Activity;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SkyBlock elements of the HUD editor: every one has a live feature behind it (one of the add-on's features, on a SkyBlock page), a name
 * of its own, appears once, and can show in the editor: with its own sample, or in the simulation of an activity.
 */
class EditorPanelsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void everyPanelHasALiveFeature() {
		assertFalse(SkyBlockFeatures.HUDS.isEmpty());
		for (AddonPanel panel : SkyBlockFeatures.HUDS) {
			assertTrue(SkyBlockFeatures.ALL.contains(panel), panel.id() + " is in the editor but not one of the add-on's features");
			Feature feature = Adapters.featureOf(panel);
			assertNotNull(feature, panel.id() + " has no feature");
			// a part of another feature (the corpse tracker's profit panel) is reached through that one's page
			Feature listed = panel == SkyBlockFeatures.CORPSE_PROFIT ? Adapters.featureOf(SkyBlockFeatures.CORPSE_TRACKER) : feature;
			assertEquals(panel == SkyBlockFeatures.CORPSE_PROFIT, SkyBlockFeatures.isPart(panel), panel.id() + ": a part needs its owner named here");
			assertTrue(onAPage(listed), panel.id() + " is on no SkyBlock page");
		}
	}

	private static boolean onAPage(Feature feature) {
		for (AddonPage category : SkyBlockFeatures.SKYBLOCK_CATEGORIES) {
			if (((FeatureListFeature) Adapters.featureOf(category)).holds(feature)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void everyPanelAppearsOnceUnderItsOwnName() {
		Map<AddonPanel, Boolean> seen = new IdentityHashMap<>();
		Set<String> names = new HashSet<>();
		for (AddonPanel panel : SkyBlockFeatures.HUDS) {
			assertEquals(null, seen.put(panel, true), panel.id() + " is listed twice");
			String name = panel.nameText();
			assertTrue(name != null && !name.isBlank() && name.equals(name.strip()), panel.id() + " has no proper name");
			assertTrue(names.add(name), "two panels are called " + name);
		}
	}

	@Test
	void everyPanelCanShowInTheEditor() {
		for (AddonPanel panel : SkyBlockFeatures.HUDS) {
			boolean simulated = false;
			for (Activity activity : Activity.values()) {
				simulated |= activity.simulatable() && Activity.belongs(panel, activity);
			}
			// the arrows are one of the status bars in their Bar style (the default): their one entry selects the bar, which has its own sample
			boolean asBar = panel == SkyBlockFeatures.ARROWS && dev.tim1337.lumio.api.experimental.ui.ExtraStatusBar.panel() == panel;
			assertTrue(panel.inEditorWhenHidden() || simulated || asBar, panel.id() + " has neither a sample of its own nor an activity that simulates it");
		}
	}
}
