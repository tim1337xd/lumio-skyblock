package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.ColorSetting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fresh config has the menu color on every panel of the add-on: they share the main mod's panel with its accent. */
class HudMenuColorTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void everyPanelStartsOnTheMenuColor() {
		List<String> fixed = new ArrayList<>();
		for (AddonPanel panel : SkyBlockFeatures.HUDS) {
			Feature feature = Adapters.featureOf(panel);
			boolean follows = false;
			for (var setting : feature.settings()) {
				follows |= setting instanceof ColorSetting color && color.isMenuByDefault() && color.usesMenu();
			}
			if (!follows) {
				fixed.add(panel.id());
			}
		}
		assertTrue(fixed.isEmpty(), "These don't start on the menu color: " + fixed);
	}
}
