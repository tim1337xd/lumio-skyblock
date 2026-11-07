package dev.tim1337.lumio.skyblock.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The lines the game itself writes under an item's name for its vanilla enchantments, which the server leaves shown: "Efficiency X" above the lore that lists the same enchant. Only a line the lore repeats counts, so nothing the item has gets lost.
 * Plain text, so it is unit tested.
 */
final class GameEnchantLines {
	private static final String LORE_SEPARATOR = ", ";

	private GameEnchantLines() {
	}

	/**
	 * @param lines the tooltip's lines as plain text, its name first
	 * @param game  the item's vanilla enchantments, as the game writes them ("Efficiency X")
	 * @return the places of the game's lines the lore repeats, from the last to the first, so that they can be taken out in this order
	 */
	static List<Integer> repeated(List<String> lines, Collection<String> game) {
		List<Integer> places = new ArrayList<>();
		// the game writes them in one block right under the name, each once, and the lore comes after, which can begin with the same words
		List<String> left = new ArrayList<>(game);
		int end = 1;
		while (end < lines.size() && left.remove(lines.get(end))) {
			end++;
		}
		for (int i = end - 1; i >= 1; i--) {
			if (inLore(lines, end, lines.get(i))) {
				places.add(i);
			}
		}
		return places;
	}

	/** @return whether a line from {@code from} on lists the enchant, alone or among others, maybe with a number after it */
	private static boolean inLore(List<String> lines, int from, String enchant) {
		for (int i = from; i < lines.size(); i++) {
			for (String part : lines.get(i).strip().split(LORE_SEPARATOR)) {
				if (part.equals(enchant) || part.startsWith(enchant + " ")) {
					return true;
				}
			}
		}
		return false;
	}
}
