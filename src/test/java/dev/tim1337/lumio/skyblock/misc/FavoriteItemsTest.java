package dev.tim1337.lumio.skyblock.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Favorite items, which took over "Protect item" and the item list's "Favorite item": what was protected or starred before is a favorite now, the
 * old protect key's bind moves to the favorite key only when that has none, and every action that was blocked for protected items is blocked for
 * favorites of either kind.
 */
class FavoriteItemsTest {
	private static final Set<String> PROTECTED_BEFORE = Set.of("6b1f0e1a-0000-4000-8000-000000000001");
	private static final Set<String> STARRED_BEFORE = Set.of("HYPERION", "ENCHANTED_DIAMOND");
	private static final ProtectionRules.Switches ALL_ON = new ProtectionRules.Switches(true, true, true, true, true, true, false);

	private static boolean favorite(String uuid, String kind) {
		return FavoriteRules.favorite(uuid, kind, PROTECTED_BEFORE::contains, STARRED_BEFORE::contains);
	}

	@Test
	void bothOldListsAreFavorites() {
		assertTrue(favorite("6b1f0e1a-0000-4000-8000-000000000001", "ASPECT_OF_THE_END"), "protected before, by its own id");
		assertTrue(favorite("", "ENCHANTED_DIAMOND"), "starred in the item list before, by its kind");
		assertTrue(favorite("6b1f0e1a-0000-4000-8000-000000000002", "HYPERION"), "an item of a starred kind");
		assertFalse(favorite("6b1f0e1a-0000-4000-8000-000000000002", "ASPECT_OF_THE_END"));
		assertFalse(favorite("", null));
	}

	@Test
	void theOldBindMovesOnlyToAnUnboundFavoriteKey() {
		assertTrue(FavoriteRules.movesBind(false, true), "only the protect key was bound");
		assertFalse(FavoriteRules.movesBind(true, true), "the favorite key keeps its own");
		assertFalse(FavoriteRules.movesBind(true, false));
		assertFalse(FavoriteRules.movesBind(false, false));
	}

	@Test
	void everyBlockedActionStaysBlockedForFavorites() {
		// a favorite is what the rules call a protected item: clicked in each menu, every action the switches cover is held back
		boolean onFavorite = favorite("", "ENCHANTED_DIAMOND");
		ProtectionRules.Click onItem = new ProtectionRules.Click(onFavorite, false, false, false, false, false, true);
		assertEquals(ProtectionRules.Blocked.SELL, ProtectionRules.check(onItem, ProtectionRules.Menu.SHOP, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.SALVAGE, ProtectionRules.check(onItem, ProtectionRules.Menu.SALVAGE, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.TRADE, ProtectionRules.check(onItem, ProtectionRules.Menu.TRADE, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.AUCTION, ProtectionRules.check(onItem, ProtectionRules.Menu.AUCTION, ALL_ON, false));
		assertEquals(ProtectionRules.Blocked.SWAP, ProtectionRules.check(onItem, ProtectionRules.Menu.DUNGEON_CHEST, ALL_ON, true));
		ProtectionRules.Click thrown = new ProtectionRules.Click(onFavorite, false, false, false, true, false, false);
		assertEquals(ProtectionRules.Blocked.DROP, ProtectionRules.check(thrown, ProtectionRules.Menu.OTHER, ALL_ON, false));
		// with one switch off that action is allowed again, the others stay held back
		ProtectionRules.Switches dropAllowed = new ProtectionRules.Switches(false, true, true, true, true, true, false);
		assertEquals(ProtectionRules.Blocked.NOTHING, ProtectionRules.check(thrown, ProtectionRules.Menu.OTHER, dropAllowed, false));
		assertEquals(ProtectionRules.Blocked.SELL, ProtectionRules.check(onItem, ProtectionRules.Menu.SHOP, dropAllowed, false));
	}
}
