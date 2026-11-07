package dev.tim1337.lumio.skyblock.data;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How many of an item the player has and where: in the inventory, and on the ender chest pages and in the backpacks as last seen (see
 * {@link StorageTracker}). Counted at most once a second while someone asks, never per frame per item.
 */
public final class ItemHoldings {
	/** A place an item is in, and how many are there. */
	public record Place(String name, int count) {
	}

	private static final long FRESH_MILLIS = 1000L;
	private static final String INVENTORY = "Inventory";
	/** Per item id: the places it is in and how many, in the order inventory, ender chest pages, backpacks. */
	private static final Map<String, Map<String, Integer>> BY_ID = new HashMap<>();
	private static long countedAt;

	private ItemHoldings() {
	}

	/** @return how many of an item the player has, in the inventory and in storage */
	public static int count(String id) {
		refresh();
		Map<String, Integer> places = BY_ID.get(id);
		if (places == null) {
			return 0;
		}
		int total = 0;
		for (int count : places.values()) {
			total += count;
		}
		return total;
	}

	/** @return where the player has an item, and how many in each place */
	public static List<Place> places(String id) {
		refresh();
		Map<String, Integer> places = BY_ID.get(id);
		if (places == null) {
			return List.of();
		}
		List<Place> out = new ArrayList<>(places.size());
		places.forEach((name, count) -> out.add(new Place(name, count)));
		return out;
	}

	/** @return the readable name of a storage page from its key: "Ender chest 2", "Backpack 3" */
	public static String pageName(String key) {
		return (key.startsWith("e") ? "Ender chest " : "Backpack ") + key.substring(1);
	}

	private static void refresh() {
		long now = System.currentTimeMillis();
		if (now - countedAt < FRESH_MILLIS) {
			return;
		}
		countedAt = now;
		BY_ID.clear();
		var player = Minecraft.getInstance().player;
		if (player != null) {
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				add(stack, INVENTORY);
			}
		}
		for (String key : StorageTracker.knownKeys()) {
			StorageTracker.Page page = StorageTracker.page(key);
			if (page != null) {
				String name = pageName(key);
				for (ItemStack stack : page.items()) {
					add(stack, name);
				}
			}
		}
	}

	private static void add(ItemStack stack, String place) {
		String id = ItemGains.idOf(stack);
		if (id != null) {
			BY_ID.computeIfAbsent(id, key -> new LinkedHashMap<>()).merge(place, stack.getCount(), Integer::sum);
		}
	}
}
