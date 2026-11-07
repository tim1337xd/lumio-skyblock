package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.skyblock.data.ItemRepo;

import java.util.ArrayList;
import java.util.List;

/**
 * The tabs of an item's window, from what the repository knows: how it is made, what it is used in, who drops it, and for a mob its drops, for an NPC its shop. A tab without data is left out. Plain data, so it is unit tested.
 */
final class ItemTabs {
	/** What a tab shows. */
	enum Kind {
		RECIPE("Recipe"),
		FORGE("Forge"),
		SHOP("Shop"),
		TRADE("Trades"),
		KAT("Kat"),
		USED_IN("Used in"),
		DROPPED_BY("Dropped by"),
		DROPS("Drops"),
		SELLS("Sells");

		final String label;

		Kind(String label) {
			this.label = label;
		}
	}

	/** A tab with its recipes, or none for what is a list of its own. */
	record Tab(Kind kind, List<ItemRepo.Recipe> recipes) {
	}

	private ItemTabs() {
	}

	/**
	 * @param making   the ways to get the thing
	 * @param uses     how many things it is used in
	 * @param fromHere what it stands for itself, for a mob or an NPC: its drops, its shop
	 * @return its tabs, in their order
	 */
	static List<Tab> of(List<ItemRepo.Recipe> making, int uses, List<ItemRepo.Recipe> fromHere) {
		List<Tab> tabs = new ArrayList<>();
		add(tabs, Kind.DROPS, fromHere, "drops");
		add(tabs, Kind.SELLS, fromHere, "npc_shop");
		add(tabs, Kind.RECIPE, making, "crafting");
		add(tabs, Kind.FORGE, making, "forge");
		add(tabs, Kind.SHOP, making, "npc_shop");
		add(tabs, Kind.TRADE, making, "trade");
		add(tabs, Kind.KAT, making, "katgrade");
		if (uses > 0) {
			tabs.add(new Tab(Kind.USED_IN, List.of()));
		}
		add(tabs, Kind.DROPPED_BY, making, "drops");
		return tabs;
	}

	private static void add(List<Tab> tabs, Kind kind, List<ItemRepo.Recipe> recipes, String type) {
		List<ItemRepo.Recipe> ofType = recipes.stream().filter(recipe -> recipe.type().equals(type)).toList();
		if (!ofType.isEmpty()) {
			tabs.add(new Tab(kind, ofType));
		}
	}

	/** @return which of its pages a step leads to, going round */
	static int stepped(int page, int step, int pages) {
		return pages <= 0 ? 0 : Math.floorMod(page + step, pages);
	}

	/** @return how many pages a list takes */
	static int pages(int count, int perPage) {
		return perPage <= 0 ? 1 : Math.max(1, (count + perPage - 1) / perPage);
	}
}
