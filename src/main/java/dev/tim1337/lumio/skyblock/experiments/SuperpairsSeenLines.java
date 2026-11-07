package dev.tim1337.lumio.skyblock.experiments;

import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.TooltipLineProvider;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The line "Seen" under a face-down card of Superpairs that the game showed in this game: its name, as the helper remembers it. */
public final class SuperpairsSeenLines implements TooltipLineProvider {
	private final ExperimentHelperFeature helper;

	public SuperpairsSeenLines(ExperimentHelperFeature helper) {
		this.helper = helper;
	}

	@Override
	public void addLines(ItemStack stack, List<TooltipLine> lines) {
		TooltipLine seen = helper.seenLine(stack);
		if (seen != null) {
			lines.add(seen);
		}
	}
}
