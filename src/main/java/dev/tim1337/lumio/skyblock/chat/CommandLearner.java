package dev.tim1337.lumio.skyblock.chat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a subcommand from a line of a help or usage message: "/party kick &lt;player&gt; - Removes them", "Usage: /ignore add &lt;player&gt;". Plain text, so it is unit tested. */
final class CommandLearner {
	private static final String ARGS = "((?: [<\\[][^<>\\[\\]]{1,24}[>\\]])*)";
	private static final String COMMAND = "/([a-zA-Z]{1,20}) ([a-zA-Z0-9_-]{1,24})" + ARGS;
	private static final Pattern HELP_LINE = Pattern.compile("^" + COMMAND + "(?:\\s*[-:\\u00BB\\u2013\\u2014].*)?$");
	private static final Pattern USAGE_LINE = Pattern.compile("^.*?[Uu]sage:? *['\"]?" + COMMAND + "['\"]?.*$");

	/** A subcommand as the game showed it. */
	record Learned(String command, String sub, String args) {
	}

	private CommandLearner() {
	}

	/** @return the subcommand a line shows, or null */
	static Learned read(String line) {
		Matcher matcher = HELP_LINE.matcher(line);
		if (!matcher.matches()) {
			matcher = USAGE_LINE.matcher(line);
			if (!matcher.matches()) {
				return null;
			}
		}
		return new Learned(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).toLowerCase(Locale.ROOT), matcher.group(3).strip());
	}
}
