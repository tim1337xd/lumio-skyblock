package dev.tim1337.lumio.skyblock.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guild members and friends seen, for the command suggestions: only from what the game's messages show (someone coming online, the lists of /g online and /f list), kept locally and
 * never sent anywhere. Plain text, so it is unit tested.
 */
public final class SocialNames {
	private static final int MOST_NAMES = 500;
	private static final String RANKED_NAME = "(?:\\[[^\\]]{1,20}\\] )?([A-Za-z0-9_]{1,16})";
	private static final Pattern GUILD_STATUS = Pattern.compile("^Guild > ([A-Za-z0-9_]{1,16}) (?:joined|left)\\.$");
	private static final Pattern GUILD_GONE = Pattern.compile("^" + RANKED_NAME + " (?:left the guild!|was kicked from the guild.*)$");
	private static final Pattern FRIEND_STATUS = Pattern.compile("^Friend > ([A-Za-z0-9_]{1,16}) (?:joined|left)\\.$");
	private static final Pattern FRIEND_GONE = Pattern.compile("^(?:You removed |Removed )" + RANKED_NAME + " from your friends list!?$");
	private static final Pattern FRIEND_HEADER = Pattern.compile("^(?:<< )?Friends \\(Page \\d{1,3} of \\d{1,3}\\)(?: >>)?$");
	private static final Pattern FRIEND_LINE = Pattern.compile("^" + RANKED_NAME + " is (?:in|currently|idle) ?.*$");
	private static final Pattern ANY_NAME = Pattern.compile("^" + RANKED_NAME + "$");
	/** The dot between the names of /g online and /g list. */
	private static final String ONLINE_DOT = "●";
	/** How many lines after its header the list of /f list runs. */
	private static final int FRIEND_LIST_LINES = 12;

	private final Set<String> guild = new LinkedHashSet<>();
	private final Set<String> friends = new LinkedHashSet<>();
	private int friendLinesLeft;

	/** Reads one plain line of a server message. */
	public void read(String line) {
		if (line.isEmpty()) {
			return;
		}
		Matcher matcher;
		if ((matcher = GUILD_STATUS.matcher(line)).matches()) {
			add(guild, matcher.group(1));
		} else if ((matcher = GUILD_GONE.matcher(line)).matches()) {
			guild.remove(matcher.group(1));
		} else if ((matcher = FRIEND_STATUS.matcher(line)).matches()) {
			add(friends, matcher.group(1));
		} else if ((matcher = FRIEND_GONE.matcher(line)).matches()) {
			friends.remove(matcher.group(1));
		} else if (FRIEND_HEADER.matcher(line).matches()) {
			friendLinesLeft = FRIEND_LIST_LINES;
		} else if (friendLinesLeft > 0) {
			friendLinesLeft--;
			if ((matcher = FRIEND_LINE.matcher(line)).matches()) {
				add(friends, matcher.group(1));
			}
		} else if (line.contains(ONLINE_DOT)) {
			readOnlineLine(line);
		}
	}

	/** A line of /g online: every part between the dots is a name, or it is not such a line. */
	private void readOnlineLine(String line) {
		String[] parts = line.split(ONLINE_DOT);
		for (String part : parts) {
			if (!part.isBlank() && !ANY_NAME.matcher(part.strip()).matches()) {
				return;
			}
		}
		for (String part : parts) {
			Matcher matcher = ANY_NAME.matcher(part.strip());
			if (matcher.matches()) {
				add(guild, matcher.group(1));
			}
		}
	}

	private static void add(Set<String> names, String name) {
		if (names.size() < MOST_NAMES) {
			names.add(name);
		}
	}

	public Collection<String> guild() {
		return guild;
	}

	public Collection<String> friends() {
		return friends;
	}

	/** Takes the names kept from before. */
	public void keep(String[] guildNames, String[] friendNames) {
		guild.addAll(Arrays.asList(guildNames));
		friends.addAll(Arrays.asList(friendNames));
	}
}
