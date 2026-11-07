package dev.tim1337.lumio.skyblock.data;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses SkyBlock durations ("1h 3m", "12m 5s", "45s", "3:20") into milliseconds, and formats them back as a clock. */
public final class SkyBlockTime {
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*([hms])", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

	private SkyBlockTime() {
	}

	/** @return the duration in the text in milliseconds, or -1 if there is none */
	public static long parse(String text) {
		if (text == null) {
			return -1;
		}
		Matcher clock = CLOCK.matcher(text);
		if (clock.find()) {
			long a = Long.parseLong(clock.group(1));
			long b = Long.parseLong(clock.group(2));
			return clock.group(3) == null ? (a * 60 + b) * 1000L : ((a * 60 + b) * 60 + Long.parseLong(clock.group(3))) * 1000L;
		}
		Matcher part = PART.matcher(text);
		long total = 0;
		boolean any = false;
		while (part.find()) {
			any = true;
			long value = Long.parseLong(part.group(1));
			total += switch (part.group(2).toLowerCase(Locale.ROOT)) {
				case "h" -> value * 3_600_000L;
				case "m" -> value * 60_000L;
				default -> value * 1000L;
			};
		}
		return any ? total : -1;
	}

	/** @return a duration formatted as a clock: 1:05:20, 4:07, 0:09 */
	public static String clock(long millis) {
		long seconds = Math.max(0, (millis + 999) / 1000);
		long hours = seconds / 3600;
		long minutes = seconds / 60 % 60;
		return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60) : String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
	}
}
