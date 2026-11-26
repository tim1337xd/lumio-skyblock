package dev.tim1337.lumio.skyblock.mining;

import java.util.List;

/**
 * The player's mining powders as the tab list's "Powders" widget shows them ("Mithril: 1,234,567"). The last value read of each powder is kept
 * while the widget is missing (another island, the widget switched off), and a powder never read stays unknown. Plain text, so it is unit tested.
 */
final class PowderTab {
	/** A powder: its name in the widget and its color in the game. */
	enum Powder {
		MITHRIL("Mithril", 0x00AA00),
		GEMSTONE("Gemstone", 0xFF55FF),
		GLACITE("Glacite", 0x55FFFF);

		final String label;
		final int rgb;
		/** "Mithril:", the start of its line, made once. */
		private final String prefix;

		Powder(String label, int rgb) {
			this.label = label;
			this.rgb = rgb;
			this.prefix = label + ":";
		}
	}

	/** A value of a powder not read yet. */
	static final long UNKNOWN = -1;
	/** The most digits an amount may have (with its separators), so that no text can make a number too large to hold. */
	private static final int MOST_CHARS = 18;

	private final long[] amounts = {UNKNOWN, UNKNOWN, UNKNOWN};

	/**
	 * Reads the powders from the player list lines (without colors); a powder the lines do not show keeps its last value.
	 *
	 * @return whether a value changed
	 */
	boolean read(List<String> lines) {
		boolean changed = false;
		for (String raw : lines) {
			String line = raw.strip();
			for (Powder powder : Powder.values()) {
				if (line.startsWith(powder.prefix)) {
					long amount = amount(line.substring(powder.prefix.length()));
					if (amount != UNKNOWN && amounts[powder.ordinal()] != amount) {
						amounts[powder.ordinal()] = amount;
						changed = true;
					}
					break;
				}
			}
		}
		return changed;
	}

	/** @return a powder's amount, or {@link #UNKNOWN} while it was never read */
	long amount(Powder powder) {
		return amounts[powder.ordinal()];
	}

	/** @return whether any powder was read at least once */
	boolean anyKnown() {
		for (long amount : amounts) {
			if (amount != UNKNOWN) {
				return true;
			}
		}
		return false;
	}

	/** @return an amount as the widget writes it, with or without thousands separators ("1,234,567", "1234567"), or {@link #UNKNOWN} */
	static long amount(String text) {
		String value = text.strip();
		if (value.isEmpty() || value.length() > MOST_CHARS) {
			return UNKNOWN;
		}
		long amount = 0;
		boolean digit = false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c >= '0' && c <= '9') {
				amount = amount * 10 + (c - '0');
				digit = true;
			} else if (c != ',') {
				return UNKNOWN;
			}
		}
		return digit ? amount : UNKNOWN;
	}
}
