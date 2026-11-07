package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Tracks the items worn in the four Hypixel SkyBlock equipment slots (necklace, cloak, belt, gloves). They are not inventory slots: the server only
 * shows them in its "Your Equipment and Stats" menu. They are read from there every time that menu is open and cached (also in the settings file),
 * so they can be shown at any time.
 */
public final class EquipmentTracker {
	/** The equipment slots in the menu: necklace, cloak, belt and gloves, stacked in the left column. */
	private static final int[] MENU_SLOTS = {10, 19, 28, 37};
	public static final String[] NAMES = {"Necklace", "Cloak", "Belt", "Gloves"};

	private static final ItemStack[] ITEMS = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private static final String[] ENCODED = {"", "", "", ""};
	private static final boolean[] TRIED = new boolean[4];
	private static int version;

	private EquipmentTracker() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(EquipmentTracker::tick);
	}

	/** @return a counter incremented when the worn equipment changes */
	public static int version() {
		return version;
	}

	/** @return the item worn in a slot (0 to 3), or an empty stack */
	public static ItemStack item(int place) {
		// items from the file are deserialized once the game can: not when the file is read, since not all game items exist then
		if (ITEMS[place].isEmpty() && !ENCODED[place].isEmpty() && !TRIED[place] && Minecraft.getInstance().level != null) {
			TRIED[place] = true;
			ITEMS[place] = PetTracker.decodeIcon(ENCODED[place]);
		}
		return ITEMS[place];
	}

	/** @return whether anything is known to be worn */
	public static boolean any() {
		for (int i = 0; i < ITEMS.length; i++) {
			if (!item(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** @return the worn items serialized, for the settings file */
	public static String[] exportItems() {
		return ENCODED.clone();
	}

	public static void restore(String[] saved) {
		for (int i = 0; i < ENCODED.length && i < saved.length; i++) {
			ENCODED[i] = saved[i] == null ? "" : saved[i];
			TRIED[i] = false;
		}
	}

	/** @return whether a menu item is the placeholder for an empty slot, such as "Empty Necklace Slot" */
	private static boolean isPlaceholder(ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("empty") || name.startsWith("slot ");
	}

	/** @return the menu slots holding the four equipment slots, or null for a menu that is not about equipment */
	private static int[] placesIn(String title, List<Slot> slots) {
		String lower = title.toLowerCase(Locale.ROOT);
		if (lower.contains("equipment sets")) {
			// the sets are side by side: the equipped one has "Slot N: Equipped" below its column, in the selector row
			if (slots.size() < 45) {
				return null;
			}
			int column = -1;
			for (int i = 36; i < 45; i++) {
				if (slots.get(i).getItem().getHoverName().getString().toLowerCase(Locale.ROOT).contains(": equipped")) {
					if (column >= 0) {
						return null;
					}
					column = i - 36;
				}
			}
			if (column < 0) {
				return null;
			}
			int[] places = new int[4];
			for (int i = 0; i < 4; i++) {
				places[i] = i * 9 + column;
			}
			return places;
		}
		// "Your Equipment and Stats" and the older "Loadouts" have them in the second column
		if (lower.contains("equipment") || lower.contains("loadouts")) {
			return slots.size() < 38 ? null : MENU_SLOTS;
		}
		return null;
	}

	private static void tick(Minecraft client) {
		// only while a menu is open and the equipment panel is enabled; otherwise nothing is read
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen) || SkyBlockFeatures.EQUIPMENT == null || !SkyBlockFeatures.EQUIPMENT.isEnabled()) {
			return;
		}
		var slots = screen.getMenu().slots;
		int[] places = placesIn(screen.getTitle().getString(), slots);
		if (places == null) {
			return;
		}
		SkyBlock.evidence(60_000);
		boolean changed = false;
		for (int i = 0; i < MENU_SLOTS.length; i++) {
			ItemStack stack = slots.get(places[i]).getItem();
			ItemStack worn = isPlaceholder(stack) ? ItemStack.EMPTY : stack.copy();
			// a menu item that has not arrived yet is not mistaken for an empty slot: the server sends items shortly after the menu opens
			if (stack.isEmpty() && !slots.get(places[0]).hasItem() && !slots.get(places[1]).hasItem()) {
				continue;
			}
			if (!ItemStack.matches(worn, ITEMS[i])) {
				ITEMS[i] = worn;
				ENCODED[i] = worn.isEmpty() ? "" : PetTracker.encodeIcon(worn);
				TRIED[i] = true;
				changed = true;
			}
		}
		if (changed) {
			version++;
			LumioApi.saveSoon();
		}
	}
}
