package dev.tim1337.lumio.skyblock.hud;

import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the storage overlay shows about each page: how many of its items match the search. Worked out per page when its contents or the search
 * change, never per frame.
 */
final class StorageCards {
	private static final int MOST_REMEMBERED = 5000;

	/** A page's numbers, as last worked out. */
	private static final class Stats {
		List<ItemStack> items;
		int size = -1;
		String searchFor;
		int matches;
	}

	private final Map<String, Stats> stats = new HashMap<>();
	/** Whether an item matches the search, per item; names are only checked again when the search changes. */
	private final Map<ItemStack, Boolean> matching = new IdentityHashMap<>();
	private String matchedFor = "";

	/** @return how many of a page's items match the search, 0 without a search */
	int matches(String key, List<ItemStack> items, String search) {
		if (search.isEmpty()) {
			return 0;
		}
		Stats page = statsOf(key, items);
		if (!search.equals(page.searchFor)) {
			page.searchFor = search;
			int count = 0;
			for (ItemStack stack : items) {
				if (matches(stack, search)) {
					count++;
				}
			}
			page.matches = count;
		}
		return page.matches;
	}

	private Stats statsOf(String key, List<ItemStack> items) {
		Stats page = stats.computeIfAbsent(key, k -> new Stats());
		// new contents (the page was open meanwhile, or it is the live page): everything is worked out again
		if (page.items != items || page.size != items.size()) {
			page.items = items;
			page.size = items.size();
			page.searchFor = null;
		}
		return page;
	}

	/** Forgets the numbers of the live page, whose contents change as items are moved. */
	void changed(String key) {
		Stats page = stats.get(key);
		if (page != null) {
			page.searchFor = null;
		}
	}

	/** @return whether an item matches the search by its name */
	boolean matches(ItemStack stack, String search) {
		if (stack.isEmpty() || search.isEmpty()) {
			return false;
		}
		if (!search.equals(matchedFor) || matching.size() > MOST_REMEMBERED) {
			matching.clear();
			matchedFor = search;
		}
		Boolean found = matching.get(stack);
		if (found == null) {
			found = stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
			matching.put(stack, found);
		}
		return found;
	}
}
