package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;

/**
 * Glacite Mineshaft corpses in one place: the highlight that finds them (shown through walls once seen, shareable with the party) and the tracker
 * of their loot value including the keys they cost. Both keep their own settings, as parts of this page.
 */
public class CorpsesFeature extends AddonFeature {
	public CorpsesFeature(CorpseHighlightFeature highlight, ProfitHudFeature profit) {
		super("corpses", "Corpses", "The corpses of the Glacite Mineshafts: lit through the walls once seen, shared with your party, and what looting them brings in.",
			true, false);
		iconRgb(0x3AF2FF);
		iconName("skull");
		summary("Glacite Mineshaft corpses and what their loot is worth");
		addChild(highlight);
		addChild(profit);
	}
}
