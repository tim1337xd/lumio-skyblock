package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.FeatureSetProvider;
import dev.tim1337.lumio.api.FeatureSetSpec;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;

import java.util.List;
import java.util.Set;

/**
 * SkyBlock's part of the feature sets: the one place that sorts the SkyBlock features. The advanced ones are for experienced players or one kind
 * of grinding (the profit trackers, the mineshaft and corpse tools, tick glide, the HotM tree); all others are basics most players want, which
 * Recommended turns on. SkyBlock Advanced turns on both.
 */
final class SkyBlockFeatureSets implements FeatureSetProvider {
	private Set<AddonFeature> advanced;

	@Override
	public String basicsName() {
		return "SkyBlock basics";
	}

	@Override
	public Set<AddonFeature> advanced() {
		if (advanced == null) {
			advanced = Set.of(SkyBlockFeatures.MINING_PROFIT, SkyBlockFeatures.FARMING_PROFIT, SkyBlockFeatures.SLAYER_PROFIT, SkyBlockFeatures.FISHING_PROFIT,
				SkyBlockFeatures.CORPSES, SkyBlockFeatures.CORPSE_PROFIT, SkyBlockFeatures.MINESHAFT_PITY, SkyBlockFeatures.TICK_GLIDE, SkyBlockFeatures.HOTM_TREE,
				SkyBlockFeatures.FARMING_HUD, SkyBlockFeatures.KEEP_TERRAIN);
		}
		return advanced;
	}

	@Override
	public List<FeatureSetSpec> sets() {
		return List.of(new FeatureSetSpec("skyblock_advanced", "SkyBlock Advanced", "Tick glide, HOTM tree and more", "pickaxe",
			SystemColors.ORANGE_RGB, SystemColors.RED_RGB, advanced()));
	}

	@Override
	public void switchOn() {
		if (!SkyBlockFeatures.SKYBLOCK_ON.get()) {
			SkyBlockFeatures.SKYBLOCK_ON.set(true);
		}
	}
}
