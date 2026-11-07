package dev.tim1337.lumio.skyblock.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * What a dungeon item's own data says about where it comes from. Only what the item carries: its quality from {@code baseStatBoostPercentage}. The floor is not read yet:
 * how the data tells a Floor from a Master item has to be seen on real items first, and until then it stays a dash.
 */
final class DungeonData {
	private static final String QUALITY = "baseStatBoostPercentage";
	/** The most quality an item can have, as the game shows it. */
	static final int MOST_QUALITY = 50;
	/** What the debug line lists: the fields of an item's data that can say anything about its floor. */
	private static final String[] FIELD_WORDS = {"tier", "floor", "boost", "dungeon", "upgrade", "star"};

	private DungeonData() {
	}

	/** @return the quality the item's own data has, within its bounds, or nothing for an item without one */
	static OptionalInt quality(CompoundTag data) {
		if (!data.contains(QUALITY)) {
			return OptionalInt.empty();
		}
		int quality = data.getInt(QUALITY).orElse(-1);
		return quality < 0 || quality > MOST_QUALITY ? OptionalInt.empty() : OptionalInt.of(quality);
	}

	/** @return the floor the item was obtained on, as far as it is known: not yet */
	static String floor(CompoundTag data) {
		return TooltipLine.MISSING;
	}

	/** @return the fields of the item's data that can say where it comes from, in one line to copy; "" for an item that has none */
	static String debugLine(CompoundTag data) {
		StringBuilder line = new StringBuilder();
		for (String key : data.keySet().stream().sorted().toList()) {
			String lower = key.toLowerCase(Locale.ROOT);
			for (String word : FIELD_WORDS) {
				if (lower.contains(word)) {
					line.append(line.isEmpty() ? "" : " ").append(key).append('=').append(data.get(key));
					break;
				}
			}
		}
		return line.toString();
	}
}
