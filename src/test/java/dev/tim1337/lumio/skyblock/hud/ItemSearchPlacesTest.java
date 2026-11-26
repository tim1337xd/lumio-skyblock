package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.hud.ItemSearchPlaces.Kind;

import org.junit.jupiter.api.Test;

/** Where the item search shows ("Show in"): the kinds of screens, the defaults, and the key that shows or hides it on any of them. */
class ItemSearchPlacesTest {
	/** The defaults of the three switches: Inventory on, Chests off, Auction House, Bazaar and other menus off. */
	private static boolean defaultSwitch(Kind kind) {
		return kind == Kind.INVENTORY;
	}

	@Test
	void theOwnInventoryIsTheInventory() {
		assertEquals(Kind.INVENTORY, ItemSearchPlaces.kind(true, false, "Crafting"));
	}

	@Test
	void containersThatHoldItemsAreChests() {
		for (String title : new String[]{"Chest", "Large Chest", "Ender Chest", "Barrel", "Shulker Box", "Ender Chest (2/9)", "Jumbo Backpack (Slot 3)",
			"Greater Backpack (Slot 12)", "Personal Vault"}) {
			assertEquals(Kind.CHEST, ItemSearchPlaces.kind(false, true, title), title);
		}
	}

	@Test
	void everyOtherMenuIsAMenu() {
		for (String title : new String[]{"Auction House", "Auctions Browser", "Bazaar", "Bazaar ➜ Farming", "SkyBlock Menu", "Craft Item", "Pets",
			"Chest Value", "Loadouts", ""}) {
			assertEquals(Kind.MENU, ItemSearchPlaces.kind(false, true, title), title);
		}
		// a screen that is no chest at all (an anvil, a crafting table), whatever it is called
		assertEquals(Kind.MENU, ItemSearchPlaces.kind(false, false, "Chest"));
		assertEquals(Kind.MENU, ItemSearchPlaces.kind(false, true, null));
	}

	@Test
	void byDefaultItShowsOnlyInTheInventory() {
		assertTrue(ItemSearchPlaces.shown(null, false, defaultSwitch(Kind.INVENTORY)));
		assertFalse(ItemSearchPlaces.shown(null, false, defaultSwitch(Kind.CHEST)));
		assertFalse(ItemSearchPlaces.shown(null, false, defaultSwitch(Kind.MENU)));
	}

	@Test
	void theKeyShowsItOnAnyScreenForThatScreen() {
		// in a chest, where "Show in" has it off: the key shows it there
		assertTrue(ItemSearchPlaces.shown(true, false, false));
		// and hides it again
		assertFalse(ItemSearchPlaces.shown(false, false, false));
	}

	@Test
	void theKeyHidesItWhereItShowsByItself() {
		assertFalse(ItemSearchPlaces.shown(null, true, true));
		// hidden by the key in the inventory, it stays away in a chest too
		assertFalse(ItemSearchPlaces.shown(null, true, false));
	}
}
