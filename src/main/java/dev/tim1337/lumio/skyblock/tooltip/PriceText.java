package dev.tim1337.lumio.skyblock.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

/** How the values of the added lines read. A value that is not known is a dash, never a made-up number. Plain text, so it is unit tested. */
final class PriceText {
	/** How numbers are written. */
	enum Style {
		SHORT,
		FULL
	}

	private static final double THOUSAND = 1_000d;
	private static final double MILLION = 1_000_000d;
	private static final double BILLION = 1_000_000_000d;
	private static final String MINUS = "−";
	private static final long MINUTE_MILLIS = 60_000L;
	private static final long HOUR_MINUTES = 60L;

	private PriceText() {
	}

	/** @return whether a value is one that can be shown */
	static boolean known(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0d;
	}

	/** @return a number as "4.20M", "258.4k" and "950", or as "4,200,000" */
	static String number(double value, Style style) {
		if (!known(value)) {
			return TooltipLine.MISSING;
		}
		if (style == Style.FULL) {
			return grouped(Math.round(value));
		}
		if (value >= BILLION) {
			return decimals(value / BILLION, 2) + "B";
		}
		if (value >= MILLION) {
			return decimals(value / MILLION, 2) + "M";
		}
		if (value >= THOUSAND) {
			return decimals(value / THOUSAND, 1) + "k";
		}
		return value == Math.rint(value) ? Long.toString((long) value) : decimals(value, 1);
	}

	private static String decimals(double value, int places) {
		long factor = places == 2 ? 100L : 10L;
		long scaled = Math.round(value * factor);
		String fraction = Long.toString(scaled % factor);
		while (fraction.length() < places) {
			fraction = "0" + fraction;
		}
		return scaled / factor + "." + fraction;
	}

	private static String grouped(long value) {
		String digits = Long.toString(value);
		StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				out.append(',');
			}
			out.append(digits.charAt(i));
		}
		return out.toString();
	}

	/** @return a change as "+6 %" or "−8 %" */
	static String percent(double change) {
		if (Double.isNaN(change) || Double.isInfinite(change)) {
			return TooltipLine.MISSING;
		}
		long rounded = Math.round(change);
		return (rounded < 0 ? MINUS : "+") + Math.abs(rounded) + " %";
	}

	/** @return how long ago something was fetched: "just now", "2 min ago", "3 h ago" */
	static String age(long millis) {
		long minutes = Math.max(0L, millis) / MINUTE_MILLIS;
		if (minutes < 1L) {
			return "just now";
		}
		return minutes < HOUR_MINUTES ? minutes + " min ago" : minutes / HOUR_MINUTES + " h ago";
	}
}
