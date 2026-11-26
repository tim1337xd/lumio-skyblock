package dev.tim1337.lumio.skyblock.data;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The server a SkyBlock lobby runs on, as the scoreboard's first line shows it after the date: "11/12/26 m14AB" gives "m14AB". Saved data that
 * belongs to one lobby (such as the places found in a Crystal Hollows lobby) is kept under it. Plain text, so it is unit tested.
 */
public final class LobbyId {
	/** The date and the server: "11/12/26 m14AB", "11/12/26 mini22BC". */
	private static final Pattern DATE_AND_SERVER = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+([mM][A-Za-z0-9]{1,12})\\b");

	private LobbyId() {
	}

	/** @return the lobby's server in the scoreboard lines (without colors), or "" when none shows */
	public static String of(List<String> sidebar) {
		for (String line : sidebar) {
			Matcher matcher = DATE_AND_SERVER.matcher(line.strip());
			if (matcher.find()) {
				return matcher.group(1);
			}
		}
		return "";
	}
}
