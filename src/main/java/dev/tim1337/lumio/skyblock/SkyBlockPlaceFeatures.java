package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.PlaceFeatureProvider;
import dev.tim1337.lumio.api.PlaceGroup;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.data.FishingRods;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The SkyBlock features for the island the player is on: the features of every activity the island is good for (from {@link Activity}),
 * grouped by activity, with what the player is doing right now first; each group links to its category page.
 */
final class SkyBlockPlaceFeatures implements PlaceFeatureProvider {
	private final SkyBlockLocation location;

	SkyBlockPlaceFeatures(SkyBlockLocation location) {
		this.location = location;
	}

	@Override
	public boolean active() {
		return location.inWorld() && SkyBlockFeatures.SKYBLOCK_ON.get();
	}

	@Override
	public String placeName() {
		return location.placeName();
	}

	@Override
	public AddonPage page() {
		return SkyBlockFeatures.SKYBLOCK_PAGE;
	}

	@Override
	public String key() {
		return placeName() + "|" + doingNow();
	}

	@Override
	public List<PlaceGroup> groups() {
		List<Activity> activities = Activity.atIsland(placeName());
		String now = doingNow();
		// the groups in the table's order, with what the player is doing now first
		Set<String> order = new LinkedHashSet<>();
		for (Activity activity : activities) {
			if (activity.group().equals(now)) {
				order.add(now);
			}
		}
		for (Activity activity : activities) {
			order.add(activity.group());
		}
		List<PlaceGroup> groups = new ArrayList<>();
		for (String group : order) {
			Set<AddonFeature> features = new LinkedHashSet<>();
			for (Activity activity : activities) {
				if (activity.group().equals(group)) {
					features.addAll(Activity.featuresOf(activity));
				}
			}
			AddonPage page = pageOf(group);
			if (!features.isEmpty() && page != null) {
				groups.add(new PlaceGroup(group, page, List.copyOf(features)));
			}
		}
		return groups;
	}

	/** @return the activity group the player is doing right now, which the card shows first: a slayer quest, a real fishing rod in hand on a fishing island, mining */
	private String doingNow() {
		Minecraft client = Minecraft.getInstance();
		if (location.has("slayer")) {
			return "Slayer";
		}
		if (client.player != null && FishingRods.holding(client.player.getMainHandItem()) && FishingRods.onFishingIsland()) {
			return "Fishing";
		}
		if (ProfitActivity.MINING.engaged()) {
			return "Mining";
		}
		return ProfitActivity.FARMING.engaged() ? "Farming" : "";
	}

	/** @return the SkyBlock category page of an activity group, which has the same name */
	private static AddonPage pageOf(String group) {
		for (AddonPage category : SkyBlockFeatures.SKYBLOCK_CATEGORIES) {
			if (category.nameText().equals(group)) {
				return category;
			}
		}
		return null;
	}
}
