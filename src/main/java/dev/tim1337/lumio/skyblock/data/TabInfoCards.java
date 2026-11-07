package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ScoreLines;
import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.experimental.util.ServerText;
import dev.tim1337.lumio.api.experimental.util.TabSections;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The info columns of SkyBlock's player list read as cards: every section the server writes (a bold heading such as "Area:" followed by its
 * lines) becomes a card with its rows, a label on the left and the value on the right. Sections it does not know become plain cards with their
 * lines; nothing is dropped except the columns' own headings ("Players (21)", "Info"), which the panel shows in its own way.
 */
public final class TabInfoCards {
	/** The most cards and rows per card read, so a strange list cannot grow the panel without end. */
	private static final int MOST_CARDS = 12;
	private static final int MOST_ROWS = 10;
	/** The heading of the players column: "Players (21)". */
	private static final Pattern PLAYERS_HEADING = Pattern.compile("^Players\\s*\\(\\d{1,4}\\)$", Pattern.CASE_INSENSITIVE);
	/** The headings of the info columns, which hold no information of their own. */
	private static final Set<String> COLUMN_HEADINGS = Set.of("info", "account info", "server info", "player info", "players");
	/** A percentage at the end of a value ("35.2%"), the progress of a skill. */
	private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,2})?)%\\s*$");

	private TabInfoCards() {
	}

	/** @return the cards of the lines, in their order */
	public static List<TabCard> read(List<Component> lines) {
		return read(lines, Debug.on());
	}

	/** @param log whether what the debug switch asks for is logged */
	static List<TabCard> read(List<Component> lines, boolean log) {
		List<TabCard> cards = new ArrayList<>();
		String title = null;
		String namesTitle = null;
		List<TabCard.Row> rows = null;
		for (Component line : lines) {
			String raw = line.getString();
			String plain = raw.strip();
			if (plain.isEmpty()) {
				// a blank line ends a section
				namesTitle = null;
				if (rows != null && !rows.isEmpty()) {
					cards.add(new TabCard(title, rows));
				}
				title = null;
				rows = null;
				continue;
			}
			if (PLAYERS_HEADING.matcher(plain).matches() || COLUMN_HEADINGS.contains(plain.toLowerCase(Locale.ROOT))) {
				continue;
			}
			boolean nameSection = TabSections.isNameSection(plain);
			if (nameSection) {
				namesTitle = plain;
			} else if (namesTitle != null && log) {
				logNames(namesTitle, raw);
			}
			if (heading(line, raw) || nameSection) {
				if (!nameSection) {
					namesTitle = null;
				}
				if (rows != null && !rows.isEmpty()) {
					cards.add(new TabCard(title, rows));
				}
				int colon = plain.indexOf(':');
				title = colon > 0 ? plain.substring(0, colon).strip() : plain;
				rows = new ArrayList<>();
				if (colon > 0 && colon < plain.length() - 1 && !nameSection) {
					// "Area: Dwarven Mines" is the card AREA with the row Area | Dwarven Mines
					Component rest = trimmed(ServerText.slice(line, raw.indexOf(':') + 1, raw.length()));
					String restText = rest.getString();
					if (restText.contains(": ")) {
						rows.add(row(rest, title, log));
					} else {
						rows.add(new TabCard.Row(Component.literal(title), rest, progress(title, restText)));
					}
				}
			} else {
				if (rows == null) {
					title = "";
					rows = new ArrayList<>();
				}
				if (rows.size() < MOST_ROWS) {
					rows.add(row(line, title, log));
				}
			}
			if (cards.size() >= MOST_CARDS) {
				break;
			}
		}
		if (rows != null && !rows.isEmpty() && cards.size() < MOST_CARDS) {
			cards.add(new TabCard(title, rows));
		}
		// the Stats card holds only stats: what the server writes after them without a heading of its own goes to Other
		return TabStats.sorted(cards, log);
	}

	/** @return whether a line starts a section: it does not start with a space and its first text is bold */
	private static boolean heading(Component line, String raw) {
		if (raw.startsWith(" ")) {
			return false;
		}
		boolean[] bold = {false};
		boolean[] seen = {false};
		line.visit((style, part) -> {
			if (!seen[0] && !part.isBlank()) {
				seen[0] = true;
				bold[0] = style.isBold();
			}
			return Optional.empty();
		}, Style.EMPTY);
		return bold[0];
	}

	/** Under the debug switch, once per text: the raw lines of a section that lists names, to send when a form is still missed. */
	private static void logNames(String section, String raw) {
		if (LOGGED.size() < MOST_LOGGED && LOGGED.add(section + raw)) {
			Debug.log("Tab list section {}: {} = {}", section, raw, ScoreLines.codes(raw));
		}
	}

	/** Under the debug switch, once per text: a line with a character that is not plain text, as the server sends it, for what the font may not have. */
	private static void logSymbols(String raw) {
		if (!raw.chars().allMatch(c -> c >= ' ' && c < 0x7F && c != ';') && LOGGED.size() < MOST_LOGGED && LOGGED.add(raw)) {
			Debug.log("Tab list line with symbols: {} = {}", raw, ScoreLines.codes(raw));
		}
	}

	private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();
	private static final int MOST_LOGGED = 200;

	/** @return a row from a line: label and value split at ": ", or the whole line as the label */
	private static TabCard.Row row(Component line, String title, boolean log) {
		String raw = line.getString();
		if (log) {
			logSymbols(raw);
		}
		TabCard.Row vote = VoteBars.row(line, raw);
		if (vote != null) {
			return vote;
		}
		int colon = raw.lastIndexOf(": ");
		if (colon <= 0) {
			return new TabCard.Row(trimmed(line), null, -1f);
		}
		Component label = trimmed(ServerText.slice(line, 0, colon));
		Component value = trimmed(ServerText.slice(line, colon + 2, raw.length()));
		return new TabCard.Row(label, value, progress(title, value.getString()));
	}

	/** @return a skill's progress from its percentage, or -1 for other rows */
	private static float progress(String title, String value) {
		if (title == null || !title.toLowerCase(Locale.ROOT).contains("skill")) {
			return -1f;
		}
		var matcher = PERCENT.matcher(value);
		if (!matcher.find()) {
			return -1f;
		}
		try {
			return Math.max(0f, Math.min(1f, Float.parseFloat(matcher.group(1)) / 100f));
		} catch (NumberFormatException e) {
			return -1f;
		}
	}

	/** @return the line without spaces at its start and end, keeping its styles */
	private static Component trimmed(Component line) {
		String raw = line.getString();
		int start = 0;
		int end = raw.length();
		while (start < end && Character.isWhitespace(raw.charAt(start))) {
			start++;
		}
		while (end > start && Character.isWhitespace(raw.charAt(end - 1))) {
			end--;
		}
		return start == 0 && end == raw.length() ? line : ServerText.slice(line, start, end);
	}

}
