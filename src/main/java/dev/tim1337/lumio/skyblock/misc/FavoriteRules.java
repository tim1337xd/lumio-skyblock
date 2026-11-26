package dev.tim1337.lumio.skyblock.misc;

import java.util.function.Predicate;

/**
 * What a favorite is, and how the old keys of the two modules that became Favorite items move: the old "protected" items (by their own id) and the
 * old "favorites" of the item list (by their kind) are both favorites now, so nothing marked before is lost.
 */
final class FavoriteRules {
	private FavoriteRules() {
	}

	/**
	 * @param uuid  the item's own id, "" for none
	 * @param kind  the item's kind (its SkyBlock id), or null when there is none or no kind is starred
	 * @param items whether an item is marked by its own id
	 * @param kinds whether a kind is starred in the item list
	 * @return whether the item is a favorite
	 */
	static boolean favorite(String uuid, String kind, Predicate<String> items, Predicate<String> kinds) {
		return !uuid.isEmpty() && items.test(uuid) || kind != null && kinds.test(kind);
	}

	/** @return whether the old protect key's bind moves to the favorite key: only when the favorite key has none and the old one has one */
	static boolean movesBind(boolean favoriteBound, boolean oldBound) {
		return !favoriteBound && oldBound;
	}
}
