package dev.tim1337.lumio.skyblock.hud;

import java.util.Locale;

/**
 * The small calculator of the item search: a search such as {@code 64*3} or {@code 1.2m/5} shows its result in the field. Only numbers, + − × ÷,
 * brackets and the suffixes k, m and b; anything else is no calculation. Plain numbers, so it is unit tested.
 */
final class SearchMath {
	/** The longest input worked out, and the deepest brackets. */
	private static final int MOST_LENGTH = 60;
	private static final int MOST_DEPTH = 16;
	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double BILLION = 1_000_000_000d;
	/** Results this large are not shown. */
	private static final double MOST_RESULT = 1e15;

	private final String text;
	private int at;
	private int depth;
	/** Whether the input has an operator or a suffix: a plain number is a search, not a calculation. */
	private boolean calculated;

	private SearchMath(String text) {
		this.text = text;
	}

	/** @return the result of a calculation, or NaN when the text is none (or can't be worked out) */
	static double evaluate(String input) {
		if (input == null || input.isBlank() || input.length() > MOST_LENGTH) {
			return Double.NaN;
		}
		SearchMath math = new SearchMath(input.strip().toLowerCase(Locale.ROOT));
		try {
			double value = math.sum();
			math.skipSpaces();
			if (math.at != math.text.length() || !math.calculated || !Double.isFinite(value) || Math.abs(value) > MOST_RESULT) {
				return Double.NaN;
			}
			return value;
		} catch (IllegalArgumentException notMath) {
			return Double.NaN;
		}
	}

	/** @return the result as shown in the field: "= 192", "= 240,000", "= 0.25" */
	static String shown(double value) {
		double rounded = Math.round(value * 100d) / 100d;
		if (rounded == Math.rint(rounded)) {
			return "= " + String.format(Locale.ROOT, "%,d", (long) rounded);
		}
		String text = String.format(Locale.ROOT, "%,.2f", rounded);
		return "= " + (text.endsWith("0") ? text.substring(0, text.length() - 1) : text);
	}

	private double sum() {
		double value = product();
		while (true) {
			skipSpaces();
			char next = peek();
			if (next == '+') {
				at++;
				calculated = true;
				value += product();
			} else if (next == '-' || next == '−') {
				at++;
				calculated = true;
				value -= product();
			} else {
				return value;
			}
		}
	}

	private double product() {
		double value = unary();
		while (true) {
			skipSpaces();
			char next = peek();
			if (next == '*' || next == '×' || next == 'x') {
				at++;
				calculated = true;
				value *= unary();
			} else if (next == '/' || next == '÷' || next == ':') {
				at++;
				calculated = true;
				double divisor = unary();
				if (divisor == 0) {
					throw new IllegalArgumentException("division by zero");
				}
				value /= divisor;
			} else {
				return value;
			}
		}
	}

	private double unary() {
		skipSpaces();
		char next = peek();
		if (next == '-' || next == '−') {
			at++;
			return -unary();
		}
		if (next == '+') {
			at++;
			return unary();
		}
		return atom();
	}

	private double atom() {
		skipSpaces();
		if (peek() == '(') {
			if (++depth > MOST_DEPTH) {
				throw new IllegalArgumentException("too deep");
			}
			at++;
			double value = sum();
			skipSpaces();
			if (peek() != ')') {
				throw new IllegalArgumentException("unclosed bracket");
			}
			at++;
			depth--;
			calculated = true;
			return value;
		}
		return number();
	}

	private double number() {
		int start = at;
		boolean point = false;
		while (at < text.length()) {
			char c = text.charAt(at);
			if (c >= '0' && c <= '9') {
				at++;
			} else if ((c == '.' || c == ',') && !point) {
				point = true;
				at++;
			} else {
				break;
			}
		}
		if (at == start || at == start + 1 && point) {
			throw new IllegalArgumentException("no number");
		}
		double value = Double.parseDouble(text.substring(start, at).replace(',', '.'));
		char suffix = peek();
		double factor = suffix == 'k' ? THOUSAND : suffix == 'm' ? MILLION : suffix == 'b' ? BILLION : 1d;
		if (factor != 1d) {
			at++;
			calculated = true;
		}
		return value * factor;
	}

	private char peek() {
		return at < text.length() ? text.charAt(at) : '\0';
	}

	private void skipSpaces() {
		while (at < text.length() && text.charAt(at) == ' ') {
			at++;
		}
	}
}
