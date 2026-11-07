package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.SetupChoice;
import dev.tim1337.lumio.api.SetupProvider;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * SkyBlock in the first setup: "Hypixel SkyBlock" as where the player plays, its general panels, and one group per thing the player does there.
 * The activity groups take their features from the {@link Activity} tags, so a newly tagged feature is in the setup too; the profit trackers are
 * in "Making coins", not in their activity.
 */
final class SkyBlockSetup implements SetupProvider {
	private static final SetupChoice WORLD = new SetupChoice("SKYBLOCK", "SkyBlock", "SkyBlock", "Trackers and helpers", "star", 0x30D158, 0x0A84FF,
		() -> List.of(SkyBlockFeatures.PET, SkyBlockFeatures.EQUIPMENT, SkyBlockFeatures.DEPLOYABLES, SkyBlockFeatures.STORAGE, SkyBlockFeatures.ITEM_LIST,
			SkyBlockFeatures.CUSTOM_SCOREBOARD));
	private static final List<SetupChoice> ACTIVITIES = List.of(
		new SetupChoice("MINING", "Mining", "Mining", "", "pickaxe", 0xFF9F0A, 0xFF6B3D,
			() -> tagged(Activity.DWARVEN_MINES, Activity.CRYSTAL_HOLLOWS, Activity.MINESHAFT)),
		new SetupChoice("SLAYER", "Slayer", "Slayer", "", "sword", 0xFF6961, 0xE5383B, () -> tagged(Activity.SLAYER)),
		new SetupChoice("FISHING", "Fishing", "Fishing", "", "fish", 0x5AC8FA, 0x0A84FF, () -> tagged(Activity.FISHING, Activity.LAVA_FISHING)),
		new SetupChoice("FARMING", "Farming", "Farming", "", "wheat", 0xFFD60A, 0x30D158, () -> tagged(Activity.FARMING)),
		new SetupChoice("COINS", "Making coins", "Coins", "", "coins", 0xFFD60A, 0xFF9F0A,
			() -> List.of(SkyBlockFeatures.MINING_PROFIT, SkyBlockFeatures.SLAYER_PROFIT, SkyBlockFeatures.FISHING_PROFIT, SkyBlockFeatures.FARMING_PROFIT)),
		new SetupChoice("EXPLORING", "Exploring", "Exploring", "", "map", 0x64D2FF, 0x30D158,
			() -> List.of(SkyBlockFeatures.KEEP_TERRAIN, SkyBlockFeatures.ETHERWARP)));

	@Override
	public SetupChoice world() {
		return WORLD;
	}

	@Override
	public String worldTitle() {
		return "Hypixel SkyBlock";
	}

	@Override
	public List<SetupChoice> activities() {
		return ACTIVITIES;
	}

	@Override
	public String welcomeLine() {
		return "Trackers and helpers for what you do.";
	}

	@Override
	public boolean worldOn() {
		return SkyBlockFeatures.SKYBLOCK_ON.get();
	}

	@Override
	public void setWorldOn(boolean on) {
		SkyBlockFeatures.SKYBLOCK_ON.set(on);
	}

	/** @return the features tagged with any of the activities, without the profit trackers, the parts of a feature and the ones always shown */
	private static List<FeatureView> tagged(Activity... activities) {
		List<FeatureView> found = new ArrayList<>();
		for (Activity activity : activities) {
			for (FeatureView feature : Activity.featuresOf(activity)) {
				if (!found.contains(feature) && !(feature instanceof ProfitHudFeature) && !SkyBlockFeatures.isPart(feature)
					&& !Activity.of(feature).contains(Activity.ALWAYS)) {
					found.add(feature);
				}
			}
		}
		return found;
	}
}
