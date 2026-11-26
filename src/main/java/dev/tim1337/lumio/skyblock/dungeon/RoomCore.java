package dev.tim1337.lumio.skyblock.dungeon;

/**
 * The fingerprint a room is known by: one column of blocks in the middle of a tile, from high above the room down to under its floor, written out
 * as text and hashed. It is the form the bundled room data's fingerprints were made with (that data is Odin's, BSD-3-Clause, see CREDITS.md), so it
 * must come out the same to the last character. Plain logic over a column given to it, so it is tested without the game.
 */
final class RoomCore {
	/** What a layer of the column is, as far as the fingerprint cares. */
	enum Layer {
		/** Nothing: air above the room is a zero, inside it the block's name. */
		EMPTY,
		/** A gold block: above the room it counts as nothing, like air. */
		GOLD,
		/** Bedrock: two of it with empty space under it, low enough, is the bottom of the room. */
		BEDROCK,
		/** Planks and chests, which differ between runs: left out. */
		LEFT_OUT,
		/** Any other block. */
		BLOCK
	}

	/** A column of layers, from its top down. */
	interface Column {
		Layer layer(int y);

		/** @return the block's name as the fingerprint writes it ("Block{minecraft:stone}") */
		String name(int y);
	}

	static final int TOP = 140;
	static final int BOTTOM = 12;
	/** Empty space under bedrock ends the column only below this height. */
	private static final int UNDER_FLOOR = 69;
	/** The ending's zeros reach down to just above the bottom. */
	private static final int ZEROS_BELOW = BOTTOM - 1;
	private static final int BEDROCK_FLOOR = 2;

	private RoomCore() {
	}

	/**
	 * @param roofOut receives the height of the room's highest block, or 0 where the column is empty
	 * @return the column written out
	 */
	static String text(Column column, int[] roofOut, StringBuilder into) {
		into.setLength(0);
		roofOut[0] = 0;
		int y = TOP;
		// the open air (and gold) above the room: a zero for each layer
		while (y >= BOTTOM && isOpen(column.layer(y))) {
			into.append('0');
			y--;
		}
		if (y < BOTTOM) {
			return into.toString();
		}
		roofOut[0] = y;
		int bedrockBelow = 0;
		for (; y >= BOTTOM; y--) {
			Layer layer = column.layer(y);
			if (layer == Layer.EMPTY && bedrockBelow >= BEDROCK_FLOOR && y < UNDER_FLOOR) {
				// the bottom of the room: the rest of the column is written as zeros
				into.repeat('0', y - ZEROS_BELOW);
				break;
			}
			bedrockBelow = layer == Layer.BEDROCK ? bedrockBelow + 1 : 0;
			if (layer != Layer.LEFT_OUT) {
				into.append(column.name(y));
			}
		}
		return into.toString();
	}

	private static boolean isOpen(Layer layer) {
		return layer == Layer.EMPTY || layer == Layer.GOLD;
	}
}
