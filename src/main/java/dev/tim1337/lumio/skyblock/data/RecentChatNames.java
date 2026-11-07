package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names of who wrote in chat lately, the newest first, for the command suggestions. Only the sender in front of a chat line is read, never what it says; kept in memory only and looked up nowhere. Plain text, so it is unit tested.
 */
public final class RecentChatNames {
	public static final RecentChatNames INSTANCE = new RecentChatNames();
	private static final int MOST = 64;
	private static final Pattern SENDER = Pattern.compile("^(?:(?:Party|Guild|Officer|Co-op) > |From |To )?(?:\\[\\d{1,4}] )?(?:\\S{1,2} )?(?:\\[[A-Za-z+]{1,12}] )?([A-Za-z0-9_]{3,16})(?: \\[[^]]{1,12}])?: ");

	/** Oldest first; a name written again moves to the end. */
	private final LinkedHashSet<String> names = new LinkedHashSet<>();

	/** @return the sender in front of a chat line without colors, or null when it has none */
	static String senderOf(String line) {
		Matcher matcher = SENDER.matcher(line);
		return matcher.find() ? matcher.group(1) : null;
	}

	/** Reads a chat line without colors. */
	public synchronized void read(String line) {
		String sender = senderOf(line);
		if (sender == null) {
			return;
		}
		names.remove(sender);
		names.add(sender);
		if (names.size() > MOST) {
			names.remove(names.iterator().next());
		}
	}

	/** @return the names, the newest first, without the player's own */
	public synchronized List<String> newestFirst(String own) {
		List<String> newest = new ArrayList<>(names.size());
		for (String name : names) {
			if (!name.toLowerCase(Locale.ROOT).equals(own.toLowerCase(Locale.ROOT))) {
				newest.addFirst(name);
			}
		}
		return newest;
	}

	/** Forgets all: another server has other players. */
	public synchronized void clear() {
		names.clear();
	}
}
