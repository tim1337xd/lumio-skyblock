package dev.tim1337.lumio.skyblock.data;

/**
 * Reads roman numbers as the game writes them (enchant levels, floors). The parsers differ in what they do with a character that is no
 * numeral, so there is one method for each way. Both read from the right and subtract a smaller numeral in front of a bigger one, without
 * checking that the number is well formed.
 */
public final class Roman {
	private Roman() {
	}

	/**
	 * Reads I, V, X, L and C.
	 *
	 * @param whenInvalid what to return when a character is none of them
	 * @return the number's value (0 for an empty text), or {@code whenInvalid}
	 */
	public static int strict(String numeral, int whenInvalid) {
		int total = 0;
		int last = 0;
		for (int i = numeral.length() - 1; i >= 0; i--) {
			int value = switch (numeral.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> 0;
			};
			if (value == 0) {
				return whenInvalid;
			}
			total += value < last ? -value : value;
			last = Math.max(last, value);
		}
		return total;
	}

	/** @return the value of a small roman number of I, V and X; every other character counts as nothing, so a text without them is 0 */
	public static int ofIvx(String numeral) {
		int total = 0;
		int last = 0;
		for (int i = numeral.length() - 1; i >= 0; i--) {
			int value = switch (numeral.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				default -> 0;
			};
			total += value < last ? -value : value;
			last = Math.max(last, value);
		}
		return total;
	}
}
