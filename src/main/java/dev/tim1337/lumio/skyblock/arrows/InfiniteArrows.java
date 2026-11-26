package dev.tim1337.lumio.skyblock.arrows;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * What makes the arrows infinite, by the SkyBlock id of an item worn (any reforge, stars or recombobulator still count) and when it holds. Only what
 * an item's own lore says: the Skeleton Master Chestplate's "Your bows don't consume arrows.", with no condition.
 */
final class InfiniteArrows {
	/** One source: the id of the chestplate worn, and when its perk holds. */
	record Source(String chestId, BooleanSupplier when) {
	}

	static final List<Source> SOURCES = List.of(new Source("SKELETON_MASTER_CHESTPLATE", () -> true));

	private InfiniteArrows() {
	}

	/** @return whether the chestplate worn (its SkyBlock id, or null for none) makes the arrows infinite now */
	static boolean from(String chestId) {
		if (chestId == null) {
			return false;
		}
		for (Source source : SOURCES) {
			if (source.chestId().equals(chestId) && source.when().getAsBoolean()) {
				return true;
			}
		}
		return false;
	}
}
